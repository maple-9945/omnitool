package com.example.modid.wireless;

import com.example.modid.item.ItemWirelessLinker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一个独立的无线电网：电量、发电/供电绑定（按玩家分组）、成员列表。
 * 电量用 double，方便用科学计数法显示且不会像 long 那样在叠加时溢出。
 */
public class WirelessNetwork {

    public final UUID networkId;
    public double storedEnergy;
    private final Map<UUID, List<BlockPos>> generators = new HashMap<>();
    private final Map<UUID, List<BlockPos>> consumers = new HashMap<>();
    private final Set<UUID> members = new HashSet<>();
    /** 绑定方块所在维度，用于结算时定位正确的世界 */
    private final Map<BlockPos, Integer> dimensions = new HashMap<>();
    /** 每个坐标的绑定方式：block = 绑定方块（拆除即失效）；pos = 绑定坐标（拆除仍保留） */
    private final Map<BlockPos, String> bindModes = new HashMap<>();

    public WirelessNetwork(UUID networkId) {
        this.networkId = networkId;
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public double getStoredEnergy() {
        return storedEnergy;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Map<UUID, List<BlockPos>> getGenerators() {
        return generators;
    }

    public Map<UUID, List<BlockPos>> getConsumers() {
        return consumers;
    }

    public List<BlockPos> generatorList(UUID player) {
        return generators.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public List<BlockPos> consumerList(UUID player) {
        return consumers.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public boolean isGenerator(UUID player, BlockPos pos) {
        List<BlockPos> list = generators.get(player);
        return list != null && list.contains(pos);
    }

    public boolean isConsumer(UUID player, BlockPos pos) {
        List<BlockPos> list = consumers.get(player);
        return list != null && list.contains(pos);
    }

    public void removeBinding(UUID player, BlockPos pos) {
        List<BlockPos> gen = generators.get(player);
        if (gen != null) {
            gen.remove(pos);
            if (gen.isEmpty()) generators.remove(player);
        }
        List<BlockPos> con = consumers.get(player);
        if (con != null) {
            con.remove(pos);
            if (con.isEmpty()) consumers.remove(player);
        }
        dimensions.remove(pos);
    }

    public void removeAllFor(UUID player) {
        generators.remove(player);
        consumers.remove(player);
    }

    /** 方块被拆除/消失时：从所有玩家的发电与供电列表里移除该坐标，返回是否真的移除了东西 */
    public boolean removePosition(BlockPos pos) {
        boolean removed = removeFromMap(generators, pos);
        removed |= removeFromMap(consumers, pos);
        dimensions.remove(pos);
        bindModes.remove(pos);
        return removed;
    }

    private static boolean removeFromMap(Map<UUID, List<BlockPos>> map, BlockPos pos) {
        boolean removed = false;
        java.util.Iterator<Map.Entry<UUID, List<BlockPos>>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            List<BlockPos> list = iterator.next().getValue();
            if (list.remove(pos)) {
                removed = true;
                if (list.isEmpty()) iterator.remove();
            }
        }
        return removed;
    }

    public int dimensionOf(BlockPos pos) {
        Integer dim = dimensions.get(pos);
        return dim == null ? 0 : dim;
    }

    public void setDimensionOf(BlockPos pos, int dimension) {
        dimensions.put(pos, dimension);
    }

    public void setBindMode(BlockPos pos, String mode) {
        bindModes.put(pos, ItemWirelessLinker.BIND_POS.equals(mode)
                ? ItemWirelessLinker.BIND_POS : ItemWirelessLinker.BIND_BLOCK);
    }

    /** 旧存档没有记录时默认按“绑定方块”处理，符合“拆除即失效”的预期 */
    public String bindModeOf(BlockPos pos) {
        String mode = bindModes.get(pos);
        return ItemWirelessLinker.BIND_POS.equals(mode) ? ItemWirelessLinker.BIND_POS : ItemWirelessLinker.BIND_BLOCK;
    }

    public boolean isBlockBound(BlockPos pos) {
        return ItemWirelessLinker.BIND_BLOCK.equals(bindModeOf(pos));
    }

    // ==================== NBT ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("IdMost", networkId.getMostSignificantBits());
        tag.setLong("IdLeast", networkId.getLeastSignificantBits());
        tag.setDouble("StoredEnergy", storedEnergy);

        NBTTagList memberList = new NBTTagList();
        for (UUID member : members) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Most", member.getMostSignificantBits());
            entry.setLong("Least", member.getLeastSignificantBits());
            memberList.appendTag(entry);
        }
        tag.setTag("Members", memberList);

        tag.setTag("Generators", writeBindings(generators));
        tag.setTag("Consumers", writeBindings(consumers));

        NBTTagList dimList = new NBTTagList();
        for (Map.Entry<BlockPos, Integer> entry : dimensions.entrySet()) {
            NBTTagCompound dimTag = new NBTTagCompound();
            dimTag.setLong("Pos", entry.getKey().toLong());
            dimTag.setInteger("Dim", entry.getValue());
            dimList.appendTag(dimTag);
        }
        tag.setTag("Dimensions", dimList);

        NBTTagList bindList = new NBTTagList();
        for (Map.Entry<BlockPos, String> entry : bindModes.entrySet()) {
            NBTTagCompound bindTag = new NBTTagCompound();
            bindTag.setLong("Pos", entry.getKey().toLong());
            bindTag.setString("Mode", entry.getValue());
            bindList.appendTag(bindTag);
        }
        tag.setTag("BindModes", bindList);
        return tag;
    }

    private static NBTTagList writeBindings(Map<UUID, List<BlockPos>> bindings) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, List<BlockPos>> entry : bindings.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            NBTTagCompound ownerTag = new NBTTagCompound();
            ownerTag.setLong("Most", entry.getKey().getMostSignificantBits());
            ownerTag.setLong("Least", entry.getKey().getLeastSignificantBits());
            NBTTagList posList = new NBTTagList();
            for (BlockPos pos : entry.getValue()) {
                NBTTagCompound posTag = new NBTTagCompound();
                posTag.setLong("Pos", pos.toLong());
                posList.appendTag(posTag);
            }
            ownerTag.setTag("Positions", posList);
            list.appendTag(ownerTag);
        }
        return list;
    }

    public static WirelessNetwork readFromNBT(NBTTagCompound tag) {
        // UUID 缺失/损坏时给一个随机 ID，避免整张网络被丢弃
        UUID id;
        if (tag.hasKey("IdMost") && tag.hasKey("IdLeast")) {
            id = new UUID(tag.getLong("IdMost"), tag.getLong("IdLeast"));
        } else {
            id = UUID.randomUUID();
        }
        WirelessNetwork network = new WirelessNetwork(id);

        // 存电兼容旧字段名（StoredEnergy 为当前格式；Energy/Stored 为早期格式），并修复非法值
        double stored = 0.0D;
        if (tag.hasKey("StoredEnergy")) stored = tag.getDouble("StoredEnergy");
        else if (tag.hasKey("Energy")) stored = tag.getDouble("Energy");
        else if (tag.hasKey("Stored")) stored = tag.getDouble("Stored");
        network.storedEnergy = sanitizeEnergy(stored);

        NBTTagList memberList = tag.getTagList("Members", 10);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound entry = memberList.getCompoundTagAt(i);
            network.members.add(new UUID(entry.getLong("Most"), entry.getLong("Least")));
        }

        readBindings(tag.getTagList("Generators", 10), network.generators);
        readBindings(tag.getTagList("Consumers", 10), network.consumers);

        NBTTagList dimList = tag.getTagList("Dimensions", 10);
        for (int i = 0; i < dimList.tagCount(); i++) {
            NBTTagCompound dimTag = dimList.getCompoundTagAt(i);
            network.dimensions.put(BlockPos.fromLong(dimTag.getLong("Pos")), dimTag.getInteger("Dim"));
        }
        NBTTagList bindList = tag.getTagList("BindModes", 10);
        for (int i = 0; i < bindList.tagCount(); i++) {
            NBTTagCompound bindTag = bindList.getCompoundTagAt(i);
            network.bindModes.put(BlockPos.fromLong(bindTag.getLong("Pos")), bindTag.getString("Mode"));
        }
        return network;
    }

    /** 防止 NaN / 无穷 / 负数把整张网络电表弄坏 */
    public static double sanitizeEnergy(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            return 0.0D;
        }
        return value;
    }

    private static void readBindings(NBTTagList list, Map<UUID, List<BlockPos>> out) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound ownerTag = list.getCompoundTagAt(i);
            UUID owner = new UUID(ownerTag.getLong("Most"), ownerTag.getLong("Least"));
            List<BlockPos> positions = new ArrayList<>();
            NBTTagList posList = ownerTag.getTagList("Positions", 10);
            for (int j = 0; j < posList.tagCount(); j++) {
                positions.add(BlockPos.fromLong(posList.getCompoundTagAt(j).getLong("Pos")));
            }
            out.put(owner, positions);
        }
    }
}
