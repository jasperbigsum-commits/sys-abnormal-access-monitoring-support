package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.ActionExecution;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactBindingTest {

    @Test
    void actionSpecificProviderDoesNotApplyToSiblingActions() {
        ActionFactProvider provider = execution -> ActionFacts.builder().build();
        FactBinding binding = FactBinding.forAction(FirstExport.class, provider, DataCountFact.class);

        assertTrue(binding.appliesTo(FirstExport.class));
        assertFalse(binding.appliesTo(SecondExport.class));
        assertEquals(Collections.<Class<? extends FactType<?>>>singleton(DataCountFact.class),
            binding.getDeclaredFacts());
        assertEquals(provider, binding.getProvider());
    }

    @Test
    void contractProviderExplicitlyAppliesToAllImplementations() {
        FactBinding binding = FactBinding.forContract(ExportContract.class,
            execution -> ActionFacts.builder().build(), DataCountFact.class);

        assertTrue(binding.appliesTo(FirstExport.class));
        assertTrue(binding.appliesTo(SecondExport.class));
        assertFalse(binding.appliesTo(UnrelatedAction.class));
    }

    @Test
    void providerReceivesTheSameExecutionContext() {
        ActionExecution execution = new ActionExecution() {
            @Override public Class<? extends ActionType> getActionType() { return FirstExport.class; }
            @Override public MonitoringRequestContext getRequestContext() { return null; }
            @Override public IdentityContext getIdentityContext() { return null; }
            @Override public ActionOutcome getOutcome() { return ActionOutcome.of(SecurityEventResult.SUCCESS); }
        };
        ActionExecution[] observed = new ActionExecution[1];
        ActionFactProvider provider = current -> {
            observed[0] = current;
            return ActionFacts.builder().build();
        };

        provider.provide(execution);

        assertSame(execution, observed[0]);
    }

    @Test
    void factDefinitionRejectsARawValueOfTheWrongRuntimeType() {
        FactDefinition<Long> definition = FactDefinition
            .builder(DataCountFact.class, "data_count", Long.class)
            .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL)
            .maxLength(12)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(FactDefinition.longCodec(value -> Math.max(0L, value)))
            .validator(value -> value <= 1_000_000L)
            .build();

        assertThrows(IllegalArgumentException.class, () -> definition.validateRaw("5000"));
        assertEquals(Long.valueOf(0L), definition.validateRaw(-10L));
        assertThrows(IllegalArgumentException.class, () -> definition.validateRaw(1_000_001L));
    }

    @Test
    void factDefinitionUsesItsCodecForNormalizationPersistenceAndLengthValidation() {
        FactDefinition<Long> definition = FactDefinition
            .builder(DataCountFact.class, "data_count", Long.class)
            .allowedSources(FactSource.HOST_PROVIDER)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL)
            .maxLength(2)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(new FactDefinition.Codec<Long>() {
                @Override
                public Long normalize(Long value) {
                    return Math.abs(value);
                }

                @Override
                public String encode(Long value) {
                    return Long.toHexString(value);
                }

                @Override
                public Long decode(String encoded) {
                    return Long.valueOf(encoded, 16);
                }
            })
            .build();

        assertEquals(Long.valueOf(15L), definition.validateRaw(-15L));
        assertEquals("f", definition.encode(-15L));
        assertEquals(Long.valueOf(15L), definition.decode("f"));
        assertThrows(IllegalArgumentException.class, () -> definition.validateRaw(256L));
    }

    @Test
    void builtInFactsExposeDefinitionsButKeepTheirOwnershipMarkerInternal() {
        assertEquals(BuiltInFacts.DataCount.class, BuiltInFacts.DATA_COUNT.getFactType());
        assertEquals("data_count", BuiltInFacts.DATA_COUNT.getKey());
        assertFalse(Modifier.isPublic(BuiltInFactType.class.getModifiers()));
        assertThrows(UnsupportedOperationException.class, () -> BuiltInFacts.all().clear());
    }

    interface ExportContract extends ActionContract {
    }

    static final class FirstExport implements ActionType, ExportContract {
    }

    static final class SecondExport implements ActionType, ExportContract {
    }

    static final class UnrelatedAction implements ActionType {
    }

    static final class DataCountFact implements FactType<Long> {
    }
}
