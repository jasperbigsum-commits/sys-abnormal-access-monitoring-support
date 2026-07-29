package io.github.jasper.monitoring.audit.spring2.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出请求中的客户端选择意图。
 *
 * <p>行数、组织范围、敏感级别和授权结论不从请求体取得；导出 Service 必须重新查询服务端数据，
 * 再把可信事实提交给监测组件。</p>
 */
public class ReportExportRequest {
    private Long minId;
    private Long maxId;
    private List<Long> selectedIds = new ArrayList<Long>();
    private List<String> fields = new ArrayList<String>();

    public Long getMinId() {
        return minId;
    }

    public void setMinId(Long minId) {
        this.minId = minId;
    }

    public Long getMaxId() {
        return maxId;
    }

    public void setMaxId(Long maxId) {
        this.maxId = maxId;
    }

    public List<Long> getSelectedIds() {
        return selectedIds;
    }

    public void setSelectedIds(List<Long> selectedIds) {
        this.selectedIds = selectedIds;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
