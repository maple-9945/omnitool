package com.example.modid.client;

import com.example.modid.item.ItemOmniTool;
import com.example.modid.network.MessageAccelSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧：记录自己标记过的方块，渲染发光边框 + 悬浮倒计时。
 *
 * 只做显示，真正的加速在服务端 TimeAccelHandler 里。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public final class ClientAccelRenderer {

    private static final int WINDOW_TICKS = 600;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int multiplier = 1024;
    private static int windowTicks = 0;
    private static boolean permanentMode = false;

    private ClientAccelRenderer() {
    }

    private static final class Entry {
        final int dimension;
        int ticks;
        boolean permanent;

        Entry(int dimension, int ticks, boolean permanent) {
            this.dimension = dimension;
            this.ticks = ticks;
            this.permanent = permanent;
        }
    }

    public static int getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(int value) {
        multiplier = Math.max(1, Math.min(1024, value));
    }

    public static boolean isPermanentMode() {
        return permanentMode;
    }

    public static void setPermanentMode(boolean value) {
        permanentMode = value;
    }

    public static void beginWindow() {
        windowTicks = WINDOW_TICKS;
    }

    // ==================== 服务端同步（渲染的唯一权威来源） ====================

    public static void applySync(List<MessageAccelSync.Entry> entries) {
        ENTRIES.clear();
        for (MessageAccelSync.Entry entry : entries) {
            ENTRIES.put(entry.pos, new Entry(entry.dimension, entry.ticks, entry.permanent));
        }
    }

    /** GTInteractionHandler 在客户端侧直接调用，给点击瞬间一个即时反馈（服务端同步随后覆盖） */
    public static void markFromClient(net.minecraft.world.World world, BlockPos pos) {
        if (!world.isRemote || windowTicks <= 0) return;
        ENTRIES.put(pos.toImmutable(), new Entry(world.provider.getDimension(), WINDOW_TICKS, permanentMode));
    }

    /** 关闭全部加速时清空本地渲染，立即去掉边框（服务端随后补发空列表） */
    public static void clearLocal() {
        ENTRIES.clear();
    }

    // ==================== 标记（客户端预测显示） ====================

    @SubscribeEvent(receiveCanceled = true)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote || windowTicks <= 0) return;
        if (!(event.getItemStack().getItem() instanceof ItemOmniTool)) return;
        markFromClient(event.getWorld(), event.getPos());
    }

    // ==================== 倒计时 ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (windowTicks > 0) windowTicks--;

        if (ENTRIES.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            if (entry.permanent) continue;   // 永久加速不倒计时，由服务端同步移除
            if (--entry.ticks <= 0) it.remove();
        }
    }

    // ==================== 渲染 ====================

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || ENTRIES.isEmpty()) return;

        int dimension = mc.world.provider.getDimension();
        float partial = event.getPartialTicks();

        double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partial;
        double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partial;
        double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partial;

        // 边框
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        for (Map.Entry<BlockPos, Entry> mapEntry : ENTRIES.entrySet()) {
            Entry entry = mapEntry.getValue();
            if (entry.dimension != dimension) continue;
            AxisAlignedBB box = new AxisAlignedBB(mapEntry.getKey())
                    .grow(0.002D)
                    .offset(-px, -py, -pz);
            RenderGlobal.drawSelectionBoundingBox(box, 0.25F, 0.85F, 1.0F, 0.9F);
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        // 悬浮倒计时
        for (Map.Entry<BlockPos, Entry> mapEntry : ENTRIES.entrySet()) {
            Entry entry = mapEntry.getValue();
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
}
