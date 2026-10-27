package com.example.modid.client;

import com.example.modid.network.MessageAccelSync;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧：记录自己标记过的方块（数据由服务端同步），真正的渲染统一在
 * {@link WorldOverlayRenderer} 里完成，避免与无线电网高亮各自管理 GL 状态而冲突。
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

    /** 加速方块显示数据（由 WorldOverlayRenderer 读取） */
    public static final class Entry {
        public final int dimension;
        public int ticks;
        public boolean permanent;

        Entry(int dimension, int ticks, boolean permanent) {
            this.dimension = dimension;
            this.ticks = ticks;
            this.permanent = permanent;
        }
    }

    public static Map<BlockPos, Entry> getEntries() {
        return ENTRIES;
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
    public static void markFromClient(World world, BlockPos pos) {
        if (!world.isRemote || windowTicks <= 0) return;
        ENTRIES.put(pos.toImmutable(), new Entry(world.provider.getDimension(), WINDOW_TICKS, permanentMode));
    }

    /** 关闭全部加速时清空本地渲染，立即去掉边框（服务端随后补发空列表） */
    public static void clearLocal() {
        ENTRIES.clear();
    }

    // ==================== 标记（客户端预测显示，只由 V 键路径调用） ====================

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
}
