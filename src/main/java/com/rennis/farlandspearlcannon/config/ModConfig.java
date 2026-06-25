package com.rennis.farlandspearlcannon.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Values values = new Values();

    public static class Values {
        public int targetDistance = 12_550_650;
        public int cooldownSeconds = 300;
        public int chargeTicks = 100;
        public int fuelRequired = 3;
        public boolean consumePearlOnFire = true;
        public boolean consumeFuelOnFire = true;
        public boolean giveAnchorPearlOnLaunch = true;
        public boolean enableReturnCommand = true;
        public boolean returnCommandRequiresPermission = false;
        // The /farlandscannon return safety command only works this far (in blocks) from world origin,
        // so it can only rescue you from the Far Lands and never doubles as an easy teleport home.
        public int returnCommandMinDistance = 1_000_000;
        public boolean createEmergencyLandingPlatform = true;
        public boolean requireRedstoneSignal = true;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("farlands-pearl-cannon.json");
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    GSON.toJson(values, writer);
                }
                return;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                Values loaded = GSON.fromJson(reader, Values.class);
                if (loaded != null) values = loaded;
            }
        } catch (IOException e) {
            FarlandsPearlCannonMod.LOGGER.warn("Could not load Farlands Pearl Cannon config; using defaults.", e);
        }
    }

    public static long cooldownTicks() {
        return Math.max(0, values.cooldownSeconds) * 20L;
    }
}
