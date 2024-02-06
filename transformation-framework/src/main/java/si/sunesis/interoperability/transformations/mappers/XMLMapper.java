package si.sunesis.interoperability.transformations.mappers;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

@Slf4j
public class XMLMapper extends AbstractMapper {

    public XMLMapper(Node node) {
        NodeList childNodes = node.getChildNodes();

        for (int iii = 0; iii < childNodes.getLength(); iii++) {
            Node childNode = childNodes.item(iii);

            switch (childNode.getNodeName()) {
                case "path" -> {
                    String path = childNode.getTextContent();
                    if (!path.startsWith("//")) {
                        if (path.startsWith("/")) {
                            path = "/" + path;
                        } else {
                            path = "//" + path;
                        }
                    }

                    setPath(path);
                    setType(childNode.getAttributes().getNamedItem("type").getNodeValue());
                }
                case "pattern" -> setPattern(childNode.getTextContent());
                case "values" -> {
                    String cleanedString = childNode.getTextContent().substring(1, childNode.getTextContent().length() - 1);
                    cleanedString = cleanedString.replace("\"", "");
                    // Split by ", "
                    values = Arrays.stream(cleanedString.split(",")).map(String::trim).toArray(String[]::new);

                    setValues(values);
                }
                default -> log.debug("Unknown node name: {}", childNode.getNodeName());
            }
        }

        log.debug("Path: {}", getPath());
        log.debug("Type: {}", getType());
        log.debug("Pattern: {}", getPattern());
        log.debug("Values: {}", Arrays.toString(getValues()));
    }

    public XMLMapper(String mapping) throws ParserConfigurationException, IOException, SAXException {
        mapping = "<mapping>" + mapping + "</mapping>";

        log.debug("Input mapping: {}", mapping);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(mapping.trim())));

        NodeList mappingList = document.getElementsByTagName("mapping");

        Node mappingNode = mappingList.item(0);
        NodeList childNodes = mappingNode.getChildNodes();

        String path = null;
        String type = null;
        String pattern = null;
        String[] values = null;

        for (int j = 0; j < childNodes.getLength(); j++) {
            Node childNode = childNodes.item(j);

            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = childNode.getNodeName();
                String nodeValue = childNode.getTextContent().trim();

                if ("path".equalsIgnoreCase(nodeName)) {
                    path = nodeValue;
                    type = childNode.getAttributes().getNamedItem("type").getNodeValue();
                } else if ("pattern".equalsIgnoreCase(nodeName)) {
                    pattern = nodeValue;
                } else if ("values".equalsIgnoreCase(nodeName)) {
                    String cleanedString = nodeValue.substring(1, nodeValue.length() - 1);
                    cleanedString = cleanedString.replace("\"", "");
                    // Split by ", "
                    values = Arrays.stream(cleanedString.split(",")).map(String::trim).toArray(String[]::new);
                }
            }
        }

        setPath(path);
        setType(type);
        setPattern(pattern);
        setValues(values);
    }
}
