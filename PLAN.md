# Implementation commits

1. Document scope and build a dependency-free Java 21 build script.
2. Add immutable model records and structural checks.
3. Serialize BPMN XML with the JDK StAX writer.
4. Add offline XSD validation using a caller-supplied official schema directory.
5. Add regression tests, an example and usage documentation.

The original model.zip is unavailable. This implementation defines a small model API;
using the original package requires a future adapter after its sources are supplied.
Scope: one non-executable process, start/end events, tasks, exclusive/parallel gateways,
and sequence flows. No BPMN DI layout, engine execution, import, or full BPMN coverage.

## BPMN-first Mermaid continuation

The original five-step exporter plan above is historical. The reader and converter
now implement the following additional commits:

1. `feat: parse supported BPMN XML securely` — strict namespace-aware subset reader,
   offline XML security, explicit metadata contract and rejection tests.
2. `feat: convert BPMN files to Mermaid flowcharts` — deterministic shapes, labels,
   generated IDs, escaping, all nodes/flows and saved-file conversion tests.
3. `feat: add BPMN-to-Mermaid command line` — UTF-8 file conversion, diagnostics,
   same-file protection and subprocess tests from the repository root.
4. `docs: explain BPMN-first Mermaid workflow` — supported subset, commands and a
   generated example kept in sync by the test harness.

Verified on Java 21.0.9: 101 checks with the supplied schemas, 97 checks without
schema validation. Mermaid rendering validation was unavailable (no mmdc installed).
