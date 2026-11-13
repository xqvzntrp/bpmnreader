package bpmn;

import java.nio.file.Path;

final class ReaderTests {
    private static int checks;
    private ReaderTests() { }
    static String xml(String body) {
        return "<definitions xmlns='" + BpmnExporter.NAMESPACE
            + "' id='D' targetNamespace='urn:test'><process id='P'>" + body + "</process></definitions>";
    }
    static void rejects(String xml, String diagnostic) {
        try { new BpmnReader().read(xml); }
        catch (IllegalArgumentException ex) {
            check(ex.getMessage().contains(diagnostic), "diagnostic: " + ex.getMessage());
            return;
        }
        throw new AssertionError("Accepted invalid XML: " + xml);
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static int run() throws Exception {
        var reader = new BpmnReader();
        check(reader.read(Path.of("examples/order-approval.bpmn")).equals(Example.model()), "saved file round trip");
        String exported = new BpmnExporter().export(Example.model());
        check(reader.read(exported).equals(Example.model()), "prefixed namespace");
        check(reader.read(exported.replace("bpmn:", "x:").replace("xmlns:bpmn", "xmlns:x"))
            .equals(Example.model()), "alternate prefix");
        check(reader.read(exported.replace("bpmn:", "").replace("xmlns:bpmn", "xmlns"))
            .equals(Example.model()), "default namespace");
        String all = "<startEvent id='s'/><task id='t'/><exclusiveGateway id='x'/>"
            + "<parallelGateway id='g'/><endEvent id='e'/>"
            + "<sequenceFlow id='f' sourceRef='s' targetRef='t' name='yes'/>"
            + "<sequenceFlow id='loop' sourceRef='t' targetRef='t'/>";
        var model = reader.read(xml(all));
        check(model.process().nodes().size() == 5 && model.process().flows().size() == 2, "all kinds, cycle and disconnected nodes");
        check(model.process().flows().getFirst().name().equals("yes"), "flow label");
        check(reader.read(xml("<documentation>hello</documentation><task id='t'><documentation>notes</documentation></task>"))
            .process().nodes().size() == 1, "documentation metadata");
        String di = "<d:BPMNDiagram xmlns:d='http://www.omg.org/spec/BPMN/20100524/DI' id='diagram'>"
            + "<d:BPMNPlane id='plane' bpmnElement='P'/></d:BPMNDiagram>";
        check(reader.read(xml(all).replace("</definitions>", di + "</definitions>" )).equals(model), "DI ignored");
        rejects("<broken>", "Invalid XML");
        rejects(xml("<task id='a'/><task id='a'/>"), "Duplicate ID");
        rejects(xml("<task/>"), "Missing id");
        rejects(xml("<task id='t'/><sequenceFlow id='f' targetRef='t'/>"), "Missing sourceRef");
        rejects(xml("<task id='t'/><sequenceFlow id='f' sourceRef='t' targetRef='missing'/>"), "Unknown target");
        rejects(xml("<task id='t'/><sequenceFlow id='f' sourceRef='missing' targetRef='t'/>"), "Unknown source");
        rejects(xml("<task id='t'><incoming>missing</incoming></task>"), "Invalid incoming");
        rejects(xml("<task id='t'><outgoing>f</outgoing></task><task id='u'/>"
            + "<sequenceFlow id='f' sourceRef='u' targetRef='t'/>"), "Invalid outgoing");
        rejects(xml("<task id='t'><outgoing>f</outgoing><outgoing>f</outgoing></task>"
            + "<sequenceFlow id='f' sourceRef='t' targetRef='t'/>"), "Duplicate flow reference");
        rejects(xml("").replace("</definitions>", "<process id='Q'/></definitions>"), "Multiple processes");
        rejects(xml("").replace("<process id='P'></process>", ""), "Exactly one");
        rejects(xml("").replace("id='P'", "id='P' isExecutable='true'"), "non-executable");
        for (String element : new String[]{"subProcess", "laneSet", "userTask", "intermediateCatchEvent", "extensionElements"})
            rejects(xml("<" + element + " id='unsupported'/>"), "Unsupported element");
        rejects(xml("<startEvent id='s'><timerEventDefinition/></startEvent>"), "Unsupported element");
        rejects(xml("<task id='t'/><sequenceFlow id='f' sourceRef='t' targetRef='t'><conditionExpression>true</conditionExpression></sequenceFlow>"), "Unsupported element");
        rejects(xml("").replace("</definitions>", "<collaboration id='c'/></definitions>"), "Unsupported element");
        rejects(xml("<exclusiveGateway id='x' default='f'/>"), "Unsupported attribute");
        rejects(xml("<task xmlns='urn:foreign' id='t'/>"), "Unsupported element");
        rejects(xml("<task id='t' xmlns:v='urn:vendor' v:behavior='custom'/>"), "Unsupported attribute");
        rejects(xml("<task id='t'>unexpected</task>"), "Unexpected text");
        rejects(xml("<startEvent id='s'/><endEvent id='e'/><sequenceFlow id='f' sourceRef='e' targetRef='s'/>"), "End events");
        for (String dtd : new String[]{"<!DOCTYPE definitions>",
                "<!DOCTYPE definitions SYSTEM 'http://127.0.0.1:9/evil.dtd'>",
                "<!DOCTYPE definitions [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>",
                "<!DOCTYPE definitions [<!ENTITY % x SYSTEM 'http://127.0.0.1:9/evil.dtd'>%x;]>",
                "<!DOCTYPE definitions [<!ENTITY a 'boom'><!ENTITY b '&a;&a;'>]>"})
            rejects(dtd + xml(""), "DOCTYPE");
        rejects(xml("<xi:include xmlns:xi='http://www.w3.org/2001/XInclude' href='file:///etc/passwd'/>"), "Unsupported element");
        rejects(xml("").replace("id='D'", "id='D' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='urn:test http://127.0.0.1:9/xsd'"), "Unsupported attribute");
        return checks;
    }
}
