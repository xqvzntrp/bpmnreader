# Reader contract

`BpmnReader.read(Path)` parses saved XML with JDK DOM APIs. `read(String)` is also
available. The result is the immutable `Model.Document`. This reader checks the
converter's subset; it does not run XSD validation.

Exactly one process is required. Supported process children are plain start/end
events, generic tasks, exclusive/parallel gateways, and sequence flows. IDs are
required and use the model's ASCII NCName subset. Node references must be local,
unqualified IDs. Optional incoming/outgoing entries must name flows attached in
the correct direction; entries may be omitted. Duplicate IDs anywhere are rejected.

Permitted metadata attributes (all unqualified):

- definitions: id, targetNamespace, name, exporter, exporterVersion
- process: id, name, isExecutable (absent, false or 0 only)
- supported nodes: id, name
- sequenceFlow: id, name, sourceRef, targetRef
- documentation: id, textFormat

Text-only BPMN documentation is allowed under definitions, process, nodes and
flows. Comments, processing instructions and namespace declarations are ignored.
Definitions metadata and documentation are not represented in Mermaid.
BPMNDiagram children of definitions and their descendants in the BPMN DI, DD DI
and DD DC namespaces are ignored as layout; their IDs still participate in global
uniqueness checks. Model elements inside a diagram are rejected.

Every other element and semantic attribute is rejected, including extensions,
subprocesses, lanes, collaborations, event definitions, conditions, default flows,
specialized tasks and executable processes. No reachability or gateway balancing
analysis is performed. Disconnected nodes and cycles are supported.

DOCTYPE is forbidden. External general/parameter entities, DTD loading, external
schemas, network access and XInclude processing are disabled. Parsing uses the
input bytes' XML encoding declaration. Errors use IllegalArgumentException for
invalid/unsupported XML and IOException for file failures. Trusted local XSD
validation remains a separate BpmnValidator operation.
