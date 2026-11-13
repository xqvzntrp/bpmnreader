package local.dev.mavenproject2bpmnreader;

/** NetBeans launcher: no arguments runs the saved fulfillment example. */
public final class Mavenproject2bpmnreader {
    private Mavenproject2bpmnreader() { }
    public static void main(String[] args) {
        boolean demo = args.length == 0;
        bpmn.BpmnToMermaid.main(demo ? new String[]{
            "examples/order-fulfillment.bpmn", "target/order-fulfillment.mmd"
        } : args);
        if (demo) {
            System.out.println("Generated target/order-fulfillment.mmd");
            System.out.println("Open target/order-fulfillment.html in your browser.");
        }
    }
}
