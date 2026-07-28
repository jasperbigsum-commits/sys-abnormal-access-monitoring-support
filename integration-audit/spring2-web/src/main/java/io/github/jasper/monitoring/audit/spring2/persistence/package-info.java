/**
 * Boot 2 验收夹具持有的 MyBatis 持久化状态。
 *
 * <p>包含虚构账号、会话、报告、导出台账和控制幂等记录的 Mapper 与仓储。该包使用 H2 数据库
 * 验证事务边界；生产系统必须将同类状态接入自身数据库迁移和备份恢复策略。</p>
 */
package io.github.jasper.monitoring.audit.spring2.persistence;
