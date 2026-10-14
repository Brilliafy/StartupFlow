package com.cleanroommc.startupflow.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflection bridge to FermiumBooter's internal {@code FermiumJarScanner} state. Everything here is
 * done reflectively so that this coremod has no compile-time dependency on FermiumBooter and can
 * support multiple FermiumBooter versions (1.5.x).
 *
 * <p>Only collection contents are mutated and one private method is invoked - no {@code final} fields
 * are reassigned, which keeps it working under modern JVMs.</p>
 */
final class ScannerBridge {

    private final Field presentModsField;
    private final Field mixinTogglesField;
    private final Method handleCachingMethod;
    private final Method applyMixinConfigMethod;
    private final Class<?> mixinConfigInfoClass;
    private final Class<?> compatInfoClass;
    private final Class<?> modInfoClass;

    ScannerBridge(Class<?> scannerClass) throws Exception {
        this.presentModsField = scannerClass.getDeclaredField("presentMods");
        this.presentModsField.setAccessible(true);
        this.mixinTogglesField = scannerClass.getDeclaredField("mixinToggles");
        this.mixinTogglesField.setAccessible(true);
        this.handleCachingMethod = scannerClass.getDeclaredMethod("handleCaching");
        this.handleCachingMethod.setAccessible(true);

        this.mixinConfigInfoClass = Class.forName("fermiumbooter.util.MixinConfigInfo");
        this.compatInfoClass = Class.forName("fermiumbooter.util.MixinConfigInfo$CompatInfo");
        this.modInfoClass = Class.forName("fermiumbooter.util.ModInfo");

        this.applyMixinConfigMethod = scannerClass.getDeclaredMethod("applyMixinConfig", this.mixinConfigInfoClass);
        this.applyMixinConfigMethod.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getPresentMods() throws Exception {
        return (Map<String, Object>) presentModsField.get(null);
    }

    @SuppressWarnings("unchecked")
    Set<Object> getMixinToggles() throws Exception {
        return (Set<Object>) mixinTogglesField.get(null);
    }

    void handleCaching() throws Exception {
        handleCachingMethod.invoke(null);
    }

    void applyMixinConfig(Object toggle) throws Exception {
        applyMixinConfigMethod.invoke(null, toggle);
    }

    Object newModInfo(String modId, String version, String name) throws Exception {
        return modInfoClass.getConstructor(String.class, String.class, String.class).newInstance(modId, version, name);
    }

    Object newMixinConfigInfo(String modId, String name, String early, String late, boolean defaultValue) throws Exception {
        return mixinConfigInfoClass
                .getConstructor(String.class, String.class, String.class, String.class, boolean.class)
                .newInstance(modId, name, early, late, defaultValue);
    }

    @SuppressWarnings("unchecked")
    List<Object> getCompatInfos(Object toggle) throws Exception {
        return (List<Object>) mixinConfigInfoClass.getField("compatInfos").get(toggle);
    }

    Object newCompatInfo(String modId, String modName, String range, boolean desired,
                         boolean disableMixin, boolean warnIngame, String reason) throws Exception {
        return compatInfoClass
                .getConstructor(String.class, String.class, String.class, boolean.class, boolean.class, boolean.class, String.class)
                .newInstance(modId, modName, range, desired, disableMixin, warnIngame, reason);
    }

    String getModVersion(Object modInfo) throws Exception {
        Object v = read(modInfo, "version");
        return v == null ? null : v.toString();
    }

    String getModName(Object modInfo) throws Exception {
        return (String) read(modInfo, "modName");
    }

    String getToggleModId(Object toggle) throws Exception {
        return (String) read(toggle, "modId");
    }

    String getToggleName(Object toggle) throws Exception {
        return (String) read(toggle, "name");
    }

    String getToggleEarly(Object toggle) throws Exception {
        return (String) read(toggle, "earlyJson");
    }

    String getToggleLate(Object toggle) throws Exception {
        return (String) read(toggle, "lateJson");
    }

    boolean getToggleDefault(Object toggle) throws Exception {
        return (Boolean) read(toggle, "defaultValue");
    }

    String getCompatModId(Object compat) throws Exception {
        return (String) read(compat, "modId");
    }

    String getCompatModName(Object compat) throws Exception {
        return (String) read(compat, "modName");
    }

    String getCompatRange(Object compat) throws Exception {
        return (String) read(compat, "modVersionRange");
    }

    boolean getCompatDesired(Object compat) throws Exception {
        return (Boolean) read(compat, "desired");
    }

    boolean getCompatDisable(Object compat) throws Exception {
        return (Boolean) read(compat, "disableMixin");
    }

    boolean getCompatWarn(Object compat) throws Exception {
        return (Boolean) read(compat, "warnIngame");
    }

    String getCompatReason(Object compat) throws Exception {
        return (String) read(compat, "reason");
    }

    private static Object read(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getField(fieldName);
        return field.get(instance);
    }
}
