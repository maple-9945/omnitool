package com.example.modid.network;

import com.example.modid.TimeAccelHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * V 键：携带倍率 + 当前瞄准的方块坐标（可为空）。
 * 服务端开 30 秒窗口，并立即标记瞄准的方块（不依赖 RightClickBlock 事件）。
 */
public class MessageTimeAccel implements IMessage {

    private int multiplier = TimeAccelHandler.MAX_MULTIPLIER;
    private boolean hasPos = false;
    private BlockPos pos = BlockPos.ORIGIN;

    public MessageTimeAccel() {
    }

    public MessageTimeAccel(int multiplier) {
        this(multiplier, null);
    }

    public MessageTimeAccel(int multiplier, BlockPos pos) {
        this.multiplier = multiplier;
        if (pos != null) {
            this.hasPos = true;
            this.pos = pos;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.multiplier = buf.readInt();
        this.hasPos = buf.readBoolean();
        if (this.hasPos) {
            this.pos = BlockPos.fromLong(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.multiplier);
        buf.writeBoolean(this.hasPos);
        if (this.hasPos) {
            buf.writeLong(this.pos.toLong());
        }
    }

    public static class Handler implements IMessageHandler<MessageTimeAccel, IMessage> {
        @Override
        public IMessage onMessage(MessageTimeAccel message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;
            int multiplier = message.multiplier;
            BlockPos pos = message.hasPos ? message.pos : null;
            player.getServerWorld().addScheduledTask(
                    () -> TimeAccelHandler.activate(player, multiplier, pos));
            return null;
        }
    }
}
