package com.example.modid.network;

import com.example.modid.WirelessEventHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 GUI -> 服务端：切换模式 / 取消全部绑定 / 切换网络。
 */
public class MessageWirelessAction implements IMessage {

    public static final int ACTION_SET_GENERATE = 0;
    public static final int ACTION_SET_CONSUME = 1;
    public static final int ACTION_CLEAR_BINDINGS = 2;
    public static final int ACTION_CYCLE_NETWORK = 3;
    public static final int ACTION_CREATE_NETWORK = 4;
    public static final int ACTION_TOGGLE_BIND_MODE = 5;

    private int action;

    public MessageWirelessAction() {
    }

    public MessageWirelessAction(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
    }

    public static class Handler implements IMessageHandler<MessageWirelessAction, IMessage> {
        @Override
        public IMessage onMessage(MessageWirelessAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;
            int action = message.action;
            player.getServerWorld().addScheduledTask(
                    () -> WirelessEventHandler.handleGuiAction(player, action));
            return null;
        }
    }
}
