package de.budgetbuddy.backend;

import de.budgetbuddy.backend.user.User;
import jakarta.annotation.Nullable;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MailService {
    private final Environment environment;

    @Autowired
    public MailService(Environment environment) {
        this.environment = environment;
    }

    @Nullable
    public String getMailServiceHost() {
        return environment.getProperty("de.budget-buddy.mail-service.address");
    }

    /**
     * Triggers the mail service
     * @return boolean
     */
    public Boolean trigger(JSONObject payload) {
        String mailServiceUrl = getMailServiceHost();
        if (mailServiceUrl == null) {
            throw new RuntimeException("Mail-service host-url is not set");
        }

        return WebhookTrigger.send(mailServiceUrl + "/send", payload.toString());
    }

    /**
     * Returns the payload for the verification mail
     * @return JSONObject
     */
    public static JSONObject getVerificationMailPayload(User user) {
        JSONObject payload = new JSONObject();
        payload.put("mail", "welcome");
        payload.put("to", user.getEmail());
        payload.put("uuid", user.getUuid().toString());

        return payload;
    }

    /**
     * Returns the payload for the password reset mail
     * @return JSONObject
     */
    public static JSONObject getRequestPasswordMailPayload(String email, UUID uuid, UUID otp) {
        JSONObject payload = new JSONObject();
        payload.put("mail", "reset_password");
        payload.put("to", email);
        payload.put("uuid", uuid.toString());
        payload.put("otp", otp.toString());

        return payload;
    }

    /**
     * Returns the payload for the password changed mail
     * @return JSONObject
     */
    public static JSONObject getPasswordChangedMailPayload(String email, String name, String targetEmailAddress) {
        JSONObject payload = new JSONObject();
        payload.put("mail", "password_changed");
        payload.put("to", email);
        payload.put("name", name);
        payload.put("targetMailAddress", targetEmailAddress);

        return payload;
    }
}
