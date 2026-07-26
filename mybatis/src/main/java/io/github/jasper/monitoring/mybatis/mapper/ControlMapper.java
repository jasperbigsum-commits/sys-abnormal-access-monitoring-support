package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.mybatis.po.ControlActionPo;
import org.apache.ibatis.annotations.Param;

/** Explicit control SQL boundary. */
public interface ControlMapper {
    ControlActionPo find(@Param("idempotencyKey") String idempotencyKey);
    int insert(ControlActionPo control);
    int update(ControlActionPo control);
}
