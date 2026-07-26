package io.github.jasper.monitoring.mybatis.mapper;

import org.apache.ibatis.annotations.Param;

/** Explicit durable notification delivery SQL boundary. */
public interface NotificationDeliveryMapper {
    int insert(@Param("deliveryId") String deliveryId, @Param("channel") String channel,
               @Param("aggregateId") String aggregateId, @Param("status") String status);
}
