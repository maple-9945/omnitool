package com.example.modid.client;

import com.example.modid.item.ItemWirelessLinker;
import com.example.modid.network.MessageSyncWireless.PosDim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 手持无线电网链接工具时，把当前网络的绑定方块画出来：
 * 发电 = 绿色线框，供电 = 蓝色线框。只有本机玩家可见。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public final class WirelessHighlightRenderer {

    private WirelessHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        if (!(mc.player.getHeldItemMainhand().getItem() instanceof ItemWirelessLinker)) return;
        if (WirelessClientData.getGenerators().isEmpty() && WirelessClientData.getConsumers().isEmpty()) return;

        int dimension = mc.world.provider.getDimension();
        float partial = event.getPartialTicks();

        double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partial;
        double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partial;
        double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partial;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        for (PosDim entry : WirelessClientData.getGenerators()) {
            if (entry.dimension != dimension) continue;
            drawBox(entry, px, py, pz, 0.2F, 1.0F, 0.2F);   // 绿色
        }
        for (PosDim entry : WirelessClientData.getConsumers()) {
            if (entry.dimension != dimension) continue;
            drawBox(entry, px, py, pz, 0.2F, 0.5F, 1.0F);   // 蓝色
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBox(PosDim entry, double px, double py, double pz,
                                float red, float green, float blue) {
        AxisAlignedBB box = new AxisAlignedBB(entry.pos)
                .grow(0.002D)
                .offset(-px, -py, -pz);
        RenderGlobal.drawSelectionBoundingBox(box, red, green, blue, 0.9F);
    }
}
