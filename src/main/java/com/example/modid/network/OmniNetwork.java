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
        INSTANCE.registerMessage(MessageTimeAccel.Handler.class, MessageTimeAccel.class, 1, Side.SERVER);
        INSTANCE.registerMessage(MessageAccelSync.Handler.class, MessageAccelSync.class, 2, Side.CLIENT);
        INSTANCE.registerMessage(MessageAccelCancel.Handler.class, MessageAccelCancel.class, 3, Side.SERVER);
        INSTANCE.registerMessage(MessageAccelOptions.Handler.class, MessageAccelOptions.class, 4, Side.SERVER);
        INSTANCE.registerMessage(MessageWirelessAction.Handler.class, MessageWirelessAction.class, 5, Side.SERVER);
        INSTANCE.registerMessage(MessageSyncWireless.Handler.class, MessageSyncWireless.class, 6, Side.CLIENT);
    }
}
