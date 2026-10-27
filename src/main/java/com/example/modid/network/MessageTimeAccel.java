package com.example.modid.network;

import com.example.modid.TimeAccelHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * V 键：携带倍率 + 当前瞄准的方块坐标。
 * 这是时间加速的唯一触发入口：服务端收到后直接对瞄准的方块开启加速，
 * 右键交互完全不参与加速逻辑。
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
