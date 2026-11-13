package bpmn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command-line conversion of a saved BPMN file to UTF-8 Mermaid text and companion HTML. */
public final class BpmnToMermaid {
    private BpmnToMermaid() { }
    private static void different(Path first, Path second) throws IOException {
        if (first.equals(second) || Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second))
            throw new IllegalArgumentException("Input and outputs must identify different files");
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: bpmn.BpmnToMermaid INPUT.bpmn OUTPUT.mmd");
            System.exit(2);
            return;
        }
        try {
            Path input = Path.of(args[0]).toAbsolutePath().normalize();
            Path output = Path.of(args[1]).toAbsolutePath().normalize();
            Path htmlOutput = MermaidHtml.companionPath(output);
            different(input, output);
            different(input, htmlOutput);
            different(output, htmlOutput);
            if (Files.isDirectory(output) || Files.isDirectory(htmlOutput))
                throw new IOException("Output path identifies a directory");
            // Finish parsing and conversion before opening/truncating the destination.
            String mermaid = new MermaidConverter().convert(input).text();
            String html = MermaidHtml.export(mermaid);
            Files.writeString(output, mermaid, StandardCharsets.UTF_8);
            Files.writeString(htmlOutput, html, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException ex) {
            String message = ex.getMessage();
            System.err.println("BPMN conversion failed: " + (message == null ? ex.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ')));
            System.exit(1);
        }
    }
}
