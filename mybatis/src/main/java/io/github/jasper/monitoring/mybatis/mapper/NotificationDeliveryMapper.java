package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.NotificationDeliveryPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Explicit durable notification delivery SQL boundary. */
public interface NotificationDeliveryMapper {
    @Insert("INSERT INTO notification_delivery (delivery_id, channel, aggregate_id, status, attempt_count, "
        + "next_attempt_at, failure_category, updated_at, version) VALUES (#{deliveryId}, #{channel}, "
        + "#{aggregateId}, #{status}, #{attemptCount}, #{nextAttemptAt}, #{failureCategory}, #{updatedAt}, #{version})")
    int insert(NotificationDeliveryPo row);

    @Update("UPDATE notification_delivery SET status = #{row.status}, attempt_count = #{row.attemptCount}, "
        + "next_attempt_at = #{row.nextAttemptAt}, failure_category = #{row.failureCategory}, "
        + "updated_at = #{row.updatedAt}, version = #{row.version} "
        + "WHERE delivery_id = #{row.deliveryId} AND version = #{expectedVersion}")
    int update(@Param("row") NotificationDeliveryPo row, @Param("expectedVersion") long expectedVersion);

    @Select("SELECT delivery_id AS deliveryId, channel, aggregate_id AS aggregateId, status, "
        + "attempt_count AS attemptCount, next_attempt_at AS nextAttemptAt, "
        + "failure_category AS failureCategory, updated_at AS updatedAt, version "
        + "FROM notification_delivery WHERE channel = #{channel} AND aggregate_id = #{aggregateId}")
    NotificationDeliveryPo find(@Param("channel") String channel, @Param("aggregateId") String aggregateId);

    @Select("SELECT delivery_id AS deliveryId, channel, aggregate_id AS aggregateId, status, "
        + "attempt_count AS attemptCount, next_attempt_at AS nextAttemptAt, "
        + "failure_category AS failureCategory, updated_at AS updatedAt, version "
        + "FROM notification_delivery WHERE channel = #{channel} AND "
        + "((status = 'PENDING' AND attempt_count = 0) OR "
        + "(status = 'RETRY_PENDING' AND next_attempt_at <= #{at})) "
        + "ORDER BY CASE WHEN status = 'PENDING' THEN updated_at ELSE next_attempt_at END, delivery_id "
        + "LIMIT #{limit}")
    List<NotificationDeliveryPo> findDue(@Param("channel") String channel,
        @Param("at") Instant at, @Param("limit") int limit);
}
