package com.example.modid.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 存档级无线电网数据（每个存档一份），管理多个互相隔离的网络。
 */
public class WirelessNetworkManager extends WorldSavedData {

    public static final String DATA_NAME = "omnitool_wireless_networks";
    /** 存档格式版本：以后加字段时递增，老存档按 v1 读取 */
    public static final int DATA_VERSION = 2;
    /** 旧版本的存档标签名，用于迁移 */
    private static final String LEGACY_TAG = "WirelessGridData";

    private final Map<UUID, WirelessNetwork> networks = new LinkedHashMap<>();

    public WirelessNetworkManager() {
        super(DATA_NAME);
    }

    public WirelessNetworkManager(String name) {
        super(name);
    }

    /** 服务端调用：取得（或创建）主世界的存档数据 */
    public static WirelessNetworkManager get(World world) {
        if (world == null || world.isRemote) return null;
        MapStorage storage = world.getMapStorage();
        WorldSavedData data = storage.getOrLoadData(WirelessNetworkManager.class, DATA_NAME);
        if (data == null) {
            data = new WirelessNetworkManager();
            storage.setData(DATA_NAME, data);
        }
        return (WirelessNetworkManager) data;
    }

    public Collection<WirelessNetwork> allNetworks() {
        return networks.values();
    }

    public WirelessNetwork getNetwork(UUID id) {
        return id == null ? null : networks.get(id);
    }

    public WirelessNetwork createNetwork(EntityPlayer player) {
        UUID teamKey = WirelessTeams.keyOf(player);
        UUID playerId = player.getUniqueID();

        // 团队只允许一张共享电网：已经存在就直接复用，避免“一队多网”
        if (teamKey != null && !teamKey.equals(playerId)) {
            for (WirelessNetwork existing : networks.values()) {
                if (existing.getMembers().contains(teamKey)) return existing;
            }
        }

        WirelessNetwork network = new WirelessNetwork(UUID.randomUUID());
        // 有团队时只记团队 key（退队后自然失去访问）；没有团队才记个人 UUID
        network.getMembers().add(teamKey != null ? teamKey : playerId);
        networks.put(network.getNetworkId(), network);
        markDirty();
        return network;
    }

    /**
     * 加入团队时的电网合并：
     *  - 团队已有网络：把玩家此前的个人网络（存电 + 绑定）并入团队网络，然后删除个人网络；
     *  - 团队还没有网络：用玩家的个人网络合出一个团队网络；
     *  - 玩家不在任何团队：返回 null（保持个人电网，不做合并）。
     */
    public WirelessNetwork ensureTeamNetwork(EntityPlayer player) {
        if (player == null) return null;
        UUID teamKey = WirelessTeams.keyOf(player);
        UUID playerId = player.getUniqueID();
        boolean inTeam = teamKey != null && !teamKey.equals(playerId);
        if (!inTeam) return null;

        WirelessNetwork teamNetwork = null;
        for (WirelessNetwork network : networks.values()) {
            if (network.getMembers().contains(teamKey)) {
                teamNetwork = network;
                break;
            }
        }

        List<WirelessNetwork> personal = new ArrayList<>();
        for (WirelessNetwork network : networks.values()) {
            if (network == teamNetwork) continue;
            if (network.getMembers().contains(playerId)) {
                personal.add(network);
            }
        }

        if (teamNetwork == null && personal.isEmpty()) return null;

        if (teamNetwork == null) {
            teamNetwork = new WirelessNetwork(UUID.randomUUID());
            teamNetwork.getMembers().add(teamKey);
            networks.put(teamNetwork.getNetworkId(), teamNetwork);
        }

        for (WirelessNetwork old : personal) {
            old.mergeInto(teamNetwork);          // 存电 + 绑定并入团队电网
            networks.remove(old.getNetworkId()); // 合并后删除个人网络，避免电量重复计算
        }

        markDirty();
        return teamNetwork;
    }

    /** 玩家是否属于该网络：团队 key 或（旧存档的）个人 UUID */
    public boolean isMember(WirelessNetwork network, EntityPlayer player) {
        if (network == null || player == null) return false;
        UUID teamKey = WirelessTeams.keyOf(player);
        UUID playerId = player.getUniqueID();
        if (network.getMembers().contains(teamKey)) return true;
        if (network.getMembers().contains(playerId)) {
            // 旧存档：把个人 UUID 升级成团队 key（退队后即失去访问，符合“退队归 0”的语义）
            if (!teamKey.equals(playerId)) {
                network.getMembers().remove(playerId);
                network.getMembers().add(teamKey);
                markDirty();
            }
            return true;
        }
        return false;
    }

    public List<WirelessNetwork> getNetworksForPlayer(EntityPlayer player) {
        List<WirelessNetwork> result = new ArrayList<>();
        for (WirelessNetwork network : networks.values()) {
            if (isMember(network, player)) {
                result.add(network);
            }
        }
        return result;
    }

    /** 找玩家所在团队的共享网络；没有则返回 null */
    public WirelessNetwork findTeamNetwork(EntityPlayer player) {
        UUID teamKey = WirelessTeams.keyOf(player);
        if (teamKey == null || teamKey.equals(player.getUniqueID())) return null;
        for (WirelessNetwork network : networks.values()) {
            if (network.getMembers().contains(teamKey)) return network;
        }
        return null;
    }

    public void deleteNetwork(UUID id) {
        if (id != null && networks.remove(id) != null) {
            markDirty();
        }
    }

    public void markDirtyNow() {
        markDirty();
    }

    // ==================== 存档读写 ====================

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        networks.clear();

        // DataVersion 用于以后扩展字段；老存档没有这个标签时按 v1 处理
        int version = nbt.hasKey("DataVersion") ? nbt.getInteger("DataVersion") : 1;

        NBTTagList list = nbt.hasKey("Networks", 9)
                ? nbt.getTagList("Networks", 10)
                : new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            WirelessNetwork network = WirelessNetwork.readFromNBT(list.getCompoundTagAt(i));
            networks.put(network.getNetworkId(), network);
        }

        // 旧版本兼容：如果有 WirelessGridData，按玩家创建对应网络并迁移电量/绑定
        if (networks.isEmpty() && nbt.hasKey(LEGACY_TAG)) {
            migrateLegacy(nbt.getCompoundTag(LEGACY_TAG));
        }

        if (version < DATA_VERSION && !networks.isEmpty()) {
            markDirty();   // 让旧格式在下一次保存时被写成新格式
        }
    }

    private void migrateLegacy(NBTTagCompound legacy) {
        for (String key : legacy.getKeySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            NBTTagCompound old = legacy.getCompoundTag(key);
            WirelessNetwork network = new WirelessNetwork(UUID.randomUUID());
            network.getMembers().add(playerId);
            network.storedEnergy = WirelessNetwork.sanitizeEnergy(old.getDouble("energy"));
            networks.put(network.getNetworkId(), network);
        }
        markDirty();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        // 只更新本 mod 的键，保留 nbt 里其它（未知的）标签，避免跨版本更新时误删数据
        nbt.setInteger("DataVersion", DATA_VERSION);

        NBTTagList list = new NBTTagList();
        for (WirelessNetwork network : networks.values()) {
            list.appendTag(network.writeToNBT());
        }
        nbt.setTag("Networks", list);
        return nbt;
    }
}
