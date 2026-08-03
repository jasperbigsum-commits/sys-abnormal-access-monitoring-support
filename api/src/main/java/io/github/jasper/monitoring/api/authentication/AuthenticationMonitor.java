package io.github.jasper.monitoring.api.authentication;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.code.ReasonCode;
import io.github.jasper.monitoring.api.event.FailureClass;

/**
 * 认证流程的监测与补充控制边界。
 *
 * <p>宿主认证服务只提交临时登录主体和服务端确认的认证结果，不接触主体密钥生成、
 * Fact 持久化或规则实现。调用方应先执行 {@link #preCheck(LoginSubjectInput)}，再在
 * 密码、验证码、多因素认证等真实决策点记录最终结果。</p>
 *
 * <p>该接口只提供安全监测和附加控制，不替代宿主的凭据校验、账号状态检查或认证授权。</p>
 */
public interface AuthenticationMonitor {
    /**
     * 在执行认证校验前查询当前登录主体和来源 IP 上生效的补充控制。
     *
     * @param subject 本次认证尝试的临时登录主体
     * @return 允许、阻断及需要验证码或 MFA 等附加要求的决策
     */
    ActionDecision preCheck(LoginSubjectInput subject);

    /**
     * 记录由业务校验明确拒绝的认证尝试。
     *
     * @param subject 本次认证尝试的临时登录主体
     * @param stage 产生拒绝结果的认证阶段
     * @param reason 已注册且适用于登录拒绝结果的稳定原因码
     */
    void recordDenied(LoginSubjectInput subject, AuthenticationStage stage, ReasonCode reason);

    /**
     * 记录因认证基础设施或执行异常而失败的认证尝试。
     *
     * @param subject 本次认证尝试的临时登录主体
     * @param stage 产生失败结果的认证阶段
     * @param reason 已注册且适用于登录失败结果的稳定原因码
     * @param failureClass 失败类别，用于区分系统、依赖或输入等故障
     */
    void recordFailure(LoginSubjectInput subject, AuthenticationStage stage,
                       ReasonCode reason, FailureClass failureClass);

    /**
     * 记录认证成功；身份必须由服务端认证流程确认且不能是匿名身份。
     *
     * @param subject 本次认证尝试的临时登录主体
     * @param authenticatedIdentity 服务端确认的已认证身份
     */
    void recordSuccess(LoginSubjectInput subject, IdentityContext authenticatedIdentity);
}
