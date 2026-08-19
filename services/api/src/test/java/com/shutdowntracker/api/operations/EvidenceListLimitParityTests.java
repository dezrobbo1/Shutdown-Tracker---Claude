package com.shutdowntracker.api.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Keeps the console's copy of the project evidence limit honest against the server's.
 *
 * <p>The console says "the most recent 200 records" when a list comes back full, so that a reader
 * knows the list was cut rather than assuming it is all the evidence there is. That sentence is
 * only true while the number matches the server's, and a stale copy fails in the worse direction:
 * it would stop warning at all, and a truncated list would read as complete.
 *
 * <p>Compared here rather than kept in step by hand, following
 * {@code CapabilityClientParityTests}.
 */
class EvidenceListLimitParityTests {

    private static final Pattern CONSOLE_LIMIT =
            Pattern.compile("export const PROJECT_EVIDENCE_LIMIT\\s*=\\s*(\\d+)\\s*;");

    @Test
    void theConsoleWarnsAtTheSameCountTheServerStopsAt() throws IOException {
        String source = Files.readString(consoleEvidenceZone());
        Matcher matcher = CONSOLE_LIMIT.matcher(source);

        assertThat(matcher.find())
                .describedAs("the console declares a project evidence limit")
                .isTrue();
        assertThat(Integer.parseInt(matcher.group(1)))
                .describedAs("console PROJECT_EVIDENCE_LIMIT matches the server's")
                .isEqualTo(OperationalRecordService.PROJECT_EVIDENCE_LIMIT);
    }

    /** Walks up from the working directory so the test runs from the module or the root. */
    private Path consoleEvidenceZone() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path zone = candidate.resolve("apps").resolve("console").resolve("src")
                    .resolve("zones").resolve("EvidenceZone.tsx");
            if (Files.isRegularFile(zone)) {
                return zone;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate apps/console/src/zones/EvidenceZone.tsx from " + Path.of("").toAbsolutePath());
    }
}
