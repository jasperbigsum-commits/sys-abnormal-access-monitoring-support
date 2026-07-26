package io.github.jasper.monitoring.api.management.model;

import java.util.List;

public final class ControlDetails extends ControlView {
    private final List<ControlAttemptView> attempts;

    public ControlDetails(String id, String scope, String status, long version, List<ControlAttemptView> attempts) {
        super(id, scope, status, version);
        this.attempts = ManagementModelValidation.attempts(attempts);
    }

    public List<ControlAttemptView> getAttempts() { return attempts; }
}
