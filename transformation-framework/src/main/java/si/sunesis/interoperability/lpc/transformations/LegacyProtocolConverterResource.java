package si.sunesis.interoperability.lpc.transformations;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;

@Slf4j
@RequestScoped
@Path("/lpc")
public class LegacyProtocolConverterResource extends Application {

    @POST
    @Path("/config")
    public Response uploadFile(
            @FormDataParam("file") InputStream uploadedInputStream) {
        String configuration = System.getenv("CONFIGURATION");

        if (configuration == null) {
            if (System.getProperty("CONFIGURATION") != null) {
                configuration = System.getProperty("CONFIGURATION");
            } else {
                configuration = "conf";
            }
        }

        File dir = new File(configuration);

        //Your local disk path where you want to store the file
        String uploadedFileLocation = dir.getAbsolutePath() + File.pathSeparator + "config.yaml";
        log.info("File location: {}", uploadedFileLocation);
        // save it
        try {
            Files.deleteIfExists(java.nio.file.Path.of(uploadedFileLocation));
        } catch (IOException e) {
            log.error("Error deleting file", e);
        }

        saveToFile(uploadedInputStream, uploadedFileLocation);

        String output = "File uploaded via Jersey based RESTFul Webservice to: " + uploadedFileLocation;

        return Response.status(200).entity(output).build();
    }

    private void saveToFile(InputStream uploadedInputStream,
                            String uploadedFileLocation) {
        try (OutputStream out = new FileOutputStream(uploadedFileLocation)) {
            int read;
            byte[] bytes = new byte[1024];

            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
        } catch (IOException e) {
            log.error("Error saving file", e);
        }
    }
}
