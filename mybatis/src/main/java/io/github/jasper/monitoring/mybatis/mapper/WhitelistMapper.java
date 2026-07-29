package io.github.jasper.monitoring.mybatis.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Explicit whitelist SQL boundary. */
public interface WhitelistMapper {
    @Select("SELECT COUNT(*) FROM monitoring_security_whitelist WHERE rule_id = #{ruleId} AND subject = #{subject} "
        + "AND status = 'ACTIVE' AND expires_at > #{at}")
    int countActive(@Param("ruleId") String ruleId, @Param("subject") String subject, @Param("at") Instant at);
    @Insert("INSERT INTO monitoring_security_whitelist (rule_id, subject, expires_at) VALUES (#{ruleId}, #{subject}, #{expiresAt})")
    int insert(@Param("ruleId") String ruleId, @Param("subject") String subject, @Param("expiresAt") Instant expiresAt);
}
