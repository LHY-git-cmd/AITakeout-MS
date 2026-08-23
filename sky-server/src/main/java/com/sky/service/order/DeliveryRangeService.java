package com.sky.service.order;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.entity.AddressBook;
import com.sky.exception.OrderBusinessException;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 配送范围校验服务
 * 通过百度地图API解析地址坐标并计算配送距离，校验用户地址是否在配送范围内
 */
@Service
@Slf4j
public class DeliveryRangeService {

    /**
     * 商家门店地址
     */
    private final String shopAddress;

    /**
     * 百度地图AK（Access Key）
     */
    private final String baiduAk;

    /**
     * 最大配送距离（单位：米）
     */
    private final int maxDeliveryDistance;

    /**
     * 构造函数
     *
     * @param shopAddress        商家门店地址
     * @param baiduAk            百度地图AK
     * @param maxDeliveryDistance 最大配送距离
     */
    public DeliveryRangeService(@Value("${sky.shop.address:}") String shopAddress,
                                @Value("${sky.baidu.ak:}") String baiduAk,
                                @Value("${sky.delivery.max-distance:5000}") int maxDeliveryDistance) {
        this.shopAddress = shopAddress;
        this.baiduAk = baiduAk;
        this.maxDeliveryDistance = maxDeliveryDistance;
    }

    /**
     * 校验配送范围
     * 通过百度地图API计算商家与用户地址的距离，超出最大配送距离则抛出异常
     *
     * @param addressBook 用户地址簿
     */
    public void check(AddressBook addressBook) {
        if (baiduAk == null || baiduAk.isBlank()) {
            log.warn("未配置BAIDU_MAP_AK，跳过配送范围校验");
            return;
        }
        if (shopAddress == null || shopAddress.isBlank()) {
            throw new OrderBusinessException("未配置商家门店地址");
        }
        String userAddress = fullAddress(addressBook);
        // 解析商家地址坐标
        Map<String, String> params = new HashMap<>();
        params.put("address", shopAddress);
        params.put("output", "json");
        params.put("ak", baiduAk);
        String shopCoordinate = coordinate(parse(HttpClientUtil.doGet(
                "https://api.map.baidu.com/geocoding/v3", params), "店铺地址解析失败"));

        // 解析用户地址坐标
        params.put("address", userAddress);
        String userCoordinate = coordinate(parse(HttpClientUtil.doGet(
                "https://api.map.baidu.com/geocoding/v3", params), "收货地址解析失败"));

        // 调用路线规划API计算距离
        params.clear();
        params.put("origin", shopCoordinate);
        params.put("destination", userCoordinate);
        params.put("steps_info", "0");
        params.put("ak", baiduAk);
        JSONObject routeResult = parse(HttpClientUtil.doGet(
                "https://api.map.baidu.com/directionlite/v1/driving", params), "配送路线规划失败");
        JSONArray routes = routeResult.getJSONObject("result").getJSONArray("routes");
        if (routes == null || routes.isEmpty()) {
            throw new OrderBusinessException("未查询到可用配送路线");
        }
        Integer distance = routes.getJSONObject(0).getInteger("distance");
        if (distance == null) {
            throw new OrderBusinessException("配送距离解析失败");
        }
        if (distance > maxDeliveryDistance) {
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 拼接完整地址（省+市+区+详细地址）
     *
     * @param addressBook 地址簿
     * @return 完整地址字符串
     */
    public String fullAddress(AddressBook addressBook) {
        return safe(addressBook.getProvinceName()) + safe(addressBook.getCityName())
                + safe(addressBook.getDistrictName()) + safe(addressBook.getDetail());
    }

    /**
     * 解析百度地图API返回的JSON结果
     *
     * @param json         JSON字符串
     * @param errorMessage 错误提示信息
     * @return 解析后的JSONObject
     */
    private JSONObject parse(String json, String errorMessage) {
        if (json == null || json.isBlank()) throw new OrderBusinessException(errorMessage);
        JSONObject result = JSON.parseObject(json);
        if (!Integer.valueOf(0).equals(result.getInteger("status"))) {
            log.warn("百度地图接口调用失败：{}", result);
            throw new OrderBusinessException(errorMessage);
        }
        return result;
    }

    /**
     * 从地理编码结果中提取坐标字符串（lat,lng）
     *
     * @param result 地理编码结果
     * @return 坐标字符串
     */
    private String coordinate(JSONObject result) {
        JSONObject location = result.getJSONObject("result").getJSONObject("location");
        if (location == null || location.getString("lat") == null || location.getString("lng") == null) {
            throw new OrderBusinessException("地址坐标解析失败");
        }
        return location.getString("lat") + "," + location.getString("lng");
    }

    /**
     * 安全获取字符串值，null则返回空字符串
     *
     * @param value 字符串值
     * @return 非null的字符串
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}