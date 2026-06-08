package me.datsuns.aidiary.neoforge;

import me.datsuns.aidiary.DiaryGenerator;
import me.datsuns.aidiary.ModConstants;
import me.datsuns.aidiary.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class NeoForgeDiaryRuntime {
    private static final NeoForgeDiaryRuntime INSTANCE = new NeoForgeDiaryRuntime();

    private final Stats stats = new Stats();
    private DiaryGenerator diaryGenerator = new DiaryGenerator("");
    private long currentDay = -1;
    private boolean hasPreviousPosition;
    private Vec3 previousPosition = Vec3.ZERO;
    private CompletableFuture<String> pendingDiary;
    private boolean registered;

    private NeoForgeDiaryRuntime() {
    }

    public static NeoForgeDiaryRuntime getInstance() {
        return INSTANCE;
    }

    public void initialize(String apiKey) {
        this.diaryGenerator = new DiaryGenerator(apiKey);
        if (!this.registered) {
            NeoForge.EVENT_BUS.register(this);
            this.registered = true;
        }
    }

    public void updateApiKey(String apiKey) {
        this.diaryGenerator = new DiaryGenerator(apiKey);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        trackMovement(player);
        trackBiome(player);

        long day = player.level().getDayTime() / ModConstants.TICKS_PER_DAY;
        if (this.currentDay == -1) {
            this.currentDay = day;
            return;
        }
        if (day != this.currentDay) {
            this.currentDay = day;
            Stats.Snapshot snapshot = this.stats.snapshot();
            this.stats.reset();
            triggerDiaryGeneration(day, snapshot);
        }
    }

    private void trackMovement(LocalPlayer player) {
        Vec3 current = player.position();
        if (!this.hasPreviousPosition) {
            this.previousPosition = current;
            this.hasPreviousPosition = true;
            return;
        }
        this.stats.addDistance(current.distanceTo(this.previousPosition));
        this.previousPosition = current;
    }

    private void trackBiome(LocalPlayer player) {
        Optional<Identifier> biomeId = player.level().getBiome(player.blockPosition()).unwrapKey().map(key -> key.identifier());
        biomeId.ifPresent(id -> this.stats.addVisitedBiome(id.getPath()));
    }

    private void triggerDiaryGeneration(long day, Stats.Snapshot snapshot) {
        if (!this.diaryGenerator.hasApiKey()) {
            ModConstants.LOGGER.error("Gemini API key is not set");
            return;
        }
        if (this.pendingDiary != null && !this.pendingDiary.isDone()) {
            ModConstants.LOGGER.warn("Diary generation already running, skipping day {}", day);
            return;
        }
        String languageLabel = Component.translatable("diary.text.language").getString();
        this.pendingDiary = this.diaryGenerator.requestDiary(day, snapshot, languageLabel);
        this.pendingDiary.whenComplete((text, throwable) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (throwable != null) {
                minecraft.execute(() -> {
                    this.pendingDiary = null;
                    notifyFailure(throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage());
                });
                return;
            }
            minecraft.execute(() -> {
                this.pendingDiary = null;
                deliverDiary(text);
            });
        });
    }

    private void deliverDiary(String diaryText) {
        if (diaryText == null || diaryText.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        for (String chunk : this.diaryGenerator.chunkForChat(diaryText)) {
            minecraft.player.displayClientMessage(Component.literal(chunk), false);
        }
    }

    private void notifyFailure(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String message = reason == null ? "Gemini request failed." : reason;
        minecraft.player.displayClientMessage(Component.literal("[AI Diary] " + message), false);
    }
}