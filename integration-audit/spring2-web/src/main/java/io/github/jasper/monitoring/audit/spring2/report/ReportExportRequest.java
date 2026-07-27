package io.github.jasper.monitoring.audit.spring2.report;

import java.util.ArrayList;
import java.util.List;

/** Client selection intent; row counts and field authority are deliberately absent. */
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
