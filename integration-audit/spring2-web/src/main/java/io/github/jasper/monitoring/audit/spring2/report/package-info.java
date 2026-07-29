/**
 * Boot 2 宿主的报告查询、资源授权、导出预检和监测触发示例。
 *
 * <p>资源范围授权在 Controller 前 fail-closed 执行；导出在生成 XLSX 前以服务端行数、
 * 敏感字段和 UTC 日累计进行预检，阻断路径提交拒绝事件，成功路径在文件生成完成后提交成功事件。
 * {@code MonitoringService.monitor(...)} 负责事件持久化、规则评估、告警和控制编排；当前请求的
 * 中断仍由宿主预检和已生效控制状态负责。</p>
 */
package io.github.jasper.monitoring.audit.spring2.report;
