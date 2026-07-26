package io.github.jasper.monitoring.api.management.command;
public final class ControlApprovalCommand extends VersionedReasonCommand { private ControlApprovalCommand(String id,long v,String r){super(id,v,r,operationKey("control-approval",id,v));} public static ControlApprovalCommand of(String id,long v,String r){return new ControlApprovalCommand(id,v,r);} }
