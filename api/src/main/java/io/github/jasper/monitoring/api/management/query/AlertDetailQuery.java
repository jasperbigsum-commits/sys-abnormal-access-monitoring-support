package io.github.jasper.monitoring.api.management.query;
import java.util.Objects;
public final class AlertDetailQuery { private final String alertId; private AlertDetailQuery(String id){this.alertId=require(id);} public static AlertDetailQuery of(String id){return new AlertDetailQuery(id);} public String getAlertId(){return alertId;} private static String require(String s){Objects.requireNonNull(s);if(s.trim().isEmpty())throw new IllegalArgumentException("alertId must not be blank");return s;} }
