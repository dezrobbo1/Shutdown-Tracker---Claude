package com.shutdowntracker.api.support;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Whether a usable Docker CLI is on this machine.
 *
 * <p>Tests that need a real PostgreSQL container are skipped without one rather than failing. A
 * missing Docker CLI is a fact about the machine, not a defect in the code under test, and a suite
 * that always reports the same handful of errors stops being read.
 *
 * <p>This deliberately probes for the CLI instead of reading a property. A property has to be set
 * correctly wherever the suite runs, and the failure mode of forgetting is that real integration
 * coverage silently stops running in CI.
 *
 * <p>It lives outside the test classes that use it so the condition can be evaluated without
 * loading them: those classes start their container from a static initializer, which is exactly
 * what must not run when Docker is absent.
 */
public final class DockerAvailability {

    private DockerAvailability() {
    }

    public static boolean dockerCliIsAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            // A CLI with no reachable daemon exits non-zero, and is no more usable than no CLI.
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
