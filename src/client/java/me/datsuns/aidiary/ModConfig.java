package me.datsuns.aidiary;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = AIDiaryClient.MOD_ID)
public class ModConfig implements ConfigData {
    public String GeminiApikey = "";
}
