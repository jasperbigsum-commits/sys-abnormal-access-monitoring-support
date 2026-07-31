package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.ControlActionType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable synchronous result for one completed-facts checkpoint. */
public final class ActionDecision {
    private final ActionDisposition disposition;
    private final Set<ActionRequirement> requirements;
    private final Set<ControlActionType> controls;
    private final Set<String> matchedRuleIds;

    private ActionDecision(ActionDisposition disposition, Set<ActionRequirement> requirements,
            Set<ControlActionType> controls, Set<String> matchedRuleIds) {
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.requirements = immutableEnum(requirements, ActionRequirement.class);
        this.controls = immutableEnum(controls, ControlActionType.class);
        this.matchedRuleIds = Collections.unmodifiableSet(new LinkedHashSet<String>(matchedRuleIds));
    }

    public static ActionDecision allow() {
        return of(ActionDisposition.ALLOW, Collections.<ActionRequirement>emptySet(),
            Collections.<ControlActionType>emptySet(), Collections.<String>emptySet());
    }

    public static ActionDecision blocked(String ruleId) {
        return of(ActionDisposition.BLOCK, Collections.<ActionRequirement>emptySet(),
            Collections.<ControlActionType>emptySet(), Collections.singleton(ruleId));
    }

    public static ActionDecision of(ActionDisposition disposition,
            Set<ActionRequirement> requirements, Set<ControlActionType> controls,
            Set<String> matchedRuleIds) {
        return new ActionDecision(disposition, requirements, controls, matchedRuleIds);
    }

    public ActionDisposition getDisposition() { return disposition; }
    public Set<ActionRequirement> getRequirements() { return requirements; }
    public Set<ControlActionType> getControls() { return controls; }
    public Set<String> getMatchedRuleIds() { return matchedRuleIds; }
    public boolean isAllowed() { return disposition == ActionDisposition.ALLOW; }

    private static <E extends Enum<E>> Set<E> immutableEnum(Set<E> values, Class<E> type) {
        if (values.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
