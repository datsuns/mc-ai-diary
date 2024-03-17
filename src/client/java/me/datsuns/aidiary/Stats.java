package me.datsuns.aidiary;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// ネタ帳
// [ ]通ったバイオーム
// [ ]mobとのかかわり
// [ ]置いたブロック数
// [ ]その日の天気
// 昔話風

public class Stats {
    public Vec3d PrevPos;
    public double TotalDistance;
    public Boolean Initialized; // need better implementation.
    public HashMap<String, HashMap<String, Integer>> Attacked;
    public ArrayList<String> VisitedBioms;

    Stats() {
        this.PrevPos = new Vec3d(0.0, 0.0, 0.0);
        this.TotalDistance = 0.0F;
        this.Initialized = false;
        Map m = new HashMap<String, Integer>();
        this.Attacked = new HashMap<String, HashMap<String, Integer>>();
        this.VisitedBioms = new ArrayList<String>();
    }

    void onClientTick(MinecraftClient client) {
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
        RegistryEntry<Biome> b = e.getWorld().getBiome(e.getBlockPos());
        String biom = b.getKey().get().getValue().getPath().toString();
        if( !this.VisitedBioms.contains(biom) ){
            this.VisitedBioms.add(biom);
        }
    }

    void onClientAttacked(String target, String how) {
        Map<String, Integer> m = this.Attacked.get(target);
        if (m != null) {
            if (m.containsKey(how)) {
                Integer next = m.get(how).intValue() + 1;
                m.put(how, next);
            } else {
                m.put(how, 1);
            }
        } else {
            HashMap<String, Integer> newEntry = new HashMap<String, Integer>();
            newEntry.put(how, 1);
            this.Attacked.put(target, newEntry);
        }
    }

    public void reset() {
        this.TotalDistance = 0.0F;
        this.Attacked.clear();
        this.VisitedBioms.clear();
    }

    public double distance() {
        return this.TotalDistance;
    }
}
