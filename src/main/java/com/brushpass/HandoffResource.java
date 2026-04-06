package com.brushpass;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Path("/handoff")
public class HandoffResource {

    private static final Logger LOG = Logger.getLogger(HandoffResource.class);

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "brushpass.validate-url")
    String validateUrl;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handoff(@HeaderParam("X-PIN") String pin, String body) {
        // Require X-PIN header
        if (pin == null || pin.isBlank()) {
            LOG.warn("Missing X-PIN header");
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Missing X-PIN header\"}")
                    .build();
        }

        // Validate JSON body is present and parseable
        if (body == null || body.isBlank()) {
            LOG.warn("Empty request body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Malformed JSON\"}")
                    .build();
        }

        // Basic JSON validation - must start with { or [
        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            LOG.warn("Malformed JSON body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Malformed JSON\"}")
                    .build();
        }

        // Try parsing JSON to validate it
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(body);
        } catch (Exception e) {
            LOG.warn("Malformed JSON body: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Malformed JSON\"}")
                    .build();
        }

        // Validate PIN against station-zero
        try {
            String validateUrlWithPin = validateUrl + "?pin=" + java.net.URLEncoder.encode(pin, "UTF-8");
            LOG.info("Validating PIN at: " + validateUrlWithPin);

            // Use HttpURLConnection with TLS trust-all for simplicity
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());

            URL url = new URL(validateUrlWithPin);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier((h, s) -> true);
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            String responseBody = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                responseBody = sb.toString();
            }

            LOG.info("Validation response: " + responseCode + " - " + responseBody);

            if (responseCode != 200 || !responseBody.contains("\"ok\":true") && !responseBody.contains("\"ok\": true")) {
                LOG.warn("PIN validation failed: " + responseCode + " " + responseBody);
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\":\"PIN validation failed\"}")
                        .build();
            }

            // Extract flag if present
            // Try to find a flag in the response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(responseBody);
            if (node.has("flag")) {
                String flag = node.get("flag").asText();
                LOG.info("=== FLAG: " + flag + " ===");
                System.out.println("=== FLAG: " + flag + " ===");
            }

            // Also log the full body for inspection
            LOG.info("Request body: " + body);
            com.fasterxml.jackson.databind.JsonNode bodyNode = mapper.readTree(body);
            if (bodyNode.has("flag")) {
                String flag = bodyNode.get("flag").asText();
                LOG.info("=== FLAG FROM BODY: " + flag + " ===");
                System.out.println("=== FLAG FROM BODY: " + flag + " ===");
            }

            // Log everything for flag discovery
            LOG.info("=== HANDOFF SUCCESS === PIN: " + pin + " Body: " + body + " Validation: " + responseBody);
            System.out.println("=== HANDOFF SUCCESS === PIN: " + pin + " Body: " + body + " Validation: " + responseBody);

            return Response.ok("{\"ack\":true}").build();

        } catch (Exception e) {
            LOG.error("Error validating PIN: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Internal error\"}")
                    .build();
        }
    }
}
