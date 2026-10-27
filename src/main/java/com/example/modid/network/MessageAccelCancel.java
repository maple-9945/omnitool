package com.example.modid.network;

import com.example.modid.TimeAccelHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 取消加速：携带瞄准方块坐标；坐标为空则取消该玩家全部加速。
 */
public class MessageAccelCancel implements IMessage {

    private boolean hasPos = false;
    private BlockPos pos = BlockPos.ORIGIN;

    public MessageAccelCancel() {
    }

    public MessageAccelCancel(BlockPos pos) {
        if (pos != null) {
            this.hasPos = true;
            this.pos = pos;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.hasPos = buf.readBoolean();
        if (this.hasPos) {
            this.pos = BlockPos.fromLong(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.hasPos);
        if (this.hasPos) {
            buf.writeLong(this.pos.toLong());
        }
    }

    public static class Handler implements IMessageHandler<MessageAccelCancel, IMessage> {
        @Override
        public IMessage onMessage(MessageAccelCancel message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;
            BlockPos pos = message.hasPos ? message.pos : null;
            player.getServerWorld().addScheduledTask(() -> TimeAccelHandler.cancel(player, pos));
            return null;
        }
    }
}
