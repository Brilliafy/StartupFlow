package com.cleanroommc.startupflow.mixin;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ModelLoaderRegistry.class, remap = false)
public interface ModelLoaderRegistryAccessor {

    @Invoker("getTextures")
    static Iterable<ResourceLocation> getTextures() {
        throw new AssertionError();
    }
}
