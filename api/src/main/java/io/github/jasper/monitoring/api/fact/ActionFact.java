package io.github.jasper.monitoring.api.fact;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a method parameter, or a property beneath it, to a typed fact. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ActionFact {

    /** @return the fact type populated by the parameter binding */
    Class<? extends FactType<?>> value();

    /** @return an optional property path relative to the annotated parameter */
    String path() default "";
}
