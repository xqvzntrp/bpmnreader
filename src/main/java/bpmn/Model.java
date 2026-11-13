package bpmn;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Small immutable export model; not a replacement for the full BPMN metamodel. */
public final class Model {
    private Model() { }

    public enum Kind {
        START("startEvent"), TASK("task"), EXCLUSIVE_GATEWAY("exclusiveGateway"),
        PARALLEL_GATEWAY("parallelGateway"), END("endEvent");
        final String element;
        Kind(String element) { this.element = element; }
    }

    public record Node(String id, String name, Kind kind) {
        public Node { id = identifier(id); name = xmlText(name); Objects.requireNonNull(kind); }
    }

    public record Flow(String id, String name, String source, String target) {
        public Flow {
            id = identifier(id); name = xmlText(name);
            source = identifier(source); target = identifier(target);
        }
    }

    public record Process(String id, String name, List<Node> nodes, List<Flow> flows) {
        public Process {
            id = identifier(id); name = xmlText(name);
            nodes = List.copyOf(nodes); flows = List.copyOf(flows);
            Set<String> ids = new HashSet<>();
            ids.add(id);
            for (Node node : nodes) unique(ids, node.id());
            for (Flow flow : flows) unique(ids, flow.id());
            for (Flow flow : flows) {
                Node source = nodes.stream().filter(n -> n.id().equals(flow.source())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + flow.source()));
                Node target = nodes.stream().filter(n -> n.id().equals(flow.target())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown target: " + flow.target()));
                if (source.kind() == Kind.END || target.kind() == Kind.START)
                    throw new IllegalArgumentException("End events cannot have outgoing flows; start events cannot have incoming flows");
            }
        }
    }

    public record Document(String id, String targetNamespace, Process process) {
        public Document {
            id = identifier(id); targetNamespace = xmlText(targetNamespace);
            if (!URI.create(targetNamespace).isAbsolute())
                throw new IllegalArgumentException("Target namespace must be an absolute URI");
            Objects.requireNonNull(process);
            String definitionsId = id;
            if (id.equals(process.id()) || process.nodes().stream().anyMatch(n -> n.id().equals(definitionsId))
                    || process.flows().stream().anyMatch(f -> f.id().equals(definitionsId)))
                throw new IllegalArgumentException("Duplicate definitions ID: " + id);
        }
    }

    private static void unique(Set<String> ids, String id) {
        if (!ids.add(id)) throw new IllegalArgumentException("Duplicate ID: " + id);
    }

    private static String identifier(String value) {
        Objects.requireNonNull(value);
        // Deliberately conservative ASCII subset of XML NCName.
        if (!value.matches("[A-Za-z_][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid ID: " + value);
        return value;
    }

    private static String xmlText(String value) {
        Objects.requireNonNull(value);
        if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13
                || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD
                || c >= 0x10000 && c <= 0x10FFFF)))
            throw new IllegalArgumentException("Text contains an illegal XML 1.0 character");
        return value;
    }
}
