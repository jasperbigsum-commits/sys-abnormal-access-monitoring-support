/**
 * Boot 3 宿主的控制动作处理器。
 *
 * <p>通过 {@code ControlTrigger} 将规则产生的控制命令绑定到验证码、限流、会话撤销、
 * MFA、拒绝和审批等宿主动作。生产处理器必须按幂等键去重，并严格限制在命令指定主体范围内执行。</p>
 */
package io.github.jasper.monitoring.audit.spring3.control;
