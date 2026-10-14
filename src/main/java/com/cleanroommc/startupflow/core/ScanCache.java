package com.cleanroommc.startupflow.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain serializable snapshot of FermiumBooter's {@code FermiumJarScanner} output. Gson (bundled with
 * Forge) is used for (de)serialization, so fields are public and simple.
 */
public final class ScanCache {

    public int format;
    public String fingerprint;
    public Map<String, ModEntry> mods = new LinkedHashMap<>();
    public List<ToggleEntry> toggles = new ArrayList<>();
    public List<String> classpathAdditions = new ArrayList<>();

    public static final class ModEntry {
        public String version;
        public String name;
    }

    public static final class ToggleEntry {
        public String modId;
        public String name;
        public String earlyJson;
        public String lateJson;
        public boolean defaultValue;
        public List<CompatEntry> compat = new ArrayList<>();
    }

    public static final class CompatEntry {
        public String modId;
        public String modName;
        public String modVersionRange;
        public String reason;
        public boolean desired;
        public boolean disableMixin;
        public boolean warnIngame;
    }
}
