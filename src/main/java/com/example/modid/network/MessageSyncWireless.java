package com.example.modid.network;

import com.example.modid.client.WirelessClientData;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 -> 客户端：同步当前网络的存电与绑定列表（最多每 20 ticks 一次）。
 * 每个条目带“绑定方式”：true = 绑定方块（拆除即失效），false = 绑定坐标。
 */
public class MessageSyncWireless implements IMessage {

    public static final class PosDim {
        public final BlockPos pos;
        public final int dimension;
        public final boolean blockBound;

        public PosDim(BlockPos pos, int dimension, boolean blockBound) {
            this.pos = pos;
            this.dimension = dimension;
            this.blockBound = blockBound;
        }
    }

    private boolean hasNetwork;
    private UUID networkId;
    private double storedEnergy;
    private List<PosDim> generators = new ArrayList<>();
    private List<PosDim> consumers = new ArrayList<>();

    public MessageSyncWireless() {
    }

    public MessageSyncWireless(boolean hasNetwork, UUID networkId, double storedEnergy,
                               List<PosDim> generators, List<PosDim> consumers) {
        this.hasNetwork = hasNetwork;
        this.networkId = networkId;
        this.storedEnergy = storedEnergy;
        this.generators = generators;
        this.consumers = consumers;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.hasNetwork = buf.readBoolean();
        if (this.hasNetwork) {
            this.networkId = new UUID(buf.readLong(), buf.readLong());
            this.storedEnergy = buf.readDouble();
        }
        this.generators = readList(buf);
        this.consumers = readList(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.hasNetwork);
        if (this.hasNetwork) {
            buf.writeLong(this.networkId.getMostSignificantBits());
            buf.writeLong(this.networkId.getLeastSignificantBits());
            buf.writeDouble(this.storedEnergy);
        }
        writeList(buf, this.generators);
        writeList(buf, this.consumers);
    }

    private static List<PosDim> readList(ByteBuf buf) {
        int size = buf.readInt();
        List<PosDim> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long pos = buf.readLong();
            int dim = buf.readInt();
            boolean blockBound = buf.readBoolean();
            list.add(new PosDim(BlockPos.fromLong(pos), dim, blockBound));
        }
        return list;
    }

    private static void writeList(ByteBuf buf, List<PosDim> list) {
        buf.writeInt(list.size());
        for (PosDim entry : list) {
            buf.writeLong(entry.pos.toLong());
            buf.writeInt(entry.dimension);
            buf.writeBoolean(entry.blockBound);
        }
    }

    public static class Handler implements IMessageHandler<MessageSyncWireless, IMessage> {
        @Override
        public IMessage onMessage(MessageSyncWireless message, MessageContext ctx) {
            boolean has = message.hasNetwork;
            UUID id = message.networkId;
            double stored = message.storedEnergy;
            List<PosDim> generators = new ArrayList<>(message.generators);
            List<PosDim> consumers = new ArrayList<>(message.consumers);
            Minecraft.getMinecraft().addScheduledTask(
                    () -> WirelessClientData.apply(has, id, stored, generators, consumers));
            return null;
        }
    }
}
