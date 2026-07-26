package io.github.jasper.monitoring.api.management.command;
public final class ControlRetryCommand extends VersionedReasonCommand { private ControlRetryCommand(String id,long v,String r){super(id,v,r,operationKey("control-retry",id,v));} public static ControlRetryCommand of(String id,long v,String r){return new ControlRetryCommand(id,v,r);} }
