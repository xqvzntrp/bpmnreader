package bpmn;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

/** Validates against a trusted local OMG schema bundle. Never fetches schemas online. */
public final class BpmnValidator {
    private final Schema schema;

    /** BPMN20.xsd must sit beside Semantic.xsd, BPMNDI.xsd, DC.xsd and DI.xsd. */
    public BpmnValidator(Path bpmn20Schema) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Trusted local schemas may include/import sibling schema files.
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        schema = factory.newSchema(bpmn20Schema.toFile());
    }

    public void validate(String xml) throws SAXException, IOException {
        var validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(new StreamSource(new StringReader(xml)));
    }
}
