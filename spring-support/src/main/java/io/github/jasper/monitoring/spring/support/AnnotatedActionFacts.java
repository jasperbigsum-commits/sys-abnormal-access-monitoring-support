package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitorActionEnricher;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Request-scoped dynamic facts collected for one annotated MVC action. */
public final class AnnotatedActionFacts {
    /** Request attribute shared by the MVC interceptor and its action aspect. */
    public static final String REQUEST_ATTRIBUTE = "io.github.jasper.monitoring.annotated-action-facts";
    private static final String RULE_TAG_ATTRIBUTE_PREFIX = "monitor.rule-tag.";
    private static final String DIAGNOSTIC_FACT_NAME = "attribute";

    private final MonitorActionDefinition definition;
    private final Method method;
    private final List<Class<? extends MonitorActionEnricher>> enrichers;
    private final AtomicReference<MonitorActionFacts> facts = new AtomicReference<MonitorActionFacts>(
        MonitorActionFacts.empty());
    private final AtomicReference<List<EventInputIssue>> inputIssues = new AtomicReference<List<EventInputIssue>>(
        Collections.<EventInputIssue>emptyList());

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

    /** Merges parameter extraction facts and their stable diagnostics. */
    public void merge(BoundParameterFactsExtractor.ExtractionResult extraction) {
        if (extraction == null) {
            return;
        }
        merge(extraction.getFacts(), EventFactSource.METHOD_PARAMETER);
        addIssues(extraction.getIssues());
    }

    /** Merges an enricher contribution without allowing protected static facts to be replaced. */
    public void merge(MonitorActionFacts contribution) {
        merge(contribution, EventFactSource.EVENT_ENRICHER);
    }

    /**
     * Merges a dynamic contribution with its trustworthy source category.
     *
     * @param contribution dynamic facts from a parameter extractor or enricher
     * @param sourceType stable source category for any protected-override diagnostic
     */
    public void merge(MonitorActionFacts contribution, EventFactSource sourceType) {
        if (contribution == null) {
            return;
        }
        Objects.requireNonNull(sourceType, "sourceType");
        MonitorActionFacts current;
        do {
            current = facts.get();
            MergeResult result = merged(current, contribution, sourceType);
            if (facts.compareAndSet(current, result.facts)) {
                addIssues(result.issues);
                return;
            }
        } while (true);
    }

    /** @return a stable snapshot for final event recording. */
    public MonitorActionFacts snapshot() {
        return facts.get();
    }

    /** @return immutable stable diagnostics accumulated while collecting dynamic facts. */
    public List<EventInputIssue> getInputIssues() {
        return inputIssues.get();
    }

    /** @return an immutable quality result suitable for the standard event recorder. */
    public EventInputValidation getInputValidation() {
        List<EventInputIssue> issues = inputIssues.get();
        if (issues.isEmpty()) {
            return EventInputValidation.valid();
        }
        return EventInputValidation.of(io.github.jasper.monitoring.api.EventInputStatus.INCOMPLETE,
            issues, Collections.<String>emptySet());
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

    private MergeResult merged(MonitorActionFacts before, MonitorActionFacts after, EventFactSource sourceType) {
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
        copyAcceptedAttributes(builder, before);
        List<EventInputIssue> issues = new ArrayList<EventInputIssue>();
        copyContributionAttributes(builder, after, sourceType, issues);
        return new MergeResult(builder.build(), issues);
    }

    private static void copyAcceptedAttributes(MonitorActionFacts.Builder builder, MonitorActionFacts source) {
        for (Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            builder.attribute(SecurityFieldSanitizer.normalizeAttributeKey(attribute.getKey()), attribute.getValue());
        }
    }

    private void copyContributionAttributes(MonitorActionFacts.Builder builder, MonitorActionFacts source,
                                            EventFactSource sourceType, List<EventInputIssue> issues) {
        for (Map.Entry<String, String> attribute : source.getAttributes().entrySet()) {
            String key = SecurityFieldSanitizer.normalizeAttributeKey(attribute.getKey());
            if (isProtectedAttribute(key)) {
                issues.add(EventInputIssue.of(BoundParameterFactsExtractor.DIAGNOSTIC_RULE_ID,
                    DIAGNOSTIC_FACT_NAME, EventInputIssueCode.PROTECTED_FACT_OVERRIDE, sourceType));
                continue;
            }
            builder.attribute(key, attribute.getValue());
        }
    }

    private boolean isProtectedAttribute(String normalizedKey) {
        return normalizedKey.startsWith(RULE_TAG_ATTRIBUTE_PREFIX)
            || definition.getAttributes().containsKey(normalizedKey);
    }

    private void addIssues(List<EventInputIssue> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        List<EventInputIssue> current;
        List<EventInputIssue> updated;
        do {
            current = inputIssues.get();
            LinkedHashSet<EventInputIssue> unique = new LinkedHashSet<EventInputIssue>(current);
            unique.addAll(additions);
            updated = Collections.unmodifiableList(new ArrayList<EventInputIssue>(unique));
        } while (!inputIssues.compareAndSet(current, updated));
    }

    private static List<Class<? extends MonitorActionEnricher>> immutableEnrichers(
        List<Class<? extends MonitorActionEnricher>> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Class<? extends MonitorActionEnricher>>(values));
    }

    private static final class MergeResult {
        private final MonitorActionFacts facts;
        private final List<EventInputIssue> issues;

        private MergeResult(MonitorActionFacts facts, List<EventInputIssue> issues) {
            this.facts = facts;
            this.issues = issues;
        }
    }
}
