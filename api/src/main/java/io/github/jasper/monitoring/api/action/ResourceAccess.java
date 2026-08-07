package io.github.jasper.monitoring.api.action;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables resource authorization as a phase of a monitored action. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResourceAccess {
    /** Missing organization ownership fails closed when enabled. */
    boolean requireOrgScope() default false;

    /** Trusted rule scope used to look up an approval pass; blank disables pass override. */
    String passRuleId() default "";

    /** Trusted exact pass subject; blank derives {@code user:<identity.userId>}. */
    String passSubject() default "";
}
