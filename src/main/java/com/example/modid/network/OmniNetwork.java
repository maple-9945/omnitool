package com.example.modid.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 1.12.2 的网络通道：SimpleNetworkWrapper（不是高版本的 SimpleChannel）。
 */
public final class OmniNetwork {

    public static final String CHANNEL_NAME = "omnitool";
    public static SimpleNetworkWrapper INSTANCE;

    private OmniNetwork() {
    }

    public static void register() {
        if (INSTANCE != null) return;
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        INSTANCE.registerMessage(MessageSetMode.Handler.class, MessageSetMode.class, 0, Side.SERVER);
    }
}
