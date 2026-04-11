package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.biome.Biome;

import java.util.concurrent.CompletableFuture;

public class Trigger {
    private final Stats stats;
    private final DiaryGenerator diaryGenerator;
    private long currentDay;
    private boolean hasPreviousPosition;
    private Vec3 previousPosition;
    private CompletableFuture<String> pendingDiary;

    Trigger(Stats stats, DiaryGenerator diaryGenerator) {
        this.stats = stats;
        this.diaryGenerator = diaryGenerator;
        this.currentDay = -1;
        this.previousPosition = Vec3.ZERO;
        registerCallback();
    }

    private void registerCallback() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity != null) {
                String target = entity.getType().getDescription().getString();
                String how = player.getItemInHand(hand).getHoverName().getString();
                this.stats.onClientAttacked(target, how);
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> onPlayerBlockBreak(state));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hitResult != null && world != null) {
                String block = world.getBlockState(hitResult.getBlockPos()).getBlock().getName().getString();
                this.stats.onBlockUsed(block);
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity != null) {
                this.stats.onEntityUsed(entity.getType().getDescription().getString());
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player != null) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack != null && !stack.isEmpty()) {
                    this.stats.onItemUsed(stack.getHoverName().getString());
                }
            }
            return InteractionResult.PASS;
        });
    }

    private void onPlayerBlockBreak(BlockState state) {
        if (state != null) {
            String block = state.getBlock().getName().getString();
            this.stats.onBlockDestroy(block);
        }
    }

    private void onClientTick(Minecraft client) {
        if (client == null || client.level == null) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        trackMovement(player);
        trackBiome(player);

        long days = client.level.getOverworldClockTime() / ModConstants.TICKS_PER_DAY;
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
        Holder<Biome> biomeEntry = player.level().getBiome(player.blockPosition());
        String biomeId = biomeEntry.unwrapKey().map(Object::toString).orElse("unknown");
        this.stats.addVisitedBiome(biomeId);
    }

    private void triggerDiaryGeneration(Minecraft client, long day, Stats.Snapshot snapshot) {
        if (!this.diaryGenerator.hasApiKey()) {
            AIDiaryClient.LOGGER.error("Gemini API key is not set");
            return;
        }
        if (this.pendingDiary != null && !this.pendingDiary.isDone()) {
            AIDiaryClient.LOGGER.warn("Diary generation already running, skipping day {}", day);
            return;
        }
        String languageLabel = Component.translatable("diary.text.language").getString();
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

    private void deliverDiary(Minecraft client, String diaryText) {
        if (diaryText == null || diaryText.isEmpty()) {
            return;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            AIDiaryClient.LOGGER.error("Cannot deliver diary because client server is null");
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        Commands commandManager = server.getCommands();
        for (String chunk : this.diaryGenerator.chunkForChat(diaryText)) {
            String cmd = "say " + chunk;
            commandManager.performPrefixedCommand(source, cmd);
        }
    }

    private void notifyFailure(Minecraft client, String reason) {
        if (client.player == null) {
            return;
        }
        String message = reason == null ? "Gemini request failed." : reason;
        client.player.sendSystemMessage(Component.literal("[AI Diary] " + message));
    }
}
