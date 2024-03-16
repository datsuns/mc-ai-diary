package me.datsuns.aidiary;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIDiaryClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("aidiary");
    public Trigger Trigger;

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        this.Trigger = new Trigger(new Stats(), new Diary());
    }
}