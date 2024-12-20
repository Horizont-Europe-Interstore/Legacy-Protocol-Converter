package si.sunesis.interoperability.lpc.certificate.management;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.io.pem.PemReader;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.Security;
import java.util.Collection;

@Slf4j
public class CertificateManagement {

    /*
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");
        String rootPath = "/Users/davidtrafela/IdeaProjects/legacy-protocol-converter/certificate-management/src/main/resources/test-cert";
        String certPath = rootPath + "/preenrollment.p12";
        String certPassword = "ktM2S<q-4ya/u=II";
        String keyFilePath = rootPath + "/device-key.key";
        String reqFilePath = rootPath + "/req.p10";
        String p10ReqFilePath = rootPath + "/req-test.p10";
        String outputFilePath = rootPath + "/device-cert.p7";

        String subject = "/C=IT/O=ENELX/OU=esol_ap3562902_qa/CN=sunesis1";

        log.info("Enrolling device certificate...");
        log.info("Preenrollment certificate: {}", certPath);
        log.info("Request file: {}", reqFilePath);
        log.info("Output file: {}", outputFilePath);

        getCertificate(certPath, certPassword, p10ReqFilePath, outputFilePath, keyFilePath, subject);
    }*/


    public static String getCertificate(String preenrollmentCertPath, String preenrollmentPassword, String requestCertPath, String outputFilePath, String keyPath, String subject) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        Process p;
        String[] command = {"openssl", "req", "-new", "-key", keyPath, "-out", requestCertPath, "-subj", subject};
        Runtime rt = Runtime.getRuntime();
        p = rt.exec(command);
        int result = p.waitFor();

        log.debug("Request file generated successfully: {}", result);

        // Load the client certificate (P12)
        KeyStore clientKeyStore = KeyStore.getInstance("PKCS12", "BC");
        try (FileInputStream fis = new FileInputStream(preenrollmentCertPath)) {
            clientKeyStore.load(fis, preenrollmentPassword.toCharArray());
        }

        // Create an SSL context with client and CA certificates
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeyStore, preenrollmentPassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        // Read the PKCS#10 request file
        byte[] requestData;
        try (FileInputStream fis = new FileInputStream(requestCertPath)) {
            requestData = fis.readAllBytes();
        }

        // Set up the HTTP connection
        URL url = new URL("https://ra-enelcl.infocert.it/.well-known/est/simpleenroll");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/pkcs10");
        connection.setDoOutput(true);

        // Send the PKCS#10 request
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestData);
        }

        // Save the response body (PKCS#7 certificate) to a file
        try (InputStream is = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        log.info("Enrollment successful. Device certificate saved to {}", outputFilePath);

        if (outputFilePath.endsWith(".p7")) {
            String outputPemPath = outputFilePath.replace(".p7", ".pem");
            convertP7ToPEM(outputFilePath, outputPemPath);

            return mergePemAndCrt(outputPemPath);
        }

        return outputFilePath;
    }

    public static void convertP7ToPEM(String inputPath, String outputPath) throws CMSException, IOException {
        byte[] base64Content = Files.readAllBytes(Paths.get(inputPath));
        byte[] derBytes = Base64.decode(base64Content);

        CMSSignedData signedData = new CMSSignedData(derBytes);
        Collection<X509CertificateHolder> certificates = signedData.getCertificates().getMatches(null);

        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(outputPath))) {
            for (X509CertificateHolder cert : certificates) {
                pemWriter.writeObject(cert);
            }
        }

        log.info("P7 to PEM conversion successful. PEM device certificate saved to {}", outputPath);
    }

    public static String mergePemAndCrt(String pemPath) throws Exception {
        X509CertificateHolder certificateHolder = getCertificateHolder(pemPath);

        // Extract information
        String commonName = getRDNValue(certificateHolder.getSubject(), BCStyle.CN);
        String organizationalUnit = getRDNValue(certificateHolder.getSubject(), BCStyle.OU);

        log.debug("Subject: {}", certificateHolder.getSubject());
        log.debug("CN (Common Name): {}", commonName);
        log.debug("OU (Organizational Unit): {}", organizationalUnit);
        log.debug("Issuer: {}", certificateHolder.getIssuer());
        log.debug("Valid From: {}", certificateHolder.getNotBefore());
        log.debug("Valid Until: {}", certificateHolder.getNotAfter());
        log.debug("Serial Number: {}", certificateHolder.getSerialNumber());

        String issuersURL = getIssuersURL(certificateHolder);
        log.debug("CA Issuers URL: {}", issuersURL);

        String folder = pemPath.substring(0, pemPath.lastIndexOf("/"));

        String fileName = folder + "/ca-certificate.crt";
        downloadCaIssuersCertificate(issuersURL, fileName);

        // Merge the certificate and CA certificate
        String mergedFilePath = pemPath.replace(".pem", "-merged.crt");

        String pemLines = Files.readString(Paths.get(pemPath), StandardCharsets.UTF_8);
        String caLines = Files.readString(Paths.get(fileName), StandardCharsets.UTF_8);

        String writableLines = pemLines + "\n" + caLines;

        Files.writeString(Paths.get(mergedFilePath), writableLines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Device certificate and CA certificate merged successfully. Merged certificate saved to {}", mergedFilePath);
        return mergedFilePath;
    }

    private static void downloadCaIssuersCertificate(String issuersURL, String outputFilePath) throws IOException {
        if (issuersURL == null) {
            log.debug("No CA Issuers URL found.");
            return;
        }

        ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(issuersURL).openStream());
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath)) {
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            throw new IOException("Error downloading CA Issuers certificate", e);
        }
    }

    private static String getIssuersURL(X509CertificateHolder certificateHolder) throws IOException {
        // Get the Authority Information Access (AIA) extension
        byte[] aiaBytes = certificateHolder.getExtension(org.bouncycastle.asn1.x509.Extension.authorityInfoAccess).getExtnValue().getOctets();

        if (aiaBytes != null) {
            // Parse the AIA extension
            ASN1Primitive asn1Primitive = ASN1Primitive.fromByteArray(aiaBytes);
            AuthorityInformationAccess authorityInformationAccess = AuthorityInformationAccess.getInstance(asn1Primitive);

            // Extract URLs (OCSP or CA Issuers)
            AccessDescription[] accessDescriptions = authorityInformationAccess.getAccessDescriptions();
            for (AccessDescription accessDescription : accessDescriptions) {
                String accessLocation = ASN1IA5String.getInstance(accessDescription.getAccessLocation().getName()).getString();

                if (AccessDescription.id_ad_caIssuers.equals(accessDescription.getAccessMethod())) {
                    return accessLocation;
                }
            }
        } else {
            log.debug("No Authority Information Access extension found.");
        }

        return null;
    }

    public static X509CertificateHolder getCertificateHolder(String certificatePath) throws IOException {
        try (PemReader pemReader = new PemReader(new FileReader(certificatePath))) {
            return new X509CertificateHolder(pemReader.readPemObject().getContent());
        } catch (Exception e) {
            throw new IOException("Error reading certificate file", e);
        }
    }

    // Utility method to get an RDN value by OID
    public static String getRDNValue(X500Name name, ASN1ObjectIdentifier identifier) {
        RDN[] rdns = name.getRDNs(identifier);
        if (rdns != null && rdns.length > 0) {
            return rdns[0].getFirst().getValue().toString();
        }
        return null; // Return null if the attribute is not found
    }
}
