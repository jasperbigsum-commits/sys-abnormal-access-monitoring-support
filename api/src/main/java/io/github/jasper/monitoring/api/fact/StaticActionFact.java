package io.github.jasper.monitoring.api.fact;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds one typed constant fact to a monitored method. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(StaticActionFact.List.class)
public @interface StaticActionFact {

    /** @return the fact type populated by this declaration */
    Class<? extends FactType<?>> fact();

    /** @return the fact codec's stable string representation */
    String value();

    /** Container used for repeatable static fact declarations. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        StaticActionFact[] value();
    }
}
