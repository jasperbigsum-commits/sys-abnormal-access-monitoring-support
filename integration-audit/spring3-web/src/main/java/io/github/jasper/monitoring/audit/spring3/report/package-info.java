/**
 * Boot 3 宿主的报告查询、资源授权、导出预检和监测触发示例。
 *
 * <p>本包把一个受保护的报告业务流程接入监测组件：资源范围授权在 Controller 前 fail-closed 执行；
 * 查询 Service 在通过已有控制检查后提交 {@code Query} Action 和服务端 Fact；导出 Service 在
 * 生成 XLSX 前以服务端行数、敏感字段和 UTC 日累计进行风险预检，阻断路径提交拒绝事件，成功路径
 * 在文件生成完成后提交成功事件。{@code MonitoringService.monitor(...)} 负责事件持久化、规则评估、
 * 告警和控制编排，但不会自动撤销已经完成的业务副作用；当前请求的中断必须由宿主预检或已生效
 * 控制状态完成。</p>
 */
package io.github.jasper.monitoring.audit.spring3.report;
