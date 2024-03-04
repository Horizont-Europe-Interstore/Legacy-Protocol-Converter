package si.sunesis.interoperability.lpc.transformations.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationsModel;

import java.io.*;

@Slf4j
public class TestRun {

    @Test
    public void test() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.findAndRegisterModules();

        InputStream inputStream = getClass().getResourceAsStream("/conf/mqtt-nats.yaml");
        String file = readFromInputStream(inputStream);
        TransformationsModel transformationsModel = objectMapper.readValue(
                file,
                TransformationsModel.class);

        Assert.assertEquals(2, transformationsModel.getConnections().size());
        Assert.assertEquals(1, transformationsModel.getTransformations().size());
        Assert.assertNull(transformationsModel.getTransformations().get(0).getToIncoming());
    }

    private static String readFromInputStream(InputStream inputStream)
            throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br
                     = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }

        return resultStringBuilder.toString();
    }
}
