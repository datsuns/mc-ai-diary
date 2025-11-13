package me.datsuns.aidiary;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

import me.datsuns.aidiary.ModConstants;

public class AIDiaryClient implements ClientModInitializer {
    public static final Logger LOGGER = ModConstants.LOGGER;
    public Trigger Trigger;
    public static ModConfig ModConfig;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(ModConfig.class, Toml4jConfigSerializer::new);
        this.ModConfig = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        this.Trigger = new Trigger(
                new Stats(),
                new Diary(this.ModConfig.GeminiApikey)
        );
    }
}
