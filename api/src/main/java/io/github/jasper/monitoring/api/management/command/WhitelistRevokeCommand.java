package io.github.jasper.monitoring.api.management.command;
public final class WhitelistRevokeCommand extends VersionedReasonCommand { private WhitelistRevokeCommand(String id,long v,String r){super(id,v,r,id+":"+v);} public static WhitelistRevokeCommand of(String id,long v,String r){return new WhitelistRevokeCommand(id,v,r);} }
