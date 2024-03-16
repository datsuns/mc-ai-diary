package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class Trigger {
    public Stats Stats;
    public Diary Diary;

    Trigger( Stats s, Diary d){
        this.Stats = s;
        this.Diary = d;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            this.Stats.onClientTick(client);
        });
    }
}
