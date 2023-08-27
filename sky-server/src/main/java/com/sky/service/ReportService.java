package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {

    /**
     * 统计指定时间段内的营业额
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO getTrunoverSTatistics(LocalDate begin, LocalDate end);

    /**
     * 统计用户数据
     * @param begin
     * @param end
     * @return
     */
    UserReportVO getUserSTatistics(LocalDate begin, LocalDate end);

}