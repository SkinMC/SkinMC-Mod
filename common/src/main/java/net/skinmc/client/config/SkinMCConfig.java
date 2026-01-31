package net.skinmc.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.skinmc.SkinMCMod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client config (config/skinmc_mod.json). Path set by platform before load(). */
public final class SkinMCConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath;
    private static CapeDisplay capeDisplay = CapeDisplay.SKINMC_OVERRIDES;
    private static boolean enableShiftRightClickPlayer = true;

    public static void setConfigPath(Path path) {
        configPath = path;
    }

    public static CapeDisplay getCapeDisplay() {
        return capeDisplay;
    }

    public static void setCapeDisplay(CapeDisplay value) {
        capeDisplay = value == null ? CapeDisplay.SKINMC_OVERRIDES : value;
    }

    public static boolean isEnableShiftRightClickPlayer() {
        return enableShiftRightClickPlayer;
    }

    public static void setEnableShiftRightClickPlayer(boolean value) {
        enableShiftRightClickPlayer = value;
    }

    public static void load() {
        if (configPath == null) return;
        if (!Files.isRegularFile(configPath)) return;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                if (data.capeDisplay != null) {
                    try {
                        capeDisplay = CapeDisplay.valueOf(data.capeDisplay);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (data.enableShiftRightClickPlayer != null) {
                    enableShiftRightClickPlayer = data.enableShiftRightClickPlayer;
                }
            }
        } catch (IOException e) {
            SkinMCMod.LOGGER.warn("[SkinMC] Could not load config: {}", e.getMessage());
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Path parent = configPath.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            Data data = new Data();
            data.capeDisplay = capeDisplay.name();
            data.enableShiftRightClickPlayer = enableShiftRightClickPlayer;
            Files.writeString(configPath, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkinMCMod.LOGGER.warn("[SkinMC] Could not save config: {}", e.getMessage());
        }
    }

    private static class Data {
        @SerializedName("cape_display")
        String capeDisplay;
        @SerializedName("enable_shift_right_click_player")
        Boolean enableShiftRightClickPlayer;
    }
}
