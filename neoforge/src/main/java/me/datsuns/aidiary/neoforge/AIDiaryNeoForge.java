package me.datsuns.aidiary.neoforge;

import me.datsuns.aidiary.ModConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(ModConstants.MOD_ID)
public class AIDiaryNeoForge {

    public AIDiaryNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.CLIENT_SPEC, "aidiary.toml");
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onConfigReload);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> NeoForgeDiaryRuntime.getInstance().initialize(NeoForgeConfig.getGeminiApiKey()));
    }

    private void onConfigReload(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == NeoForgeConfig.CLIENT_SPEC) {
            NeoForgeDiaryRuntime.getInstance().updateApiKey(NeoForgeConfig.getGeminiApiKey());
        }
    }
}
