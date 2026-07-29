package io.github.jasper.monitoring.api.action;

/**
 * Action 类型 token（强类型业务动作标识）。
 * <p>
 * 用途：
 * 1) 作为动作目录注册键，绑定事件类型、资源类型、Fact 约束和失败策略；<br>
 * 2) 让规则、FactBinding、注解埋点围绕同一动作语义协作。<br>
 * 示例：BuiltInActions.ReportExport、BuiltInActions.Query。
 */
public interface ActionType {
}
