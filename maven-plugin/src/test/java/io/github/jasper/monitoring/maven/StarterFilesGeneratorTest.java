package io.github.jasper.monitoring.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StarterFilesGeneratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesSafeStarterFilesInConfiguredDirectory() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated-monitoring");

        StarterFilesGenerator.GenerationResult result = StarterFilesGenerator.generate(
            outputDirectory, "com.example.orders.monitoring", "orders-service");

        assertEquals(4, result.getCreatedFiles().size());
        assertTrue(result.getSkippedFiles().isEmpty());
        String yaml = read(outputDirectory.resolve("application-abnormal-access-monitoring.yml"));
        assertTrue(yaml.contains("abnormal:\n  access:\n    monitor:"));
        assertTrue(yaml.contains("mode: OBSERVE"));
        assertTrue(yaml.contains("system-id: orders-service"));
        assertFalse(yaml.toLowerCase().contains("password"));

        String hostSpi = read(outputDirectory.resolve("host-spi/HostMonitoringSpi.java"));
        assertTrue(hostSpi.contains("package com.example.orders.monitoring;"));
        assertTrue(hostSpi.contains("IdentityContextProvider, ResourceScopeAuthorizer"));
        assertTrue(hostSpi.contains("EventEnricher, TrustedProxyResolver"));
        assertTrue(hostSpi.contains("HOST_SCOPE_AUTHORIZATION_NOT_IMPLEMENTED"));

        String controlHandler = read(outputDirectory.resolve("host-spi/HostControlHandler.java"));
        assertTrue(controlHandler.contains("implements ControlHandler"));
        assertTrue(controlHandler.contains("HOST_CONTROL_NOT_IMPLEMENTED"));

        String frontendSignal = read(outputDirectory.resolve("frontend-signal-v1.example.json"));
        assertTrue(frontendSignal.contains("\"contract_version\": \"1.0\""));
        assertTrue(frontendSignal.contains("\"device_id_hash\": \"sha256:"));
    }

    @Test
    void preservesExistingFilesInsteadOfOverwritingThem() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("safe-output");
        Files.createDirectories(outputDirectory);
        Path configuration = outputDirectory.resolve("application-abnormal-access-monitoring.yml");
        Files.write(configuration, "keep-this-value\n".getBytes(StandardCharsets.UTF_8));

        StarterFilesGenerator.GenerationResult result = StarterFilesGenerator.generate(
            outputDirectory, "com.example.monitoring", "sample-system");

        assertEquals("keep-this-value\n", read(configuration));
        assertTrue(result.getSkippedFiles().contains(configuration));
        assertEquals(3, result.getCreatedFiles().size());
    }

    @Test
    void rejectsUnsafeTemplateParameters() {
        assertThrows(IllegalArgumentException.class, () -> StarterFilesGenerator.generate(
            temporaryDirectory, "com.example; import java.io.File;", "sample-system"));
        assertThrows(IllegalArgumentException.class, () -> StarterFilesGenerator.generate(
            temporaryDirectory, "com.example.monitoring", "unsafe\nvalue"));
    }

    @Test
    void initializeMojoUsesConfiguredOutputDirectory() throws Exception {
        Path outputDirectory = temporaryDirectory.resolve("mojo-output");
        InitializeMojo mojo = new InitializeMojo();
        setField(mojo, "outputDirectory", outputDirectory.toFile());
        setField(mojo, "packageName", "com.example.host");
        setField(mojo, "systemId", "host-service");

        mojo.execute();

        assertTrue(Files.exists(outputDirectory.resolve("application-abnormal-access-monitoring.yml")));
        assertTrue(Files.exists(outputDirectory.resolve("host-spi/HostMonitoringSpi.java")));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
