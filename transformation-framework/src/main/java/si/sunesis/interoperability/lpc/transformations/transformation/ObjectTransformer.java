/*
 *  Copyright (c) 2023-2024 Sunesis and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package si.sunesis.interoperability.lpc.transformations.transformation;

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
import org.xml.sax.SAXException;
import si.sunesis.interoperability.common.ieee2030dot5.IEEEObjectFactory;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;
import si.sunesis.interoperability.lpc.transformations.constants.Constants;
import si.sunesis.interoperability.lpc.transformations.mappers.AbstractMapper;
import si.sunesis.interoperability.lpc.transformations.mappers.JSONMapper;
import si.sunesis.interoperability.lpc.transformations.mappers.XMLMapper;

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
import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class ObjectTransformer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    public String transform(Object objectInput, String mappingDefinition, String fromFormat, String toFormat) {
        if (mappingDefinition == null) {
            return null;
        }

        long millisecond = System.currentTimeMillis();
        String patternZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternZ);
        Date date = new Date(millisecond);
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String timestampZ = String.format("\"%s\"", ldt.format(formatter));

        mappingDefinition = mappingDefinition.replace("\"$timestampZ\"", timestampZ);
        mappingDefinition = mappingDefinition.replace("$timestampZ", timestampZ);
        mappingDefinition = mappingDefinition.replace("\"$timestamp\"", String.valueOf(millisecond));
        mappingDefinition = mappingDefinition.replace("$timestamp", String.valueOf(millisecond));

        if (toFormat == null) {
            if (isValidJson(mappingDefinition) != null) {
                toFormat = "JSON";
            } else if (isValidXml(mappingDefinition) != null) {
                toFormat = "XML";
            }
        }

        try {
            if (objectInput instanceof String input) {
                JsonNode jsonNode = isValidJson(input);

                if (jsonNode != null) {
                    if (fromFormat == null || fromFormat.isEmpty()) {
                        fromFormat = "JSON";
                    }
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
                    if (fromFormat == null || fromFormat.isEmpty()) {
                        fromFormat = "XML";
                    }
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

    public Map<Integer, Float> transformToModbus(List<ModbusModel> modbusModels, String input, String fromFormat) throws ParseException {
        HashMap<Integer, Float> result = new HashMap<>();

        JsonNode jsonNode = isValidJson(input);

        if (jsonNode != null) {
            if (fromFormat == null || fromFormat.isEmpty()) {
                fromFormat = "JSON";
            }
            if (fromFormat.equalsIgnoreCase("XML")) {
                return result;
            }

            for (ModbusModel modbusModel : modbusModels) {
                JSONMapper jsonMapper = new JSONMapper(modbusModel.getPath(), modbusModel.getType(), modbusModel.getValues(), modbusModel.getPattern());
                String value = jsonMapper.getMappedValueJSON(jsonNode);

                log.debug("Added value for register: {} with value: {}", modbusModel.getAddress(), value);

                if (value == null) {
                    result.put(modbusModel.getAddress(), null);
                    continue;
                }

                result.put(modbusModel.getAddress(), Float.valueOf(value));
            }

            return result;
        }

        Document document = isValidXml(input);

        if (document != null) {
            if (fromFormat == null || fromFormat.isEmpty()) {
                fromFormat = "XML";
            }
            if (fromFormat.equalsIgnoreCase("JSON")) {
                return result;
            }

            for (ModbusModel modbusModel : modbusModels) {
                XMLMapper xmlMapper = new XMLMapper(modbusModel.getPath(), modbusModel.getType(), modbusModel.getValues(), modbusModel.getPattern());
                String value = xmlMapper.getMappedValueXML(document);

                log.debug("Added value for register: {} with value: {}", modbusModel.getAddress(), value);

                if (value == null || value.equals("null")) {
                    result.put(modbusModel.getAddress(), null);
                    continue;
                } else if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                result.put(modbusModel.getAddress(), Float.valueOf(value));
            }

            return result;
        }

        return result;
    }

    public String mockTransform(String mappingDefinition, Boolean validateIEEE2030dot5) throws IOException, SAXException {
        if (mappingDefinition == null) {
            throw new IllegalArgumentException("Mapping definition is null");
        }

        long millisecond = System.currentTimeMillis();
        String patternZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternZ);
        Date date = new Date(millisecond);
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String timestampZ = String.format("\"%s\"", ldt.format(formatter));

        mappingDefinition = mappingDefinition.replace("\"$timestampZ\"", timestampZ);
        mappingDefinition = mappingDefinition.replace("$timestampZ", timestampZ);
        mappingDefinition = mappingDefinition.replace("\"$timestamp\"", String.valueOf(millisecond));
        mappingDefinition = mappingDefinition.replace("$timestamp", String.valueOf(millisecond));

        JsonNode jsonNode = isValidJson(mappingDefinition);

        String transformedString;

        if (jsonNode != null) {
            Pattern mappingPattern = Pattern.compile("\\{\\s*\"" + Constants.MAPPING_NAME + "\":\\s*\\{(.*?)}\\s*}", Pattern.DOTALL);
            Matcher mappingMatcher = mappingPattern.matcher(mappingDefinition);

            StringBuilder modifiedMapping = new StringBuilder();
            while (mappingMatcher.find()) {
                String mappingContent = mappingMatcher.group(0);
                JSONMapper mapper = new JSONMapper(mappingContent);

                String value = switch (mapper.getType()) {
                    case "string" -> "\"string\"";
                    case "int", "long", "integer" -> "0";
                    case "float", "double" -> "0.0";
                    case "date", "datetime" -> String.valueOf(millisecond);
                    case "boolean" -> "true";
                    default -> {
                        // Check for "contains" condition in the default case
                        String type = mapper.getType();
                        if (type.contains("int") || type.contains("long")) {
                            yield "0"; // For integer types
                        } else if (type.contains("float") || type.contains("double")) {
                            yield "0.0"; // For integer types
                        } else {
                            yield "null"; // For any other types
                        }
                    }
                };

                mappingMatcher.appendReplacement(modifiedMapping, value);
            }
            mappingMatcher.appendTail(modifiedMapping);

            JsonNode transformedJsonNode = objectMapper.readTree(modifiedMapping.toString());
            transformedString = transformedJsonNode.toPrettyString();

            JsonNode validatedJsonNode = isValidJson(transformedString);

            if (validatedJsonNode == null) {
                throw new IllegalArgumentException("Invalid transformation. Json is not valid.");
            }

            if (Boolean.TRUE.equals(validateIEEE2030dot5)) {
                IEEEObjectFactory.validateIEEE2030dot5(transformedString);
            }

            log.debug("Transformation validated successfully");

            return transformedString;
        }

        Document document = isValidXml(mappingDefinition);

        if (document != null) {
            NodeList flowList = document.getElementsByTagName(Constants.MAPPING_NAME);
            while (flowList.getLength() != 0) {
                for (int i = 0; i < flowList.getLength(); i++) {
                    Node parentNode = flowList.item(i).getParentNode();

                    Node mappingNode = flowList.item(i);

                    XMLMapper mapper = new XMLMapper(mappingNode);

                    if (mappingNode.getNodeName().equals(Constants.MAPPING_NAME)) {
                        parentNode.removeChild(mappingNode);
                    }

                    String value = switch (mapper.getType()) {
                        case "string" -> "string";
                        case "int", "long", "integer" -> "0";
                        case "float", "double" -> "0.0";
                        case "date", "datetime" -> String.valueOf(millisecond);
                        case "boolean" -> "true";
                        default -> {
                            // Check for "contains" condition in the default case
                            String type = mapper.getType();
                            if (type.contains("int") || type.contains("long")) {
                                yield "0"; // For integer types
                            } else if (type.contains("float") || type.contains("double")) {
                                yield "0.0"; // For integer types
                            } else {
                                yield "null"; // For any other types
                            }
                        }
                    };

                    if (value.equals("null")) {
                        value = "";
                    } else if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    log.debug("path: {}, value: {}", mapper.getPath(), value);

                    parentNode.setTextContent(value);
                }
                flowList = document.getElementsByTagName(Constants.MAPPING_NAME);
            }

            transformedString = transformXMLToString(document);

            Document validatedDocument = isValidXml(transformedString);

            if (validatedDocument == null) {
                throw new IllegalArgumentException("Invalid transformation. XML is not valid.");
            }

            if (Boolean.TRUE.equals(validateIEEE2030dot5)) {
                IEEEObjectFactory.validateIEEE2030dot5(transformedString);
            }

            log.debug("Transformation validated successfully");

            return transformedString;
        }

        objectMapper.readTree(mappingDefinition);

        throw new IllegalArgumentException("Invalid transformation. Input is not valid.");
    }

    private String transformToXML(Object input, Document mappedDocument) throws ParseException {
        NodeList flowList = mappedDocument.getElementsByTagName(Constants.MAPPING_NAME);
        while (flowList.getLength() != 0) {
            for (int i = 0; i < flowList.getLength(); i++) {
                Node parentNode = flowList.item(i).getParentNode();

                Node mappingNode = flowList.item(i);

                XMLMapper mapper = new XMLMapper(mappingNode);

                if (mappingNode.getNodeName().equals(Constants.MAPPING_NAME)) {
                    parentNode.removeChild(mappingNode);
                }

                String value = getValueFromMapper(mapper, input);

                if (value == null || value.equals("null")) {
                    value = "";
                } else if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                log.debug("path: {}, value: {}", mapper.getPath(), value);

                parentNode.setTextContent(value);
            }
            flowList = mappedDocument.getElementsByTagName(Constants.MAPPING_NAME);
        }

        return transformXMLToString(mappedDocument);
    }

    private String transformToJSON(Object input, String mappingDefinition) throws ParseException {
        Pattern mappingPattern = Pattern.compile("\\{\\s*\"" + Constants.MAPPING_NAME + "\":\\s*\\{(.*?)}\\s*}", Pattern.DOTALL);
        Matcher mappingMatcher = mappingPattern.matcher(mappingDefinition);

        StringBuilder modifiedMapping = new StringBuilder();
        while (mappingMatcher.find()) {
            String mappingContent = mappingMatcher.group(0);
            JSONMapper mapper = new JSONMapper(mappingContent);

            String value = getValueFromMapper(mapper, input);

            log.debug("path: {}, value: {}", mapper.getPath(), value);

            if (value == null) {
                value = "null";
            }

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

            String xmlString = writer.getBuffer().toString();
            xmlString = xmlString.replace(">\n", ">");
            int index = xmlString.indexOf("?>");
            if (index != -1) {
                xmlString = xmlString.substring(0, index + 2) + "\n" + xmlString.substring(index + 2);
            }

            return xmlString;
        } catch (TransformerException e) {
            log.error("Error transforming XML", e);
        }

        return null;
    }
}
