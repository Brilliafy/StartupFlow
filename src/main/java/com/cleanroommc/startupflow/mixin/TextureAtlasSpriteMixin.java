package com.cleanroommc.startupflow.mixin;

import com.cleanroommc.startupflow.config.ModConfig;
import com.google.common.collect.Lists;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import net.minecraft.util.ReportedException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {

    @Shadow
    protected List<int[][]> framesTextureData;
    @Shadow
    protected int width;
    @Shadow
    protected int height;

    @Shadow
    public abstract void setFramesTextureData(List<int[][]> newFramesTextureData);

    private static final float[] COLOR_GAMMAS = new float[256];

    static {
        for (int i = 0; i < 256; ++i) {
            COLOR_GAMMAS[i] = (float) Math.pow((double) i / 255.0, 2.2);
        }
    }

    /**
     * @author Brilliafy
     * @reason High-performance row-major cache-coherent mipmap downsampling and parallel multi-frame generation.
     */
    @Overwrite
    public void generateMipmaps(int level) {
        if (!ModConfig.MODEL_BAKING.enableParallelMipmaps || this.framesTextureData == null || this.framesTextureData.isEmpty()) {
            faststartup$generateMipmapsVanilla(level);
            return;
        }

        int frameCount = this.framesTextureData.size();
        int[][][] generated = new int[frameCount][][];

        if (frameCount > 1) {
            ForkJoinPool.commonPool().submit(() ->
                    java.util.stream.IntStream.range(0, frameCount).parallel().forEach(i -> {
                        int[][] aint = this.framesTextureData.get(i);
                        if (aint != null) {
                            try {
                                generated[i] = faststartup$generateMipmapDataFast(level, this.width, aint);
                            } catch (Throwable t) {
                                faststartup$handleMipmapCrash(t, i);
                            }
                        }
                    })
            ).join();
        } else {
            int[][] aint = this.framesTextureData.get(0);
            if (aint != null) {
                try {
                    generated[0] = faststartup$generateMipmapDataFast(level, this.width, aint);
                } catch (Throwable t) {
                    faststartup$handleMipmapCrash(t, 0);
                }
            }
        }

        ArrayList<int[][]> list = Lists.newArrayListWithCapacity(frameCount);
        for (int i = 0; i < frameCount; ++i) {
            if (generated[i] != null) {
                list.add(generated[i]);
            }
        }
        this.setFramesTextureData(list);
    }

    private void faststartup$generateMipmapsVanilla(int level) {
        ArrayList<int[][]> list = Lists.newArrayList();
        for (int i = 0; i < this.framesTextureData.size(); ++i) {
            int[][] aint = this.framesTextureData.get(i);
            if (aint != null) {
                try {
                    list.add(TextureUtil.generateMipmapData(level, this.width, aint));
                } catch (Throwable throwable) {
                    faststartup$handleMipmapCrash(throwable, i);
                }
            }
        }
        this.setFramesTextureData(list);
    }

    private void faststartup$handleMipmapCrash(Throwable throwable, int frameIndex) {
        CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Generating mipmaps for frame");
        CrashReportCategory category = crashreport.makeCategory("Frame being iterated");
        category.addCrashSection("Frame index", frameIndex);
        category.addDetail("Frame sizes", (ICrashReportDetail<String>) () -> {
            StringBuilder s = new StringBuilder();
            int[][] data = framesTextureData.get(frameIndex);
            if (data != null) {
                for (int[] a : data) {
                    if (s.length() > 0) s.append(", ");
                    s.append(a == null ? "null" : a.length);
                }
            }
            return s.toString();
        });
        throw new ReportedException(crashreport);
    }

    private static int[][] faststartup$generateMipmapDataFast(int mipmapLevels, int width, int[][] data) {
        int[][] aint = new int[mipmapLevels + 1][];
        aint[0] = data[0];
        if (mipmapLevels > 0 && aint[0] != null) {
            boolean hasTransparency = false;
            for (int i = 0; i < data[0].length; ++i) {
                if ((data[0][i] >> 24) == 0) {
                    hasTransparency = true;
                    break;
                }
            }

            for (int level = 1; level <= mipmapLevels; ++level) {
                if (data.length > level && data[level] != null) {
                    aint[level] = data[level];
                    continue;
                }

                int[] prevLevel = aint[level - 1];
                int curWidth = width >> level;
                if (curWidth > 0 && prevLevel != null && prevLevel.length > 0) {
                    int curHeight = (prevLevel.length >> 2) / curWidth;
                    int[] currentLevel = new int[curWidth * curHeight];
                    int prevStride = curWidth << 1;

                    for (int y = 0; y < curHeight; ++y) {
                        int outRow = y * curWidth;
                        int inRow = 2 * y * prevStride;
                        int inRowNext = inRow + prevStride;

                        for (int x = 0; x < curWidth; ++x) {
                            int x2 = x << 1;
                            currentLevel[outRow + x] = faststartup$blendColors(
                                    prevLevel[inRow + x2],
                                    prevLevel[inRow + x2 + 1],
                                    prevLevel[inRowNext + x2],
                                    prevLevel[inRowNext + x2 + 1],
                                    hasTransparency
                            );
                        }
                    }
                    aint[level] = currentLevel;
                } else {
                    aint[level] = new int[0];
                }
            }
        }
        return aint;
    }

    private static int faststartup$blendColors(int c0, int c1, int c2, int c3, boolean hasTransparency) {
        if (hasTransparency) {
            float fA = 0.0f, fR = 0.0f, fG = 0.0f, fB = 0.0f;
            int[] buffer = {c0, c1, c2, c3};
            for (int i = 0; i < 4; ++i) {
                int c = buffer[i];
                int a = (c >> 24) & 0xFF;
                if (a != 0) {
                    fA += COLOR_GAMMAS[a];
                    fR += COLOR_GAMMAS[(c >> 16) & 0xFF];
                    fG += COLOR_GAMMAS[(c >> 8) & 0xFF];
                    fB += COLOR_GAMMAS[c & 0xFF];
                }
            }
            int outA = (int) (Math.pow(fA * 0.25f, 0.45454545454545453) * 255.0);
            int outR = (int) (Math.pow(fR * 0.25f, 0.45454545454545453) * 255.0);
            int outG = (int) (Math.pow(fG * 0.25f, 0.45454545454545453) * 255.0);
            int outB = (int) (Math.pow(fB * 0.25f, 0.45454545454545453) * 255.0);
            if (outA < 96) {
                outA = 0;
            }
            return (outA << 24) | (outR << 16) | (outG << 8) | outB;
        }

        int outA = faststartup$blendComponent(c0, c1, c2, c3, 24);
        int outR = faststartup$blendComponent(c0, c1, c2, c3, 16);
        int outG = faststartup$blendComponent(c0, c1, c2, c3, 8);
        int outB = faststartup$blendComponent(c0, c1, c2, c3, 0);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int faststartup$blendComponent(int c0, int c1, int c2, int c3, int shift) {
        float f0 = COLOR_GAMMAS[(c0 >> shift) & 0xFF];
        float f1 = COLOR_GAMMAS[(c1 >> shift) & 0xFF];
        float f2 = COLOR_GAMMAS[(c2 >> shift) & 0xFF];
        float f3 = COLOR_GAMMAS[(c3 >> shift) & 0xFF];
        return (int) (Math.pow((f0 + f1 + f2 + f3) * 0.25f, 0.45454545454545453) * 255.0);
    }
}
