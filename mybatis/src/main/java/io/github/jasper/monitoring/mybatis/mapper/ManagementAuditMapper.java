package io.github.jasper.monitoring.mybatis.mapper;

import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import org.apache.ibatis.annotations.Insert;

/** Append-only management audit SQL. This mapper intentionally has no update or delete method. */
public interface ManagementAuditMapper {
    @Insert("INSERT INTO monitoring_management_audit (audit_id, system_id, actor_id, action, target_type, target_id, outcome, occurred_at) "
        + "VALUES (#{auditId}, #{systemScope}, #{actorId}, #{operation}, #{targetType}, #{targetId}, #{outcome}, #{occurredAt})")
    int insert(ManagementAuditRecord record);
}
