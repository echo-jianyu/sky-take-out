package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
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
import java.util.List;

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

        Orders orders1 = new Orders();
        orders1.setId(ordersDB.getId());
        orders1.setStatus(Orders.TO_BE_CONFIRMED);
        orders1.setPayStatus(Orders.PAID);
        orders1.setCheckoutTime(LocalDateTime.now());

        orderMapper.update(orders);
    }


    /**
     * 查询历史订单
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
        if(ordersPage != null && ordersPage.size() > 0){
            for (Orders orders : ordersPage) {
                //查询每个订单的详细信息
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO = new OrderVO();
                //将order信息拷贝给VO对象
                log.info("copyProperties前：orders={}", orders);
                log.info("copyProperties前：orderVO={}", orderVO);
                BeanUtils.copyProperties(orders, orderVO);
                log.info("copyProperties后：orders={}", orders);
                log.info("copyProperties后：orderVO={}", orderVO);
                //将订单详细信息放进OrderVO中
                orderVO.setOrderDetailList(orderDetailList);
                //将构建好的VO对象放到列表中
                list.add(orderVO);
            }
        }
        return new PageResult(ordersPage.getTotal(), list);
    }
}
