package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/** 从零开始的分页请求对象；排序值必须来自对应查询定义的枚举。 */
public final class ManagementPageRequest {
    private final int page;
    private final int size;
    private final Enum<?> sort;
    private final boolean descending;

    private ManagementPageRequest(int page, int size, Enum<?> sort, boolean descending) {
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (size < 1 || size > 200) throw new IllegalArgumentException("size must be between 1 and 200");
        this.page = page;
        this.size = size;
        this.sort = Objects.requireNonNull(sort, "sort");
        this.descending = descending;
    }

    /** @return 默认升序分页请求 */
    public static ManagementPageRequest of(int page, int size, Enum<?> sort) {
        return new ManagementPageRequest(page, size, sort, false);
    }

    /** @return 指定排序方向的分页请求 */
    public static ManagementPageRequest of(int page, int size, Enum<?> sort, boolean descending) {
        return new ManagementPageRequest(page, size, sort, descending);
    }

    /** @return 页码（从 0 开始） */
    public int getPage() { return page; }
    /** @return 每页大小 */
    public int getSize() { return size; }
    /** @return 排序字段枚举 */
    public Enum<?> getSort() { return sort; }
    /** @return 是否降序 */
    public boolean isDescending() { return descending; }
}
