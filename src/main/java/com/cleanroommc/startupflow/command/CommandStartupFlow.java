package com.cleanroommc.startupflow.command;

import com.cleanroommc.startupflow.StartupFlow;
import com.cleanroommc.startupflow.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class CommandStartupFlow extends CommandBase {

    @Override
    public String getName() {
        return "startupflow";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/startupflow [status|toggle]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();

        if (sub.equals("status")) {
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Startup Flow: Model Baking ==="));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Parallel model baking: " +
                    (ModConfig.MODEL_BAKING.enableParallelModelBaking ? TextFormatting.GREEN + "ENABLED" : TextFormatting.RED + "DISABLED") +
                    TextFormatting.GRAY + " | Configured threads: " + TextFormatting.WHITE +
                    (ModConfig.MODEL_BAKING.modelBakingThreads > 0 ? ModConfig.MODEL_BAKING.modelBakingThreads : "auto") +
                    TextFormatting.GRAY + " (effective " + ModConfig.getEffectiveModelBakingThreads() + ")"));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Parallel mipmaps: " +
                    (ModConfig.MODEL_BAKING.enableParallelMipmaps ? TextFormatting.GREEN + "ENABLED" : TextFormatting.RED + "DISABLED") +
                    TextFormatting.GRAY + " | Configured threads: " + TextFormatting.WHITE +
                    (ModConfig.MODEL_BAKING.mipmapThreads > 0 ? ModConfig.MODEL_BAKING.mipmapThreads : "auto") +
                    TextFormatting.GRAY + " (effective " + ModConfig.getEffectiveMipmapThreads() + ")"));
            return;
        }

        if (sub.equals("toggle")) {
            ModConfig.MODEL_BAKING.enableParallelModelBaking = !ModConfig.MODEL_BAKING.enableParallelModelBaking;
            ConfigManager.sync(StartupFlow.MOD_ID, Config.Type.INSTANCE);
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Parallel model baking now: " +
                    (ModConfig.MODEL_BAKING.enableParallelModelBaking ? TextFormatting.GREEN + "ENABLED" : TextFormatting.RED + "DISABLED")));
            return;
        }

        sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: " + getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "toggle");
        }
        return Collections.emptyList();
    }
}
