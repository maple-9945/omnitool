package com.example.modid.network;

import com.example.modid.item.ItemOmniTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 GUI 点击模式按钮 -> 服务端写入 NBT -> 回同步手持槽位。
 * 1.12.2 的写法：IMessage + IMessageHandler + SimpleNetworkWrapper。
 */
public class MessageSetMode implements IMessage {

    private String mode = ItemOmniTool.MODE_WRENCH;

    public MessageSetMode() {
    }

    public MessageSetMode(String mode) {
        this.mode = mode;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mode = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.mode);
    }

    public static class Handler implements IMessageHandler<MessageSetMode, IMessage> {
        @Override
        public IMessage onMessage(MessageSetMode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null) return null;

            // 1.12.2：网络包回调不在主线程，切到主线程执行
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack held = player.getHeldItemMainhand();
                if (held.isEmpty() || !(held.getItem() instanceof ItemOmniTool)) return;
                if (!ItemOmniTool.isGtToolMode(message.mode)) return;

                ItemOmniTool.setMode(held, message.mode);
                player.inventory.markDirty();

                // 把改过的 NBT 同步回客户端（窗口 0 = 玩家背包，hotbar 槽位 = 36 + currentItem）
                player.connection.sendPacket(new SPacketSetSlot(
                        0, 36 + player.inventory.currentItem, held.copy()));
            });
            return null;
        }
    }
}
