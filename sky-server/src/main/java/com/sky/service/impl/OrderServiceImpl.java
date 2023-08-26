package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //判断地址是否为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //判断购物车是否为空
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //往orders表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());  //设置订单创建时间
        orders.setPayStatus(Orders.UN_PAID);  //设置订单支付状态（未支付）
        orders.setStatus(Orders.PENDING_PAYMENT); //设置订单状态（未付款）
        orders.setNumber(String.valueOf(System.currentTimeMillis()));  //设用当前系统时间作为订单号
        orders.setPhone(addressBook.getPhone());  //设置手机号码
        orders.setConsignee(addressBook.getConsignee()); //设置收货人
        orders.setUserId(BaseContext.getCurrentId());  //设置下单人的id
        orderMapper.insert(orders);

        //往order_detail表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {  //遍历购物车中的所有商品
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());  //设置关联orders的id
            orderDetailList.add(orderDetail);  //添加到OrderDetail集合中
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //清空当前用户的购物车
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());

        //封装返回对象VO
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;

    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
        //跳过调用微信支付，直接生成空的json
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        //通过webSocket向服务端后台推送信息 type orderId content
        //封装推送信息
        Map map = new HashMap();
        map.put("type", 1);   //type:1 来单提醒     type:2 客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + outTradeNo);
        //转为Json字符串
        String json = JSON.toJSONString(map);
        //推送
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 查询历史订单
     *
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(Integer page, Integer pageSize, Integer status) {
        PageHelper.startPage(page, pageSize);
        //条件分页查询
        //构建DTO对象
        OrdersPageQueryDTO ordersPageQueryDTO = OrdersPageQueryDTO.builder()
                .userId(BaseContext.getCurrentId())
                .status(status)
                .build();
        //查询
        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);
        //构建返回VO对象
        List<OrderVO> list = new ArrayList<>();
        //查询所有的历史订单明细
        if (ordersPage != null && ordersPage.size() > 0) {
            for (Orders orders : ordersPage) {
                //查询每个订单的详细信息
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO = new OrderVO();
                //将order信息拷贝给VO对象
//                log.info("copyProperties前：orders={}", orders);
//                log.info("copyProperties前：orderVO={}", orderVO);
                BeanUtils.copyProperties(orders, orderVO);
//                log.info("copyProperties后：orders={}", orders);
//                log.info("copyProperties后：orderVO={}", orderVO);
                //将订单详细信息放进OrderVO中
                orderVO.setOrderDetailList(orderDetailList);
                //将构建好的VO对象放到列表中
                list.add(orderVO);
            }
        }
        return new PageResult(ordersPage.getTotal(), list);
    }

    /**
     * 查询订单详细信息
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO detail(Long id) {
        OrderVO orderVO = new OrderVO();
        //获得Orders
        Orders orders = orderMapper.getById(id);

        //将Orders对象属性复制到OrderVO
        BeanUtils.copyProperties(orders, orderVO);
        //获得OrderDetail
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        //返回OrderVO
        return orderVO;
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    public void cencel(Long id) throws Exception {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        //订单不存在
        if (ordersDB == null) {
            //抛出异常
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //根据订单状态指向不同处置  //订单状态： 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            //抛出异常
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //正常取消订单
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        //订单处于“待接单”状态，需要进行退款
        if (Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            //调用微信支付退款接口
            //跳过退款
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(),  //商户订单号
//                    ordersDB.getNumber(),  //商户退款单号
//                    new BigDecimal(0.01),  //退款金额
//                    new BigDecimal(0.01)  //原订单金额
//            );
            orders.setPayStatus(Orders.REFUND);
        }

        //将订单设为已取消状态，设置取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //根据orderId查询当前订单的详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //创建ShoppingCart列表存放要加入购物车的数据
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        //遍历订单的每个商品
        orderDetailList.forEach(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            //将订单详细信息复制到购物车对象
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            //设置userId、创建时间
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            //插入到购物车列表中
            shoppingCartList.add(shoppingCart);
        });
        //插入数据库
        log.info("购物车内容：{}", shoppingCartList);
        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        //开启分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        //执行条件查询
        Page<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);

        //创建返回VO对象列表
        List<OrderVO> orderVOList = new ArrayList<>();
        ordersList.forEach(orders -> {
            //将Orders对象转换为VO对象
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            //查询关联菜品、套餐名称，并拼接成字符串
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
            String dishesName = new String();
            for (OrderDetail orderDetail : orderDetailList) {
                dishesName += orderDetail.getName();  //拼接名称
                dishesName += "*";
                dishesName += orderDetail.getNumber().toString();  //拼接数量
                dishesName += ", ";
            }
            dishesName = dishesName.substring(0, dishesName.length() - 2);  //去掉最后的“, "
            //设置菜品名称
            orderVO.setOrderDishes(dishesName);

            //放入list中
            orderVOList.add(orderVO);
        });

        return new PageResult(ordersList.getTotal(), orderVOList);
    }

    /**
     * 各个订单状态数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //查询待接单、待派送、派送中订单数量
        Integer toBeConfirmedCount = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmedCount = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgressCount = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        //创建返回对象
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmedCount);
        orderStatisticsVO.setConfirmed(confirmedCount);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgressCount);

        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //将订单改为接单状态
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        //更新数据库
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //查询订单状态
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (orderDB == null || !Orders.TO_BE_CONFIRMED.equals(orderDB.getStatus())) {
            //订单不存在，订单不是待接单状态
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            //订单已支付，调用微信支付退款接口
            //跳过退款
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(),  //商户订单号
//                    ordersDB.getNumber(),  //商户退款单号
//                    new BigDecimal(0.01),  //退款金额
//                    new BigDecimal(0.01)  //原订单金额
//            );
            log.info("拒单退款……");
        }
        //拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        //更新数据库
        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        //查询订单
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());
        if (orderDB == null) {
            //订单不存在
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //是否已支付
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            //订单已支付，调用微信支付退款接口
            //跳过退款
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(),  //商户订单号
//                    ordersDB.getNumber(),  //商户退款单号
//                    new BigDecimal(0.01),  //退款金额
//                    new BigDecimal(0.01)  //原订单金额
//            );
        }
        //修改订单状态
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        //查询订单
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null || !Orders.CONFIRMED.equals(orderDB.getStatus())) {
            //非已结单状态
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //将订单修改为派送中状态
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);

    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        //只有状态为“派送中”的订单可以执行订单完成操作
        //查询订单
        Orders orderDB = orderMapper.getById(id);
        if(orderDB == null || !Orders.DELIVERY_IN_PROGRESS.equals(orderDB.getStatus())){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //完成订单其实就是将订单状态修改为“已完成”
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }
}
