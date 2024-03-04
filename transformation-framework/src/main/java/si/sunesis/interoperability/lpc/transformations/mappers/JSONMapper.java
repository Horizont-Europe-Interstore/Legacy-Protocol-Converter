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
package si.sunesis.interoperability.lpc.transformations.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
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

    public JSONMapper(String path, String type, String[] values, String pattern) {
        setPath(path);
        setType(type);
        setValues(values);
        setPattern(pattern);
    }
}
