package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间段内的营业额
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTrunoverSTatistics(LocalDate begin, LocalDate end) {
        //构建返回日期的集合
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        //将begin到end的日期加到dateList中
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);  //加一天
            //放入dateList中
            dateList.add(begin);
        }

        //构建返回营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //当天开始、结束时间
            LocalDateTime beginDateTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endDateTime = LocalDateTime.of(date, LocalTime.MAX);
            //封装查询条件Map
            Map map = new HashMap();
            map.put("begin", beginDateTime);
            map.put("end", endDateTime);
            map.put("status", Orders.COMPLETED);
            //查询对应日期的营业额：“已完成”状态订单的金额合计
            Double turnover = orderMapper.sumByMap(map);
            turnover = (turnover == null) ? 0.0 : turnover;  //当天订单数为0，返回null。赋值0.0
            turnoverList.add(turnover);
        }

        //组装返回数据
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();

        return turnoverReportVO;
    }

    /**
     * 统计用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserSTatistics(LocalDate begin, LocalDate end) {
        //构建返回日期的集合
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        //将begin到end的日期加到dateList中
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);  //加一天
            //放入dateList中
            dateList.add(begin);
        }

        //存放每天用户总量
        List<Integer> totalUserList = new ArrayList<>();
        //存放每天新增用户量
        List<Integer> newUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //当天开始时间
            LocalDateTime beginDateTime = LocalDateTime.of(begin, LocalTime.MIN);
            LocalDateTime endDateTime = LocalDateTime.of(end, LocalTime.MAX);

            //查询截止到endDateTime的总用户数量 create_time < endDateTime
            Map map = new HashMap();
            map.put("end", endDateTime);
            Integer totlaUserCount = userMapper.countByMap(map);
            totalUserList.add(totlaUserCount);

            //查询每天新增的用户总量  beginDateTIme < create_time < endDateTime
            map.put("begin", beginDateTime);
            Integer newUserCount = userMapper.countByMap(map);
            newUserList.add(newUserCount);
        }
        UserReportVO userReportVO = UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();

        return userReportVO;
    }
}
