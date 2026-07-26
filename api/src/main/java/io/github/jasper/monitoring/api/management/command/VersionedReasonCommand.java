package io.github.jasper.monitoring.api.management.command;
import java.util.Objects;
/** Immutable command payload shared by state-changing management use cases. */
public class VersionedReasonCommand {
 private final String resourceId, reason; private final long expectedVersion;
 protected VersionedReasonCommand(String id,long version,String reason){if(version<1)throw new IllegalArgumentException("expectedVersion must be positive");this.resourceId=require(id);this.reason=require(reason);this.expectedVersion=version;}
 public static VersionedReasonCommand of(String id,long version,String reason){return new VersionedReasonCommand(id,version,reason);}
 public String getResourceId(){return resourceId;} public long getExpectedVersion(){return expectedVersion;} public String getReason(){return reason;}
 private static String require(String s){Objects.requireNonNull(s);if(s.trim().isEmpty())throw new IllegalArgumentException("value must not be blank");return s;}
}
