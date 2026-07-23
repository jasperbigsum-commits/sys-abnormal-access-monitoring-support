package io.github.jasper.monitoring.api;

/** Identifies the dynamic fact or static attribute represented by an action attribute declaration. */
public enum MonitorActionAttributeTarget {
    /** A non-sensitive extension attribute. */
    ATTRIBUTE,
    /** The runtime resource identifier. */
    RESOURCE_ID,
    /** The runtime organization or tenant scope. */
    ORG_SCOPE
}
