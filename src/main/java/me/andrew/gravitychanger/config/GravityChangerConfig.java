package me.andrew.gravitychanger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class GravityChangerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean keepWorldLook = true;
    public boolean resetGravityOnDimensionChange = true;
    public boolean resetGravityOnRespawn = true;
    public boolean voidDamageAboveWorld = true;

    public static GravityChangerConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("gravitychanger.json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                GravityChangerConfig config = GSON.fromJson(reader, GravityChangerConfig.class);
                if (config != null) return config;
            } catch (IOException ignored) {
            }
        }

        GravityChangerConfig config = new GravityChangerConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
        return config;
    }
}
