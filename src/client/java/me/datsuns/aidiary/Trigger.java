package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class Trigger {
    public final long TIME_PER_DAY = 24000;

    public Stats Stats;
    public Diary Diary;
    public long  CurrentDay;

    // いずれはInterfaceの配列にでも
    Trigger( Stats s, Diary d){
        this.Stats = s;
        this.Diary = d;
        this.CurrentDay = -1;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            onClientTick(client);
            this.Stats.onClientTick(client);
        });
    }

    public void onClientTick(MinecraftClient client){
        if( client == null || client.world == null ) {
            return;
        }
        long tod = client.world.getTimeOfDay();
        long days = tod / TIME_PER_DAY;
        if( this.CurrentDay == -1 ) {
            this.CurrentDay = days;
            return;
        }
        if( days != this.CurrentDay ){
            this.CurrentDay = days;
            this.Diary.onSave();
        }
    }
}
