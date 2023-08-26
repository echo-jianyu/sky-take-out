package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时未付款订单
     */
    @Scheduled(cron = "0 * * * * ? ")  //每分钟执行一次
//    @Scheduled(cron = "1/10 * * * * ?")
    public void processTimeoutOrder() {
        LocalDateTime now = LocalDateTime.now();
        log.info("定时处理超时订单：{}", now);

        //当前超时订单时间
        LocalDateTime timeout = now.plusMinutes(-15);  //15分钟前
        //查询15分钟前的未付款订单

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, timeout);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orderDB : ordersList) {
                //将订单修改为取消状态
                Orders orders = Orders.builder()
                        .id(orderDB.getId())
                        .status(Orders.CANCELLED)
                        .cancelReason("超时未支付自动取消")
                        .cancelTime(now)
                        .build();
                //更新数据库

                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ? ")  //每天凌晨1点触发一次
//    @Scheduled(cron = "0/20 * * * * ?")
    public void processDeliveryOrder(){
        LocalDateTime now = LocalDateTime.now();
        log.info("定时处理派送中的订单：{}", now);

        //查询上一工作日处于派送中状态的订单
        LocalDateTime lastDay = now.plusHours(-1);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, lastDay);
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orderDB : ordersList) {
                //将订单修改为完成状态
                Orders orders = Orders.builder()
                        .id(orderDB.getId())
                        .status(Orders.COMPLETED)
                        .build();
                //更新数据库
                orderMapper.update(orders);
            }
        }


    }


}
