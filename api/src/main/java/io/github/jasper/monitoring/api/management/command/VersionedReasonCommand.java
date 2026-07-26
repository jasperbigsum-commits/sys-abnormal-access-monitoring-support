package io.github.jasper.monitoring.api.management.command;
import java.util.Objects;
/** Immutable versioned command with a bounded replay/idempotency key. */
public class VersionedReasonCommand {
 private final String resourceId, reason, idempotencyKey; private final long expectedVersion;
 protected VersionedReasonCommand(String id,long version,String reason,String key){if(version<1)throw new IllegalArgumentException("expectedVersion must be positive");this.resourceId=require(id,"resourceId");this.reason=requireBounded(reason,"reason",512);this.idempotencyKey=requireBounded(key,"idempotencyKey",128);this.expectedVersion=version;}
 public static VersionedReasonCommand of(String id,long version,String reason){return of(id,version,reason,operationKey("versioned",id,version));}
 public static VersionedReasonCommand of(String id,long version,String reason,String key){return new VersionedReasonCommand(id,version,reason,key);}
 public String getResourceId(){return resourceId;} public long getExpectedVersion(){return expectedVersion;} public String getReason(){return reason;} public String getIdempotencyKey(){return idempotencyKey;}
 protected static String operationKey(String operation,String id,long version){return requireBounded(operation,"operation",32)+":"+require(id,"resourceId")+":"+version;}
 private static String require(String s,String n){return requireBounded(s,n,256);} private static String requireBounded(String s,String n,int max){Objects.requireNonNull(s,n);if(s.trim().isEmpty()||s.length()>max)throw new IllegalArgumentException(n+" must be non-empty and bounded");return s;}
}
