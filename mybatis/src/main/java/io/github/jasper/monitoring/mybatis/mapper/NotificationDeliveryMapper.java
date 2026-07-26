package io.github.jasper.monitoring.mybatis.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Explicit durable notification delivery SQL boundary. */
public interface NotificationDeliveryMapper {
    @Insert("INSERT INTO notification_delivery (delivery_id, channel, aggregate_id, status) VALUES (#{deliveryId}, #{channel}, #{aggregateId}, #{status})")
    int insert(@Param("deliveryId") String deliveryId, @Param("channel") String channel,
               @Param("aggregateId") String aggregateId, @Param("status") String status);
    @Select("SELECT COUNT(*) FROM notification_delivery WHERE delivery_id = #{deliveryId}")
    int count(@Param("deliveryId") String deliveryId);
}
