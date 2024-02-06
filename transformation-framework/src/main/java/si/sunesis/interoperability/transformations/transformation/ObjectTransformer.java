package si.sunesis.interoperability.transformations.transformation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import si.sunesis.interoperability.transformations.mappers.AbstractMapper;
import si.sunesis.interoperability.transformations.mappers.JSONMapper;
import si.sunesis.interoperability.transformations.mappers.XMLMapper;

import javax.enterprise.context.ApplicationScoped;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class ObjectTransformer {

    private static final String MAPPING_NAME = "lpc:mapping";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    public String transform(Object objectInput, String mappingDefinition, String fromFormat, String toFormat) {
        if (mappingDefinition == null) {
            return null;
        }
        try {
            if (objectInput instanceof String input) {
                mappingDefinition = mappingDefinition.replace("$timestamp", String.valueOf(System.currentTimeMillis()));

                JsonNode jsonNode = isValidJson(input);

                if (jsonNode != null) {
                    if (fromFormat.equalsIgnoreCase("XML")) {
                        return null;
                    }

                    if (Objects.equals(toFormat, "XML")) {
                        return transformToXML(jsonNode, isValidXml(mappingDefinition));
                    } else if (Objects.equals(toFormat, "JSON")) {
                        return transformToJSON(jsonNode, mappingDefinition);
                    }
                }

                Document document = isValidXml(input);

                if (document != null) {
                    if (fromFormat.equalsIgnoreCase("JSON")) {
                        return null;
                    }

                    if (Objects.equals(toFormat, "XML")) {
                        return transformToXML(document, isValidXml(mappingDefinition));
                    } else if (Objects.equals(toFormat, "JSON")) {
                        return transformToJSON(document, mappingDefinition);
                    }
                }

            } else {
                if (Objects.equals(toFormat, "XML")) {
                    return transformToXML(objectInput, isValidXml(mappingDefinition));
                } else if (Objects.equals(toFormat, "JSON")) {
                    return transformToJSON(objectInput, mappingDefinition);
                }
            }
        } catch (Exception e) {
            log.error("Error transforming object", e);
        }

        return null;
    }

    private String transformToXML(Object input, Document mappedDocument) throws ParseException {
        NodeList flowList = mappedDocument.getElementsByTagName(MAPPING_NAME);
        log.debug("flowList: {}", flowList.getLength());
        while (flowList.getLength() != 0) {
            for (int i = 0; i < flowList.getLength(); i++) {
                log.debug("i: {}, length: {}", i, flowList.getLength());
                Node parentNode = flowList.item(i).getParentNode();

                Node mappingNode = flowList.item(i);

                XMLMapper mapper = new XMLMapper(mappingNode);

                if (mappingNode.getNodeName().equals(MAPPING_NAME)) {
                    parentNode.removeChild(mappingNode);
                }

                String value = null;
                HashMap<Integer, Object> registersMap = isValidMap(input);
                if (input instanceof JsonNode node) {
                    log.debug("Input is JSON: {}", node);

                    value = mapper.getMappedValueJSON(node);
                } else if (input instanceof Document document) {
                    log.debug("Input is XML: {}", document);

                    value = mapper.getMappedValueXML(document);
                } else if (registersMap != null && !registersMap.isEmpty()) {
                    log.debug("Input is Modbus: {}", registersMap);

                    value = mapper.getMappedValueModbus(registersMap);
                }

                log.debug("path: {}, value: {}", mapper.getPath(), value);

                parentNode.setTextContent(value);
            }
            flowList = mappedDocument.getElementsByTagName(MAPPING_NAME);
        }

        return transformXMLToString(mappedDocument);
    }

    private String transformToJSON(Object input, String mappingDefinition) throws ParseException {
        Pattern mappingPattern = Pattern.compile("\\{\\s*\"" + MAPPING_NAME + "\":\\s*\\{(.*?)}\\s*}", Pattern.DOTALL);
        Matcher mappingMatcher = mappingPattern.matcher(mappingDefinition);

        StringBuilder modifiedMapping = new StringBuilder();
        while (mappingMatcher.find()) {
            String mappingContent = mappingMatcher.group(0);
            JSONMapper mapper = new JSONMapper(mappingContent);

            String value = getValueFromMapper(mapper, input);

            log.debug("path: {}, value: {}", mapper.getPath(), value);

            mappingMatcher.appendReplacement(modifiedMapping, value);
        }
        mappingMatcher.appendTail(modifiedMapping);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement je = new JsonParser().parse(modifiedMapping.toString());

        return gson.toJson(je);
    }

    public JsonNode isValidJson(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            return null;
        }
    }

    public Document isValidXml(String xmlString) {
        try {
            if (!xmlString.startsWith("<?xml")) {
                xmlString = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + xmlString;
            }

            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource inputSource = new InputSource(new java.io.StringReader(xmlString));

            return builder.parse(inputSource);
        } catch (Exception e) {
            return null;
        }
    }

    private HashMap<Integer, Object> isValidMap(Object object) {
        try {
            return objectMapper.convertValue(object, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String getValueFromMapper(AbstractMapper mapper, Object input) throws ParseException {
        String value = null;
        HashMap<Integer, Object> registersMap = isValidMap(input);
        if (input instanceof JsonNode node) {
            value = mapper.getMappedValueJSON(node);
        } else if (input instanceof Document document) {
            value = mapper.getMappedValueXML(document);
        } else if (registersMap != null && !registersMap.isEmpty()) {
            value = mapper.getMappedValueModbus(registersMap);
        }

        return value;
    }

    private String transformXMLToString(Document document) {
        Transformer transformer;
        try {
            transformerFactory.setAttribute("indent-number", 2);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            return writer.getBuffer().toString();
        } catch (TransformerException e) {
            log.error("Error transforming XML", e);
        }

        return null;
    }
}
