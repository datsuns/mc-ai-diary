package me.datsuns.aidiary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

public class DiaryGenerator {
    private static final Pattern RETRYABLE_ERROR_PATTERN = Pattern.compile("overloaded|unavailable|timeout|temporarily", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LENGTH_PER_CHAT = 240;
    private static final String GEMINI_MODEL_NAME = "gemini-2.5-flash";
    private static final int GEMINI_MAX_RETRIES = 3;
    private static final long GEMINI_RETRY_DELAY_MS = 2500L;

    private final String apiKey;

    public DiaryGenerator(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean hasApiKey() {
        return !this.apiKey.isEmpty();
    }

    public CompletableFuture<String> requestDiary(long day, Stats.Snapshot stats, String languageLabel) {
        if (!hasApiKey()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        String prompt = generatePrompt(day, stats, languageLabel);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateDiaryText(prompt);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public List<String> chunkForChat(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        int length = text.length();
        for (int i = 0; i < length; i += MAX_LENGTH_PER_CHAT) {
            chunks.add(text.substring(i, Math.min(length, i + MAX_LENGTH_PER_CHAT)));
        }
        return chunks;
    }

    private String generateDiaryText(String prompt) throws IOException {
        IOException lastIo = null;
        RuntimeException lastRuntime = null;
        for (int attempt = 1; attempt <= GEMINI_MAX_RETRIES; attempt++) {
            try {
                String rawJson = issueGeminiRequest(prompt);
                return parseGeminiResponseJson(rawJson);
            } catch (IOException e) {
                lastIo = e;
                if (attempt >= GEMINI_MAX_RETRIES) {
                    throw e;
                }
                ModConstants.LOGGER.warn("Gemini request failed (attempt {}/{}): {}", attempt, GEMINI_MAX_RETRIES, e.getMessage());
                sleepQuietly(GEMINI_RETRY_DELAY_MS * attempt);
            } catch (RuntimeException e) {
                lastRuntime = e;
                boolean retryable = attempt < GEMINI_MAX_RETRIES && isRetryableGeminiError(e);
                ModConstants.LOGGER.warn("Gemini response error (attempt {}/{}): {}", attempt, GEMINI_MAX_RETRIES, e.getMessage());
                if (!retryable) {
                    throw new IOException(e);
                }
                sleepQuietly(GEMINI_RETRY_DELAY_MS * attempt);
            }
        }
        if (lastIo != null) {
            throw lastIo;
        }
        if (lastRuntime != null) {
            throw new IOException(lastRuntime);
        }
        throw new IOException("Gemini request failed");
    }

    private String generatePrompt(long nthDay, Stats.Snapshot stats, String languageLabel) {
        String attacked = buildAttackedSection(stats.attacked());
        String biomes = generatePromptContents(stats.visitedBiomes(), "   - ");
        String items = generatePromptContents(stats.usedItem(), "    - %s, %d times\n");
        String blocks = generatePromptContents(stats.usedBlock(), "    - %s, %d times\n");
        String destroyed = generatePromptContents(stats.destroyBlock(), "    - %s, %d blocks\n");
        String entities = generatePromptContents(stats.usedEntity(), "    - %s, %d times\n");

        return String.format(
                "write a diary about Minecraft in %s with the character encoding set to UTF-8.\n"
                        + "write weather and playing day on the top of diary.\n"
                        + "sentences of diary should be funny and passionate.\n"
                        + "write within %d lines.\n"
                        + "Here are played information of today.\n"
                        + "The information below is for reference only.\n"
                        + "- the %d th day of playing \n"
                        + "- move %d meters\n"
                        + "- weather of the day\n"
                        + "- attack result\n"
                        + "%s\n"
                        + "- visited bioms\n"
                        + "%s\n"
                        + "- used items\n"
                        + "%s\n"
                        + "- used blocks\n"
                        + "%s\n"
                        + "- destroy blocks\n"
                        + "%s\n"
                        + "- communicated mobs\n"
                        + "%s\n",
                languageLabel,
                7,
                nthDay,
                (int) stats.totalDistance(),
                attacked,
                biomes,
                items,
                blocks,
                destroyed,
                entities
        );
    }

    private String buildAttackedSection(Map<String, Map<String, Integer>> attacked) {
        if (attacked.isEmpty()) {
            return "    - nothing\n";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Map<String, Integer>> entry : attacked.entrySet()) {
            builder.append(String.format("    - target: %s%n", entry.getKey()));
            for (Map.Entry<String, Integer> details : entry.getValue().entrySet()) {
                builder.append(String.format("       - by %s, %d times%n", details.getKey(), details.getValue()));
            }
        }
        return builder.toString();
    }

    private String generatePromptContents(Map<String, Integer> map, String format) {
        if (map.isEmpty()) {
            return "    - nothing\n";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            builder.append(String.format(format, entry.getKey(), entry.getValue()));
        }
        return builder.toString();
    }

    private String generatePromptContents(List<String> list, String prefix) {
        if (list.isEmpty()) {
            return "    - nothing\n";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : list) {
            builder.append(prefix).append(value).append("\n");
        }
        return builder.toString();
    }

    private String buildGeminiRequestBody(String prompt) {
        JsonObject text = new JsonObject();
        text.addProperty("text", prompt);

        JsonArray parts = new JsonArray();
        parts.add(text);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);
        return root.toString();
    }

    private String parseGeminiResponseJson(String rawJson) {
        JsonObject jsonObj = new Gson().fromJson(rawJson, JsonObject.class);
        if (jsonObj == null) {
            throw new RuntimeException("Gemini response is empty");
        }
        JsonArray candidates = jsonObj.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            JsonObject error = jsonObj.getAsJsonObject("error");
            if (error != null) {
                String message = error.has("message") ? error.get("message").getAsString() : error.toString();
                throw new RuntimeException("Gemini error: " + message);
            }
            throw new RuntimeException("Gemini response missing candidates: " + rawJson);
        }
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.getAsJsonObject("content");
        if (content == null) {
            throw new RuntimeException("Gemini response missing content: " + rawJson);
        }
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Gemini response missing parts: " + rawJson);
        }
        JsonObject part = parts.get(0).getAsJsonObject();
        return part.get("text").getAsString();
    }

    private String issueGeminiRequest(String prompt) throws IOException {
        String reqUrl = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                GEMINI_MODEL_NAME,
                this.apiKey
        );
        String body = buildGeminiRequestBody(prompt);
        try {
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(reqUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new IOException("Gemini request failed with status code: " + response.statusCode() + ", body: " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini request interrupted", e);
        }
    }

    private boolean isRetryableGeminiError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        return RETRYABLE_ERROR_PATTERN.matcher(message).find();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
