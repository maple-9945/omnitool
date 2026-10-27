package com.example.modid.client;

import com.example.modid.item.ItemWirelessLinker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 手持无线电网链接工具时，在屏幕右上角显示网络 ID 与存电。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public final class WirelessHudRenderer {

    private WirelessHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;
        if (!(player.getHeldItemMainhand().getItem() instanceof ItemWirelessLinker)) return;

        int right = event.getResolution().getScaledWidth() - 8;
        int y = 8;

        String id = "网络: " + WirelessClientData.shortId();
        mc.fontRenderer.drawStringWithShadow(id,
                right - mc.fontRenderer.getStringWidth(id), y, 0x55FFFF);

        String energy = "存电: " + WirelessClientData.formatEnergy();
        mc.fontRenderer.drawStringWithShadow(energy,
                right - mc.fontRenderer.getStringWidth(energy), y + 12, 0xFFFFFF);

        boolean blockBound = !ItemWirelessLinker.BIND_POS.equals(
                ItemWirelessLinker.getBindMode(player.getHeldItemMainhand()));
        String mode = "绑定方式: " + (blockBound ? "方块" : "坐标");
        mc.fontRenderer.drawStringWithShadow(mode,
                right - mc.fontRenderer.getStringWidth(mode), y + 24, blockBound ? 0x55FF55 : 0xFFDD55);
    }
}
