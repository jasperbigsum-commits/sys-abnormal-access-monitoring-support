package io.github.jasper.monitoring.api.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 通用管理查询分页响应。 */
public final class ManagementPage<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;

    private ManagementPage(List<T> items, int page, int size, long total) {
        if (page < 0 || size < 1 || total < 0) throw new IllegalArgumentException("invalid page metadata");
        List<T> copy = new ArrayList<T>(Objects.requireNonNull(items, "items"));
        if (copy.size() > size || total < copy.size()) {
            throw new IllegalArgumentException("page content is inconsistent with metadata");
        }
        this.items = Collections.unmodifiableList(copy);
        this.page = page;
        this.size = size;
        this.totalElements = total;
    }

    /** @return 不可变分页结果 */
    public static <T> ManagementPage<T> of(List<T> items, int page, int size, long total) {
        return new ManagementPage<T>(items, page, size, total);
    }

    /** @return 当前页条目 */
    public List<T> getItems() { return items; }
    /** @return 当前页码（从 0 开始） */
    public int getPage() { return page; }
    /** @return 每页大小 */
    public int getSize() { return size; }
    /** @return 符合条件的总条目数 */
    public long getTotalElements() { return totalElements; }
}
