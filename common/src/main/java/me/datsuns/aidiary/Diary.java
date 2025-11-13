package me.datsuns.aidiary;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.http.impl.client.HttpClientBuilder;
import com.google.gson.Gson;


public class Diary {
    public final Integer MaxLengthPerOneChat = 240;
    public final String GeminiModelName = "gemini-2.5-flash";
    private static final int GEMINI_MAX_RETRIES = 3;
    private static final long GEMINI_RETRY_DELAY_MS = 2500L;
    private static final Pattern RETRYABLE_ERROR_PATTERN = Pattern.compile("overloaded|unavailable|timeout|temporarily", Pattern.CASE_INSENSITIVE);
    public GenerationState State;
    public String DiaryText;
    public String ApiKey;

    Diary(String apiKey) {
        this.ApiKey = apiKey;
        this.State = GenerationState.Idle;
    }

    public void onClientTick(MinecraftClient client) {
        if (this.State != GenerationState.Completed) {
            return;
        }
        IntegratedServer s = client.getServer();
        if (s == null) {
            ModConstants.LOGGER.error("Diary::onClientTick: client.getServer() ERROR");
            return;
        }
        ServerCommandSource src = s.getCommandSource();
        CommandManager cm = s.getCommandManager();
        for( String t : usingSplitMethod(this.DiaryText, MaxLengthPerOneChat) ) {
            String cmd = String.format("say %s", t);
            cm.parseAndExecute(src, cmd);
        }
        this.DiaryText = "";
        this.State = GenerationState.Idle;
    }

    public void onSave(MinecraftClient client, Stats stats) {
        if (this.ApiKey.isEmpty()) {
            ModConstants.LOGGER.error("api key is not set");
            return;
        }
        if (this.State != GenerationState.Idle) {
            ModConstants.LOGGER.error("now on busy. skip.");
        }
        this.State = GenerationState.Generating;
        long days = client.world.getTimeOfDay() / ModConstants.TICKS_PER_DAY;
        String prompt = generatePrompt(days, stats);
        CompletableFuture.runAsync(() -> {
            try {
                this.DiaryText = generateDiaryText(prompt);
            } catch (IOException e) {
                ModConstants.LOGGER.error("generate error", e);
                notifyPlayerFailure(client, e.getMessage());
                this.State = GenerationState.Idle;
                return;
            }
            this.State = GenerationState.Completed;
        });
    }

    public String generateDiaryText(String prompt) throws IOException {
        IOException lastIo = null;
        RuntimeException lastRuntime = null;
        for (int attempt = 1; attempt <= GEMINI_MAX_RETRIES; attempt++) {
            try {
                String rawJson = issueGeminiRequest(prompt);
                return parseGeminiResponseJson(rawJson);
            } catch (IOException e) {
                lastIo = e;
                boolean willRetry = attempt < GEMINI_MAX_RETRIES;
                ModConstants.LOGGER.warn("Gemini request failed (attempt {}/{}): {}", attempt, GEMINI_MAX_RETRIES, e.getMessage());
                if (!willRetry) {
                    throw e;
                }
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
        throw new IOException("Gemini request failed without specific error");
    }

    public String generatePromptContents(HashMap<String, Integer> map, String format) {
        if (map.size() == 0) {
            return "    - nothing\n";
        }
        String ret = "";
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            ret += String.format(format, entry.getKey(), entry.getValue());
        }
        return ret;
    }

    public String generatePromptContents(ArrayList<String> list, String prefix) {
        String ret = prefix;
        for (String v : list) {
            ret += String.format("%s,", v);
        }
        return ret;
    }

    public String generatePrompt(long nthDay, Stats stats) {
        String attacked = "";
        if (stats.Attacked.size() == 0) {
            attacked = "    - nothing\n";
        } else {
            for (Map.Entry<String, HashMap<String, Integer>> entry : stats.Attacked.entrySet()) {
                attacked += String.format("    - target: %s\n", entry.getKey());
                for (Map.Entry<String, Integer> details : entry.getValue().entrySet()) {
                    attacked += String.format("       - by %s, %d times\n", details.getKey(), details.getValue());
                }
            }
        }
        String bioms = generatePromptContents(stats.VisitedBioms, "   - ");
        String items = generatePromptContents(stats.UsedItem, "    - %s, %d times\n");
        String blocks = generatePromptContents(stats.UsedBlock, "    - %s, %d times\n");
        String destroyBlocks = generatePromptContents(stats.DestroyBlock, "    - %s, %d blocks\n");
        String entities = generatePromptContents(stats.UsedEntity, "    - %s, %d times\n");
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
                        + "%s\n"
                , Text.translatable("diary.text.language").getString()
                , 7
                , nthDay
                , (int) stats.distance()
                , attacked
                , bioms
                , items
                , blocks
                , destroyBlocks
                , entities
        );
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
                GeminiModelName,
                this.ApiKey
        );
        String body = buildGeminiRequestBody(prompt);
        HttpClient client = HttpClientBuilder.create().build();
        StringEntity input = new StringEntity(body, StandardCharsets.UTF_8);
        input.setContentType("application/json; charset=UTF-8");
        HttpPost post = new HttpPost(reqUrl);
        post.setHeader("Content-Type", "application/json; charset=UTF-8");
        post.setEntity(input);

        HttpResponse response = client.execute(post);
        HttpEntity httpEntity = response.getEntity();
        InputStream in = httpEntity.getContent();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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

    private void notifyPlayerFailure(MinecraftClient client, String reason) {
        client.execute(() -> {
            if (client.player != null) {
                String msg = reason == null ? "Gemini request failed." : reason;
                client.player.sendMessage(Text.literal("[AI Diary] Gemini request failed: " + msg), false);
            }
        });
    }

    List<String> usingSplitMethod(String text, int n) {
        //String[] results = text.split("(?<=\\G.{" + n + "})");
        //return Arrays.asList(results);
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += n) {
            chunks.add(text.substring(i, Math.min(length, i + n)));
        }
        return chunks;
    }

    public enum GenerationState {
        Idle(0),
        Generating(1),
        Completed(2);

        private final int n;

        GenerationState(int i) {
            this.n = i;
        }
    }
}
