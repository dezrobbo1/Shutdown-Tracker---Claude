package com.shutdowntracker.projectworker.exporter;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The order MSPDI requires for the child elements of an {@code <Assignment>}.
 *
 * <p>Same rationale and mechanism as {@link MspdiTaskElementOrder}: MSPDI declares assignment
 * children as an {@code xsd:sequence}, so a derived completion field the accepted source did not
 * carry — an assignment with no recorded actuals gaining an {@code ActualFinish} — must land at
 * its schema position, and that order is read from MPXJ's JAXB binding rather than transcribed.
 */
final class MspdiAssignmentElementOrder {

    private static final String MSPDI_ASSIGNMENT_CLASS =
            "org.mpxj.mspdi.schema.Project$Assignments$Assignment";

    /** Element local name to its position in the schema sequence. */
    private static final Map<String, Integer> POSITIONS = loadPositions();

    private MspdiAssignmentElementOrder() {
    }

    /**
     * The schema position of an {@code <Assignment>} child element.
     *
     * @throws IllegalArgumentException if the element is not part of the MSPDI assignment sequence
     */
    static int positionOf(String elementLocalName) {
        Integer position = POSITIONS.get(elementLocalName);
        if (position == null) {
            throw new IllegalArgumentException(
                    "'" + elementLocalName + "' is not an MSPDI assignment element."
            );
        }
        return position;
    }

    /**
     * The schema position of an element the candidate did not write, when this binding models it.
     * Empty for an element the binding does not know; see
     * {@link MspdiTaskElementOrder#knownPositionOf(String)} for why that must not stop placement.
     */
    static OptionalInt knownPositionOf(String elementLocalName) {
        Integer position = POSITIONS.get(elementLocalName);
        return position == null ? OptionalInt.empty() : OptionalInt.of(position);
    }

    private static Map<String, Integer> loadPositions() {
        Class<?> assignmentClass;
        try {
            assignmentClass = Class.forName(MSPDI_ASSIGNMENT_CLASS);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "MPXJ's MSPDI assignment binding is required to place derived completion "
                            + "fields in schema order.",
                    exception
            );
        }

        XmlType xmlType = assignmentClass.getAnnotation(XmlType.class);
        if (xmlType == null || xmlType.propOrder().length == 0) {
            throw new IllegalStateException(
                    "MPXJ's MSPDI assignment binding no longer declares an element order."
            );
        }

        Map<String, String> elementNamesByProperty = new HashMap<>();
        for (Field field : assignmentClass.getDeclaredFields()) {
            XmlElement element = field.getAnnotation(XmlElement.class);
            boolean named = element != null && !"##default".equals(element.name());
            elementNamesByProperty.put(field.getName(), named ? element.name() : field.getName());
        }

        List<String> ordered = new ArrayList<>();
        for (String property : xmlType.propOrder()) {
            String elementName = elementNamesByProperty.get(property);
            if (elementName == null) {
                throw new IllegalStateException(
                        "MPXJ's MSPDI assignment binding declares an element order entry with no "
                                + "matching field: " + property
                );
            }
            ordered.add(elementName);
        }

        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            positions.putIfAbsent(ordered.get(index), index);
        }
        return Map.copyOf(positions);
    }
}
