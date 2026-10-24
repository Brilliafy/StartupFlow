package com.cleanroommc.startupflow.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * Companion coremod for FermiumBooter.
 *
 * <p>FermiumBooter's {@code FermiumJarScanner} uses ClassGraph to parse every {@code .class} file in
 * every mod jar on every launch, to collect mod ids ({@code @Mod}/{@code mcmod.info}) and
 * {@code @MixinConfig} toggles. On large packs this takes tens of seconds. This coremod caches that
 * result to disk, keyed on a fingerprint of {@code mods/*} and {@code config/fermiumbooter.cfg}, and
 * pre-populates the scanner before FermiumBooter would run it.</p>
 *
 * <p>Timing is important: the scan is triggered by the first
 * {@code FermiumRegistryAPI.isModPresent(...)} call, and some coremods call it from their
 * <b>constructor</b> during coremod discovery (before any {@code injectData}). This plugin is a
 * non-tweaker coremod, so its constructor runs earlier in that discovery pass than those tweaker
 * coremods, which is where the cache is loaded. Toggles are enqueued later, in {@code injectData},
 * immediately before FermiumBooter's own ({@code @SortingIndex(989)} vs its {@code 990}).</p>
 *
 * <p>It does not modify FermiumBooter and degrades gracefully to a normal scan on any error.</p>
 */
@IFMLLoadingPlugin.Name("StartupFlow")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(989)
public class StartupFlowCorePlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("FermiumScanCache");

    private static final String SCANNER_CLASS = "fermiumbooter.util.FermiumJarScanner";
    private static final File DISABLE_MARKER = new File("config", "fermiumscancache.disabled");

    private static Class<?> scannerClass;
    private static boolean populatedFromCache;

    public StartupFlowCorePlugin() {
        // Runs during coremod discovery, before any coremod constructor that calls
        // FermiumRegistryAPI.isModPresent() can trigger the expensive scan.
        if (isDisabled()) {
            return;
        }
        try {
            scannerClass = Class.forName(SCANNER_CLASS);
            populatedFromCache = FermiumScanCacheLogic.tryPopulateFromCache(scannerClass, LOGGER);
        } catch (ClassNotFoundException ignored) {
            // FermiumBooter is not installed or not on the classpath at constructor time; injectData will check
        } catch (Throwable t) {
            LOGGER.debug("[FermiumScanCache] Constructor-stage cache load skipped: {}", t.toString());
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        if (isDisabled()) {
            return;
        }
        try {
            if (scannerClass == null) {
                try {
                    scannerClass = Class.forName(SCANNER_CLASS);
                } catch (ClassNotFoundException e) {
                    LOGGER.info("[StartupFlow] FermiumBooter is not installed; jar scan caching is disabled.");
                    return;
                }
            }

            if (populatedFromCache) {
                FermiumScanCacheLogic.applyCachedToggles(scannerClass, LOGGER);
                return;
            }

            if (FermiumScanCacheLogic.hasScanData(scannerClass)) {
                // The scan already ran (e.g. triggered by another coremod's constructor). Just record it.
                FermiumScanCacheLogic.scanAndStore(scannerClass, LOGGER);
                return;
            }

            // Constructor-stage load failed (FermiumBooter jar was not on the classpath yet). Retry now.
            if (FermiumScanCacheLogic.tryPopulateFromCache(scannerClass, LOGGER)) {
                FermiumScanCacheLogic.applyCachedToggles(scannerClass, LOGGER);
                return;
            }

            // Genuine miss: let FermiumBooter scan, then cache the result.
            FermiumScanCacheLogic.scanAndStore(scannerClass, LOGGER);
        } catch (Throwable t) {
            LOGGER.error("[FermiumScanCache] Unexpected error; deferring to FermiumBooter's own jar scan.", t);
            if (scannerClass != null) {
                FermiumScanCacheLogic.clearState(scannerClass);
            }
        }
    }

    private static boolean isDisabled() {
        if (Boolean.getBoolean("fermiumscancache.disable")) {
            return true;
        }
        return DISABLE_MARKER.isFile();
    }
}
