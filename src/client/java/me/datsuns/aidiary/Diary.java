package me.datsuns.aidiary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;


public class Diary {
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
        AIDiaryClient.LOGGER.info("diary generated");
        IntegratedServer s = client.getServer();
        if (s == null) {
            return;
        }
        ServerCommandSource src = s.getCommandSource();
        CommandManager cm = s.getCommandManager();
        String cmd = String.format("say %s", this.DiaryText);
        cm.executeWithPrefix(src, cmd);
        this.DiaryText = "";
        this.State = GenerationState.Idle;
    }

    public void onSave(MinecraftClient client, Stats stats) {
        if (this.ApiKey == "") {
            AIDiaryClient.LOGGER.info("api key is not set");
            return;
        }
        if (this.State != GenerationState.Idle) {
            AIDiaryClient.LOGGER.info("now on busy. skip.");
        }
        AIDiaryClient.LOGGER.info("save diary");
        this.State = GenerationState.Generating;
        long days = client.world.getTimeOfDay() / Trigger.TIME_PER_DAY;
        String prompt = generatePrompt(days, stats);
        CompletableFuture.runAsync(() -> {
            try {
                this.DiaryText = generateDiaryText(prompt);
            } catch (IOException e) {
                //throw new RuntimeException(e);
                AIDiaryClient.LOGGER.info("generate error");
                this.State = GenerationState.Idle;
                return;
            }
            AIDiaryClient.LOGGER.info("done");
            this.State = GenerationState.Completed;
        });
    }

    public String generateDiaryText(String prompt) throws IOException {
        AIDiaryClient.LOGGER.info("prompt is {}", prompt);
        JsonNode r = issueGeminiRequest(prompt);
        String generated = "";
        try {
            generated = r.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            AIDiaryClient.LOGGER.info("generated text {}", generated);
        } finally {
            AIDiaryClient.LOGGER.warn("Generate Error");
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
                "write a diary about Minecraft in %s. \n"
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

    private JsonNode issueGeminiRequest(String prompot) throws IOException {
        String reqUrl = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=%s",
                this.ApiKey
        );
        HttpClient client = HttpClientBuilder.create().build();
        JSONObject json = new JSONObject();
        JSONObject child = new JSONObject();
        JSONObject text = new JSONObject();
        text.put("text", prompot);
        child.append("parts", text);
        json.append("contents", child);
        StringEntity input = new StringEntity(json.toString());
        HttpPost post = new HttpPost(reqUrl);
        post.setEntity(input);
        HttpResponse response = client.execute(post);
        HttpEntity httpEntity = response.getEntity();
        InputStream in = httpEntity.getContent();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode r = objectMapper.readTree(in);
        return r;
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