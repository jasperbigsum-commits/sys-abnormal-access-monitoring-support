package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationFactsTest {
    @Test
    void registersProtectedLoginSubjectAndStageFacts() {
        FactCatalog catalog = new FactCatalog();
        BuiltInFacts.registerInto(catalog);
        catalog.freeze();

        assertTrue(catalog.asMap().containsKey(BuiltInFacts.LoginSubjectKey.class));
        FactDefinition<String> subject = BuiltInFacts.LOGIN_SUBJECT_KEY;
        assertEquals("login_subject_key", subject.getKey());
        assertEquals(FactDefinition.Sensitivity.SENSITIVE, subject.getSensitivity());
        assertEquals(FactDefinition.Storage.EXTENSION, subject.getStorage());
        assertTrue(subject.allows(FactSource.FRAMEWORK_OUTCOME));

        assertTrue(catalog.asMap().containsKey(BuiltInFacts.AuthenticationStageFact.class));
        FactDefinition<AuthenticationStage> stage = BuiltInFacts.AUTHENTICATION_STAGE;
        assertEquals("authentication_stage", stage.getKey());
        assertEquals(AuthenticationStage.CAPTCHA,
            stage.decode(stage.encode(AuthenticationStage.CAPTCHA)));
    }
}
