package io.github.jasper.monitoring.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将一项监测控制动作绑定到宿主系统的公开方法。
 *
 * <p>无框架依赖的控制处理器会发现带此注解的方法；该方法必须且只能接收一个控制命令。
 * 绑定范围限定为方法，以明确控制动作的归属。{@link ControlActionType#RECORD} 不可用，
 * 因其仅用于审计记录，不触发宿主控制调用。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ControlTrigger {
    /** @return 由已注解宿主方法执行的控制动作 */
    ControlActionType value();
}
