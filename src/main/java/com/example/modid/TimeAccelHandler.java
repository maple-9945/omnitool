package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import com.example.modid.network.MessageAccelSync;
import com.example.modid.network.OmniNetwork;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Torcherino 式外部驱动加速（服务端逻辑）。
 *
 * V 键开启 30 秒（600 ticks）标记窗口；窗口内用 OmniTool 右键任意带 TileEntity 的方块即可标记，
 * 之后每 tick 手动调用 ITickable#update() N 次（N = 玩家倍率 1~1024）。
 *
 * 关键实现点（都来自反编译 GTCEu #2901）：
 *  1) AbstractRecipeLogic.updateRecipeProgress() 每次 update 都 drawEnergy(recipeEUt)，
 *     扣不起就 decreaseProgress()。所以每次 update 前都要补满 EU 缓存。
 *  2) canRecipeProgress 只在 getOffsetTimer()%20==0 时刷新；外部驱动时可能一直是 false，
 *     导致 updateRecipeProgress 只走 decreaseProgress。这里反射强制置 true。
 *  3) MetaTileEntity.update() 有同 tick 去重（受 GT 配置 allowTickAcceleration 控制），
 *     反射重置 lastTick 保证 1024 次都真正执行。
 */
@Mod.EventBusSubscriber(modid = "omnitool")
public final class TimeAccelHandler {

    public static final int WINDOW_TICKS = 600;
    public static final int MAX_MULTIPLIER = 1024;

    private static final Map<UUID, Integer> WINDOW = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_MULT = new HashMap<>();
    private static final Map<Long, Accel> ACTIVE = new HashMap<>();
    /** 开启了「永久加速」标记模式的玩家：之后标记的方块不会倒计时 */
    private static final Set<UUID> PERMANENT_MODE = new HashSet<>();

    private static int globalMultiplier = 1024;
    private static int syncTimer = 0;

    private static final Field FIELD_LAST_TICK = findField(MetaTileEntity.class, "lastTick");
    private static final Field FIELD_CAN_PROGRESS = findField(AbstractRecipeLogic.class, "canRecipeProgress");
    private static final Field FIELD_HAS_NOT_ENOUGH_ENERGY = findField(AbstractRecipeLogic.class, "hasNotEnoughEnergy");

    /** AbstractRecipeLogic#updateRecipeProgress()：protected，直接驱动进度，绕过 update() 里的 canRecipeProgress 重算 */
    private static final Method METHOD_UPDATE_PROGRESS = findMethod(AbstractRecipeLogic.class, "updateRecipeProgress");

    /** 已经同步过渲染数据的玩家，用于在他们没有加速方块时补发空列表（否则客户端边框清不掉） */
    private static final Set<UUID> SYNCED = new HashSet<>();

