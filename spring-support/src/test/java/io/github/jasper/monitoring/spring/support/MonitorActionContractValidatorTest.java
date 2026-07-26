package io.github.jasper.monitoring.spring.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactSource;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class MonitorActionContractValidatorTest {
    @Test
    void compilesAcceptedMethodFactsIntoImmutableBindings() throws Exception {
        MonitorActionContractValidator validator = validator(Collections.<FactBinding>emptyList());

        MonitorActionContractValidator.MethodBinding binding = validator.validate(
            Fixture.class.getMethod("export", Long.class));

        assertEquals(BuiltInActions.ReportExport.class, binding.getActionType());
        assertEquals(1, binding.getFacts().size());
        assertThrows(UnsupportedOperationException.class, () -> binding.getFacts().clear());
    }

    @Test
    void rejectsFactNotOwnedByActionAndDuplicateMethodProducer() throws Exception {
        MonitorActionContractValidator validator = validator(Collections.<FactBinding>emptyList());
        assertThrows(MonitoringConfigurationException.class, () -> validator.validate(
            Fixture.class.getMethod("query", Long.class)));
        assertThrows(MonitoringConfigurationException.class, () -> validator.validate(
            Fixture.class.getMethod("duplicate", Long.class, Long.class)));
    }

    @Test
    void rejectsDuplicateProviderAndMethodProducer() throws Exception {
        FactBinding provider = FactBinding.forAction(BuiltInActions.ReportExport.class,
            FactSource.HOST_PROVIDER, execution -> ActionFacts.builder()
                .put(BuiltInFacts.DataCount.class, 1L).build(), BuiltInFacts.DataCount.class);
        MonitorActionContractValidator validator = validator(Collections.singletonList(provider));

        Method method = Fixture.class.getMethod("export", Long.class);
        assertThrows(MonitoringConfigurationException.class, () -> validator.validate(method));
    }

    private static MonitorActionContractValidator validator(java.util.List<FactBinding> bindings) {
        ActionCatalog actions = new ActionCatalog();
        BuiltInActions.registerInto(actions);
        actions.freeze();
        FactCatalog facts = new FactCatalog();
        BuiltInFacts.registerInto(facts);
        facts.freeze();
        return new MonitorActionContractValidator(actions, facts, bindings);
    }

    public static final class Fixture {
        @MonitorAction(BuiltInActions.ReportExport.class)
        public void export(@ActionFact(BuiltInFacts.DataCount.class) Long count) { }

        @MonitorAction(BuiltInActions.Query.class)
        public void query(@ActionFact(BuiltInFacts.DataCount.class) Long count) { }

        @MonitorAction(BuiltInActions.ReportExport.class)
        public void duplicate(@ActionFact(BuiltInFacts.DataCount.class) Long first,
                @ActionFact(BuiltInFacts.DataCount.class) Long second) { }
    }
}
