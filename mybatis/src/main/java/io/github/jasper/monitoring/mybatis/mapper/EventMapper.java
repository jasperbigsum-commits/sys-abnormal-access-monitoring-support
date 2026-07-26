package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.SecurityEventPo;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Explicit event SQL boundary. Implementations are supplied by MyBatis configuration. */
public interface EventMapper {
    int insert(SecurityEventPo event);
    SecurityEventPo find(@Param("eventId") String eventId);
    List<SecurityEventPo> findSince(@Param("systemId") String systemId, @Param("since") Instant since);
}
