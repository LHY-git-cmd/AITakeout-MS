package com.sky.mapper;

import com.sky.entity.OrderTimelineEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 订单时间轴事件只追加持久化接口。 */
@Mapper
public interface OrderTimelineEventMapper {
    @Insert("insert into order_timeline_event(event_no,order_id,event_type,business_no,display_message," +
            "operator_type,operator_id,payload_json,event_time,create_time) values(" +
            "#{eventNo},#{orderId},#{eventType},#{businessNo},#{displayMessage},#{operatorType}," +
            "#{operatorId},#{payloadJson},#{eventTime},#{createTime})")
    int insert(OrderTimelineEvent event);
}
