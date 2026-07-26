package io.github.jasper.monitoring.api.management;
import java.util.*;
public final class ManagementPageRequest {
    private final int page; private final int size; private final String sortBy; private final boolean descending;
    private static final Set<String> ALLOWED_SORT_FIELDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("id", "createdAt", "updatedAt", "occurredAt", "severity", "status", "name")));
    private ManagementPageRequest(int page,int size,String sortBy,boolean descending){if(page<0)throw new IllegalArgumentException("page must be non-negative");if(size<1||size>200)throw new IllegalArgumentException("size must be between 1 and 200");this.page=page;this.size=size;this.sortBy=Objects.requireNonNull(sortBy,"sortBy");if(!ALLOWED_SORT_FIELDS.contains(sortBy))throw new IllegalArgumentException("sortBy is not an allowed management field");this.descending=descending;}
    public static ManagementPageRequest of(int page,int size,String sortBy){return new ManagementPageRequest(page,size,sortBy,false);}
    public static ManagementPageRequest of(int page,int size,String sortBy,boolean descending){return new ManagementPageRequest(page,size,sortBy,descending);}
    public int getPage(){return page;} public int getSize(){return size;} public String getSortBy(){return sortBy;} public boolean isDescending(){return descending;}
}
