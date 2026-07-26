package io.github.jasper.monitoring.api.management.command;
public final class AlertDispositionCommand extends VersionedReasonCommand { private AlertDispositionCommand(String id,long v,String r){super(id,v,r);} public static AlertDispositionCommand of(String id,long v,String r){return new AlertDispositionCommand(id,v,r);} public String getAlertId(){return getResourceId();} }
