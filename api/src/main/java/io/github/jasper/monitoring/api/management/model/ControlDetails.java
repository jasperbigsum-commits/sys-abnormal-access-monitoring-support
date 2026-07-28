package io.github.jasper.monitoring.api.management.model;

import java.util.List;

/** 控制记录详情视图，包含全部尝试历史。 */
public final class ControlDetails extends ControlView {
    private final List<ControlAttemptView> attempts;

    public ControlDetails(String id, String scope, String status, long version, List<ControlAttemptView> attempts) {
        super(id, scope, status, version);
        this.attempts = ManagementModelValidation.attempts(attempts);
    }

    /** @return 按时间顺序排列的尝试列表 */
    public List<ControlAttemptView> getAttempts() { return attempts; }
}
