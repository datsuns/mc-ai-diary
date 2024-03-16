package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Stats {
    public Vec3d PrevPos;
    public double TotalDistance;
    public Boolean Initialized; // need better implementation.

    Stats() {
        this.PrevPos = new Vec3d(0.0, 0.0, 0.0);
        this.TotalDistance = 0.0F;
        this.Initialized = false;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTickHandler(client);
        });
    }

    void clientTickHandler(MinecraftClient client) {
        ClientPlayerEntity e = client.player;
        if (e == null) {
            return;
        }
        if (!this.Initialized) {
            this.PrevPos = e.getPos();
            AIDiaryClient.LOGGER.info("set prev {}", this.PrevPos);
            this.Initialized = true;
            return;
        }
        Vec3d cur = e.getPos();
        double prev = this.TotalDistance;
        this.TotalDistance += cur.distanceTo(this.PrevPos);
        this.PrevPos = cur;
        //AIDiaryClient.LOGGER.info("distance {}", distance());
    }

    public void reset() {
        this.TotalDistance = 0.0F;
        this.PrevPos = null;
    }

    public double distance() {
        return this.TotalDistance;
    }
}
