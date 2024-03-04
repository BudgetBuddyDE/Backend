package de.budgetbuddy.backend;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
public class WebhookTrigger {
    public static Boolean send(String webhookUrl, String jsonPayload) {
        try {
            sendPostRequest(webhookUrl, jsonPayload);
            return true;
        } catch (Exception e) {
            log.trace("Mail-Service couldn't get triggered", e);
            return false;
        }
    }

    private static void sendPostRequest(String url, String payload) throws Exception {
        HttpURLConnection connection = getHttpURLConnection(url, payload);
        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            log.info("Mail-Service was triggered");
        } else {
            // Handle errors
            log.error("Mail-Service couldn't get triggered");
        }

        connection.disconnect();
    }

    private static HttpURLConnection getHttpURLConnection(String url, String payload) throws IOException {
        URL webhookURL = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) webhookURL.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }
}
