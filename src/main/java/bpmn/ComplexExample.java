package bpmn;

import java.nio.file.Path;
import java.util.List;

/** Order fulfillment with a rework loop, rejection and parallel preparation. */
public final class ComplexExample {
    private ComplexExample() { }

    public static Model.Document model() {
        return new Model.Document("FulfillmentDefinitions", "urn:example:fulfillment",
            new Model.Process("Fulfillment", "Order fulfillment", List.of(
                new Model.Node("Received", "Order received", Model.Kind.START),
                new Model.Node("Review", "Review order", Model.Kind.TASK),
                new Model.Node("Decision", "Review result", Model.Kind.EXCLUSIVE_GATEWAY),
                new Model.Node("Revise", "Request corrections", Model.Kind.TASK),
                new Model.Node("Reject", "Notify customer of rejection", Model.Kind.TASK),
                new Model.Node("Rejected", "Order rejected", Model.Kind.END),
                new Model.Node("Prepare", "Prepare fulfillment", Model.Kind.PARALLEL_GATEWAY),
                new Model.Node("Reserve", "Reserve stock", Model.Kind.TASK),
                new Model.Node("Charge", "Collect payment", Model.Kind.TASK),
                new Model.Node("Ready", "Preparation complete", Model.Kind.PARALLEL_GATEWAY),
                new Model.Node("Ship", "Pack & ship", Model.Kind.TASK),
                new Model.Node("Notify", "Send tracking details", Model.Kind.TASK),
                new Model.Node("Completed", "Order completed", Model.Kind.END)), List.of(
                new Model.Flow("F01", "", "Received", "Review"),
                new Model.Flow("F02", "", "Review", "Decision"),
                new Model.Flow("F03", "Needs correction", "Decision", "Revise"),
                new Model.Flow("F04", "Resubmitted", "Revise", "Review"),
                new Model.Flow("F05", "Rejected", "Decision", "Reject"),
                new Model.Flow("F06", "", "Reject", "Rejected"),
                new Model.Flow("F07", "Approved", "Decision", "Prepare"),
                new Model.Flow("F08", "", "Prepare", "Reserve"),
                new Model.Flow("F09", "", "Prepare", "Charge"),
                new Model.Flow("F10", "Stock reserved", "Reserve", "Ready"),
                new Model.Flow("F11", "Payment collected", "Charge", "Ready"),
                new Model.Flow("F12", "", "Ready", "Ship"),
                new Model.Flow("F13", "", "Ship", "Notify"),
                new Model.Flow("F14", "", "Notify", "Completed"))));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: bpmn.ComplexExample OUTPUT.bpmn");
        new BpmnExporter().write(model(), Path.of(args[0]));
    }
}
