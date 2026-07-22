package io.github.jasper.monitoring.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Generates conservative host-side integration templates without changing existing files. */
final class StarterFilesGenerator {
    private static final Pattern JAVA_PACKAGE = Pattern.compile(
        "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern SYSTEM_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private StarterFilesGenerator() {
    }

    static GenerationResult generate(Path outputDirectory, String packageName, String systemId) throws IOException {
        Path output = validateOutputDirectory(outputDirectory);
        String safePackageName = validatePackageName(packageName);
        String safeSystemId = validateSystemId(systemId);
        List<Path> created = new ArrayList<Path>();
        List<Path> skipped = new ArrayList<Path>();

        writeNew(output, "application-abnormal-access-monitoring.yml", configuration(safeSystemId), created, skipped);
        writeNew(output, "host-spi/HostMonitoringSpi.java", hostMonitoringSpi(safePackageName), created, skipped);
        writeNew(output, "host-spi/HostControlHandler.java", hostControlHandler(safePackageName), created, skipped);
        writeNew(output, "frontend-signal-v1.example.json", frontendSignal(), created, skipped);
        return new GenerationResult(created, skipped);
    }

    private static Path validateOutputDirectory(Path outputDirectory) throws IOException {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory is required");
        }
        Path normalized = outputDirectory.toAbsolutePath().normalize();
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            throw new IOException("Output path is not a directory: " + normalized);
        }
        Files.createDirectories(normalized);
        return normalized;
    }

    private static String validatePackageName(String packageName) {
        if (packageName == null || !JAVA_PACKAGE.matcher(packageName).matches()) {
            throw new IllegalArgumentException("packageName must be a valid Java package name");
        }
        return packageName;
    }

    private static String validateSystemId(String systemId) {
        if (systemId == null || !SYSTEM_ID.matcher(systemId).matches()) {
            throw new IllegalArgumentException("systemId may contain only letters, numbers, dots, underscores, and hyphens");
        }
        return systemId;
    }

    private static void writeNew(Path outputDirectory, String relativeFile, String content,
                                 List<Path> created, List<Path> skipped) throws IOException {
        Path target = outputDirectory.resolve(relativeFile).normalize();
        if (!target.startsWith(outputDirectory)) {
            throw new IOException("Refusing to write outside configured output directory");
        }
        Path parent = target.getParent();
        if (Files.exists(parent) && !Files.isDirectory(parent)) {
            throw new IOException("Template parent is not a directory: " + parent);
        }
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            skipped.add(target);
            return;
        }
        try {
            Files.write(target, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
            created.add(target);
        } catch (FileAlreadyExistsException ignored) {
            skipped.add(target);
        }
    }

    private static String configuration(String systemId) {
        return "# Generated as a safe starting point. Complete host SPI and database migration before switching to ENFORCE.\n"
            + "abnormal:\n"
            + "  access:\n"
            + "    monitor:\n"
            + "      system-id: " + systemId + "\n"
            + "      mode: OBSERVE\n"
            + "      frontend:\n"
            + "        enabled: true\n"
            + "      trusted-proxies: []\n";
    }

    private static String hostMonitoringSpi(String packageName) {
        return "package " + packageName + ";\n\n"
            + "import io.github.jasper.monitoring.api.AuthorizationDecision;\n"
            + "import io.github.jasper.monitoring.api.EventEnricher;\n"
            + "import io.github.jasper.monitoring.api.IdentityContext;\n"
            + "import io.github.jasper.monitoring.api.IdentityContextProvider;\n"
            + "import io.github.jasper.monitoring.api.MonitoringRequestContext;\n"
            + "import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;\n"
            + "import io.github.jasper.monitoring.api.ResourceScopeRequest;\n"
            + "import io.github.jasper.monitoring.api.SecurityEventDraft;\n"
            + "import io.github.jasper.monitoring.api.TrustedProxyResolver;\n\n"
            + "/**\n"
            + " * Host bridge template. Replace the conservative defaults with the application's\n"
            + " * server-side authentication, resource authorization, and trusted proxy policy.\n"
            + " */\n"
            + "public final class HostMonitoringSpi implements IdentityContextProvider, ResourceScopeAuthorizer,\n"
            + "    EventEnricher, TrustedProxyResolver {\n\n"
            + "    @Override\n"
            + "    public IdentityContext resolve(MonitoringRequestContext request) {\n"
            + "        // TODO: Resolve identity only from the host's authenticated server-side context.\n"
            + "        return IdentityContext.anonymous();\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    public AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest request) {\n"
            + "        // TODO: Delegate to the host's resource-level authorization service.\n"
            + "        return AuthorizationDecision.denied(\"HOST_SCOPE_AUTHORIZATION_NOT_IMPLEMENTED\");\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    public SecurityEventDraft enrich(SecurityEventDraft draft, MonitoringRequestContext request,\n"
            + "                                      IdentityContext identity) {\n"
            + "        // TODO: Add only approved non-sensitive business attributes.\n"
            + "        return draft;\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    public String resolveClientIp(String directRemoteAddress, String forwardedForHeader) {\n"
            + "        // TODO: Parse forwardedForHeader only after verifying a configured trusted proxy.\n"
            + "        return directRemoteAddress;\n"
            + "    }\n"
            + "}\n";
    }

    private static String hostControlHandler(String packageName) {
        return "package " + packageName + ";\n\n"
            + "import io.github.jasper.monitoring.api.ControlActionType;\n"
            + "import io.github.jasper.monitoring.core.ControlCommand;\n"
            + "import io.github.jasper.monitoring.core.ControlExecution;\n"
            + "import io.github.jasper.monitoring.core.ControlHandler;\n\n"
            + "/**\n"
            + " * Conservative control adapter. Enable only the actions the host can execute idempotently.\n"
            + " */\n"
            + "public final class HostControlHandler implements ControlHandler {\n\n"
            + "    @Override\n"
            + "    public boolean supports(ControlActionType action) {\n"
            + "        // TODO: Return true only for explicitly implemented, idempotent actions.\n"
            + "        return false;\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    public ControlExecution execute(ControlCommand command) {\n"
            + "        // TODO: Apply the control and persist/replay by command.getIdempotencyKey().\n"
            + "        return ControlExecution.skipped(command.getIdempotencyKey(), \"HOST_CONTROL_NOT_IMPLEMENTED\");\n"
            + "    }\n"
            + "}\n";
    }

    private static String frontendSignal() {
        return "{\n"
            + "  \"contract_version\": \"1.0\",\n"
            + "  \"client_event_id\": \"00000000-0000-4000-8000-000000000000\",\n"
            + "  \"occurred_at\": \"2026-01-01T00:00:00Z\",\n"
            + "  \"request_id\": \"replace-with-server-request-id\",\n"
            + "  \"route\": \"/replace-with-route\",\n"
            + "  \"action\": \"VIEW\",\n"
            + "  \"device_id_hash\": \"sha256:0000000000000000000000000000000000000000000000000000000000000000\",\n"
            + "  \"resource_type\": \"replace-with-resource-type\",\n"
            + "  \"resource_id_hash\": \"sha256:0000000000000000000000000000000000000000000000000000000000000000\",\n"
            + "  \"attributes\": {\n"
            + "    \"feature\": \"replace-with-feature\",\n"
            + "    \"interaction\": \"replace-with-interaction\"\n"
            + "  }\n"
            + "}\n";
    }

    static final class GenerationResult {
        private final List<Path> createdFiles;
        private final List<Path> skippedFiles;

        private GenerationResult(List<Path> createdFiles, List<Path> skippedFiles) {
            this.createdFiles = Collections.unmodifiableList(new ArrayList<Path>(createdFiles));
            this.skippedFiles = Collections.unmodifiableList(new ArrayList<Path>(skippedFiles));
        }

        List<Path> getCreatedFiles() {
            return createdFiles;
        }

        List<Path> getSkippedFiles() {
            return skippedFiles;
        }
    }
}
