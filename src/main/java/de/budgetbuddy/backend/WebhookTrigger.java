package de.budgetbuddy.backend;

import de.budgetbuddy.backend.log.Log;
import de.budgetbuddy.backend.log.LogType;
import de.budgetbuddy.backend.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookTrigger {
    public static Boolean send(String webhookUrl, String jsonPayload) {
        try {
            sendPostRequest(webhookUrl, jsonPayload);
            return true;
        } catch (Exception e) {
            Logger.getInstance()
                    .log(new Log("Backend", LogType.ERROR, "mail-service", e.getMessage()));
            return false;
        }
    }

    private static void sendPostRequest(String url, String payload) throws Exception {
        HttpURLConnection connection = getHttpURLConnection(url, payload);
        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            Logger.getInstance()
                    .log(new Log("Backend", LogType.LOG, "mail-service", "Mail-Service was triggered"));
        } else {
            // Handle errors
            Logger.getInstance()
                    .log(new Log("Backend", LogType.ERROR, "mail-service", "Mail-Service couldn't get triggered"));
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
