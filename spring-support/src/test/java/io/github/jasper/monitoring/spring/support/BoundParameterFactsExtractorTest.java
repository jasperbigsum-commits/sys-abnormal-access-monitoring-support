package io.github.jasper.monitoring.spring.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.MonitorActionAttribute;
import io.github.jasper.monitoring.api.MonitorActionAttributeTarget;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoundParameterFactsExtractorTest {
    private final BoundParameterFactsExtractor extractor = new BoundParameterFactsExtractor();

    @Test
    void skipsCredentialLikePathSegmentsBeforeInvokingGetters() throws Exception {
        CredentialPayload payload = new CredentialPayload();

        MonitorActionFacts facts = extractor.extract(
            method("credentialPaths", CredentialPayload.class, CredentialPayload.class, CredentialPayload.class,
                CredentialPayload.class),
            new Object[] {payload, payload, payload, payload});

        assertTrue(facts.getAttributes().isEmpty());
        assertEquals(0, payload.getPasswordReads());
        assertEquals(0, payload.getTokenReads());
        assertEquals(0, payload.getPrivateKeyReads());
        assertEquals(0, payload.getSmsCodeReads());
    }

    @Test
    void bindsSafeCamelCasePath() throws Exception {
        MonitorActionFacts facts = extractor.extract(method("safeCamelCasePath", SafeCamelCasePayload.class),
            new Object[] {new SafeCamelCasePayload()});

        assertEquals("order-42", facts.getResourceId());
    }

    @Test
    void skipsEmptyPathCollectionsArraysMapsAndObjectsWithoutStringifyingThem() throws Exception {
        StringifiedMap map = new StringifiedMap();
        StringifiedPayload payload = new StringifiedPayload();

        MonitorActionFacts facts = extractor.extract(
            method("emptyPathUnsafe", List.class, Set.class, String[].class, Map.class, StringifiedPayload.class),
            new Object[] {Arrays.asList("report-1"), new LinkedHashSet<String>(Arrays.asList("report-2")),
                new String[] {"report-3"}, map, payload});

        assertTrue(facts.getAttributes().isEmpty());
        assertEquals(0, map.getToStringCalls());
        assertEquals(0, payload.getToStringCalls());
    }

    @Test
    void bindsAnEmptyPathScalarValue() throws Exception {
        MonitorActionFacts facts = extractor.extract(method("emptyPathScalar", String.class),
            new Object[] {"report-42"});

        assertEquals("report-42", facts.getResourceId());
        assertNull(facts.getOrgScope());
    }

    @Test
    void skipsCustomScalarImplementationsWithoutStringifyingThem() throws Exception {
        LeakingCharSequence characters = new LeakingCharSequence();
        LeakingNumber number = new LeakingNumber();

        MonitorActionFacts facts = extractor.extract(method("customScalars", CharSequence.class, Number.class),
            new Object[] {characters, number});

        assertTrue(facts.getAttributes().isEmpty());
        assertEquals(0, characters.getToStringCalls());
        assertEquals(0, number.getToStringCalls());
    }

    @Test
    void skipsCustomResolvedScalarsWithoutStringifyingThem() throws Exception {
        LeakingCharSequence characters = new LeakingCharSequence();
        LeakingNumber number = new LeakingNumber();

        MonitorActionFacts facts = extractor.extract(method("customResolvedScalars", ScalarPayload.class),
            new Object[] {new ScalarPayload(characters, number)});

        assertTrue(facts.getAttributes().isEmpty());
        assertEquals(0, characters.getToStringCalls());
        assertEquals(0, number.getToStringCalls());
    }

    @Test
    void reportsUnresolvedPathsWithoutExposingThePathValueOrGetterFailure() throws Exception {
        BoundParameterFactsExtractor.ExtractionResult result = extractor.extractWithDiagnostics(
            method("unresolvedPath", ExplodingPathPayload.class), new Object[] {new ExplodingPathPayload()});

        assertNull(result.getFacts().getResourceId());
        assertEquals(1, result.getIssues().size());
        EventInputIssue issue = result.getIssues().get(0);
        assertEquals("MONITOR-ACTION", issue.getRuleId());
        assertEquals("resourceId", issue.getFactName());
        assertEquals("UNRESOLVED_PARAMETER_PATH", issue.getIssueCode());
        assertEquals(EventFactSource.METHOD_PARAMETER.name(), issue.getSourceType());
        assertTrue(!issue.toString().contains("value"));
        assertTrue(!issue.toString().contains("must-not-leak"));
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        return Fixture.class.getDeclaredMethod(name, parameterTypes);
    }

    private static final class Fixture {
        @SuppressWarnings("unused")
        private void credentialPaths(
            @MonitorActionAttribute(name = "first", path = "password") CredentialPayload passwordPayload,
            @MonitorActionAttribute(name = "second", path = "token") CredentialPayload tokenPayload,
            @MonitorActionAttribute(name = "third", path = "privateKey") CredentialPayload privateKeyPayload,
            @MonitorActionAttribute(name = "fourth", path = "smsCode") CredentialPayload smsCodePayload) {
        }

        @SuppressWarnings("unused")
        private void safeCamelCasePath(
            @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "orderId")
            SafeCamelCasePayload payload) {
        }

        @SuppressWarnings("unused")
        private void emptyPathUnsafe(
            @MonitorActionAttribute(name = "list") List<String> list,
            @MonitorActionAttribute(name = "set") Set<String> set,
            @MonitorActionAttribute(name = "array") String[] array,
            @MonitorActionAttribute(name = "map") Map<String, String> map,
            @MonitorActionAttribute(name = "object") StringifiedPayload payload) {
        }

        @SuppressWarnings("unused")
        private void emptyPathScalar(
            @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID) String resourceId) {
        }

        @SuppressWarnings("unused")
        private void customScalars(
            @MonitorActionAttribute(name = "characters") CharSequence characters,
            @MonitorActionAttribute(name = "number") Number number) {
        }

        @SuppressWarnings("unused")
        private void customResolvedScalars(
            @MonitorActionAttribute(name = "characters", path = "characters")
            @MonitorActionAttribute(name = "number", path = "number") ScalarPayload payload) {
        }

        @SuppressWarnings("unused")
        private void unresolvedPath(
            @MonitorActionAttribute(target = MonitorActionAttributeTarget.RESOURCE_ID, path = "value")
            ExplodingPathPayload payload) {
        }
    }

    private static final class CredentialPayload {
        private int passwordReads;
        private int tokenReads;
        private int privateKeyReads;
        private int smsCodeReads;

        public String getPassword() {
            passwordReads++;
            return "not-recorded";
        }

        public String getToken() {
            tokenReads++;
            return "not-recorded";
        }

        public String getPrivateKey() {
            privateKeyReads++;
            return "not-recorded";
        }

        public String getSmsCode() {
            smsCodeReads++;
            return "not-recorded";
        }

        private int getPasswordReads() {
            return passwordReads;
        }

        private int getTokenReads() {
            return tokenReads;
        }

        private int getPrivateKeyReads() {
            return privateKeyReads;
        }

        private int getSmsCodeReads() {
            return smsCodeReads;
        }
    }

    private static final class SafeCamelCasePayload {
        public String getOrderId() {
            return "order-42";
        }
    }

    private static final class StringifiedPayload {
        private int toStringCalls;

        @Override
        public String toString() {
            toStringCalls++;
            return "not-recorded";
        }

        private int getToStringCalls() {
            return toStringCalls;
        }
    }

    private static final class StringifiedMap extends LinkedHashMap<String, String> {
        private int toStringCalls;

        @Override
        public String toString() {
            toStringCalls++;
            return "not-recorded";
        }

        private int getToStringCalls() {
            return toStringCalls;
        }
    }

    private static final class LeakingCharSequence implements CharSequence {
        private int toStringCalls;

        @Override
        public int length() {
            return 6;
        }

        @Override
        public char charAt(int index) {
            return "secret".charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return "secret".subSequence(start, end);
        }

        @Override
        public String toString() {
            toStringCalls++;
            return "secret";
        }

        private int getToStringCalls() {
            return toStringCalls;
        }
    }

    private static final class ScalarPayload {
        private final CharSequence characters;
        private final Number number;

        private ScalarPayload(CharSequence characters, Number number) {
            this.characters = characters;
            this.number = number;
        }

        public CharSequence getCharacters() {
            return characters;
        }

        public Number getNumber() {
            return number;
        }
    }

    private static final class LeakingNumber extends Number {
        private int toStringCalls;

        @Override
        public int intValue() {
            return 7;
        }

        @Override
        public long longValue() {
            return 7L;
        }

        @Override
        public float floatValue() {
            return 7.0F;
        }

        @Override
        public double doubleValue() {
            return 7.0D;
        }

        @Override
        public String toString() {
            toStringCalls++;
            return "secret";
        }

        private int getToStringCalls() {
            return toStringCalls;
        }
    }

    private static final class ExplodingPathPayload {
        public String getValue() {
            throw new IllegalStateException("must-not-leak");
        }
    }
}
