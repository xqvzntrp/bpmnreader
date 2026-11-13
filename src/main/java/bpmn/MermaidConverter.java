package bpmn;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic view of the supported model read from saved BPMN XML. */
public final class MermaidConverter {
    public record Result(String text, Map<String, String> nodeIds) {
        public Result { nodeIds = Collections.unmodifiableMap(new LinkedHashMap<>(nodeIds)); }
    }

    /** The file is parsed completely before conversion; no Java-model export shortcut. */
    public Result convert(Path source) throws IOException {
        return convert(new BpmnReader().read(source));
    }

    public Result convert(Model.Document document) {
        var ids = new LinkedHashMap<String, String>();
        var output = new StringBuilder("flowchart TD\n");
        for (Model.Node node : document.process().nodes()) {
            String id = "n" + ids.size();
            ids.put(node.id(), id);
            String label = node.name().isEmpty() ? node.id() : node.name();
            String open;
            String close;
            switch (node.kind()) {
                case START, END -> { open = "((\""; close = "\"))"; }
                case TASK -> { open = "[\""; close = "\"]"; }
                case EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY -> {
                    open = "{\""; close = "\"}";
                    label = (node.kind() == Model.Kind.EXCLUSIVE_GATEWAY ? "X" : "+")
                        + (node.name().isEmpty() ? "" : ": " + node.name());
                }
                default -> throw new IllegalArgumentException("Unsupported node kind: " + node.kind());
            }
            output.append("  ").append(id).append(open).append(escape(label)).append(close).append('\n');
        }
        for (Model.Flow flow : document.process().flows()) {
            output.append("  ").append(ids.get(flow.source())).append(" -->");
            if (!flow.name().isEmpty()) output.append("|\"").append(escape(flow.name())).append("\"|");
            output.append(' ').append(ids.get(flow.target())).append('\n');
        }
        return new Result(output.toString(), ids);
    }

    /** Mermaid decimal entities keep user text out of Mermaid syntax and HTML. */
    private static String escape(String label) {
        var output = new StringBuilder();
        label.codePoints().forEach(c -> {
            if (c == ' ' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9')
                output.appendCodePoint(c);
            else output.append('#').append(c).append(';');
        });
        return output.toString();
    }
}
