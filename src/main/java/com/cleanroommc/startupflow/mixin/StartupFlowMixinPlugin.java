package com.cleanroommc.startupflow.mixin;

import com.cleanroommc.startupflow.StartupFlow;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class StartupFlowMixinPlugin implements IMixinConfigPlugin {

    private static Boolean optifinePresent = null;

    private static boolean isOptifinePresent() {
        if (optifinePresent == null) {
            boolean present = false;
            try {
                Class.forName("optifine.OptiFineClassTransformer", false, Launch.classLoader);
                present = true;
            } catch (ClassNotFoundException ignored) {
            }
            if (!present) {
                try {
                    Class.forName("net.optifine.Config", false, Launch.classLoader);
                    present = true;
                } catch (ClassNotFoundException ignored) {
                }
            }
            optifinePresent = present;
        }
        return optifinePresent;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("TextureAtlasSpriteMixin")) {
            if (isOptifinePresent()) {
                StartupFlow.LOGGER.info("[StartupFlow] OptiFine detected; disabling TextureAtlasSpriteMixin to preserve OptiFine texture pipeline.");
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
