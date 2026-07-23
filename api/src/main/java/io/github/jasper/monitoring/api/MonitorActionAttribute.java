package io.github.jasper.monitoring.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a static action attribute or a dynamic fact bound from a method parameter.
 *
 * <p>Type and method declarations are static attributes. Parameter declarations describe a
 * dynamic value and are validated before a host adapter reads the parameter.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(MonitorActionAttributes.class)
public @interface MonitorActionAttribute {
    /** @return the fact destination */
    MonitorActionAttributeTarget target() default MonitorActionAttributeTarget.ATTRIBUTE;

    /** @return the static or dynamic attribute name */
    String name() default "";

    /** @return the static attribute value; parameter declarations must leave this empty */
    String value() default "";

    /** @return the parameter-relative bean path; an empty path selects the parameter itself */
    String path() default "";
}
