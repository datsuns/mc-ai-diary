package me.datsuns.aidiary;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

import me.datsuns.aidiary.ModConstants;

public class AIDiaryClient implements ClientModInitializer {
    public static final Logger LOGGER = ModConstants.LOGGER;
    public static Trigger Trigger;
    public static ModConfig ModConfig;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing AI Diary client...");
        AutoConfig.register(ModConfig.class, Toml4jConfigSerializer::new);
        this.ModConfig = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        Trigger = new Trigger(
                new Stats(),
                new DiaryGenerator(ModConfig.GeminiApikey)
        );
    }
}
