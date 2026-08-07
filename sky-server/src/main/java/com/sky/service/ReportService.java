package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * 数据统计业务接口
 */
public interface ReportService {

    /**
     * 统计指定日期范围内每天的营业额
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 营业额统计数据
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 统计指定日期范围内每天的新增用户数和用户总数
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 用户统计数据
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 统计指定日期范围内每天的订单数据
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 订单统计数据
     */
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    /**
     * 统计指定日期范围内的商品销量排名 Top10
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 销量排名数据
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出最近30天的运营数据报表
     *
     * @param response HTTP响应对象
     */
    void exportBusinessData(HttpServletResponse response);
}
