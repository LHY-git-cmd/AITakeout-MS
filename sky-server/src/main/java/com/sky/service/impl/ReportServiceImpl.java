package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定日期范围内每天的营业额
     * 营业额是指状态为“已完成”的订单金额合计
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 营业额统计数据
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //校验日期范围
        if (begin == null || end == null) {
            throw new OrderBusinessException("日期范围不能为空");
        }
        if (begin.isAfter(end)) {
            throw new OrderBusinessException("开始日期不能晚于结束日期");
        }

        List<LocalDate> dateList = new ArrayList<>();
        List<Double> turnoverList = new ArrayList<>();

        //遍历开始日期到结束日期，统计每天的营业额
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);

            //当天零点作为开始时间，次日零点作为结束时间
            LocalDateTime beginTime = date.atStartOfDay();
            LocalDateTime endTime = date.plusDays(1).atStartOfDay();

            //查询当天已完成订单的金额合计
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("begin", beginTime);
            queryMap.put("end", endTime);
            queryMap.put("status", Orders.COMPLETED);

            Double turnover = orderMapper.sumByMap(queryMap);
            turnoverList.add(turnover == null ? 0.0 : turnover);
        }

        //将日期和营业额分别拼接为逗号分隔的字符串
        String dates = dateList.stream()
                .map(LocalDate::toString)
                .collect(Collectors.joining(","));
        String turnovers = turnoverList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        //封装返回结果
        return TurnoverReportVO.builder()
                .dateList(dates)
                .turnoverList(turnovers)
                .build();
    }

    /**
     * 统计指定日期范围内每天的新增用户数和用户总数
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 用户统计数据
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //校验日期范围
        if (begin == null || end == null) {
            throw new OrderBusinessException("日期范围不能为空");
        }
        if (begin.isAfter(end)) {
            throw new OrderBusinessException("开始日期不能晚于结束日期");
        }

        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();

        //遍历开始日期到结束日期，统计每天的新增用户数和用户总数
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);

            //当天零点作为开始时间，次日零点作为结束时间
            LocalDateTime beginTime = date.atStartOfDay();
            LocalDateTime endTime = date.plusDays(1).atStartOfDay();

            //查询截至当天结束时的用户总数
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("end", endTime);
            Integer totalUser = userMapper.countByMap(queryMap);

            //增加开始时间条件，查询当天新增用户数
            queryMap.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(queryMap);

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }

        //将日期和用户数量分别拼接为逗号分隔的字符串
        String dates = dateList.stream()
                .map(LocalDate::toString)
                .collect(Collectors.joining(","));
        String totalUsers = totalUserList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String newUsers = newUserList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        //封装返回结果
        return UserReportVO.builder()
                .dateList(dates)
                .totalUserList(totalUsers)
                .newUserList(newUsers)
                .build();
    }

    /**
     * 统计指定日期范围内每天的订单总数和有效订单数
     * 有效订单是指状态为“已完成”的订单
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 订单统计数据
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //校验日期范围
        if (begin == null || end == null) {
            throw new OrderBusinessException("日期范围不能为空");
        }
        if (begin.isAfter(end)) {
            throw new OrderBusinessException("开始日期不能晚于结束日期");
        }

        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        //遍历开始日期到结束日期，统计每天的订单总数和有效订单数
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);

            LocalDateTime beginTime = date.atStartOfDay();
            LocalDateTime endTime = date.plusDays(1).atStartOfDay();

            Integer orderCount = getOrderCount(beginTime, endTime, null);
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //计算日期范围内的订单总数、有效订单数和订单完成率
        int totalOrderCount = orderCountList.stream().mapToInt(Integer::intValue).sum();
        int validOrderCount = validOrderCountList.stream().mapToInt(Integer::intValue).sum();
        double orderCompletionRate = totalOrderCount == 0
                ? 0.0
                : (double) validOrderCount / totalOrderCount;

        //将日期和每日订单数量分别拼接为逗号分隔的字符串
        String dates = dateList.stream()
                .map(LocalDate::toString)
                .collect(Collectors.joining(","));
        String orderCounts = orderCountList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String validOrderCounts = validOrderCountList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        //封装返回结果
        return OrderReportVO.builder()
                .dateList(dates)
                .orderCountList(orderCounts)
                .validOrderCountList(validOrderCounts)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 根据时间范围和订单状态统计订单数量
     *
     * @param begin 开始时间
     * @param end 结束时间
     * @param status 订单状态
     * @return 订单数量
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("begin", begin);
        queryMap.put("end", end);
        queryMap.put("status", status);
        return orderMapper.countByMap(queryMap);
    }

    /**
     * 统计指定日期范围内的商品销量排名 Top10
     * 仅统计状态为“已完成”订单中的商品
     *
     * @param begin 开始日期
     * @param end 结束日期
     * @return 销量排名数据
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        //校验日期范围
        if (begin == null || end == null) {
            throw new OrderBusinessException("日期范围不能为空");
        }
        if (begin.isAfter(end)) {
            throw new OrderBusinessException("开始日期不能晚于结束日期");
        }

        //结束时间使用结束日期的次日零点，确保包含结束日期全天数据
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        //分别拼接商品名称和销量
        String nameList = salesTop10.stream()
                .map(GoodsSalesDTO::getName)
                .collect(Collectors.joining(","));
        String numberList = salesTop10.stream()
                .map(GoodsSalesDTO::getNumber)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        //封装返回结果
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }
}
