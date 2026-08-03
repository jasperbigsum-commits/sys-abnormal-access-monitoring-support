package io.github.jasper.monitoring.api;

/** @deprecated use {@link io.github.jasper.monitoring.api.control.ControlStatus}. */
@Deprecated
public final class ControlStatus {
    private ControlStatus() { }
    public static final io.github.jasper.monitoring.api.control.ControlStatus PENDING = io.github.jasper.monitoring.api.control.ControlStatus.PENDING;
    public static final io.github.jasper.monitoring.api.control.ControlStatus AWAITING_APPROVAL = io.github.jasper.monitoring.api.control.ControlStatus.AWAITING_APPROVAL;
    public static final io.github.jasper.monitoring.api.control.ControlStatus UNDEFINED = io.github.jasper.monitoring.api.control.ControlStatus.UNDEFINED;
    public static final io.github.jasper.monitoring.api.control.ControlStatus SUCCEEDED = io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED;
    public static final io.github.jasper.monitoring.api.control.ControlStatus FAILED = io.github.jasper.monitoring.api.control.ControlStatus.FAILED;
    public static final io.github.jasper.monitoring.api.control.ControlStatus SKIPPED = io.github.jasper.monitoring.api.control.ControlStatus.SKIPPED;
    public static final io.github.jasper.monitoring.api.control.ControlStatus REJECTED = io.github.jasper.monitoring.api.control.ControlStatus.REJECTED;
}
