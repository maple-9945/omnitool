package com.example.modid.network;

import com.example.modid.TimeAccelHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * GUI 控制包：
 *   action 1 -> 切换「永久加速」标记模式（flag = 开/关）
 *   action 2 -> 关闭存档中所有由全能工具设置的加速
 */
public class MessageAccelOptions implements IMessage {

    public static final int ACTION_PERMANENT = 1;
    public static final int ACTION_CANCEL_ALL = 2;

    private int action;
    private boolean flag;

    public MessageAccelOptions() {
    }

    public MessageAccelOptions(int action, boolean flag) {
        this.action = action;
        this.flag = flag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.flag = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeBoolean(this.flag);
    }

    public static class Handler implements IMessageHandler<MessageAccelOptions, IMessage> {
        @Override
        public IMessage onMessage(MessageAccelOptions message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;
            int action = message.action;
            boolean flag = message.flag;
            player.getServerWorld().addScheduledTask(() -> {
                if (action == ACTION_PERMANENT) {
                    TimeAccelHandler.setPermanentMode(player, flag);
                } else if (action == ACTION_CANCEL_ALL) {
                    TimeAccelHandler.cancelAll(player);
                }
            });
            return null;
        }
    }
}
