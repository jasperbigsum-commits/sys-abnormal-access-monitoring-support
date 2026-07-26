package io.github.jasper.monitoring.mybatis.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** Explicit whitelist SQL boundary. */
public interface WhitelistMapper {
    int countActive(@Param("ruleId") String ruleId, @Param("subject") String subject, @Param("at") Instant at);
    int insert(@Param("ruleId") String ruleId, @Param("subject") String subject, @Param("expiresAt") Instant expiresAt);
}
