package si.sunesis.interoperability.lpc.transformations;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;
import si.sunesis.interoperability.lpc.transformations.constants.Constants;

import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;

@Slf4j
@RequestScoped
@Path("/lpc")
public class LegacyProtocolConverterResource extends Application {

    @SneakyThrows
    @POST
    @Path("/config")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@FormDataParam("file") InputStream file, @Context HttpServletRequest req) {
        String API_KEY = System.getenv(Constants.API_KEY_HEADER);
        if (API_KEY == null && System.getProperty(Constants.API_KEY_HEADER) != null) {
            API_KEY = System.getProperty(Constants.API_KEY_HEADER);
        }

        if (API_KEY == null || !API_KEY.equals(req.getHeader(Constants.API_KEY_HEADER))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String configuration = System.getenv(Constants.CONFIGURATION_FOLDER);

        if (configuration == null) {
            if (System.getProperty(Constants.CONFIGURATION_FOLDER) != null) {
                configuration = System.getProperty(Constants.CONFIGURATION_FOLDER);
            } else {
                configuration = "conf";
            }
        }

        File dir = new File(configuration);

        //Your local disk path where you want to store the file
        String uploadedFileLocation = dir.getAbsolutePath() + File.separator + "config.yaml";

        // save it
        try {
            Files.deleteIfExists(java.nio.file.Path.of(uploadedFileLocation));
        } catch (IOException e) {
            log.error("Error deleting file", e);
        }

        boolean success = saveToFile(file, uploadedFileLocation);

        if (!success) {
            return Response.serverError().build();
        }

        log.info("Uploaded configuration to file location: {}", uploadedFileLocation);

        return Response.ok().build();
    }

    private boolean saveToFile(InputStream uploadedInputStream,
                               String uploadedFileLocation) {
        try (OutputStream out = new FileOutputStream(uploadedFileLocation)) {
            int read;
            byte[] bytes = new byte[1024];

            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();

            return true;
        } catch (IOException e) {
            log.error("Error saving file", e);
        }

        return false;
    }
}
