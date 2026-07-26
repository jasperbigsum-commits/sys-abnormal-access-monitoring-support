package io.github.jasper.monitoring.api.management.command;
public final class WhitelistGrantCommand extends VersionedReasonCommand { private WhitelistGrantCommand(String id,long v,String r){super(id,v,r,id+":"+v);} public static WhitelistGrantCommand of(String id,long v,String r){return new WhitelistGrantCommand(id,v,r);} }
