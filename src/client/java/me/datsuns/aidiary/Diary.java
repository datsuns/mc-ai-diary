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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.apache.http.impl.client.HttpClientBuilder;
import com.google.gson.Gson;


public class Diary {
    public final Integer MaxLengthPerOneChat = 240;
    public final String GeminiModelName = "gemini-2.5-flash";
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
        //AIDiaryClient.LOGGER.info("diary generated");
        IntegratedServer s = client.getServer();
        if (s == null) {
            AIDiaryClient.LOGGER.error("Diary::onClientTick: client.getServer() ERROR");
            return;
        }
        ServerCommandSource src = s.getCommandSource();
        CommandManager cm = s.getCommandManager();
        for( String t : usingSplitMethod(this.DiaryText, MaxLengthPerOneChat) ) {
            String cmd = String.format("say %s", t);
            //AIDiaryClient.LOGGER.info("issue command [{}]", cmd);
            cm.parseAndExecute(src, cmd);
        }
        this.DiaryText = "";
        this.State = GenerationState.Idle;
    }

    public void onSave(MinecraftClient client, Stats stats) {
        if (this.ApiKey.isEmpty()) {
            AIDiaryClient.LOGGER.error("api key is not set");
            return;
        }
        if (this.State != GenerationState.Idle) {
            AIDiaryClient.LOGGER.error("now on busy. skip.");
        }
        //AIDiaryClient.LOGGER.info("save diary");
        this.State = GenerationState.Generating;
        long days = client.world.getTimeOfDay() / Trigger.TIME_PER_DAY;
        String prompt = generatePrompt(days, stats);
        CompletableFuture.runAsync(() -> {
            try {
                this.DiaryText = generateDiaryText(prompt);
            } catch (IOException e) {
                //throw new RuntimeException(e);
                AIDiaryClient.LOGGER.error("generate error {}", e);
                this.State = GenerationState.Idle;
                return;
            }
            //AIDiaryClient.LOGGER.info("generate diary done");
            this.State = GenerationState.Completed;
        });
    }

    public String generateDiaryText(String prompt) throws IOException {
        //AIDiaryClient.LOGGER.info("prompt is {}", prompt);
        String rawJson = issueGeminiRequest(prompt);
        String generated = "";
        try {
            generated = parseGeminiResponseJson(rawJson);
            //AIDiaryClient.LOGGER.info("generated text {}", generated);
        } catch (RuntimeException e) {
            AIDiaryClient.LOGGER.error("Generate Error {}", e);
        }
        return generated;

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

    private String buildGeminiRequestBody(String prompot) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject child = new JsonObject();
        JsonObject text = new JsonObject();
        text.addProperty("text", prompot);
        child.add("parts", text);
        root.add("contents", child);
        return root.toString();
    }

    private String parseGeminiResponseJson(String rawJson) {
        JsonObject jsonObj = (JsonObject) new Gson().fromJson(rawJson, JsonObject.class);
        JsonArray candidates = jsonObj.get("candidates").getAsJsonArray();
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.get("content").getAsJsonObject();
        JsonArray parts = content.get("parts").getAsJsonArray();
        JsonObject part = parts.get(0).getAsJsonObject();
        return part.get("text").getAsString();
    }

    private String issueGeminiRequest(String prompot) throws IOException {
        String reqUrl = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                GeminiModelName,
                this.ApiKey
        );
        String body = buildGeminiRequestBody(prompot);
        HttpClient client = HttpClientBuilder.create().build();
        StringEntity input = new StringEntity(body);
        HttpPost post = new HttpPost(reqUrl);
        post.setEntity(input);

        HttpResponse response = client.execute(post);
        HttpEntity httpEntity = response.getEntity();
        InputStream in = httpEntity.getContent();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
