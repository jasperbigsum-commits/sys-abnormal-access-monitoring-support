package io.github.jasper.monitoring.api.management.model;
public final class WhitelistView { private final String id,systemScope; private WhitelistView(String id,String scope){this.id=id;this.systemScope=scope;} public static WhitelistView of(String id,String scope){return new WhitelistView(id,scope);} public String getId(){return id;} public String getSystemScope(){return systemScope;} }
