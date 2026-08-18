package com.shutdowntracker.projectworker.exporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Reports every way a generated candidate schedule differs from the accepted source.
 *
 * <p>This is the proof that Shutdown Tracker authored nothing but the approved execution inputs.
 * The candidate is produced by editing the accepted source in place, and this comparison is what
 * turns that intent into evidence: anything the caller cannot explain with an approved
 * {@code (task UID, field)} pair is a difference, and generation fails.
 *
 * <p>It is deliberately a whole-document walk rather than a check of the fields that were written.
 * A check that only inspects what it meant to change cannot notice what it changed by accident,
 * which is the failure this exists to make impossible.
 *
 * <h2>Repeated sibling elements</h2>
 *
 * <p>MSPDI repeats element names constantly: a calendar carries seven {@code <WeekDay>} children,
 * a working day carries several {@code <WorkingTime>} children, a task carries one
 * {@code <PredecessorLink>} per dependency. Matching children by element name alone would compare
 * only the first of each and silently ignore the rest, so a dropped dependency or an altered
 * working time would pass. Children are therefore matched by name <em>and</em> occurrence, which
 * also makes a change in how many there are a difference rather than a blind spot.
 *
 * <p>Tasks are the exception: they are matched on UID so that like is compared with like, since a
 * task's meaning is its UID rather than its position in the file.
 */
final class MspdiCandidateDifference {

    private MspdiCandidateDifference() {
    }

    /**
     * Compares a candidate against the accepted source it was derived from.
     *
     * @param source              the accepted source document element
     * @param candidate           the generated candidate document element
     * @param approvedByTaskUid   Microsoft Project task UID to approved element name to canonical
     *                            value; a difference matching one of these is explained rather
     *                            than reported
     * @return every unexplained difference, in document order; empty when the candidate is the
     *         source with only the approved inputs applied
     */
    static List<String> find(
            Element source,
            Element candidate,
            Map<String, Map<String, String>> approvedByTaskUid
    ) {
        List<String> differences = new ArrayList<>();
        compare(source, candidate, "", approvedByTaskUid, differences);
        return List.copyOf(differences);
    }

    private static void compare(
            Element source,
            Element candidate,
            String path,
            Map<String, Map<String, String>> approved,
            List<String> differences
    ) {
        compareAttributes(source, candidate, path, differences);

        List<Element> sourceChildren = elementChildren(source);
        List<Element> candidateChildren = elementChildren(candidate);

        if (sourceChildren.isEmpty() && candidateChildren.isEmpty()) {
            if (!textOf(source).equals(textOf(candidate))) {
                differences.add("changed " + path);
            }
            return;
        }

        Map<String, Element> sourceByKey = indexByKey(sourceChildren);
        Map<String, Element> candidateByKey = indexByKey(candidateChildren);

        for (String key : sourceByKey.keySet()) {
            if (!candidateByKey.containsKey(key)) {
                differences.add("removed " + path + "/" + key);
            }
        }
        for (String key : candidateByKey.keySet()) {
            if (!sourceByKey.containsKey(key) && !isApprovedField(path, key, approved)) {
                differences.add("added " + path + "/" + key);
            }
        }
        for (Map.Entry<String, Element> entry : sourceByKey.entrySet()) {
            Element candidateChild = candidateByKey.get(entry.getKey());
            if (candidateChild == null || isApprovedField(path, entry.getKey(), approved)) {
                continue;
            }
            compare(entry.getValue(), candidateChild, path + "/" + entry.getKey(), approved, differences);
        }
    }

    /**
     * MSPDI carries its data in elements rather than attributes, but the candidate must be the
     * source in every respect, not only in the respects the writer happens to touch.
     *
     * <p>Namespace declarations are excluded: a serializer may legitimately move or repeat one
     * without changing what the document means.
     */
    private static void compareAttributes(
            Element source,
            Element candidate,
            String path,
            List<String> differences
    ) {
        Map<String, String> sourceAttributes = attributesOf(source);
        Map<String, String> candidateAttributes = attributesOf(candidate);

        for (Map.Entry<String, String> entry : sourceAttributes.entrySet()) {
            String candidateValue = candidateAttributes.get(entry.getKey());
            if (candidateValue == null) {
                differences.add("removed " + path + "/@" + entry.getKey());
            } else if (!candidateValue.equals(entry.getValue())) {
                differences.add("changed " + path + "/@" + entry.getKey());
            }
        }
        for (String name : candidateAttributes.keySet()) {
            if (!sourceAttributes.containsKey(name)) {
                differences.add("added " + path + "/@" + name);
            }
        }
    }

    private static Map<String, String> attributesOf(Element element) {
        Map<String, String> attributes = new LinkedHashMap<>();
        NamedNodeMap nodes = element.getAttributes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Attr attribute = (Attr) nodes.item(index);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                continue;
            }
            attributes.put(attribute.getName(), attribute.getValue());
        }
        return attributes;
    }

    /**
     * Keys children by identity and occurrence, so that the second and later siblings sharing an
     * element name are compared rather than collapsed onto the first.
     */
    private static Map<String, Element> indexByKey(List<Element> elements) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        Map<String, Element> byKey = new LinkedHashMap<>();
        for (Element element : elements) {
            String identity = identityOf(element);
            int occurrence = seen.merge(identity, 1, Integer::sum) - 1;
            byKey.put(identity + "#" + occurrence, element);
        }
        return byKey;
    }

    private static String identityOf(Element element) {
        if (!"Task".equals(element.getLocalName())) {
            return element.getLocalName();
        }
        Element uid = firstChild(element, "UID");
        return "Task[" + (uid == null ? "?" : textOf(uid)) + "]";
    }

    /**
     * Whether a difference at this key is one of the approved inputs for the task that owns it.
     *
     * <p>Only the first element of a given name inside a task can be an approved field. A second
     * {@code <PercentComplete>} is not an approved input however the file came to hold one.
     */
    private static boolean isApprovedField(
            String parentPath,
            String key,
            Map<String, Map<String, String>> approved
    ) {
        int occurrenceMark = key.lastIndexOf('#');
        if (occurrenceMark < 0 || !"0".equals(key.substring(occurrenceMark + 1))) {
            return false;
        }
        return approvedFieldsFor(parentPath, approved).containsKey(key.substring(0, occurrenceMark));
    }

    /** The approved fields for the task element the path ends at, or none if it does not. */
    private static Map<String, String> approvedFieldsFor(
            String parentPath,
            Map<String, Map<String, String>> approved
    ) {
        int start = parentPath.lastIndexOf("Task[");
        if (start < 0) {
            return Map.of();
        }
        int end = parentPath.indexOf(']', start);
        if (end < 0 || parentPath.indexOf('/', end) >= 0) {
            return Map.of();
        }
        return approved.getOrDefault(parentPath.substring(start + "Task[".length(), end), Map.of());
    }

    private static List<Element> elementChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element) {
                children.add(element);
            }
            child = child.getNextSibling();
        }
        return children;
    }

    private static Element firstChild(Element parent, String localName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static String textOf(Element element) {
        StringBuilder text = new StringBuilder();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
            child = child.getNextSibling();
        }
        return text.toString().trim();
    }
}
