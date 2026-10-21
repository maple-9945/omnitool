package com.example.modid;

import com.example.modid.item.ItemWirelessLinker;
import com.example.modid.network.MessageSyncWireless;
import com.example.modid.network.OmniNetwork;
import com.example.modid.wireless.WirelessNetwork;
import com.example.modid.wireless.WirelessNetworkManager;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 无线电网服务端逻辑：绑定交互、能量结算、GUI 动作、同步。
 */
@Mod.EventBusSubscriber(modid = "omnitool")
public final class WirelessEventHandler {

    private static final int SYNC_INTERVAL = 20;
    private static int syncTimer = 0;

    private WirelessEventHandler() {
    }

    // ==================== 存档加载 ====================

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        WirelessNetworkManager.get(world);
    }

    // ==================== 绑定交互 ====================

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world == null) return;

        EntityPlayer player = event.getEntityPlayer();
        if (player == null) return;

        // 一次右键会分别给主手/副手各发一次事件，只处理主手，否则会“绑定又立刻解除”
        if (event.getHand() != EnumHand.MAIN_HAND) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ItemWirelessLinker)) return;
        if (!player.isSneaking()) return;

        // 客户端：阻止潜行右键触发方块本身的交互（服务端负责真正的绑定）
        if (world.isRemote) {
            event.setCanceled(true);
            return;
        }

        BlockPos pos = event.getPos();
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null || (gtContainer(tile) == null && forgeContainer(tile) == null)) {
            player.sendMessage(new TextComponentString("§c[无线电网] 该方块没有能量容器。"));
            return;
        }

        WirelessNetworkManager manager = WirelessNetworkManager.get(world);
        if (manager == null) return;

        WirelessNetwork network = resolveNetwork(manager, player, held);
        UUID owner = player.getUniqueID();
        String mode = ItemWirelessLinker.getMode(held);
        List<BlockPos> target;
        String label;

        if (ItemWirelessLinker.MODE_GENERATE.equals(mode)) {
            network.consumerList(owner).remove(pos);   // 互斥
            target = network.generatorList(owner);
            label = "发电";
        } else {
            network.generatorList(owner).remove(pos);
            target = network.consumerList(owner);
            label = "供电";
        }

        if (target.contains(pos)) {
            target.remove(pos);
            player.sendMessage(new TextComponentString("§e[无线电网] §7已解除§b" + label + "§7绑定: "
                    + pos.getX() + "," + pos.getY() + "," + pos.getZ()));
        } else {
            target.add(pos);
            network.setDimensionOf(pos, world.provider.getDimension());
            network.setBindMode(pos, ItemWirelessLinker.getBindMode(held));
            player.sendMessage(new TextComponentString("§e[无线电网] §b已绑定为§a " + label + "§b: "
                    + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " §7(绑定方式: " + (ItemWirelessLinker.BIND_BLOCK.equals(ItemWirelessLinker.getBindMode(held))
                    ? "方块" : "坐标") + ")"));
        }

        manager.markDirtyNow();
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            syncTo(serverPlayer, manager);
            serverPlayer.inventoryContainer.detectAndSendChanges();
        }
        event.setCanceled(true);
    }

    // ==================== 方块被拆除 ====================

    /**
     * 绑定方块模式：目标方块被拆除时立即解绑并同步（渲染随之消失）。
     * 绑定坐标模式：保留坐标绑定，不做处理。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;

        WirelessNetworkManager manager = WirelessNetworkManager.get(world);
        if (manager == null) return;

        BlockPos pos = event.getPos();
        boolean removed = false;
        for (WirelessNetwork network : manager.allNetworks()) {
            if (!network.isBlockBound(pos)) continue;
            if (network.removePosition(pos)) removed = true;
        }

        if (removed) {
            manager.markDirtyNow();
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) syncAllLinkerHolders(server, manager);
        }
    }

    // ==================== GUI 动作 ====================

    public static void handleGuiAction(EntityPlayerMP player, int action) {
        if (player == null) return;
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty() || !(held.getItem() instanceof ItemWirelessLinker)) return;

        WirelessNetworkManager manager = WirelessNetworkManager.get(player.world);
        if (manager == null) return;

        if (action == com.example.modid.network.MessageWirelessAction.ACTION_SET_GENERATE) {
            ItemWirelessLinker.setMode(held, ItemWirelessLinker.MODE_GENERATE);
            player.sendMessage(new TextComponentString("§e[无线电网] §b当前为发电模式。"));
        } else if (action == com.example.modid.network.MessageWirelessAction.ACTION_SET_CONSUME) {
            ItemWirelessLinker.setMode(held, ItemWirelessLinker.MODE_CONSUME);
            player.sendMessage(new TextComponentString("§e[无线电网] §b当前为供电模式。"));
        } else if (action == com.example.modid.network.MessageWirelessAction.ACTION_CLEAR_BINDINGS) {
            WirelessNetwork network = resolveNetwork(manager, player, held);
            network.removeAllFor(player.getUniqueID());
            manager.markDirtyNow();
            player.sendMessage(new TextComponentString("§e[无线电网] §7已清空当前网络下你的全部绑定。"));
        } else if (action == com.example.modid.network.MessageWirelessAction.ACTION_CREATE_NETWORK) {
            WirelessNetwork created = manager.createNetwork(player);
            ItemWirelessLinker.setNetworkId(held, created.getNetworkId());
            player.sendMessage(new TextComponentString("§e[无线电网] §b已创建新网络并切换到 §a"
                    + created.getNetworkId().toString().substring(0, 8)));
        } else if (action == com.example.modid.network.MessageWirelessAction.ACTION_CYCLE_NETWORK) {
            cycleNetwork(manager, player, held);
        } else if (action == com.example.modid.network.MessageWirelessAction.ACTION_TOGGLE_BIND_MODE) {
            String next = ItemWirelessLinker.toggleBindMode(ItemWirelessLinker.getBindMode(held));
            ItemWirelessLinker.setBindMode(held, next);
            player.sendMessage(new TextComponentString(ItemWirelessLinker.BIND_BLOCK.equals(next)
                    ? "§e[无线电网] §b绑定方式: §a绑定方块 §7（目标方块被拆除后绑定自动失效）"
                    : "§e[无线电网] §b绑定方式: §a绑定坐标 §7（方块拆除后坐标绑定仍保留）"));
        }

        player.inventoryContainer.detectAndSendChanges();
        syncTo(player, manager);
    }

    private static void cycleNetwork(WirelessNetworkManager manager, EntityPlayerMP player, ItemStack held) {
        List<WirelessNetwork> networks = manager.getNetworksForPlayer(player);
        if (networks.isEmpty()) {
            player.sendMessage(new TextComponentString("§e[无线电网] §7你还没有网络，请先点「创建新网络」。"));
            return;
        }
        if (networks.size() <= 1) {
            player.sendMessage(new TextComponentString("§e[无线电网] §7你只有一个网络 §a"
                    + networks.get(0).getNetworkId().toString().substring(0, 8)
                    + "§7，先点「创建新网络」才能切换。"));
            return;
        }

        UUID current = ItemWirelessLinker.getNetworkId(held);
        int index = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).getNetworkId().equals(current)) {
                index = (i + 1) % networks.size();
                break;
            }
        }
        WirelessNetwork next = networks.get(index);
        ItemWirelessLinker.setNetworkId(held, next.getNetworkId());

        StringBuilder builder = new StringBuilder();
        for (WirelessNetwork network : networks) {
            if (builder.length() > 0) builder.append("§7, §a");
            builder.append(network.getNetworkId().toString().substring(0, 8));
        }
        player.sendMessage(new TextComponentString("§e[无线电网] §b已切换到 §a"
                + next.getNetworkId().toString().substring(0, 8) + "§7；你拥有的网络: §a" + builder));
    }

    private static WirelessNetwork resolveNetwork(WirelessNetworkManager manager, EntityPlayer player, ItemStack held) {
        WirelessNetwork network = manager.getNetwork(ItemWirelessLinker.getNetworkId(held));
        if (network == null || !network.getMembers().contains(player.getUniqueID())) {
            network = manager.createNetwork(player);
            ItemWirelessLinker.setNetworkId(held, network.getNetworkId());
        }
        return network;
    }

    // ==================== 能量结算 + 同步 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        World overworld = server.getWorld(0);
        if (overworld == null) return;

        WirelessNetworkManager manager = WirelessNetworkManager.get(overworld);
        if (manager == null) return;

        boolean changed = false;
        for (WirelessNetwork network : manager.allNetworks()) {
            changed |= settle(server, network);
        }
        if (changed) {
            manager.markDirtyNow();
            syncAllLinkerHolders(server, manager);
        }

        if (++syncTimer % SYNC_INTERVAL == 0) {
            syncAllLinkerHolders(server, manager);
        }
    }

    private static void syncAllLinkerHolders(MinecraftServer server, WirelessNetworkManager manager) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getHeldItemMainhand().getItem() instanceof ItemWirelessLinker) {
                syncTo(player, manager);
            }
        }
    }

    private static boolean settle(MinecraftServer server, WirelessNetwork network) {
        List<BlockPos> stale = new ArrayList<>();
        // 发电：从方块抽能
        for (List<BlockPos> positions : network.getGenerators().values()) {
            for (BlockPos pos : positions) {
                World world = server.getWorld(network.dimensionOf(pos));
                if (world == null) continue;
                TileEntity tile = world.getTileEntity(pos);
                if (tile == null) {
                    if (world.isBlockLoaded(pos) && network.isBlockBound(pos)) stale.add(pos);
                    continue;
                }

                IEnergyContainer gt = gtContainer(tile);
                if (gt != null) {
                    MetaTileEntity mte = metaTileEntity(tile);

                    // 创造能量单元这类“无限源”：它没有真实缓存（getEnergyStored 是常数），
                    // 只能按它的输出速率（电压 × 电流）每 tick 计入电网。
                    if (isCreativeSource(mte)) {
                        long voltage = gt.getOutputVoltage();
                        long amperage = gt.getOutputAmperage();
                        if (voltage > 0 && amperage > 0) {
                            network.storedEnergy += (double) voltage * (double) amperage;
                        }
                        continue;
                    }

                    // 普通机器 / 能源仓 / 电池：把真实缓存抽空（用前后差值算实际抽出量）
                    long before = gt.getEnergyStored();
                    if (before > 0) {
                        gt.changeEnergy(-before);
                        long after = gt.getEnergyStored();
                        long removed = Math.max(0L, before - after);
                        if (removed > 0) {
                            network.storedEnergy += (double) removed;
                        }
                    }
                    continue;
                }

                IEnergyStorage forge = forgeContainer(tile);
                if (forge != null) {
                    int stored = forge.getEnergyStored();
                    if (stored > 0) {
                        network.storedEnergy += (double) forge.extractEnergy(stored, false);
                    }
                }
            }
        }

        // 供电：按容量与网络存电填充
        consumerLoop:
        for (List<BlockPos> positions : network.getConsumers().values()) {
            for (BlockPos pos : positions) {
                if (network.storedEnergy <= 0.0D) break consumerLoop;
                World world = server.getWorld(network.dimensionOf(pos));
                if (world == null) continue;
                TileEntity tile = world.getTileEntity(pos);
                if (tile == null) {
                    if (world.isBlockLoaded(pos) && network.isBlockBound(pos)) stale.add(pos);
                    continue;
                }

                IEnergyContainer gt = gtContainer(tile);
                if (gt != null) {
                    long before = gt.getEnergyStored();
                    long space = gt.getEnergyCapacity() - before;
                    if (space <= 0) continue;
                    long want = (long) Math.min(network.storedEnergy, (double) space);
                    if (want <= 0) continue;
                    gt.addEnergy(want);
                    long after = gt.getEnergyStored();
                    long accepted = Math.max(0L, after - before);
                    network.storedEnergy -= (double) accepted;
                    continue;
                }

                IEnergyStorage forge = forgeContainer(tile);
                if (forge != null) {
                    int space = forge.getMaxEnergyStored() - forge.getEnergyStored();
                    if (space <= 0) continue;
                    int want = (int) Math.min(network.storedEnergy, (double) space);
                    if (want <= 0) continue;
                    int accepted = forge.receiveEnergy(want, false);
                    network.storedEnergy -= (double) accepted;
                }
            }
        }

        if (!stale.isEmpty()) {
            for (BlockPos pos : stale) {
                network.removePosition(pos);   // 目标方块没了，绑定方块模式立即失效
            }
            return true;
        }
        return false;
    }

    private static void syncTo(EntityPlayerMP player, WirelessNetworkManager manager) {
        if (player == null || manager == null) return;

        UUID id = ItemWirelessLinker.getNetworkId(player.getHeldItemMainhand());
        WirelessNetwork network = manager.getNetwork(id);
        if (network == null) {
            OmniNetwork.INSTANCE.sendTo(new MessageSyncWireless(false, null, 0.0D,
                    new ArrayList<>(), new ArrayList<>()), player);
            return;
        }

        List<MessageSyncWireless.PosDim> generators = new ArrayList<>();
        for (List<BlockPos> positions : network.getGenerators().values()) {
            for (BlockPos pos : positions) {
                generators.add(new MessageSyncWireless.PosDim(pos, network.dimensionOf(pos),
                        network.isBlockBound(pos)));
            }
        }
        List<MessageSyncWireless.PosDim> consumers = new ArrayList<>();
        for (List<BlockPos> positions : network.getConsumers().values()) {
            for (BlockPos pos : positions) {
                consumers.add(new MessageSyncWireless.PosDim(pos, network.dimensionOf(pos),
                        network.isBlockBound(pos)));
            }
        }

        OmniNetwork.INSTANCE.sendTo(new MessageSyncWireless(true, network.getNetworkId(),
                network.storedEnergy, generators, consumers), player);
    }

    // ==================== 能量容器辅助 ====================

    private static final Map<String, Field> CONTAINER_FIELDS = new HashMap<>();

    private static MetaTileEntity metaTileEntity(TileEntity tile) {
        return tile instanceof IGregTechTileEntity
                ? ((IGregTechTileEntity) tile).getMetaTileEntity()
                : null;
    }

    /** 创造能量单元这类“无限源”：IEnergyContainer 本身是恒定的，只能按输出速率计入 */
    private static boolean isCreativeSource(MetaTileEntity mte) {
        return mte != null && mte.getClass().getName().contains("CreativeEnergy");
    }

    private static IEnergyContainer gtContainer(TileEntity tile) {
        MetaTileEntity mte = metaTileEntity(tile);
        if (mte == null) return null;

        // 1) 普通 GT 能量容器（机器 / 电池 / 创造能量单元）
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, facing);
            if (container != null) return container;
        }
        IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
        if (container != null) return container;

        // 2) 激光网络容器：ILaserContainer extends IEnergyContainer，走的是 CAPABILITY_LASER
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyContainer laser = mte.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, facing);
            if (laser != null) return laser;
        }
        IEnergyContainer laser = mte.getCapability(GregtechTileCapabilities.CAPABILITY_LASER, null);
        if (laser != null) return laser;

        // 3) 多方块部件（能源仓 / 激光仓）只把容器注册成 MultiblockAbility，不暴露 Forge capability，
        //    这里反射取 energyContainer（能源仓）或 buffer（激光仓）。
        return partEnergyContainer(mte);
    }

    private static IEnergyContainer partEnergyContainer(MetaTileEntity mte) {
        for (String name : new String[]{"energyContainer", "buffer"}) {
            try {
                Field field = findContainerField(mte.getClass(), name);
                if (field == null) continue;
                Object value = field.get(mte);
                if (value instanceof IEnergyContainer) {
                    return (IEnergyContainer) value;
                }
            } catch (Throwable ignored) {
                // 尝试下一个字段
            }
        }
        return null;
    }

    private static Field findContainerField(Class<?> type, String name) {
        String cacheKey = type.getName() + "#" + name;
        Field cached = CONTAINER_FIELDS.get(cacheKey);
        if (cached != null) return cached;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!IEnergyContainer.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                CONTAINER_FIELDS.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static IEnergyStorage forgeContainer(TileEntity tile) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyStorage storage = tile.getCapability(CapabilityEnergy.ENERGY, facing);
            if (storage != null) return storage;
        }
        return null;
    }
}
