package io.github.jasper.monitoring.audit.spring3.report;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出请求中的客户端选择意图。
 *
 * <p>字段只用于选择报告行范围和导出列，不代表已授权组织、真实行数、敏感级别或最终结果。
 * 导出 Service 必须重新查询并统计服务端数据，再将可信事实提交给监测组件；不能将请求体直接
 * 转换成 {@code DataCount} 或资源授权结论。</p>
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
