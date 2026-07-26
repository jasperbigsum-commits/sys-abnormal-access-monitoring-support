package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.SecurityAlertPo;
import org.apache.ibatis.annotations.Param;

/** Explicit alert SQL boundary. */
public interface AlertMapper {
    int insert(SecurityAlertPo alert);
    int update(SecurityAlertPo alert);
    SecurityAlertPo find(@Param("alertId") String alertId);
    SecurityAlertPo findOpen(@Param("fingerprint") String fingerprint);
}
