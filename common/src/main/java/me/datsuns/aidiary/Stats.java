package me.datsuns.aidiary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Stats {
    private double totalDistance;
    private final Map<String, Map<String, Integer>> attacked;
    private final Set<String> visitedBiomes;
    private final List<String> weather;
    private final Map<String, Integer> usedItem;
    private final Map<String, Integer> usedBlock;
    private final Map<String, Integer> destroyBlock;
    private final Map<String, Integer> usedEntity;

    public Stats() {
        this.totalDistance = 0.0D;
        this.attacked = new HashMap<>();
        this.visitedBiomes = new LinkedHashSet<>();
        this.weather = new ArrayList<>();
        this.usedItem = new HashMap<>();
        this.usedBlock = new HashMap<>();
        this.destroyBlock = new HashMap<>();
        this.usedEntity = new HashMap<>();
    }

    public void addDistance(double delta) {
        this.totalDistance += delta;
    }

    public void addVisitedBiome(String biome) {
        if (biome != null && !biome.isEmpty()) {
            this.visitedBiomes.add(biome);
        }
    }

    public void addWeather(String weatherDescription) {
        if (weatherDescription != null && !weatherDescription.isEmpty()) {
            this.weather.add(weatherDescription);
        }
    }

    public void onClientAttacked(String target, String how) {
        if (target == null || how == null) {
            return;
        }
        Map<String, Integer> byMethod = this.attacked.computeIfAbsent(target, key -> new HashMap<>());
        byMethod.merge(how, 1, Integer::sum);
    }

    public void onItemUsed(String item) {
        recordCount(this.usedItem, item);
    }

    public void onEntityUsed(String entity) {
        recordCount(this.usedEntity, entity);
    }

    public void onBlockUsed(String block) {
        recordCount(this.usedBlock, block);
    }

    public void onBlockDestroy(String block) {
        recordCount(this.destroyBlock, block);
    }

    private void recordCount(Map<String, Integer> map, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        map.merge(key, 1, Integer::sum);
    }

    public Snapshot snapshot() {
        Map<String, Map<String, Integer>> attackedCopy = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : this.attacked.entrySet()) {
            attackedCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new Snapshot(
                this.totalDistance,
                attackedCopy,
                new ArrayList<>(this.weather),
                new ArrayList<>(this.visitedBiomes),
                new HashMap<>(this.usedItem),
                new HashMap<>(this.usedBlock),
                new HashMap<>(this.destroyBlock),
                new HashMap<>(this.usedEntity)
        );
    }

    public void reset() {
        this.totalDistance = 0.0D;
        this.attacked.clear();
        this.weather.clear();
        this.visitedBiomes.clear();
        this.usedItem.clear();
        this.usedBlock.clear();
        this.destroyBlock.clear();
        this.usedEntity.clear();
    }

    public static final class Snapshot {
        private final double totalDistance;
        private final Map<String, Map<String, Integer>> attacked;
        private final List<String> weather;
        private final List<String> visitedBiomes;
        private final Map<String, Integer> usedItem;
        private final Map<String, Integer> usedBlock;
        private final Map<String, Integer> destroyBlock;
        private final Map<String, Integer> usedEntity;

        private Snapshot(double totalDistance,
                          Map<String, Map<String, Integer>> attacked,
                          List<String> weather,
                          List<String> visitedBiomes,
                          Map<String, Integer> usedItem,
                          Map<String, Integer> usedBlock,
                          Map<String, Integer> destroyBlock,
                          Map<String, Integer> usedEntity) {
            this.totalDistance = totalDistance;
            this.attacked = attacked;
            this.weather = weather;
            this.visitedBiomes = visitedBiomes;
            this.usedItem = usedItem;
            this.usedBlock = usedBlock;
            this.destroyBlock = destroyBlock;
            this.usedEntity = usedEntity;
        }

        public double totalDistance() {
            return totalDistance;
        }

        public Map<String, Map<String, Integer>> attacked() {
            return attacked;
        }

        public List<String> weather() {
            return weather;
        }

        public List<String> visitedBiomes() {
            return visitedBiomes;
        }

        public Map<String, Integer> usedItem() {
            return usedItem;
        }

        public Map<String, Integer> usedBlock() {
            return usedBlock;
        }

        public Map<String, Integer> destroyBlock() {
            return destroyBlock;
        }

        public Map<String, Integer> usedEntity() {
            return usedEntity;
        }

        public boolean isEmpty() {
            return attacked.isEmpty() && visitedBiomes.isEmpty() && usedItem.isEmpty()
                    && usedBlock.isEmpty() && destroyBlock.isEmpty() && usedEntity.isEmpty()
                    && totalDistance <= 0.0D && weather.isEmpty();
        }
    }
}
