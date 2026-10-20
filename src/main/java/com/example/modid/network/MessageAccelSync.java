package com.example.modid.network;

import com.example.modid.client.ClientAccelRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步“正在加速的方块列表”，用于渲染高亮边框和倒计时/∞。
 */
public class MessageAccelSync implements IMessage {

    private List<Entry> entries = new ArrayList<>();

    public MessageAccelSync() {
    }

    public MessageAccelSync(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public static final class Entry {
        public final int dimension;
        public final BlockPos pos;
        public final int ticks;
        public final boolean permanent;

        public Entry(int dimension, BlockPos pos, int ticks, boolean permanent) {
            this.dimension = dimension;
            this.pos = pos;
            this.ticks = ticks;
            this.permanent = permanent;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int dim = buf.readInt();
            long pos = buf.readLong();
            int ticks = buf.readInt();
            boolean permanent = buf.readBoolean();
            entries.add(new Entry(dim, BlockPos.fromLong(pos), ticks, permanent));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (Entry entry : entries) {
            buf.writeInt(entry.dimension);
            buf.writeLong(entry.pos.toLong());
            buf.writeInt(entry.ticks);
            buf.writeBoolean(entry.permanent);
        }
    }

    public static class Handler implements IMessageHandler<MessageAccelSync, IMessage> {
        @Override
        public IMessage onMessage(MessageAccelSync message, MessageContext ctx) {
            List<Entry> entries = new ArrayList<>(message.getEntries());
            Minecraft.getMinecraft().addScheduledTask(() -> ClientAccelRenderer.applySync(entries));
            return null;
        }
    }
}
