package bpmn;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class Tests {
    private static int checks;
    private Tests() { }

    public static void main(String[] args) throws Exception {
        var exporter = new BpmnExporter();
        String xml = exporter.export(Example.model());
        check(xml.equals(exporter.export(Example.model())), "deterministic XML");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var parsed = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        check(parsed.getDocumentElement().getNamespaceURI().equals(BpmnExporter.NAMESPACE), "namespace");
        check(parsed.getElementsByTagNameNS(BpmnExporter.NAMESPACE, "task").item(0)
            .getAttributes().getNamedItem("name").getNodeValue().equals("Review & approve"), "escaping round trip");
        check(parsed.getElementsByTagNameNS(BpmnExporter.NAMESPACE, "outgoing").getLength() == 2, "outgoing refs");
        expectInvalid(() -> new Model.Node("bad:id", "", Model.Kind.TASK));
        expectInvalid(() -> new Model.Node("valid", "\u0000", Model.Kind.TASK));
        expectInvalid(() -> new Model.Node("valid", "\uD800", Model.Kind.TASK));
        var start = new Model.Node("start", "", Model.Kind.START);
        var end = new Model.Node("end", "", Model.Kind.END);
        expectInvalid(() -> new Model.Process("p", "", List.of(start, start), List.of()));
        expectInvalid(() -> new Model.Process("p", "", List.of(start),
            List.of(new Model.Flow("f", "", "start", "missing"))));
        expectInvalid(() -> new Model.Process("p", "", List.of(start, end),
            List.of(new Model.Flow("f", "", "end", "start"))));
        expectInvalid(() -> new Model.Document("Start_1", "urn:x", Example.model().process()));
        var nodes = new ArrayList<>(List.of(start));
        var process = new Model.Process("p", "", nodes, List.of());
        nodes.clear();
        check(process.nodes().size() == 1, "defensive copy");
        String hostile = "\"<>& é 😀";
        var kinds = new ArrayList<Model.Node>();
        for (var kind : Model.Kind.values()) kinds.add(new Model.Node("N_" + kind.name(), hostile, kind));
        String allKinds = exporter.export(new Model.Document("D", "urn:test",
            new Model.Process("P", hostile, kinds, List.of())));
        factory.newDocumentBuilder().parse(new InputSource(new StringReader(allKinds)));
        check(allKinds.contains("😀"), "unicode retained");
        if (args.length == 1) {
            var validator = new BpmnValidator(Path.of(args[0]));
            validator.validate(xml);
            validator.validate(allKinds);
            validator.validate(java.nio.file.Files.readString(Path.of("examples/order-fulfillment.bpmn")));
            checks++;
            checks += 2;
            try {
                validator.validate(xml.replace("isExecutable=\"false\"", "isExecutable=\"invalid\""));
                throw new AssertionError("Schema accepted invalid boolean");
            } catch (SAXException expected) { checks++; }
            try {
                validator.validate("<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]>"
                    + xml.substring(xml.indexOf("<bpmn:")).replace("Review &amp; approve", "&ext;"));
                throw new AssertionError("External entity accepted");
            } catch (SAXException expected) { checks++; }
        } else System.out.println("Schema checks skipped: supply local BPMN20.xsd path");
        checks += ReaderTests.run();
        checks += MermaidTests.run();
        checks += CliTests.run();
        System.out.println("Passed " + checks + " checks");
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid model rejection");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
