package io.github.jasper.monitoring.api.management;
import java.util.*;
public final class ManagementPage<T> {
    private final List<T> items; private final int page,size; private final long totalElements;
    private ManagementPage(List<T> items,int page,int size,long total){if(page<0||size<1||total<0)throw new IllegalArgumentException("invalid page metadata");List<T> copy=new ArrayList<T>(Objects.requireNonNull(items,"items"));if(copy.size()>size||total<copy.size())throw new IllegalArgumentException("page content is inconsistent with metadata");this.items=Collections.unmodifiableList(copy);this.page=page;this.size=size;this.totalElements=total;}
    public static <T> ManagementPage<T> of(List<T> items,int page,int size,long total){return new ManagementPage<T>(items,page,size,total);}
    public List<T> getItems(){return items;} public int getPage(){return page;} public int getSize(){return size;} public long getTotalElements(){return totalElements;}
}
