package com.shutdowntracker.projectworker.exporter;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order MSPDI requires for the child elements of a {@code <Task>}.
 *
 * <p>MSPDI declares those children as an {@code xsd:sequence}, so an element inserted in the wrong
 * position produces a document Microsoft Project may reject. When a candidate adds a field that the
 * accepted source did not carry — a task with no recorded progress gaining a {@code PercentComplete}
 * — the new element has to land in the right place rather than at the end.
 *
 * <p>The order is read from MPXJ's own JAXB binding for the MSPDI schema rather than transcribed
 * into a list here. A hand-maintained copy of 109 element names would be one upstream schema change
 * away from being silently wrong, and nothing in the test suite would notice until a planner could
 * not open a candidate.
 */
final class MspdiTaskElementOrder {

    private static final String MSPDI_TASK_CLASS = "org.mpxj.mspdi.schema.Project$Tasks$Task";

    /** Element local name to its position in the schema sequence. */
    private static final Map<String, Integer> POSITIONS = loadPositions();

    private MspdiTaskElementOrder() {
    }

    /**
     * The schema position of a {@code <Task>} child element.
     *
     * @throws IllegalArgumentException if the element is not part of the MSPDI task sequence
     */
    static int positionOf(String elementLocalName) {
        Integer position = POSITIONS.get(elementLocalName);
        if (position == null) {
            throw new IllegalArgumentException(
                    "'" + elementLocalName + "' is not an MSPDI task element."
            );
        }
        return position;
    }

    /**
     * The schema position of an element the candidate did not write, used only to decide where an
     * inserted element belongs relative to it.
     *
     * <p>Unknown elements sort last. A source file may legitimately carry extension elements this
     * MSPDI binding does not model, and refusing to place an approved field because of one would
     * fail a candidate over something unrelated to the approved input.
     */
    static int positionOfOrLast(String elementLocalName) {
        return POSITIONS.getOrDefault(elementLocalName, Integer.MAX_VALUE);
    }

    private static Map<String, Integer> loadPositions() {
        Class<?> taskClass;
        try {
            taskClass = Class.forName(MSPDI_TASK_CLASS);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "MPXJ's MSPDI task binding is required to place approved fields in schema order.",
                    exception
            );
        }

        XmlType xmlType = taskClass.getAnnotation(XmlType.class);
        if (xmlType == null || xmlType.propOrder().length == 0) {
            throw new IllegalStateException(
                    "MPXJ's MSPDI task binding no longer declares an element order."
            );
        }

        Map<String, String> elementNamesByProperty = new HashMap<>();
        for (Field field : taskClass.getDeclaredFields()) {
            XmlElement element = field.getAnnotation(XmlElement.class);
            boolean named = element != null && !"##default".equals(element.name());
            elementNamesByProperty.put(field.getName(), named ? element.name() : field.getName());
        }

        List<String> ordered = new ArrayList<>();
        for (String property : xmlType.propOrder()) {
            ordered.add(elementNamesByProperty.getOrDefault(property, property));
        }

        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            positions.putIfAbsent(ordered.get(index), index);
        }
        return Map.copyOf(positions);
    }
}
