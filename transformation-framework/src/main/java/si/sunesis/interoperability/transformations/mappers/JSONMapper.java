package si.sunesis.interoperability.transformations.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.json.JsonObject;
import java.util.Arrays;

@Slf4j
public class JSONMapper extends AbstractMapper {

    @SneakyThrows
    public JSONMapper(String mapping) {
        JsonNode root = objectMapper.readTree(mapping);
        root = root.get("lpc:mapping");

        setPath(root.get("path").asText());

        if (!getPath().startsWith("/")) {
            setPath("/" + getPath());
        }

        setPath(getPath().replace(".", "/"));

        setType(root.get("type").asText());
        if (root.has("pattern")) {
            setPattern(root.get("pattern").asText());
        }

        if (root.has("values")) {
            String nodeValue = root.get("values").toString();
            String cleanedString = nodeValue.substring(1, nodeValue.length() - 1);
            cleanedString = cleanedString.replace("\"", "");
            setValues(Arrays.stream(cleanedString.split(",")).map(String::trim).toArray(String[]::new));
        }
    }
}
