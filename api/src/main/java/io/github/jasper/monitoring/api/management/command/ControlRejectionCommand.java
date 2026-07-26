package io.github.jasper.monitoring.api.management.command;
public final class ControlRejectionCommand extends VersionedReasonCommand { private ControlRejectionCommand(String id,long v,String r){super(id,v,r,operationKey("control-rejection",id,v));} public static ControlRejectionCommand of(String id,long v,String r){return new ControlRejectionCommand(id,v,r);} }
