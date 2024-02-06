package si.sunesis.interoperability.transformations.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Data
@Slf4j
public abstract class AbstractMapper {

    protected String path;
    protected String type;
    protected String[] values;
    protected String pattern;

    @ToString.Exclude
    protected DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    @ToString.Exclude
    protected ObjectMapper objectMapper = new ObjectMapper();

    protected AbstractMapper() {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getMappedValueXML(String xmlInput) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlInput)));

            return getMappedValueXML(document);
        } catch (Exception e) {
            log.error("Error parsing XML", e);
        }

        return null;
    }

    public String getMappedValueXML(Document xmlInput) {
        try {
            // Parse the input XML string
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            // Example: Extract value using XPath expression
            String statusXPath = getPath();
            XPathExpression expr = xpath.compile(statusXPath);
            String value = (String) expr.evaluate(xmlInput, XPathConstants.STRING);

            log.debug("Value at XML path '" + statusXPath + "': " + value);

            return getValue(value);
        } catch (Exception e) {
            log.error("Error parsing XML", e);
        }

        return null;
    }

    public String getMappedValueJSON(String jsonInput) throws JsonProcessingException, ParseException {
        JsonNode rootNode = objectMapper.readTree(jsonInput);

        return getMappedValueJSON(rootNode);
    }

    public String getMappedValueJSON(JsonNode jsonInput) throws ParseException {
        if (getPath().startsWith("//")) {
            setPath(getPath().substring(1));
        }
        setPath(getPath().replace(".", "/"));

        JsonNode resultNode = jsonInput.at(getPath());

        // Check if the result node exists
        if (!resultNode.isMissingNode()) {
            log.debug("Value at JSON path '" + getPath() + "': " + resultNode);
            return getValue(resultNode.asText());
        } else {
            log.debug("No value found at JSON path '" + getPath() + "'");
        }

        return "null";
    }

    public String getMappedValueModbus(Map<Integer, Object> modbusInput) {
        Object value = modbusInput.get(Integer.parseInt(getPath()));

        if (value instanceof Double d) {
            return String.valueOf(d);
        } else if (value instanceof Integer i) {
            return String.valueOf(i);
        } else if (value instanceof Float f) {
            return String.valueOf(f);
        } else if (value instanceof Long l) {
            return String.valueOf(l);
        }

        return null;
    }

    private String getValue(String cleanedValue) throws ParseException {
        cleanedValue = cleanedValue.trim();
        if (getValues() != null) {
            if (getType().toLowerCase().contains("int")) {
                if (isNumber(cleanedValue)) {
                    return "\"" + getValues()[Integer.parseInt(cleanedValue)] + "\"";
                }
                for (int iii = 0; iii < getValues().length; iii++) {
                    if (getValues()[iii].equalsIgnoreCase(cleanedValue.trim())) {
                        return String.valueOf(iii);
                    }
                }
            } else if (getType().toLowerCase().contains("str")) {
                return "\"" + getValues()[Integer.parseInt(cleanedValue)] + "\"";
            }
        } else if (getPattern() != null && (getType().equalsIgnoreCase("date") || getType().equalsIgnoreCase("datetime"))) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getPattern());
            log.debug("Pattern: {}", getPattern());

            if (isNumber(cleanedValue)) {
                Date date = new Date(Long.parseLong(cleanedValue));
                return "\"" + simpleDateFormat.format(date) + "\"";
            }

            log.debug("Cleaned value: {}", cleanedValue);
            Date date = simpleDateFormat.parse(cleanedValue);

            log.debug("Date: {}", date);

            return "\"" + date.getTime() + "\"";
        }

        if (getType().toLowerCase().contains("str")) {
            return "\"" + cleanedValue + "\"";
        }

        return cleanedValue;
    }

    public static boolean isNumber(String input) {
        try {
            Long.parseLong(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

