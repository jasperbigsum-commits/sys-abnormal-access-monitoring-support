package io.github.jasper.monitoring.mybatis.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Explicit whitelist SQL boundary. */
public interface WhitelistMapper {
    @Select("SELECT COUNT(*) FROM monitoring_security_whitelist WHERE system_id = #{systemId} AND rule_id = #{ruleId} AND subject = #{subject} "
        + "AND status = 'ACTIVE' AND expires_at > #{at}")
    int countActive(@Param("systemId") String systemId, @Param("ruleId") String ruleId,
                    @Param("subject") String subject, @Param("at") Instant at);
    @Insert("INSERT INTO monitoring_security_whitelist (whitelist_id, system_id, rule_id, subject, expires_at, approved_by, reason) VALUES (#{whitelistId}, #{systemId}, #{ruleId}, #{subject}, #{expiresAt}, #{approvedBy}, #{reason})")
    int insert(@Param("whitelistId") String whitelistId, @Param("systemId") String systemId,
               @Param("ruleId") String ruleId, @Param("subject") String subject,
               @Param("expiresAt") Instant expiresAt, @Param("approvedBy") String approvedBy,
               @Param("reason") String reason);
}
