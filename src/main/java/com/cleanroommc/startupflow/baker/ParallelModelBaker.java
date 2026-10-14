package com.cleanroommc.startupflow.baker;

import com.cleanroommc.startupflow.StartupFlow;
import com.cleanroommc.startupflow.config.ModConfig;
import com.google.common.collect.Multimap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ProgressManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelModelBaker {

    public static void bakeModelsInParallel(
            Multimap<IModel, ModelResourceLocation> models,
            Map<IModel, IBakedModel> bakedModels,
            IModel missingModel,
            IBakedModel missingBaked,
            ProgressManager.ProgressBar bakeBar) {

        int totalModels = models.keySet().size();
        if (totalModels == 0) {
            return;
        }

        int threads = ModConfig.getEffectiveModelBakingThreads();
        StartupFlow.LOGGER.info("[StartupFlow] Starting parallel model baking for {} models using {} worker threads...", totalModels, threads);

        long startTime = System.currentTimeMillis();
        AtomicInteger counter = new AtomicInteger(0);

        ForkJoinPool bakerPool = new ForkJoinPool(threads, pool -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("StartupFlow-Baker-" + worker.getPoolIndex());
            worker.setDaemon(true);
            return worker;
        }, null, false);

        try {
            bakerPool.submit(() -> models.keySet().parallelStream().forEach(iModel -> {
                int current = counter.incrementAndGet();
                if (current % 150 == 0 || current == totalModels) {
                    synchronized (bakeBar) {
                        bakeBar.step("Baking: " + current + "/" + totalModels);
                    }
                }

                if (iModel == missingModel) {
                    bakedModels.put(iModel, missingBaked);
                    return;
                }

                try {
                    IBakedModel baked = iModel.bake(
                            iModel.getDefaultState(),
                            DefaultVertexFormats.ITEM,
                            ModelLoader.defaultTextureGetter()
                    );
                    if (baked != null) {
                        bakedModels.put(iModel, baked);
                    } else {
                        bakedModels.put(iModel, missingBaked);
                    }
                } catch (Throwable t) {
                    Collection<ModelResourceLocation> locs = models.get(iModel);
                    String modelLocations = locs != null ? locs.toString() : "[unknown]";
                    FMLLog.log.error("Exception baking model for location(s) {}:", modelLocations, t);
                    bakedModels.put(iModel, missingBaked);
                }
            })).get();
        } catch (InterruptedException | ExecutionException e) {
            FMLLog.log.error("[StartupFlow] Error during parallel model baking", e);
        } finally {
            bakerPool.shutdown();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        StartupFlow.LOGGER.info("[StartupFlow] Parallel model baking completed: {} models baked in {} ms ({} models/ms across {} threads).",
                totalModels, elapsed, String.format("%.3f", totalModels / (double) Math.max(1, elapsed)), threads);
    }
}
