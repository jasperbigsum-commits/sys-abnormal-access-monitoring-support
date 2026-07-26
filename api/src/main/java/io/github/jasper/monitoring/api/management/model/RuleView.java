package io.github.jasper.monitoring.api.management.model;
public final class RuleView { private final String id,systemScope; private RuleView(String id,String scope){this.id=id;this.systemScope=scope;} public static RuleView of(String id,String scope){return new RuleView(id,scope);} public String getId(){return id;} public String getSystemScope(){return systemScope;} }
