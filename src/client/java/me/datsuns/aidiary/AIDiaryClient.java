package me.datsuns.aidiary;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.ClientModInitializer;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIDiaryClient implements ClientModInitializer {
    public static final String MOD_ID = "aidiary";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
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