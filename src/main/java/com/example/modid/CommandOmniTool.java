package com.example.modid;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * 控制台 / OP 命令：/omnitool accel <1-1024>
 * 修改的是全局默认倍率（玩家在 GUI 里设过的值优先）。
 */
public class CommandOmniTool extends CommandBase {

    @Override
    public String getName() {
        return "omnitool";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/omnitool accel <1-1024>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2 || !"accel".equalsIgnoreCase(args[0])) {
            throw new CommandException(getUsage(sender));
        }

        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new CommandException("倍率必须是数字: " + args[1]);
        }

        TimeAccelHandler.setGlobalMultiplier(value);
        sender.sendMessage(new TextComponentString(
                "§e[全能工具] §b默认加速倍率已设为 §f" + TimeAccelHandler.getGlobalMultiplier()));
    }
}
