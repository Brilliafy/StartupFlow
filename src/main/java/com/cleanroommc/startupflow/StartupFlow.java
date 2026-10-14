package com.cleanroommc.startupflow;

import com.cleanroommc.startupflow.command.CommandStartupFlow;
import com.cleanroommc.startupflow.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Startup Flow - startup optimizations for Cleanroom 1.12.2.
 *
 * <p>This jar is <b>both</b> a coremod and a mod. The coremod half (see
 * {@code com.cleanroommc.startupflow.core.StartupFlowCorePlugin}) caches FermiumBooter's expensive
 * startup jar scan to disk and runs during launchwrapper/coremod discovery. This {@code @Mod} half
 * runs on both the client and dedicated servers (it is intentionally <b>not</b> client-side only) and
 * initialises the model-baking configuration. The model-baking and mipmap mixins themselves only
 * apply on the client via the late mixin config.</p>
 */
@Mod(
        modid = StartupFlow.MOD_ID,
        name = StartupFlow.MOD_NAME,
        version = StartupFlow.VERSION,
        acceptableRemoteVersions = "*"
)
public class StartupFlow {

    public static final String MOD_ID = "startupflow";
    public static final String MOD_NAME = "Startup Flow";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.Instance(MOD_ID)
    public static StartupFlow instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.init();
        LOGGER.info("[StartupFlow] Parallel startup optimizer initialized with {} model baking threads and {} mipmap threads.",
                ModConfig.getEffectiveModelBakingThreads(),
                ModConfig.getEffectiveMipmapThreads());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandStartupFlow());
    }
}
