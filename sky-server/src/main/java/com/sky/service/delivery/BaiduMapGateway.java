package com.sky.service.delivery;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.DeliveryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.sky.service.delivery.DeliveryModels.*;

/** 百度地图实现，使用短超时并对瞬时失败重试一次。 */
@Component
@ConditionalOnProperty(name = "sky.delivery.map-provider", havingValue = "baidu")
public class BaiduMapGateway implements DeliveryMapGateway {
    private final DeliveryProperties properties;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public BaiduMapGateway(DeliveryProperties properties) {
        this.properties = properties;
        if (properties.getBaiduAk() == null || properties.getBaiduAk().isBlank()) {
            throw new IllegalStateException("BAIDU_MAP_AK must be configured when sky.delivery.map-provider=baidu");
        }
    }

    @Override public GeocodeResult geocode(String address) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3")
                    .queryParam("address", address).queryParam("output", "json")
                    .queryParam("ak", properties.getBaiduAk()).build().encode().toUriString();
            JSONObject location = request(url).getJSONObject("result").getJSONObject("location");
            return new GeocodeResult(true, new Coordinate(location.getBigDecimal("lat"),
                    location.getBigDecimal("lng")), provider(), null, null);
        } catch (Exception exception) {
            return new GeocodeResult(false, null, provider(), "GEOCODE_FAILED", "地址解析失败，请检查后重试");
        }
    }

    @Override public DistanceResult drivingDistance(Coordinate origin, Coordinate destination) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/directionlite/v1/driving")
                    .queryParam("origin", coordinate(origin)).queryParam("destination", coordinate(destination))
                    .queryParam("ak", properties.getBaiduAk()).build().encode().toUriString();
            JSONArray routes = request(url).getJSONObject("result").getJSONArray("routes");
            if (routes == null || routes.isEmpty()) throw new IllegalStateException("no route");
            return new DistanceResult(true, routes.getJSONObject(0).getIntValue("distance"), provider(), null, null);
        } catch (Exception exception) {
            return new DistanceResult(false, 0, provider(), "ROUTE_FAILED", "配送路线计算失败，请重试");
        }
    }

    private JSONObject request(String url) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
                JSONObject result = JSON.parseObject(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
                if (result.getIntValue("status") != 0) throw new IllegalStateException("baidu request failed");
                return result;
            } catch (Exception exception) { last = exception; }
        }
        throw last;
    }

    private String coordinate(Coordinate value) { return value.latitude() + "," + value.longitude(); }
    @Override public String provider() { return "baidu"; }
}
