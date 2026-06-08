package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.util.concurrent.CompletableFuture;

public class Trigger {
    private final Stats stats;
    private DiaryGenerator diaryGenerator;
    private long currentDay;
    private boolean hasPreviousPosition;
    private Vec3d previousPosition;
    private CompletableFuture<String> pendingDiary;

    Trigger(Stats stats, DiaryGenerator diaryGenerator) {
        this.stats = stats;
        this.diaryGenerator = diaryGenerator;
        this.currentDay = -1;
        this.previousPosition = Vec3d.ZERO;
        registerCallback();
    }

    public void updateApiKey(String newKey) {
        this.diaryGenerator = new DiaryGenerator(newKey);
    }

    private void registerCallback() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity != null) {
                String target = entity.getType().getName().getString();
                String how = player.getStackInHand(hand).getItem().getName().getString();
                this.stats.onClientAttacked(target, how);
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> onPlayerBlockBreak(state));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hitResult != null && world != null) {
                String block = world.getBlockState(hitResult.getBlockPos()).getBlock().getName().getString();
                this.stats.onBlockUsed(block);
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity != null) {
                this.stats.onEntityUsed(entity.getType().getName().getString());
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player != null) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack != null && !stack.isEmpty()) {
                    this.stats.onItemUsed(stack.getItem().getName().getString());
                }
            }
            return ActionResult.PASS;
        });
    }

    private void onPlayerBlockBreak(BlockState state) {
        if (state != null) {
            String block = state.getBlock().getName().getString();
            this.stats.onBlockDestroy(block);
        }
    }

    private void onClientTick(MinecraftClient client) {
        if (client == null || client.world == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        trackMovement(player);
        trackBiome(player);

        long days = client.world.getTimeOfDay() / ModConstants.TICKS_PER_DAY;
        if (this.currentDay == -1) {
            this.currentDay = days;
            return;
        }
        if (days != this.currentDay) {
            this.currentDay = days;
            Stats.Snapshot snapshot = this.stats.snapshot();
            this.stats.reset();
            triggerDiaryGeneration(client, days, snapshot);
        }
    }

    private void trackMovement(ClientPlayerEntity player) {
        Vec3d current = player.getEntityPos();
        if (!this.hasPreviousPosition) {
            this.previousPosition = current;
            this.hasPreviousPosition = true;
            return;
        }
        this.stats.addDistance(current.distanceTo(this.previousPosition));
        this.previousPosition = current;
    }

    private void trackBiome(ClientPlayerEntity player) {
        RegistryEntry<Biome> biomeEntry = player.getEntityWorld().getBiome(player.getBlockPos());
        String biomeId = biomeEntry.getKey().map(key -> key.getValue().getPath()).orElse("unknown");
        this.stats.addVisitedBiome(biomeId);
    }

    private void triggerDiaryGeneration(MinecraftClient client, long day, Stats.Snapshot snapshot) {
        if (!this.diaryGenerator.hasApiKey()) {
            AIDiaryClient.LOGGER.error("Gemini API key is not set");
            return;
        }
        if (this.pendingDiary != null && !this.pendingDiary.isDone()) {
            AIDiaryClient.LOGGER.warn("Diary generation already running, skipping day {}", day);
            return;
        }
        String languageLabel = Text.translatable("diary.text.language").getString();
        this.pendingDiary = this.diaryGenerator.requestDiary(day, snapshot, languageLabel);
        this.pendingDiary.whenComplete((text, throwable) -> {
            if (throwable != null) {
                client.execute(() -> {
                    this.pendingDiary = null;
                    notifyFailure(client, throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage());
                });
                return;
            }
            client.execute(() -> {
                this.pendingDiary = null;
                deliverDiary(client, text);
            });
        });
    }

    private void deliverDiary(MinecraftClient client, String diaryText) {
        if (diaryText == null || diaryText.isEmpty()) {
            return;
        }
        if (client.player == null) {
            return;
        }
        for (String chunk : this.diaryGenerator.chunkForChat(diaryText)) {
            client.player.sendMessage(Text.literal(chunk), false);
        }
    }

    private void notifyFailure(MinecraftClient client, String reason) {
        if (client.player == null) {
            return;
        }
        String message = reason == null ? "Gemini request failed." : reason;
        client.player.sendMessage(Text.literal("[AI Diary] " + message), false);
    }
}
