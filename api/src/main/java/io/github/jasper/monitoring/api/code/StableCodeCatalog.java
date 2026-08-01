package io.github.jasper.monitoring.api.code;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Mutable during startup and frozen for runtime stable-code lookup. */
public final class StableCodeCatalog {
    private static final Pattern CODE_PATTERN =
        Pattern.compile("[A-Z][A-Z0-9_]*(?:\\.[A-Z][A-Z0-9_]*){2,}");

    private final String hostOwner;
    private final Map<String, CodeDefinition> definitions =
        new LinkedHashMap<String, CodeDefinition>();
    private boolean frozen;

    public StableCodeCatalog(String hostOwner) {
        this.hostOwner = hostOwner == null ? "" : hostOwner.trim().toUpperCase(java.util.Locale.ROOT);
        if (!this.hostOwner.isEmpty() && !this.hostOwner.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("hostOwner must be an uppercase namespace token");
        }
    }

    public void registerBuiltIn(CodeDefinition definition) {
        register(definition, true);
    }

    public void registerHost(CodeDefinition definition) {
        register(definition, false);
    }

    public GovernedCode require(String code) {
        CodeDefinition definition = definitions.get(Objects.requireNonNull(code, "code"));
        if (definition == null) {
            throw configuration("Stable code is not registered: " + code);
        }
        return definition.getCodeDefinition();
    }

    public void validateReason(ReasonCode reason, Class<? extends ActionType> actionType,
            SecurityEventResult result) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(result, "result");
        CodeDefinition definition = definitions.get(reason.getCode());
        if (definition == null || definition.getFamily() != CodeFamily.OUTCOME_REASON
                || !(definition.getCodeDefinition() instanceof ReasonCode)
                || !definition.appliesTo(actionType)
                || !definition.getAllowedOutcomes().contains(result)) {
            throw configuration("Reason code is not applicable: " + reason.getCode()
                + " action=" + actionType.getName() + " result=" + result);
        }
        if (result == SecurityEventResult.SUCCESS) {
            throw configuration("Outcome reason cannot be used for success: " + reason.getCode());
        }
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Map<String, CodeDefinition> asMap() {
        return java.util.Collections.unmodifiableMap(
            new LinkedHashMap<String, CodeDefinition>(definitions));
    }

    private void register(CodeDefinition definition, boolean builtIn) {
        requireMutable();
        Objects.requireNonNull(definition, "definition");
        String code = definition.getCode();
        validateCode(code);
        String owner = code.substring(0, code.indexOf('.'));
        if (builtIn && !"MON".equals(owner)) {
            throw configuration("Built-in stable code must use MON namespace: " + code);
        }
        if (!builtIn && ("MON".equals(owner) || !owner.equals(hostOwner))) {
            throw configuration("Host stable code must use configured namespace " + hostOwner + ": " + code);
        }
        if (definitions.containsKey(code)) {
            throw configuration("Stable code is already registered: " + code);
        }
        if (definition.getFamily() == CodeFamily.OUTCOME_REASON
                && definition.getAllowedOutcomes().contains(SecurityEventResult.SUCCESS)) {
            throw configuration("Outcome reason cannot allow SUCCESS: " + code);
        }
        definitions.put(code, definition);
    }

    private void requireMutable() {
        if (frozen) {
            throw configuration("Stable code catalog is frozen");
        }
    }

    private static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Stable code must use OWNER.DOMAIN.CAUSE uppercase segments");
        }
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }
}
