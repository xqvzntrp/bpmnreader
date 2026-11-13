package bpmn;

import java.nio.file.Path;

/** Minimal browser wrapper; the renderer is loaded at viewing time, not by Java. */
public final class MermaidHtml {
    private MermaidHtml() { }

    public static Path companionPath(Path mermaid) {
        String name = mermaid.getFileName().toString();
        return mermaid.resolveSibling(name.endsWith(".mmd")
            ? name.substring(0, name.length() - 4) + ".html" : name + ".html");
    }

    public static String export(String mermaid) {
        String escaped = mermaid.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>BPMN flowchart</title>
            </head>
            <body>
            <pre class="mermaid">
            """ + escaped + """
            </pre>
            <script type="module">
              import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@12.3.0/dist/mermaid.esm.min.mjs';
              mermaid.initialize({ startOnLoad: true, securityLevel: 'strict' });
            </script>
            </body>
            </html>
            """;
    }
}
