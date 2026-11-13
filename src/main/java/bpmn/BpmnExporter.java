package bpmn;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Stateless serializer. Output order follows the immutable model's list order. */
public final class BpmnExporter {
    public static final String NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    public String export(Model.Document document) throws XMLStreamException {
        var buffer = new StringWriter();
        var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(buffer);
        try {
            writer.writeStartDocument("UTF-8", "1.0");
            writer.setPrefix("bpmn", NAMESPACE);
            element(writer, "definitions");
            writer.writeNamespace("bpmn", NAMESPACE);
            writer.writeAttribute("id", document.id());
            writer.writeAttribute("targetNamespace", document.targetNamespace());
            element(writer, "process");
            Model.Process process = document.process();
            attributes(writer, process.id(), process.name());
            writer.writeAttribute("isExecutable", "false");
            for (Model.Node node : process.nodes()) {
                element(writer, node.kind().element);
                attributes(writer, node.id(), node.name());
                for (Model.Flow flow : process.flows()) {
                    if (flow.target().equals(node.id())) reference(writer, "incoming", flow.id());
                }
                for (Model.Flow flow : process.flows()) {
                    if (flow.source().equals(node.id())) reference(writer, "outgoing", flow.id());
                }
                writer.writeEndElement();
            }
            for (Model.Flow flow : process.flows()) {
                element(writer, "sequenceFlow");
                attributes(writer, flow.id(), flow.name());
                writer.writeAttribute("sourceRef", flow.source());
                writer.writeAttribute("targetRef", flow.target());
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            return buffer.toString();
        } finally { writer.close(); }
    }

    public void write(Model.Document document, Path path) throws XMLStreamException, IOException {
        Files.writeString(path, export(document), StandardCharsets.UTF_8);
    }

    private static void element(XMLStreamWriter writer, String name) throws XMLStreamException {
        writer.writeStartElement("bpmn", name, NAMESPACE);
    }

    private static void attributes(XMLStreamWriter writer, String id, String name) throws XMLStreamException {
        writer.writeAttribute("id", id);
        writer.writeAttribute("name", name);
    }

    private static void reference(XMLStreamWriter writer, String name, String id) throws XMLStreamException {
        element(writer, name);
        writer.writeCharacters(id);
        writer.writeEndElement();
    }
}
