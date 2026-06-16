package org.blood.bloodJail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

public final class DiscordWebhook {

    private final Logger logger;
    private final HttpClient httpClient;
    private String webhookUrl;

    public DiscordWebhook(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void setUrl(String url) {
        this.webhookUrl = url;
    }

    public boolean isReady() {
        return webhookUrl != null && !webhookUrl.isBlank() && !webhookUrl.equals("disabled");
    }

    public void sendJail(String playerName, String jailedBy, String duration, String reason) {
        if (!isReady()) return;
        String json = buildEmbed(
                "🔒 Игрок посажен в тюрьму",
                16711680, // red
                playerName,
                jailedBy,
                duration,
                reason,
                false
        );
        sendAsync(json);
    }

    public void sendRelease(String playerName, String releaseReason) {
        if (!isReady()) return;
        String json = buildEmbed(
                "🔓 Игрок освобождён из тюрьмы",
                3066993, // green
                playerName,
                null,
                null,
                releaseReason,
                true
        );
        sendAsync(json);
    }

    private String buildEmbed(String title, int color, String playerName,
                               String moderator, String duration, String reason,
                               boolean isRelease) {
        String safePlayer = escape(playerName == null || playerName.isBlank() ? "Unknown" : playerName);
        String safeReason = escape(reason == null || reason.isBlank() ? "Не указана" : reason);

        StringBuilder fields = new StringBuilder();
        fields.append("{\"name\": \"📚 | Причина\", \"value\": \"```\\n").append(safeReason).append("\\n```\", \"inline\": true}");

        if (!isRelease && moderator != null) {
            String safeMod = escape(moderator.isBlank() ? "Console" : moderator);
            fields.append(",{\"name\": \"👮 | Модератор\", \"value\": \"```\\n").append(safeMod).append("\\n```\", \"inline\": true}");
        }

        if (!isRelease && duration != null) {
            String safeDur = escape(duration.isBlank() ? "Неизвестно" : duration);
            fields.append(",{\"name\": \"⌛ | Длительность\", \"value\": \"```\\n").append(safeDur).append("\\n```\", \"inline\": true}");
        }

        return "{\"embeds\": [{"
                + "\"title\": \"" + escape(title) + "\","
                + "\"color\": " + color + ","
                + "\"description\": \"Игрок **" + safePlayer + "**\","
                + "\"fields\": [" + fields + "],"
                + "\"thumbnail\": {\"url\": \"https://mc-heads.net/avatar/" + safePlayer + "\"}"
                + "}]}";
    }

    private void sendAsync(String json) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() / 100 != 2) {
                        logger.warning("[BloodJail] Discord webhook error " + response.statusCode() + ": " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    logger.warning("[BloodJail] Could not send Discord webhook: " + ex.getMessage());
                    return null;
                });
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
