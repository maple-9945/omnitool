package com.example.modid.client;

import com.example.modid.network.MessageSyncWireless.PosDim;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 客户端缓存的当前网络数据（由 MessageSyncWireless 更新）。
 */
public final class WirelessClientData {

    private static boolean hasNetwork;
    private static UUID networkId;
    private static double storedEnergy;
    private static List<PosDim> generators = Collections.emptyList();
    private static List<PosDim> consumers = Collections.emptyList();

    private WirelessClientData() {
    }

    public static void apply(boolean has, UUID id, double stored,
                             List<PosDim> gens, List<PosDim> cons) {
        hasNetwork = has;
        networkId = id;
        storedEnergy = stored;
        generators = new ArrayList<>(gens);
        consumers = new ArrayList<>(cons);
    }

    public static void clear() {
        hasNetwork = false;
        networkId = null;
        storedEnergy = 0.0D;
        generators = Collections.emptyList();
        consumers = Collections.emptyList();
    }

    public static boolean hasNetwork() {
        return hasNetwork;
    }

    public static UUID getNetworkId() {
        return networkId;
    }

    public static double getStoredEnergy() {
        return storedEnergy;
    }

    public static List<PosDim> getGenerators() {
        return generators;
    }

    public static List<PosDim> getConsumers() {
        return consumers;
    }

    public static String shortId() {
        if (networkId == null) return "---------";
        return networkId.toString().substring(0, 8);
    }

    public static String formatEnergy() {
        return String.format("%.2e", storedEnergy) + " EU";
    }

    public static boolean isBoundAt(BlockPos pos) {
        for (PosDim entry : generators) {
            if (entry.pos.equals(pos)) return true;
        }
        for (PosDim entry : consumers) {
            if (entry.pos.equals(pos)) return true;
        }
        return false;
    }
}
