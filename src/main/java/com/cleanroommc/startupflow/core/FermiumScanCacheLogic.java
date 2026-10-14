package com.cleanroommc.startupflow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads (or produces) a cached snapshot of FermiumBooter's jar scan and pushes it into
 * {@code FermiumJarScanner} so the expensive ClassGraph pass is skipped.
 *
 * <p>Timing matters: the scan is triggered by the first {@code FermiumRegistryAPI.isModPresent(...)}
 * call, and at least one coremod ({@code savemyankhshield.SaveMyAnkhShieldPlugin}) calls it from its
 * <b>constructor</b> during coremod discovery - before any {@code injectData}. This plugin's own
 * constructor runs during the same discovery pass (it is a non-tweaker coremod, so it is instantiated
 * in {@code CleanroomModDiscoverer.discoverCoreMods} rather than the deferred
 * {@code rescueDroppedCoremods} pass), so the cache is populated in the constructor via
 * {@link #tryPopulateFromCache(Class, Logger)}. Enqueuing the cached {@code @MixinConfig} toggles is
 * deferred to {@code injectData} (right before FermiumBooter's own), which is still before
 * FermiumBooter processes the enqueued configs.</p>
 */
public final class FermiumScanCacheLogic {

    /** Bump when the cache layout changes, to invalidate old files. */
    private static final int FORMAT = 3;

    private static final File CACHE_FILE = new File("config", "fermiumscancache.json");
    private static final File BOOTER_CONFIG = new File("config", "fermiumbooter.cfg");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Classpath entries recorded on a cache hit, replayed when the toggles are applied. */
    private static volatile List<String> pendingClasspath = new ArrayList<>();

    private final ScannerBridge bridge;
    private final Logger log;

    private FermiumScanCacheLogic(ScannerBridge bridge, Logger log) {
        this.bridge = bridge;
        this.log = log;
    }

    /**
     * Called from the plugin constructor, before any FermiumBooter scan can be triggered. Populates
     * the scanner's maps from cache if a valid cache exists. Does not enqueue mixins yet.
     *
     * @return true if the scanner was populated from cache.
     */
    public static boolean tryPopulateFromCache(Class<?> scannerClass, Logger log) throws Exception {
        FermiumScanCacheLogic logic = new FermiumScanCacheLogic(new ScannerBridge(scannerClass), log);

        ScanCache cache = logic.load(fingerprint());
        if (cache == null) {
            return false;
        }

        try {
            logic.populate(cache);
            pendingClasspath = cache.classpathAdditions;
            log.info("[FermiumScanCache] Cache hit: pre-loaded FermiumBooter scan ({} mods, {} @MixinConfig toggles); the startup jar scan will be skipped.",
                    cache.mods.size(), cache.toggles.size());
            return true;
        } catch (Throwable t) {
            logic.clear();
            log.warn("[FermiumScanCache] Could not populate from cache; falling back to a full scan.", t);
            return false;
        }
    }

    /**
     * Called from injectData when the constructor populated the scanner from cache. Replays the
     * classloader additions and enqueues every cached {@code @MixinConfig} toggle.
     */
    public static void applyCachedToggles(Class<?> scannerClass, Logger log) throws Exception {
        FermiumScanCacheLogic logic = new FermiumScanCacheLogic(new ScannerBridge(scannerClass), log);
        long start = System.currentTimeMillis();
        try {
            replayClasspath(pendingClasspath);
            int applied = 0;
            for (Object toggle : logic.bridge.getMixinToggles()) {
                logic.bridge.applyMixinConfig(toggle);
                applied++;
            }
            log.info("[FermiumScanCache] Applied {} cached @MixinConfig toggles in {} ms (jar scan skipped).",
                    applied, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            // Leave the scanner empty so FermiumBooter's injectData performs a clean scan of its own.
            logic.clear();
            throw t;
        }
    }

    /** Called from injectData when the constructor did NOT have a cache (miss or early failure). */
    public static void scanAndStore(Class<?> scannerClass, Logger log) throws Exception {
        new FermiumScanCacheLogic(new ScannerBridge(scannerClass), log).scanAndStore(fingerprint());
    }

    /** Clears any partially-populated scanner state so FermiumBooter will perform a clean scan. */
    public static void clearState(Class<?> scannerClass) {
        try {
            new FermiumScanCacheLogic(new ScannerBridge(scannerClass), StartupFlowCorePlugin.LOGGER).clear();
        } catch (Throwable ignored) {
        }
    }

    /** True if the scanner already has data (a scan already happened this launch). */
    public static boolean hasScanData(Class<?> scannerClass) {
        try {
            return !new ScannerBridge(scannerClass).getPresentMods().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private void populate(ScanCache cache) throws Exception {
        Map<String, Object> presentMods = bridge.getPresentMods();
        presentMods.clear();
        for (Map.Entry<String, ScanCache.ModEntry> entry : cache.mods.entrySet()) {
            ScanCache.ModEntry mod = entry.getValue();
            presentMods.put(entry.getKey(), bridge.newModInfo(entry.getKey(), mod.version, mod.name));
        }

        Set<Object> mixinToggles = bridge.getMixinToggles();
        mixinToggles.clear();
        for (ScanCache.ToggleEntry entry : cache.toggles) {
            Object toggle = bridge.newMixinConfigInfo(
                    entry.modId, entry.name, entry.earlyJson, entry.lateJson, entry.defaultValue);
            if (entry.compat != null) {
                List<Object> compatInfos = bridge.getCompatInfos(toggle);
                for (ScanCache.CompatEntry compat : entry.compat) {
                    compatInfos.add(bridge.newCompatInfo(
                            compat.modId, compat.modName, compat.modVersionRange,
                            compat.desired, compat.disableMixin, compat.warnIngame, compat.reason));
                }
            }
            mixinToggles.add(toggle);
        }
    }

    private void clear() {
        try {
            bridge.getPresentMods().clear();
            bridge.getMixinToggles().clear();
        } catch (Throwable ignored) {
        }
    }

    private void scanAndStore(String fingerprint) throws Exception {
        Set<String> urlsBefore = currentClasspathUrls();

        long start = System.currentTimeMillis();
        try {
            bridge.handleCaching();
        } catch (Throwable t) {
            clear();
            throw t;
        }
        long elapsed = System.currentTimeMillis() - start;

        ScanCache cache = new ScanCache();
        cache.format = FORMAT;
        cache.fingerprint = fingerprint;

        for (Map.Entry<String, Object> entry : bridge.getPresentMods().entrySet()) {
            Object modInfo = entry.getValue();
            ScanCache.ModEntry mod = new ScanCache.ModEntry();
            mod.version = bridge.getModVersion(modInfo);
            mod.name = bridge.getModName(modInfo);
            cache.mods.put(entry.getKey(), mod);
        }

        for (Object toggle : bridge.getMixinToggles()) {
            ScanCache.ToggleEntry entry = new ScanCache.ToggleEntry();
            entry.modId = bridge.getToggleModId(toggle);
            entry.name = bridge.getToggleName(toggle);
            entry.earlyJson = bridge.getToggleEarly(toggle);
            entry.lateJson = bridge.getToggleLate(toggle);
            entry.defaultValue = bridge.getToggleDefault(toggle);
            for (Object compatInfo : bridge.getCompatInfos(toggle)) {
                ScanCache.CompatEntry compat = new ScanCache.CompatEntry();
                compat.modId = bridge.getCompatModId(compatInfo);
                compat.modName = bridge.getCompatModName(compatInfo);
                compat.modVersionRange = bridge.getCompatRange(compatInfo);
                compat.desired = bridge.getCompatDesired(compatInfo);
                compat.disableMixin = bridge.getCompatDisable(compatInfo);
                compat.warnIngame = bridge.getCompatWarn(compatInfo);
                compat.reason = bridge.getCompatReason(compatInfo);
                entry.compat.add(compat);
            }
            cache.toggles.add(entry);
        }

        // FermiumBooter adds jars containing @MixinConfig classes to the launch classloader during the
        // scan. On a cache hit we skip the scan, so record (and later replay) exactly which URLs it added.
        Set<String> urlsAfter = currentClasspathUrls();
        for (String url : urlsAfter) {
            if (!urlsBefore.contains(url)) {
                cache.classpathAdditions.add(url);
            }
        }

        save(cache);
        log.info("[FermiumScanCache] Cached FermiumBooter scan ({} mods, {} toggles, took {} ms). Subsequent launches will skip it.",
                cache.mods.size(), cache.toggles.size(), elapsed);
    }

    private ScanCache load(String fingerprint) {
        if (!CACHE_FILE.isFile()) {
            return null;
        }
        try (Reader reader = new FileReader(CACHE_FILE)) {
            ScanCache cache = GSON.fromJson(reader, ScanCache.class);
            if (cache == null) {
                return null;
            }
            if (cache.format != FORMAT) {
                log.info("[FermiumScanCache] Cache format {} != {}; ignoring old cache.", cache.format, FORMAT);
                return null;
            }
            if (cache.fingerprint == null || !cache.fingerprint.equals(fingerprint)) {
                log.info("[FermiumScanCache] Mod set changed since last launch; rescanning.");
                return null;
            }
            if (cache.mods == null || cache.mods.isEmpty() || cache.toggles == null) {
                return null;
            }
            return cache;
        } catch (Throwable t) {
            log.warn("[FermiumScanCache] Could not read cache; rescanning.", t);
            return null;
        }
    }

    private void save(ScanCache cache) {
        try {
            File parent = CACHE_FILE.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                log.warn("[FermiumScanCache] Could not create {}; cache not written.", parent);
                return;
            }
            try (Writer writer = new FileWriter(CACHE_FILE)) {
                GSON.toJson(cache, writer);
            }
        } catch (Throwable t) {
            log.warn("[FermiumScanCache] Could not write cache; next launch will rescan.", t);
        }
    }

    private static void replayClasspath(List<String> additions) throws Exception {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        LaunchClassLoader classLoader = Launch.classLoader;
        Set<String> existing = currentClasspathUrls();
        for (String url : additions) {
            if (url == null || existing.contains(url)) {
                continue;
            }
            classLoader.addURL(new URL(url));
            existing.add(url);
        }
    }

    private static Set<String> currentClasspathUrls() {
        Set<String> urls = new LinkedHashSet<>();
        for (URL url : Launch.classLoader.getURLs()) {
            urls.add(url.toString());
        }
        return urls;
    }

    /**
     * Fingerprint of everything the scan result depends on: the contents of {@code mods/} (names, sizes
     * and mtimes) plus FermiumBooter's own config file. Any mod add/remove/update invalidates the cache.
     */
    private static String fingerprint() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        File modsDir = new File("mods");
        File[] files = modsDir.listFiles();
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            update(digest, file.getName());
            update(digest, Long.toString(file.length()));
            update(digest, Long.toString(file.lastModified()));
        }

        if (BOOTER_CONFIG.isFile()) {
            update(digest, BOOTER_CONFIG.getName());
            update(digest, Long.toString(BOOTER_CONFIG.length()));
            update(digest, Long.toString(BOOTER_CONFIG.lastModified()));
        }

        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
