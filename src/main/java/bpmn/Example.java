package bpmn;

import java.nio.file.Path;
import java.util.List;

public final class Example {
    private Example() { }

    public static Model.Document model() {
        return new Model.Document("Definitions_1", "urn:example:bpmn", new Model.Process(
            "Process_1", "Order approval", List.of(
                new Model.Node("Start_1", "Received", Model.Kind.START),
                new Model.Node("Task_1", "Review & approve", Model.Kind.TASK),
                new Model.Node("End_1", "Done", Model.Kind.END)), List.of(
                new Model.Flow("Flow_1", "", "Start_1", "Task_1"),
                new Model.Flow("Flow_2", "", "Task_1", "End_1"))));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: bpmn.Example OUTPUT.bpmn");
        new BpmnExporter().write(model(), Path.of(args[0]));
    }
}
