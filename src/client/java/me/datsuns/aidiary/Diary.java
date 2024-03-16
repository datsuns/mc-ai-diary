package me.datsuns.aidiary;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.integrated.IntegratedServer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class Diary {
    public GenerationState State;
    Diary() {
        this.State = GenerationState.Idle;
    }

    public void onClientTick(MinecraftClient client) {
        if( this.State != GenerationState.Completed ){
            return;
        }
        AIDiaryClient.LOGGER.info("diary generated");
        this.State = GenerationState.Idle;
    }

    public void onSave(MinecraftClient client) {
        if( this.State != GenerationState.Idle ){
            AIDiaryClient.LOGGER.info("now on busy. skip.");
        }
        AIDiaryClient.LOGGER.info("save diary");
        IntegratedServer s = client.getServer();
        if (s == null) {
            return;
        }
        ServerCommandSource src = s.getCommandSource();
        CommandManager cm = s.getCommandManager();
        String cmd = String.format("say %s", "hello");
        cm.executeWithPrefix(src, cmd);
        this.State = GenerationState.Generating;
        CompletableFuture.runAsync( () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            AIDiaryClient.LOGGER.info("done");
            this.State = GenerationState.Completed;
        });
    }

    public enum GenerationState {
        Idle(0),
        Generating(1),
        Completed(2);

        private final int n;
        GenerationState(int i){
            this.n = i;
        }
    }
}