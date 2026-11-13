package bpmn;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Strict supported-subset reader, independent of optional XSD validation. */
public final class BpmnReader {
    private static final String BPMN = BpmnExporter.NAMESPACE;
    private static final Set<String> DI = Set.of(
        "http://www.omg.org/spec/BPMN/20100524/DI",
        "http://www.omg.org/spec/DD/20100524/DI",
        "http://www.omg.org/spec/DD/20100524/DC");

    public Model.Document read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return parse(new InputSource(input));
        }
    }

    public Model.Document read(String xml) {
        try { return parse(new InputSource(new StringReader(xml))); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot read XML", ex); }
    }

    private Model.Document parse(InputSource source) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External XML resources are forbidden");
            });
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
            });
            Element root = builder.parse(source).getDocumentElement();
            require(root, "definitions");
            uniqueIds(root, new HashSet<>());
            attributes(root, "id", "targetNamespace", "name", "exporter", "exporterVersion");
            Element process = null;
            for (Element child : children(root)) {
                if (is(child, "documentation")) documentation(child);
                else if (DI.contains(child.getNamespaceURI()) && "BPMNDiagram".equals(child.getLocalName())
                        && "http://www.omg.org/spec/BPMN/20100524/DI".equals(child.getNamespaceURI())) diagram(child);
                else if (is(child, "process")) {
                    if (process != null) throw invalid("Multiple processes are unsupported");
                    process = child;
                } else throw unsupported(child);
            }
            if (process == null) throw invalid("Exactly one process is required");
            return new Model.Document(required(root, "id"), required(root, "targetNamespace"), process(process));
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("Secure XML parser unavailable", ex);
        } catch (SAXException ex) {
            throw invalid("Invalid XML: " + ex.getMessage());
        }
    }

    private static Model.Process process(Element process) {
        attributes(process, "id", "name", "isExecutable");
        if (process.hasAttribute("isExecutable") && !Set.of("false", "0").contains(process.getAttribute("isExecutable")))
            throw invalid("Only non-executable processes are supported");
        var nodes = new ArrayList<Model.Node>();
        var flows = new ArrayList<Model.Flow>();
        var nodeElements = new ArrayList<Element>();
        for (Element child : children(process)) {
            if (is(child, "documentation")) { documentation(child); continue; }
            if (is(child, "sequenceFlow")) {
                attributes(child, "id", "name", "sourceRef", "targetRef");
                for (Element nested : children(child)) {
                    if (is(nested, "documentation")) documentation(nested);
                    else throw unsupported(nested);
                }
                flows.add(new Model.Flow(required(child, "id"), child.getAttribute("name"),
                    required(child, "sourceRef"), required(child, "targetRef")));
                continue;
            }
            Model.Kind kind = null;
            for (var candidate : Model.Kind.values()) if (is(child, candidate.element)) kind = candidate;
            if (kind == null) throw unsupported(child);
            attributes(child, "id", "name");
            nodes.add(new Model.Node(required(child, "id"), child.getAttribute("name"), kind));
            nodeElements.add(child);
        }
        var result = new Model.Process(required(process, "id"), process.getAttribute("name"), nodes, flows);
        for (Element node : nodeElements) {
            var incoming = new HashSet<String>();
            var outgoing = new HashSet<String>();
            for (Element child : children(node)) {
                if (is(child, "documentation")) documentation(child);
                else if (is(child, "incoming") || is(child, "outgoing")) {
                    attributes(child);
                    if (!children(child, true).isEmpty()) throw unsupported(child);
                    String ref = child.getTextContent().strip();
                    boolean in = is(child, "incoming");
                    if (!(in ? incoming : outgoing).add(ref)) throw invalid("Duplicate flow reference: " + ref);
                    boolean found = flows.stream().anyMatch(f -> f.id().equals(ref)
                        && (in ? f.target() : f.source()).equals(node.getAttribute("id")));
                    if (!found) throw invalid("Invalid " + child.getLocalName() + " reference: " + ref);
                } else throw unsupported(child);
            }
        }
        return result;
    }

    private static void documentation(Element element) {
        attributes(element, "id", "textFormat");
        if (!children(element, true).isEmpty()) throw invalid("Only text documentation is supported");
    }

    private static void diagram(Element element) {
        if (!DI.contains(element.getNamespaceURI())) throw unsupported(element);
        for (Element child : children(element)) diagram(child);
    }

    private static void uniqueIds(Element element, Set<String> ids) {
        if (element.hasAttribute("id")) {
            String id = required(element, "id");
            if (!ids.add(id)) throw invalid("Duplicate ID: " + id);
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element nested) uniqueIds(nested, ids);
    }

    private static void attributes(Element element, String... allowed) {
        Set<String> names = Set.of(allowed);
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) continue;
            if (attr.getNamespaceURI() != null || !names.contains(attr.getNodeName()))
                throw invalid("Unsupported attribute on " + element.getLocalName() + ": " + attr.getNodeName());
        }
    }

    private static List<Element> children(Element element) { return children(element, false); }
    private static List<Element> children(Element element, boolean textAllowed) {
        var result = new ArrayList<Element>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element nested) result.add(nested);
            else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !textAllowed && !child.getTextContent().isBlank())
                throw invalid("Unexpected text in " + element.getLocalName());
        }
        return result;
    }
    private static boolean is(Element e, String name) { return BPMN.equals(e.getNamespaceURI()) && name.equals(e.getLocalName()); }
    private static void require(Element e, String name) { if (!is(e, name)) throw unsupported(e); }
    private static String required(Element e, String name) {
        String value = e.getAttribute(name);
        if (value.isBlank()) throw invalid("Missing " + name + " on " + e.getLocalName());
        return value;
    }
    private static IllegalArgumentException unsupported(Element e) {
        return invalid("Unsupported element: {" + e.getNamespaceURI() + "}" + e.getLocalName());
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
