/**
 * Boot 2 宿主的权限变更安全边界示例。
 *
 * <p>在角色关系提交前识别自我提权，并将服务端确认的目标用户和权限增量作为监测事实提交；
 * 用于验证拒绝、告警和控制发生在业务副作用之前。</p>
 */
package io.github.jasper.monitoring.audit.spring2.privilege;
