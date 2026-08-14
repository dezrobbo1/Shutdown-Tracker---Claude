package com.shutdowntracker.api.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the client's capability map honest against this enum.
 *
 * The console and field app hide or disable controls the caller cannot use. That is a
 * usability layer — the server is the authority — but a stale copy is still a real defect in
 * both directions: it either offers an action that will be refused, or hides one the user is
 * entitled to. Neither is discoverable by reading one side alone, so the two are compared
 * here rather than kept in step by hand.
 */
class CapabilityClientParityTests {

    private static final Pattern ENTRY = Pattern.compile(
            "^\\s{2}([A-Z_]+):\\s*\\[(.*?)],?\\s*$", Pattern.MULTILINE | Pattern.DOTALL);

    @Test
    void theClientGrantsExactlyWhatTheServerGrants() throws IOException {
        Map<String, Set<String>> client = parseClientCapabilities();
        Map<String, Set<String>> server = serverCapabilities();

        assertThat(client.keySet())
                .describedAs("every capability the server enforces is known to the client, and no others")
                .containsExactlyInAnyOrderElementsOf(server.keySet());

        for (Map.Entry<String, Set<String>> entry : server.entrySet()) {
            assertThat(client.get(entry.getKey()))
                    .describedAs("roles allowed %s", entry.getKey())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    @Test
    void exportApprovalStaysWithThePlannerOnBothSides() throws IOException {
        Map<String, Set<String>> client = parseClientCapabilities();

        assertThat(client.get("APPROVE_EXPORT_BATCH"))
                .describedAs("administering access is not approving what returns to Microsoft Project")
                .containsExactly("planner");
        assertThat(Capability.APPROVE_EXPORT_BATCH.allows(ProjectRole.ADMIN)).isFalse();
    }

    @Test
    void supervisorReviewNeverCarriesAnExportCapability() throws IOException {
        Map<String, Set<String>> client = parseClientCapabilities();

        for (String capability : Set.of(
                "APPROVE_EXPORT_BATCH", "GENERATE_EXPORT_ARTIFACT", "RECORD_EXPORT_VERIFICATION")) {
            assertThat(client.get(capability))
                    .describedAs("%s must not be granted to a supervisor", capability)
                    .doesNotContain("supervisor");
        }
        assertThat(Capability.REVIEW_TASK_PROGRESS.allows(ProjectRole.SUPERVISOR)).isTrue();
    }

    private Map<String, Set<String>> serverCapabilities() {
        Map<String, Set<String>> capabilities = new LinkedHashMap<>();
        for (Capability capability : Capability.values()) {
            capabilities.put(
                    capability.name(),
                    capability.allowedRoles().stream()
                            .map(ProjectRole::databaseValue)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }
        return capabilities;
    }

    /**
     * Reads the roles out of the client's {@code capabilityRoles} literal.
     *
     * Parsing the source rather than running Node keeps this test a plain unit test with no
     * toolchain dependency, at the cost of being tied to the literal's shape. If the shape
     * changes the parse finds nothing, and the assertion on the key set fails loudly rather
     * than passing on an empty comparison.
     */
    private Map<String, Set<String>> parseClientCapabilities() throws IOException {
        String source = Files.readString(clientIdentityModule());
        int start = source.indexOf("const capabilityRoles");
        assertThat(start).describedAs("capabilityRoles literal is present in identity.ts").isNotNegative();
        String body = source.substring(start, source.indexOf("};", start));

        Map<String, Set<String>> capabilities = new LinkedHashMap<>();
        Matcher matcher = ENTRY.matcher(body);
        while (matcher.find()) {
            Set<String> roles = Arrays.stream(matcher.group(2).split(","))
                    .map(role -> role.replaceAll("[\"'\\s]", ""))
                    .filter(role -> !role.isEmpty())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            capabilities.put(matcher.group(1), roles);
        }

        assertThat(capabilities)
                .describedAs("the client capability literal parsed into entries")
                .isNotEmpty();
        return capabilities;
    }

    /** Walks up from the working directory so the test runs from the module or the root. */
    private Path clientIdentityModule() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path module = candidate
                    .resolve("packages").resolve("api-client").resolve("src").resolve("identity.ts");
            if (Files.isRegularFile(module)) {
                return module;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate packages/api-client/src/identity.ts from " + Path.of("").toAbsolutePath());
    }
}
