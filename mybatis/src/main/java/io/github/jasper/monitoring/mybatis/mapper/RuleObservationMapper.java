package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.RuleObservationPo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Append-only SQL boundary for observe-only rule evidence. */
public interface RuleObservationMapper {
    @Insert("INSERT INTO monitoring_rule_observation (observation_id, rule_id, event_id, subject, observed_at) "
        + "VALUES (#{observationId}, #{ruleId}, #{eventId}, #{subject}, #{observedAt})")
    int insert(RuleObservationPo observation);

    @Select("SELECT observation_id AS observationId, rule_id AS ruleId, event_id AS eventId, "
        + "subject, observed_at AS observedAt FROM monitoring_rule_observation "
        + "WHERE observation_id = #{observationId}")
    RuleObservationPo find(@Param("observationId") String observationId);
}
