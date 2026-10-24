package com.cleanroommc.startupflow.mixin;

import com.cleanroommc.startupflow.baker.ParallelModelBaker;
import com.cleanroommc.startupflow.config.ModConfig;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ModelLoader.class, remap = false)
public abstract class ModelLoaderMixin extends ModelBakery {

    @Shadow
    private boolean isLoading;
    @Shadow
    private IModel missingModel;
    @Shadow
    private Map<ModelResourceLocation, IModel> stateModels;

    @Shadow
    protected abstract void loadBlocks();

    public ModelLoaderMixin() {
        super(null, null, null);
    }

    @Inject(method = "setupModelRegistry", at = @At("HEAD"), cancellable = true)
    private void faststartup$parallelSetupModelRegistry(CallbackInfoReturnable<IRegistry<ModelResourceLocation, IBakedModel>> cir) {
        if (!ModConfig.MODEL_BAKING.enableParallelModelBaking) {
            return;
        }

        if (FMLClientHandler.instance().hasError()) {
            cir.setReturnValue(this.bakedRegistry);
            return;
        }

        this.isLoading = true;
        this.loadBlocks();
        this.loadVariantItemModels();
        this.missingModel = ModelLoaderRegistry.getMissingModel();
        this.stateModels.put(MODEL_MISSING, this.missingModel);

        Set<ResourceLocation> textures = Sets.newHashSet(ModelLoaderRegistryAccessor.getTextures());
        textures.remove(TextureMap.LOCATION_MISSING_TEXTURE);
        textures.addAll(LOCATIONS_BUILTIN_TEXTURES);
        this.textureMap.loadSprites(this.resourceManager, map -> textures.forEach(map::registerSprite));

        IBakedModel missingBaked = this.missingModel.bake(
                this.missingModel.getDefaultState(),
                DefaultVertexFormats.ITEM,
                ModelLoader.defaultTextureGetter()
        );

        ConcurrentHashMap<IModel, IBakedModel> bakedModels = new ConcurrentHashMap<>(this.stateModels.size());
        HashMultimap<IModel, ModelResourceLocation> models = HashMultimap.create();
        Multimaps.invertFrom(Multimaps.forMap(this.stateModels), models);

        ParallelModelBaker.bakeModelsInParallel(models, bakedModels, this.missingModel, missingBaked);

        for (Map.Entry<ModelResourceLocation, IModel> entry : this.stateModels.entrySet()) {
            IBakedModel baked = bakedModels.get(entry.getValue());
            this.bakedRegistry.putObject(entry.getKey(), baked != null ? baked : missingBaked);
        }

        cir.setReturnValue(this.bakedRegistry);
    }
}