    private TimeAccelHandler() {
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            Method method = owner.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            ExampleMod.LOGGER.warn("[Omni Tool] 反射找不到 {}.{}（将退回普通 tick 驱动）", owner.getSimpleName(), name);
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            ExampleMod.LOGGER.warn("[Omni Tool] 反射找不到 {}.{}（加速可能不完整）", owner.getSimpleName(), name);
            return null;
        }
    }

    private static final class Accel {
        UUID owner;
        int dimension;
        BlockPos pos;
        int ticks;
        int multiplier;
        boolean permanent;
        IEnergyContainer energy;
        long originalEnergy;
    }

    // ==================== 配置 / 命令 ====================

    public static int clampMultiplier(int value) {
        return Math.max(1, Math.min(value, MAX_MULTIPLIER));
    }

    public static void setGlobalMultiplier(int value) {
        globalMultiplier = clampMultiplier(value);
    }

    public static int getGlobalMultiplier() {
        return globalMultiplier;
    }

    public static int getPlayerMultiplier(EntityPlayer player) {
        Integer m = PLAYER_MULT.get(player.getUniqueID());
        return m == null ? globalMultiplier : clampMultiplier(m);
    }

    // ==================== V 键窗口 ====================

    public static void activate(EntityPlayerMP player, int multiplier) {
        activate(player, multiplier, null);
    }

    /**
     * @param targetPos 玩家按 V 时瞄准的方块（可为 null）。有值时立即标记，
     *                  这样即使 RightClickBlock 被其它模组取消也不会“只有提示没效果”。
     */
    public static void activate(EntityPlayerMP player, int multiplier, BlockPos targetPos) {
        if (player == null) return;
        int m = clampMultiplier(multiplier);
        PLAYER_MULT.put(player.getUniqueID(), m);
        WINDOW.put(player.getUniqueID(), WINDOW_TICKS);
        player.sendMessage(new TextComponentString(
                "§e[全能工具] §b时间加速标记已开启 §7(30 秒，倍率 " + m + "，右键方块标记)"));
        if (targetPos != null) {
            mark(player.world, targetPos, player);
        }
    }

    // ==================== 取消加速 ====================

    /** 切换「永久加速」标记模式：开启后新标记的方块不会倒计时 */
    public static void setPermanentMode(EntityPlayerMP player, boolean enabled) {
        if (player == null) return;
        if (enabled) {
            PERMANENT_MODE.add(player.getUniqueID());
        } else {
            PERMANENT_MODE.remove(player.getUniqueID());
        }
        player.sendMessage(new TextComponentString(enabled
                ? "§e[全能工具] §b永久加速模式已开启 §7（之后标记的方块会一直加速，直到取消）"
                : "§e[全能工具] §7永久加速模式已关闭。"));
    }

    public static boolean isPermanentMode(EntityPlayer player) {
        return player != null && PERMANENT_MODE.contains(player.getUniqueID());
    }

    /** 关闭存档中所有由全能工具设置的加速（不分玩家） */
    public static void cancelAll(EntityPlayerMP requester) {
        if (ACTIVE.isEmpty()) {
            if (requester != null) {
                requester.sendMessage(new TextComponentString("§e[全能工具] §7当前没有任何加速。"));
            }
            return;
        }

        for (Accel accel : ACTIVE.values()) {
            restoreEnergy(accel);
        }
        ACTIVE.clear();

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            syncToOwners(server);   // 给所有同步过的玩家补发空列表
        }
        if (requester != null) {
            requester.sendMessage(new TextComponentString("§e[全能工具] §b已关闭存档中全部加速。"));
        }
    }

    /** pos == null 表示取消该玩家全部加速 */
    public static void cancel(EntityPlayerMP player, BlockPos pos) {
        if (player == null) return;
        UUID owner = player.getUniqueID();
        boolean removed = false;

        Iterator<Map.Entry<Long, Accel>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Accel accel = it.next().getValue();
            if (!owner.equals(accel.owner)) continue;
            if (pos == null || accel.pos.equals(pos)) {
                restoreEnergy(accel);
                it.remove();
                removed = true;
            }
        }

        if (!removed) {
            player.sendMessage(new TextComponentString("§e[全能工具] §7没有可取消的加速方块。"));
            return;
        }

        player.sendMessage(new TextComponentString(pos == null
                ? "§e[全能工具] §b已取消全部加速。"
                : "§e[全能工具] §b已取消该方块的加速。"));

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            syncToOwners(server);   // 立刻通知客户端移除高亮
        }
    }

    // ==================== 右键标记 ====================

    /** 由 GTInteractionHandler 直接调用（GT 机器那条路径一定会走到） */
    public static void markFromInteraction(World world, BlockPos pos, EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return;
        Integer window = WINDOW.get(player.getUniqueID());
        if (window == null || window <= 0) return;
        mark(world, pos, player);
    }

    /** 普通方块路径（未被其它模组 cancel 时走这里） */
    @SubscribeEvent(receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        if (!(event.getItemStack().getItem() instanceof ItemOmniTool)) return;
        markFromInteraction(event.getWorld(), event.getPos(), event.getEntityPlayer());
    }

    private static void mark(World world, BlockPos pos, EntityPlayer player) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            if (OmniConfig.debug) ExampleMod.LOGGER.info("[Omni Tool] 右键 {} 没有 TileEntity，跳过", pos);
            return;
        }
        if (!(tile instanceof ITickable)) {
            if (OmniConfig.debug) ExampleMod.LOGGER.info("[Omni Tool] 右键 {} 的 {} 不是 ITickable，无法驱动",
                    pos, tile.getClass().getSimpleName());
            return;
        }

        long key = posKey(world, pos);
        int multiplier = getPlayerMultiplier(player);
        boolean permanent = PERMANENT_MODE.contains(player.getUniqueID());
        Accel accel = ACTIVE.get(key);

        if (accel != null) {
            accel.ticks = WINDOW_TICKS;
            accel.multiplier = multiplier;
            accel.permanent = permanent;
            player.sendMessage(new TextComponentString(permanent
                    ? "§e[全能工具] §b已设为永久加速 §7(" + multiplier + "x)"
                    : "§e[全能工具] §b已刷新加速 §7(" + multiplier + "x, 30 秒)"));
            return;
        }

        accel = new Accel();
        accel.owner = player.getUniqueID();
        accel.dimension = world.provider.getDimension();
        accel.pos = pos.toImmutable();
        accel.ticks = WINDOW_TICKS;
        accel.multiplier = multiplier;
        accel.permanent = permanent;
        attachEnergy(world, pos, accel);
        ACTIVE.put(key, accel);

        player.sendMessage(new TextComponentString(permanent
                ? "§e[全能工具] §b开始永久加速 §7" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " (" + multiplier + "x, 直到手动取消)"
                : "§e[全能工具] §b开始加速 §7" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " (" + multiplier + "x, 30 秒)"));
    }

    // ==================== 每 tick 驱动 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!WINDOW.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> wit = WINDOW.entrySet().iterator();
            while (wit.hasNext()) {
                Map.Entry<UUID, Integer> entry = wit.next();
                int left = entry.getValue() - 1;
                if (left <= 0) wit.remove();
                else entry.setValue(left);
            }
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<Long, Accel>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Accel accel = it.next().getValue();
            if (!accel.permanent) {
                accel.ticks--;
            }

            World world = server.getWorld(accel.dimension);
            TileEntity tile = world == null ? null : world.getTileEntity(accel.pos);

            boolean expired = !accel.permanent && accel.ticks <= 0;
            if (!(tile instanceof ITickable) || expired) {
                restoreEnergy(accel);
                it.remove();
                continue;
            }

            ITickable tickable = (ITickable) tile;
            MetaTileEntity mte = (tile instanceof IGregTechTileEntity)
                    ? ((IGregTechTileEntity) tile).getMetaTileEntity()
                    : null;
            int before = progressOf(mte);

            // 完全照搬 TorcherinoGT 的做法：
            //   GT 机器 -> 记录电量 -> 调一次 update() -> 把消耗的电量补回去（相当于这次 tick 免电）
            //   多方块 -> 对每个能量输入仓单独记录/回补；蒸汽多方块则记录/回补蒸汽
            //   普通 TileEntity -> 直接 update()
            for (int i = 0; i < accel.multiplier; i++) {
                if (tile.isInvalid()) break;
                accelerateOnce(tile, mte);
                // GT 只有在真实线缆输入足够时才清 hasNotEnoughEnergy，这里每步手动清
                clearEnergyFlag(mte);
            }
            clearEnergyFlag(mte);

            if (OmniConfig.debug && accel.ticks % 20 == 0) {
                ExampleMod.LOGGER.info("[Omni Tool] accel {} @ {} x{} progress {} -> {}",
                        accel.ticks, accel.pos, accel.multiplier, before, progressOf(mte));
            }
        }

        // 每 10 ticks 把“正在加速的方块”同步给客户端用于渲染
        if (++syncTimer % 10 == 0) {
            syncToOwners(server);
        }
    }

    private static void syncToOwners(MinecraftServer server) {
        Map<UUID, List<MessageAccelSync.Entry>> byOwner = new HashMap<>();
        for (Accel accel : ACTIVE.values()) {
            byOwner.computeIfAbsent(accel.owner, k -> new ArrayList<>())
                    .add(new MessageAccelSync.Entry(accel.dimension, accel.pos, accel.ticks, accel.permanent));
        }

        // 之前同步过、但现在一个加速方块都没有的玩家：补发空列表，否则客户端边框永远清不掉
        List<UUID> stale = new ArrayList<>();
        for (UUID owner : SYNCED) {
            if (!byOwner.containsKey(owner)) stale.add(owner);
        }
        for (UUID owner : stale) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(owner);
            if (player != null) {
                OmniNetwork.INSTANCE.sendTo(new MessageAccelSync(new ArrayList<>()), player);
            }
            SYNCED.remove(owner);
        }

        for (Map.Entry<UUID, List<MessageAccelSync.Entry>> entry : byOwner.entrySet()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (player != null) {
                OmniNetwork.INSTANCE.sendTo(new MessageAccelSync(entry.getValue()), player);
                SYNCED.add(entry.getKey());
            }
        }
    }

    // ==================== GT 反射辅助 ====================

    private static void bypassTickGuard(MetaTileEntity mte) {
        if (mte == null || FIELD_LAST_TICK == null) return;
        try {
            FIELD_LAST_TICK.setInt(mte, Integer.MIN_VALUE);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 一次“免电 tick”，与 TorcherinoGT 的 TileTorcherino#tickBlock 等价：
     *  GT 单方块：记录电量 -> update() -> 把消耗的电量补回
     *  GT 多方块：对每个 INPUT_ENERGY 仓记录/回补；蒸汽多方块记录/回补蒸汽
     *  普通 TE：直接 update()
     */
    private static void accelerateOnce(TileEntity tile, MetaTileEntity mte) {
        ITickable tickable = (ITickable) tile;
        if (mte == null) {
            tickable.update();
            return;
        }

        bypassTickGuard(mte);

        if (mte instanceof MultiblockControllerBase) {
            MultiblockControllerBase controller = (MultiblockControllerBase) mte;
            if (!controller.isStructureFormed() || !controller.isValid()) return;

            List<IEnergyContainer> energyInputs = controller.getAbilities(MultiblockAbility.INPUT_ENERGY);
            if (!energyInputs.isEmpty()) {
                long[] cached = new long[energyInputs.size()];
                for (int j = 0; j < cached.length; j++) {
                    cached[j] = energyInputs.get(j).getEnergyStored();
                }
                tickable.update();
                for (int j = 0; j < cached.length; j++) {
                    long now = energyInputs.get(j).getEnergyStored();
                    if (now < cached[j]) {
                        energyInputs.get(j).addEnergy(cached[j] - now);
                    }
                }
                return;
            }

            if (mte instanceof RecipeMapSteamMultiblockController) {
                IMultipleTankHandler steam = ((RecipeMapSteamMultiblockController) mte).getSteamFluidTank();
                int tanks = steam.getTanks();
                int[] cached = new int[tanks];
                for (int j = 0; j < tanks; j++) {
                    cached[j] = steam.getTankAt(j).getFluidAmount();
                }
                tickable.update();
                for (int j = 0; j < tanks; j++) {
                    int now = steam.getTankAt(j).getFluidAmount();
                    if (now < cached[j]) {
                        steam.getTankAt(j).fill(FluidRegistry.getFluidStack("steam", cached[j] - now), true);
                    }
                }
                return;
            }

            tickable.update();
            return;
        }

        // 单方块 GT 机器
        IEnergyContainer container = null;
        long cached = 0L;
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyContainer candidate = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, facing);
            if (candidate != null) {
                container = candidate;
                cached = candidate.getEnergyStored();
            }
        }

        tickable.update();

        if (container != null) {
            long now = container.getEnergyStored();
            if (now < cached) {
                container.addEnergy(cached - now);
            }
        }
    }

    /** canRecipeProgress 默认只在 offsetTimer%20==0 刷新，外部加速时可能一直是 false */
    private static void forceCanProgress(MetaTileEntity mte) {
        if (mte == null || FIELD_CAN_PROGRESS == null) return;
        AbstractRecipeLogic logic = mte.getRecipeLogic();
        if (logic == null) return;
        try {
            FIELD_CAN_PROGRESS.setBoolean(logic, true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * GT 的 "能量不足" 状态来自 AbstractRecipeLogic.hasNotEnoughEnergy 这个"粘性"标记：
     * updateRecipeProgress() 里只有
     *     getEnergyInputPerSecond() > 19 * recipeEUt
     * 才会把它清掉。我们是直接往容器里灌电、没有真实线缆输入，
     * getEnergyInputPerSecond() 很小甚至为 0，所以这个标记一旦被置 true 就永远清不掉，
     * UI 于是一直显示“能量不足”（即使进度在正常推进）。
     */
    private static void clearEnergyFlag(MetaTileEntity mte) {
        if (mte == null || FIELD_HAS_NOT_ENOUGH_ENERGY == null) return;
        AbstractRecipeLogic logic = mte.getRecipeLogic();
        if (logic == null) return;
        try {
            FIELD_HAS_NOT_ENOUGH_ENERGY.setBoolean(logic, false);
        } catch (Throwable ignored) {
        }
    }

    private static int progressOf(MetaTileEntity mte) {
        if (mte == null || mte.getRecipeLogic() == null) return -1;
        return mte.getRecipeLogic().getProgress();
    }

    // ==================== GT 能量 ====================

    private static void attachEnergy(World world, BlockPos pos, Accel accel) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof IGregTechTileEntity)) return;

        MetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
        if (mte == null) return;

        IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
        if (container == null) return;

        accel.energy = container;
        accel.originalEnergy = container.getEnergyStored();
    }

    private static void fillEnergy(Accel accel) {
        if (accel.energy == null) return;
        long capacity = accel.energy.getEnergyCapacity();
        long stored = accel.energy.getEnergyStored();
        if (capacity > stored) {
            accel.energy.addEnergy(capacity - stored);
        }
    }

    private static void restoreEnergy(Accel accel) {
        if (accel.energy == null) return;
        long stored = accel.energy.getEnergyStored();
        if (stored > accel.originalEnergy) {
            accel.energy.removeEnergy(stored - accel.originalEnergy);
        } else if (stored < accel.originalEnergy) {
            accel.energy.addEnergy(accel.originalEnergy - stored);
        }
    }

    private static long posKey(World world, BlockPos pos) {
        return ((long) world.provider.getDimension() << 56) ^ pos.toLong();
    }
}
