package io.github.jasper.monitoring.maven;

import java.io.File;
import java.io.IOException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Initializes conservative integration templates for an abnormal-access monitoring host. */
@Mojo(name = "initialize", requiresProject = false, threadSafe = true)
public final class InitializeMojo extends AbstractMojo {
    /** Directory that receives generated templates. Existing files are never overwritten. */
    @Parameter(property = "abnormal.access.monitor.outputDirectory",
        defaultValue = "${project.basedir}/src/main/resources/abnormal-access-monitoring")
    private File outputDirectory;

    /** Java package to use in host SPI templates. */
    @Parameter(property = "abnormal.access.monitor.packageName", defaultValue = "com.example.monitoring")
    private String packageName;

    /** Stable host system identifier used in the generated starter configuration. */
    @Parameter(property = "abnormal.access.monitor.systemId", defaultValue = "change-me")
    private String systemId;

    @Override
    public void execute() throws MojoExecutionException {
        if (outputDirectory == null) {
            throw new MojoExecutionException("Unable to resolve outputDirectory; set -Dabnormal.access.monitor.outputDirectory");
        }
        try {
            StarterFilesGenerator.GenerationResult result = StarterFilesGenerator.generate(
                outputDirectory.toPath(), packageName, systemId);
            for (java.nio.file.Path created : result.getCreatedFiles()) {
                getLog().info("Created abnormal-access monitoring template: " + created);
            }
            for (java.nio.file.Path skipped : result.getSkippedFiles()) {
                getLog().warn("Preserved existing file: " + skipped);
            }
        } catch (IllegalArgumentException exception) {
            throw new MojoExecutionException("Invalid abnormal-access monitoring plugin parameter: " + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new MojoExecutionException("Unable to initialize abnormal-access monitoring templates", exception);
        }
    }
}
