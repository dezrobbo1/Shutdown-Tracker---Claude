package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * The candidate schedule's authority guarantee is this comparison. If it can miss a difference,
 * the guarantee is only as strong as the writer's intent, which is exactly what it exists not to
 * rely on — so it is tested directly rather than only through a generated artifact.
 */
class MspdiCandidateDifferenceTests {

    private static final Map<String, Map<String, String>> APPROVED_PERCENT_ON_TASK_2 =
            Map.of("2", Map.of("PercentComplete", "50"));

    private static final String SOURCE = """
            <Project xmlns="http://schemas.microsoft.com/project">
              <Name>Synthetic</Name>
              <Calendars>
                <Calendar>
                  <UID>1</UID>
                  <WeekDays>
                    <WeekDay><DayType>1</DayType><DayWorking>0</DayWorking></WeekDay>
                    <WeekDay><DayType>2</DayType><DayWorking>1</DayWorking></WeekDay>
                    <WeekDay><DayType>3</DayType><DayWorking>1</DayWorking></WeekDay>
                  </WeekDays>
                </Calendar>
              </Calendars>
              <Tasks>
                <Task><UID>1</UID><ID>1</ID><Name>Summary</Name><Summary>1</Summary></Task>
                <Task><UID>2</UID><ID>2</ID><Name>Leaf</Name><PercentComplete>0</PercentComplete></Task>
                <Task>
                  <UID>3</UID><ID>3</ID><Name>Dependent</Name>
                  <PredecessorLink><PredecessorUID>2</PredecessorUID><Type>1</Type></PredecessorLink>
                  <PredecessorLink><PredecessorUID>1</PredecessorUID><Type>1</Type></PredecessorLink>
                </Task>
              </Tasks>
            </Project>
            """;

    @Test
    void reportsNothingWhenTheCandidateIsTheSource() {
        assertThat(differences(SOURCE, SOURCE)).isEmpty();
    }

    /**
     * The failure this class was extracted to fix. Matching children by element name alone compares
     * only the first {@code <WeekDay>}, so a calendar could be rewritten from the second day onward
     * without the comparison noticing.
     */
    @Test
    void reportsAChangeToARepeatedSiblingAfterTheFirst() {
        String candidate = SOURCE.replace(
                "<WeekDay><DayType>3</DayType><DayWorking>1</DayWorking></WeekDay>",
                "<WeekDay><DayType>3</DayType><DayWorking>0</DayWorking></WeekDay>"
        );

        assertThat(differences(SOURCE, candidate))
                .containsExactly("changed /Calendars#0/Calendar#0/WeekDays#0/WeekDay#2/DayWorking#0");
    }

    @Test
    void reportsARepeatedSiblingTheCandidateDropped() {
        String candidate = SOURCE.replace(
                "<PredecessorLink><PredecessorUID>1</PredecessorUID><Type>1</Type></PredecessorLink>",
                ""
        );

        assertThat(differences(SOURCE, candidate))
                .containsExactly("removed /Tasks#0/Task[3]#0/PredecessorLink#1");
    }

    @Test
    void reportsARepeatedSiblingTheCandidateAdded() {
        String candidate = SOURCE.replace(
                "</WeekDays>",
                "<WeekDay><DayType>4</DayType><DayWorking>1</DayWorking></WeekDay></WeekDays>"
        );

        assertThat(differences(SOURCE, candidate))
                .containsExactly("added /Calendars#0/Calendar#0/WeekDays#0/WeekDay#3");
    }

    @Test
    void reportsAChangeOutsideTheTasksAltogether() {
        assertThat(differences(SOURCE, SOURCE.replace("<Name>Synthetic</Name>", "<Name>Renamed</Name>")))
                .containsExactly("changed /Name#0");
    }

    @Test
    void acceptsAnApprovedFieldChangedOnTheTaskItWasApprovedFor() {
        String candidate = SOURCE.replace(
                "<UID>2</UID><ID>2</ID><Name>Leaf</Name><PercentComplete>0</PercentComplete>",
                "<UID>2</UID><ID>2</ID><Name>Leaf</Name><PercentComplete>50</PercentComplete>"
        );

        assertThat(differences(SOURCE, candidate, APPROVED_PERCENT_ON_TASK_2)).isEmpty();
    }

    @Test
    void acceptsAnApprovedFieldTheSourceDidNotCarry() {
        String source = SOURCE.replace("<PercentComplete>0</PercentComplete>", "");
        String candidate = SOURCE.replace(
                "<PercentComplete>0</PercentComplete>",
                "<PercentComplete>50</PercentComplete>"
        );

        assertThat(differences(source, candidate, APPROVED_PERCENT_ON_TASK_2)).isEmpty();
    }

    /** An approval for one task explains nothing about another. */
    @Test
    void reportsTheSameFieldChangedOnATaskItWasNotApprovedFor() {
        String source = SOURCE.replace(
                "<UID>3</UID><ID>3</ID><Name>Dependent</Name>",
                "<UID>3</UID><ID>3</ID><Name>Dependent</Name><PercentComplete>0</PercentComplete>"
        );
        String candidate = source.replace(
                "<Name>Dependent</Name><PercentComplete>0</PercentComplete>",
                "<Name>Dependent</Name><PercentComplete>50</PercentComplete>"
        );

        assertThat(differences(source, candidate, APPROVED_PERCENT_ON_TASK_2))
                .containsExactly("changed /Tasks#0/Task[3]#0/PercentComplete#0");
    }

    /** An approved field explains the first such element, not a duplicate of it. */
    @Test
    void reportsASecondCopyOfAnApprovedField() {
        String candidate = SOURCE.replace(
                "<PercentComplete>0</PercentComplete>",
                "<PercentComplete>50</PercentComplete><PercentComplete>50</PercentComplete>"
        );

        assertThat(differences(SOURCE, candidate, APPROVED_PERCENT_ON_TASK_2))
                .containsExactly("added /Tasks#0/Task[2]#0/PercentComplete#1");
    }

    /** A task's meaning is its UID, so a task compares against the task it is, not the one beside it. */
    @Test
    void comparesTasksByUidRatherThanByPosition() {
        String summaryTask = "<Task><UID>1</UID><ID>1</ID><Name>Summary</Name><Summary>1</Summary></Task>";
        String leafTask = "<Task><UID>2</UID><ID>2</ID><Name>Leaf</Name><PercentComplete>0</PercentComplete></Task>";
        String candidate = SOURCE
                .replace(summaryTask, "<!--swap-->")
                .replace(leafTask, summaryTask)
                .replace("<!--swap-->", leafTask);

        assertThat(candidate).isNotEqualTo(SOURCE);
        assertThat(differences(SOURCE, candidate)).isEmpty();
    }

    @Test
    void reportsAnAlteredAttribute() {
        String source = SOURCE.replace("<Tasks>", "<Tasks scope=\"all\">");
        String candidate = SOURCE.replace("<Tasks>", "<Tasks scope=\"approved\">");

        assertThat(differences(source, candidate)).containsExactly("changed /Tasks#0/@scope");
    }

    private List<String> differences(String source, String candidate) {
        return differences(source, candidate, Map.of());
    }

    private List<String> differences(
            String source,
            String candidate,
            Map<String, Map<String, String>> approved
    ) {
        return MspdiCandidateDifference.find(parse(source), parse(candidate), approved);
    }

    private Element parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
        } catch (Exception exception) {
            throw new AssertionError("Test document could not be parsed.", exception);
        }
    }
}
