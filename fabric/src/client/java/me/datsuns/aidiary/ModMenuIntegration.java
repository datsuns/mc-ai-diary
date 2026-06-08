package me.datsuns.aidiary;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.datsuns.aidiary.client.gui.AIDiaryConfigScreen;
import me.shedaniel.autoconfig.AutoConfig;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new AIDiaryConfigScreen(parent, AIDiaryClient.ModConfig.GeminiApikey, newKey -> {
            AIDiaryClient.ModConfig.GeminiApikey = newKey;
            AutoConfig.getConfigHolder(ModConfig.class).save();
            if (AIDiaryClient.Trigger != null) {
                AIDiaryClient.Trigger.updateApiKey(newKey);
            }
        });
    }
}
