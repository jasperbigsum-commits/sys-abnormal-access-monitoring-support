/**
 * Boot 2 宿主的报告查询、资源授权和导出预检示例。
 *
 * <p>资源范围授权在 Controller 前 fail-closed 执行；导出在生成 XLSX 前以服务端行数、
 * 敏感字段和 UTC 日累计进行预检，并提交可用于规则评估的可信事实。</p>
 */
package io.github.jasper.monitoring.audit.spring2.report;
