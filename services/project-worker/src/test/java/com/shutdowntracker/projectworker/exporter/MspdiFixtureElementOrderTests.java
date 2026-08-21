package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Every committed MSPDI fixture, checked against the schema sequence its own exporter uses.
 *
 * <p>MSPDI declares the children of {@code <Task>} as an {@code xsd:sequence}. A fixture that
 * violates it may never open in Microsoft Project at all, which would leave every expected-output
 * test in the repository validating a document Project would reject — the tests would pass and the
 * fixture would be worthless.
 *
 * <p>The order is not transcribed here. It comes from {@link MspdiTaskElementOrder}, which reflects
 * over MPXJ's own JAXB binding, so this asserts fixtures against exactly the authority the writer
 * uses rather than against a second copy of it that could drift.
 *
 * <p>This is also what makes hand-authoring a fixture safe. Generating one through MPXJ would give
 * correct ordering by construction, but at the cost of hundreds of defaulted elements and
 * writer-version markers in a file the fixture policy requires a person to review — and it would
 * still leave the existing fixture, which nothing checked, unguarded.
 */
class MspdiFixtureElementOrderTests {

    static Stream<Path> committedFixtures() throws IOException {
        try (Stream<Path> files = Files.walk(repositoryRoot().resolve("fixtures/import-export"))) {
            List<Path> fixtures = files
                    .filter(path -> path.getFileName().toString().endsWith(".mspdi.xml"))
                    .sorted()
                    .toList();
            // A discovery test that silently discovers nothing is the failure mode worth guarding.
            Assertions.assertFalse(fixtures.isEmpty(), "no committed MSPDI fixtures were found");
            return fixtures.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("committedFixtures")
    void taskChildrenFollowTheSchemaSequence(Path fixture) throws Exception {
        List<String> violations = new ArrayList<>();

        for (Element task : tasksIn(fixture)) {
            int highestSoFar = Integer.MIN_VALUE;
            String previousName = null;
            for (Element child : childElementsOf(task)) {
                OptionalInt position = MspdiTaskElementOrder.knownPositionOf(child.getLocalName());
                if (position.isEmpty()) {
                    violations.add(taskLabel(task) + ": <" + child.getLocalName()
                            + "> is not an element the MSPDI task binding models");
                    continue;
                }
                if (position.getAsInt() < highestSoFar) {
                    violations.add(taskLabel(task) + ": <" + child.getLocalName() + "> at position "
                            + position.getAsInt() + " follows <" + previousName + "> at " + highestSoFar);
                }
                highestSoFar = Math.max(highestSoFar, position.getAsInt());
                previousName = child.getLocalName();
            }
        }

        assertThat(violations)
                .describedAs("%s must be a document Microsoft Project can open", fixture.getFileName())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("committedFixtures")
    void everyFixtureCarriesAtLeastOneTask(Path fixture) throws Exception {
        // Cheap, and it stops the ordering check above passing vacuously on a file that parsed but
        // contained nothing to order.
        assertThat(tasksIn(fixture))
                .describedAs("%s parsed but holds no tasks", fixture.getFileName())
                .isNotEmpty();
    }

    private static List<Element> tasksIn(Path fixture) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // A fixture is repository content, but the parser should still not be talked into
        // fetching anything while reading one.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(fixture.toFile());
        NodeList nodes = document.getElementsByTagNameNS(
                "http://schemas.microsoft.com/project", "Task");

        List<Element> tasks = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            tasks.add((Element) nodes.item(index));
        }
        return tasks;
    }

    private static List<Element> childElementsOf(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private static String taskLabel(Element task) {
        for (Element child : childElementsOf(task)) {
            if ("UID".equals(child.getLocalName())) {
                return "task UID " + child.getTextContent();
            }
        }
        return "task with no UID";
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("fixtures/import-export"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root with an import/export fixture folder was not found.");
    }
}
