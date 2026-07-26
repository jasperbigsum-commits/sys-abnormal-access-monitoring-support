package io.github.jasper.monitoring.api.management;
import java.util.Objects;
/** Zero-based page request. Sort values come only from query-specific enums. */
public final class ManagementPageRequest {
 private final int page,size; private final Enum<?> sort; private final boolean descending;
 private ManagementPageRequest(int page,int size,Enum<?> sort,boolean descending){if(page<0)throw new IllegalArgumentException("page must be non-negative");if(size<1||size>200)throw new IllegalArgumentException("size must be between 1 and 200");this.page=page;this.size=size;this.sort=Objects.requireNonNull(sort,"sort");this.descending=descending;}
 public static ManagementPageRequest of(int page,int size,Enum<?> sort){return new ManagementPageRequest(page,size,sort,false);} public static ManagementPageRequest of(int page,int size,Enum<?> sort,boolean descending){return new ManagementPageRequest(page,size,sort,descending);}
 public int getPage(){return page;} public int getSize(){return size;} public Enum<?> getSort(){return sort;} public boolean isDescending(){return descending;}
}
