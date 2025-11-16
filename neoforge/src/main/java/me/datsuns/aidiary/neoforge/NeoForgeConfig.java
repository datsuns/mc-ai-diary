package me.datsuns.aidiary.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    private static final ConfigValues CLIENT_VALUES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CLIENT_VALUES = new ConfigValues(builder);
        CLIENT_SPEC = builder.build();
    }

    private NeoForgeConfig() {
    }

    public static String getGeminiApiKey() {
        return CLIENT_VALUES.geminiApiKey.get();
    }

    private static final class ConfigValues {
        private final ModConfigSpec.ConfigValue<String> geminiApiKey;

        ConfigValues(ModConfigSpec.Builder builder) {
            this.geminiApiKey = builder
                    .comment("Gemini API key used to generate diary entries.")
                    .define("GeminiApikey", "");
        }
    }
}
