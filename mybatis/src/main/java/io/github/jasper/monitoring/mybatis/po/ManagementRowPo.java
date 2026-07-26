package io.github.jasper.monitoring.mybatis.po;

/** Minimal internal projection used by the management query mapper. */
public final class ManagementRowPo {
    private String id;
    private String status;
    private long version;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
