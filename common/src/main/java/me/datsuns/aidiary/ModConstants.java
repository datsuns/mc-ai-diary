package me.datsuns.aidiary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared identifiers and logger for cross-loader code.
 */
public final class ModConstants {
    public static final String MOD_ID = "ai-diary";
    public static final String CONFIG_ID = "aidiary";
    public static final long TICKS_PER_DAY = 24000L;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ModConstants() {
    }
}
