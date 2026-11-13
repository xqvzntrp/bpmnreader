package bpmn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class MermaidTests {
    private static int checks;
    private MermaidTests() { }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static int run() throws Exception {
        var converter = new MermaidConverter();
        Path complex = Path.of("examples/order-fulfillment.bpmn");
        check(new BpmnReader().read(complex).equals(ComplexExample.model()), "complex saved model round trip");
        check(converter.convert(complex).text().equals(Files.readString(Path.of("examples/order-fulfillment.mmd"))),
            "complex generated example stays in sync");
        var result = converter.convert(Path.of("examples/order-approval.bpmn"));
        check(MermaidHtml.companionPath(Path.of("chart.mmd")).equals(Path.of("chart.html")), "HTML companion naming");
        check(MermaidHtml.companionPath(Path.of("chart.txt")).equals(Path.of("chart.txt.html")), "non-mmd companion naming");
        String hostileHtml = "flowchart TD\n  n0[\"</pre><script>alert(1)</script>&\"]\n";
        String html = MermaidHtml.export(hostileHtml);
        check(html.contains("&lt;/pre&gt;&lt;script&gt;alert(1)&lt;/script&gt;&amp;"), "HTML wrapper escapes embedded markup");
        check(html.contains("charset=\"utf-8\"") && html.contains("securityLevel: 'strict'"), "HTML encoding and renderer configuration");
        for (String name : List.of("order-approval", "order-fulfillment")) {
            String saved = Files.readString(Path.of("examples", name + ".mmd"));
            check(Files.readString(Path.of("examples", name + ".html")).equals(MermaidHtml.export(saved)),
                "checked-in HTML matches Mermaid: " + name);
        }
        String expected = "flowchart TD\n  n0((\"Received\"))\n  n1[\"Review #38; approve\"]\n"
            + "  n2((\"Done\"))\n  n0 --> n1\n  n1 --> n2\n";
        check(result.text().equals(expected), "exact file conversion");
        check(result.text().equals(Files.readString(Path.of("examples/order-approval.mmd"))), "checked-in example matches saved BPMN");
        check(result.equals(converter.convert(Path.of("examples/order-approval.bpmn"))), "deterministic text and mapping");
        check(result.nodeIds().equals(java.util.Map.of("Start_1", "n0", "Task_1", "n1", "End_1", "n2")), "BPMN ID mapping");
        try { result.nodeIds().put("bad", "id"); throw new AssertionError("mutable mapping"); }
        catch (UnsupportedOperationException expectedException) { checks++; }
        String hostile = "\"&[]{}()|<>#;`\\\n\r\t é 😀 --> click";
        var nodes = List.of(new Model.Node("end", hostile, Model.Kind.START),
            new Model.Node("n0", "", Model.Kind.TASK),
            new Model.Node("a-b", "Choice", Model.Kind.EXCLUSIVE_GATEWAY),
            new Model.Node("a.b", "Fork", Model.Kind.PARALLEL_GATEWAY),
            new Model.Node("e", "", Model.Kind.END),
            new Model.Node("isolated", "Alone", Model.Kind.TASK));
        var flows = List.of(new Model.Flow("f", hostile, "end", "n0"),
            new Model.Flow("g", "yes", "n0", "a-b"), new Model.Flow("h", "no", "n0", "a.b"),
            new Model.Flow("cycle", "again", "a.b", "n0"), new Model.Flow("done", "", "a-b", "e"));
        var model = new Model.Document("D", "urn:test", new Model.Process("P", "", nodes, flows));
        Path temp = Files.createTempFile("bpmn-mermaid-", ".bpmn");
        try {
            Files.writeString(temp, new BpmnExporter().export(model)
                .replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;"), StandardCharsets.UTF_8);
            byte[] before = Files.readAllBytes(temp);
            String text = converter.convert(temp).text();
            check(java.util.Arrays.equals(before, Files.readAllBytes(temp)), "source bytes unchanged");
            check(text.contains("n2{\"X#58; Choice\"}") && text.contains("n3{\"#43;#58; Fork\"}"), "gateway markers and diamonds");
            check(text.contains("n1[\"n0\"]") && text.contains("n4((\"e\"))"), "empty-name fallback");
            check(text.contains("n5[\"Alone\"]"), "disconnected node preserved");
            check(text.contains("n3 -->|\"again\"| n1"), "cycle preserved");
            check(text.contains("n1 -->|\"yes\"| n2") && text.contains("n1 -->|\"no\"| n3"), "branching and flow labels");
            String escaped = "#34;#38;#91;#93;#123;#125;#40;#41;#124;#60;#62;#35;#59;#96;#92;#10;#13;#9; #233; #128512; #45;#45;#62; click";
            check(text.contains("n0((\"" + escaped + "\"))"), "node escaping");
            check(text.contains("n0 -->|\"" + escaped + "\"| n1"), "flow escaping");
            check(text.lines().count() == 1 + nodes.size() + flows.size(), "every node and flow emitted once");
            Files.writeString(temp, ReaderTests.xml("<task id='fromFile' name='Saved XML'/>"), StandardCharsets.UTF_8);
            check(converter.convert(temp).text().contains("Saved XML"), "conversion uses saved file contents");
        } finally { Files.deleteIfExists(temp); }
        return checks;
    }
}
