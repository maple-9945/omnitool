package com.example.modid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 首次进服发放全能工具。
 *
 * 标记写在 Forge 的 per-player 持久化 NBT（EntityPlayer.PERSISTED_NBT_TAG = "PlayerPersisted"）里，
 * 它随玩家存档保存、且死亡后会被复制到重生实体，所以重进、重生都不会重复发。
 */
@Mod.EventBusSubscriber(modid = "omnitool")
public class PlayerEventHandler {

    private static final String GIVEN_TAG = "omnitool.starterGiven";

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.world.isRemote) return;

        NBTTagCompound forgeData = player.getEntityData();
        NBTTagCompound persisted = forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_TAG)) return;

        // 先置标记，避免任何异常路径导致重复发放
        persisted.setBoolean(GIVEN_TAG, true);
        forgeData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);

        ItemStack tool = new ItemStack(ModItems.OMNI_TOOL);
        if (!player.inventory.addItemStackToInventory(tool)) {
            player.dropItem(tool, false); // 背包满了掉脚下
        }

        player.sendMessage(new TextComponentString(
                "§e[全能工具] §f已发放全能工具：按 §bG §f打开配置，§bShift+右键空气 §f切换精准/时运。"));
    }
}
