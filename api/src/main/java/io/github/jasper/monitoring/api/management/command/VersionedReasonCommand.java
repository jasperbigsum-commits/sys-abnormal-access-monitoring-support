package io.github.jasper.monitoring.api.management.command;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/** Immutable versioned command with a bounded replay/idempotency key. */
public class VersionedReasonCommand {
 private final String resourceId, reason, idempotencyKey; private final long expectedVersion;
 protected VersionedReasonCommand(String id,long version,String reason,String key){if(version<0)throw new IllegalArgumentException("expectedVersion must be non-negative");this.resourceId=require(id,"resourceId");this.reason=requireBounded(reason,"reason",512);this.idempotencyKey=requireBounded(key,"idempotencyKey",128);this.expectedVersion=version;}
 /**
  * The generic command has no operation identity and therefore cannot safely
  * derive a deterministic idempotency key. Callers must provide an explicit
  * key, or use an operation-specific command type.
  */
 public static VersionedReasonCommand of(String id,long version,String reason){throw new IllegalArgumentException("idempotencyKey must be explicit for a generic command");}
 public static VersionedReasonCommand of(String id,long version,String reason,String key){return new VersionedReasonCommand(id,version,reason,key);}
 public String getResourceId(){return resourceId;} public long getExpectedVersion(){return expectedVersion;} public String getReason(){return reason;} public String getIdempotencyKey(){return idempotencyKey;}
 /** Creates a bounded, operation-scoped deterministic replay key. */
 protected static String operationKey(String operation,String id,long version){
  String namespace=requireBounded(operation,"operation",32);
  String resource=require(id,"resourceId");
  return namespace+":"+sha256(resource)+":"+version;
 }
 private static String sha256(String value){
  try {
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder(digest.length*2);
   for(byte b:digest){hex.append(Character.forDigit((b>>>4)&0x0f,16));hex.append(Character.forDigit(b&0x0f,16));}
   return hex.toString();
  } catch(NoSuchAlgorithmException e){
   throw new IllegalStateException("SHA-256 is required by the runtime",e);
  }
 }
 private static String require(String s,String n){return requireBounded(s,n,256);} private static String requireBounded(String s,String n,int max){Objects.requireNonNull(s,n);if(s.trim().isEmpty()||s.length()>max)throw new IllegalArgumentException(n+" must be non-empty and bounded");return s;}
}
