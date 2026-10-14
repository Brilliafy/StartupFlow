package com.cleanroommc.startupflow.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = "startupflow", name = "startupflow")
public class ModConfig {

    @Config.Name("Model Baking")
    @Config.Comment("Parallelisation of block/item model baking and texture atlas mipmap generation.")
    public static ModelBaking MODEL_BAKING = new ModelBaking();

    @Config.Comment("Log model baking and mipmap thread counts to the game log.")
    @Config.Name("Debug Logging")
    public static boolean debugLogging = false;

    public static class ModelBaking {
        @Config.Comment("Bake block and item models across multiple worker threads instead of a single thread.")
        @Config.Name("Enable Parallel Model Baking")
        public boolean enableParallelModelBaking = true;

        @Config.Comment("Number of threads for model baking (0 = auto-detect CPU cores).")
        @Config.RangeInt(min = 0, max = 128)
        @Config.Name("Model Baking Threads")
        public int modelBakingThreads = 0;

        @Config.Comment("Compute texture atlas mipmap levels concurrently across multiple threads.")
        @Config.Name("Enable Parallel Mipmaps")
        public boolean enableParallelMipmaps = true;

        @Config.Comment("Number of threads for mipmap generation (0 = auto-detect CPU cores).")
        @Config.RangeInt(min = 0, max = 128)
        @Config.Name("Mipmap Threads")
        public int mipmapThreads = 0;
    }

    /** Ensures the config is loaded and on disk. Called once from the mod's preInit. */
    public static void init() {
        ConfigManager.sync("startupflow", Config.Type.INSTANCE);
    }

    public static int getEffectiveModelBakingThreads() {
        if (MODEL_BAKING.modelBakingThreads > 0) {
            return MODEL_BAKING.modelBakingThreads;
        }
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    public static int getEffectiveMipmapThreads() {
        if (MODEL_BAKING.mipmapThreads > 0) {
            return MODEL_BAKING.mipmapThreads;
        }
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    @Mod.EventBusSubscriber(modid = "startupflow")
    public static class ConfigSyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals("startupflow")) {
                ConfigManager.sync("startupflow", Config.Type.INSTANCE);
            }
        }
    }
}
