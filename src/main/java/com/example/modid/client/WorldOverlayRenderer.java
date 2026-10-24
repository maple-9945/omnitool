package com.example.modid.client;

import com.example.modid.item.ItemWirelessLinker;
import com.example.modid.network.MessageSyncWireless.PosDim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;

/**
 * 世界内高亮的唯一渲染入口：时间加速边框/倒计时 + 无线电网绑定边框。
 *
 * 之前两个类各自监听 RenderWorldLastEvent、各自修改 GlStateManager（depthMask、blend、
 * lineWidth 等），事件顺序不固定时会出现状态互相覆盖、边框闪烁/花屏。现在合并到一次
 * 渲染里，GL 状态只设置和恢复一次；同一坐标同时被加速与电网绑定时只画加速框，避免重叠。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public final class WorldOverlayRenderer {

    private WorldOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        Map<BlockPos, ClientAccelRenderer.Entry> accel = ClientAccelRenderer.getEntries();
        boolean holdingLinker = mc.player.getHeldItemMainhand().getItem() instanceof ItemWirelessLinker;
        boolean hasWireless = holdingLinker
                && (!WirelessClientData.getGenerators().isEmpty() || !WirelessClientData.getConsumers().isEmpty());
        if (accel.isEmpty() && !hasWireless) return;

        int dimension = mc.world.provider.getDimension();
        float partial = event.getPartialTicks();

        double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partial;
        double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partial;
        double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partial;

        // ---------- 线框：一次 GL 状态设置 ----------
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        // 加速方块（同一坐标优先画加速框，避免与电网框重叠闪烁）
        for (Map.Entry<BlockPos, ClientAccelRenderer.Entry> mapEntry : accel.entrySet()) {
            if (mapEntry.getValue().dimension != dimension) continue;
            drawBox(mapEntry.getKey(), px, py, pz, 0.25F, 0.85F, 1.0F);
        }

        if (hasWireless) {
            for (PosDim entry : WirelessClientData.getGenerators()) {
                if (entry.dimension != dimension || accel.containsKey(entry.pos)) continue;
                // 绑定方块：绿色；绑定坐标：黄色
                if (entry.blockBound) {
                    drawBox(entry.pos, px, py, pz, 0.2F, 1.0F, 0.2F);
                } else {
                    drawBox(entry.pos, px, py, pz, 1.0F, 0.85F, 0.2F);
                }
            }
            for (PosDim entry : WirelessClientData.getConsumers()) {
                if (entry.dimension != dimension || accel.containsKey(entry.pos)) continue;
                // 绑定方块：蓝色；绑定坐标：紫色
                if (entry.blockBound) {
                    drawBox(entry.pos, px, py, pz, 0.2F, 0.5F, 1.0F);
                } else {
                    drawBox(entry.pos, px, py, pz, 0.8F, 0.3F, 1.0F);
                }
            }
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        // ---------- 加速倒计时文字（独立一遍，避免与线框共用矩阵/深度状态） ----------
        for (Map.Entry<BlockPos, ClientAccelRenderer.Entry> mapEntry : accel.entrySet()) {
            ClientAccelRenderer.Entry entry = mapEntry.getValue();
            if (entry.dimension != dimension) continue;

            BlockPos pos = mapEntry.getKey();
            String text = entry.permanent
                    ? "∞"
                    : "Time: " + Math.max(0, entry.ticks / 20) + "s";

            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.getX() + 0.5D - px,
                    pos.getY() + 1.25D - py,
                    pos.getZ() + 0.5D - pz);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.025F, -0.025F, 0.025F);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();

            mc.fontRenderer.drawStringWithShadow(
                    text, -mc.fontRenderer.getStringWidth(text) / 2.0F, 0.0F, 0x66CCFF);

            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void drawBox(BlockPos pos, double px, double py, double pz,
                                float red, float green, float blue) {
        AxisAlignedBB box = new AxisAlignedBB(pos)
                .grow(0.002D)
                .offset(-px, -py, -pz);
        RenderGlobal.drawSelectionBoundingBox(box, red, green, blue, 0.9F);
    }
}
