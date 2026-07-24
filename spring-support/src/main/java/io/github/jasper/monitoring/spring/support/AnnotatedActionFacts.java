package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitorActionEnricher;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Request-scoped dynamic facts collected for one annotated MVC action. */
public final class AnnotatedActionFacts {
    /** Request attribute shared by the MVC interceptor and its action aspect. */
    public static final String REQUEST_ATTRIBUTE = "io.github.jasper.monitoring.annotated-action-facts";
    private static final String RULE_TAG_ATTRIBUTE_PREFIX = "monitor.rule-tag.";

    private final MonitorActionDefinition definition;
    private final Method method;
    private final List<Class<? extends MonitorActionEnricher>> enrichers;
    private final AtomicReference<MonitorActionFacts> facts = new AtomicReference<MonitorActionFacts>(
        MonitorActionFacts.empty());

    public AnnotatedActionFacts(MonitorActionDefinition definition, Method method,
                                List<Class<? extends MonitorActionEnricher>> enrichers) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.method = Objects.requireNonNull(method, "method");
        this.enrichers = immutableEnrichers(enrichers);
    }

    public MonitorActionDefinition getDefinition() {
        return definition;
    }

    public Method getMethod() {
        return method;
    }

    public List<Class<? extends MonitorActionEnricher>> getEnrichers() {
        return enrichers;
    }

    /** Merges a later dynamic contribution without allowing static attributes to be replaced. */
    public void merge(MonitorActionFacts contribution) {
        if (contribution == null) {
            return;
        }
        MonitorActionFacts current;
        MonitorActionFacts merged;
        do {
            current = facts.get();
            merged = merged(current, contribution);
        } while (!facts.compareAndSet(current, merged));
    }

    /** @return a stable snapshot for final event recording. */
    public MonitorActionFacts snapshot() {
        return facts.get();
    }

    /** Applies a supplied snapshot to a pre-populated event draft. */
    public void apply(SecurityEventDraft.Builder draft, MonitorActionFacts snapshot) {
        Objects.requireNonNull(draft, "draft");
        MonitorActionFacts value = snapshot == null ? MonitorActionFacts.empty() : snapshot;
        if (value.getResourceId() != null) {
            draft.resourceId(value.getResourceId());
        }
        if (value.getOrgScope() != null) {
            draft.orgScope(value.getOrgScope());
        }
        if (value.getDataCount().isPresent()) {
            draft.dataCount(value.getDataCount().getAsLong());
        }
        if (value.getLatencyMs().isPresent()) {
            draft.latencyMs(value.getLatencyMs().getAsLong());
        }
        for (Map.Entry<String, String> attribute : value.getAttributes().entrySet()) {
            draft.attribute(attribute.getKey(), attribute.getValue());
        }
    }

    private MonitorActionFacts merged(MonitorActionFacts before, MonitorActionFacts after) {
        MonitorActionFacts.Builder builder = MonitorActionFacts.builder();
        String resourceId = after.getResourceId() == null ? before.getResourceId() : after.getResourceId();
        String orgScope = after.getOrgScope() == null ? before.getOrgScope() : after.getOrgScope();
        if (resourceId != null) {
            builder.resourceId(resourceId);
        }
        if (orgScope != null) {
            builder.orgScope(orgScope);
        }
        if (after.getDataCount().isPresent()) {
            builder.dataCount(after.getDataCount().getAsLong());
        } else if (before.getDataCount().isPresent()) {
            builder.dataCount(before.getDataCount().getAsLong());
        }
        if (after.getLatencyMs().isPresent()) {
            builder.latencyMs(after.getLatencyMs().getAsLong());
        } else if (before.getLatencyMs().isPresent()) {
            builder.latencyMs(before.getLatencyMs().getAsLong());
        }
        if (after.getResult() != null) {
            builder.result(after.getResult());
        } else if (before.getResult() != null) {
            builder.result(before.getResult());
        }
        if (after.getReasonCode() != null) {
            builder.reasonCode(after.getReasonCode());
        } else if (before.getReasonCode() != null) {
            builder.reasonCode(before.getReasonCode());
        }
        copyAttributes(builder, before);
        copyAttributes(builder, after);
        return builder.build();
    }

    private void copyAttributes(MonitorActionFacts.Builder builder, MonitorActionFacts source) {
        for (Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            if (!isRuleTag(attribute.getKey()) && !definition.getAttributes().containsKey(attribute.getKey())) {
                builder.attribute(attribute.getKey(), attribute.getValue());
            }
        }
    }

    private static boolean isRuleTag(String key) {
        return key.toLowerCase(Locale.ROOT).startsWith(RULE_TAG_ATTRIBUTE_PREFIX);
    }

    private static List<Class<? extends MonitorActionEnricher>> immutableEnrichers(
        List<Class<? extends MonitorActionEnricher>> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Class<? extends MonitorActionEnricher>>(values));
    }
}
