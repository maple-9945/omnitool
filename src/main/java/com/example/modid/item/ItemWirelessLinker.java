package com.example.modid.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 无线电网链接工具：把方块绑定到某个独立电网的发电/供电列表。
 * 数据全部存在物品 NBT 里：networkId（UUID）与 mode（generate / consume）。
 */
public class ItemWirelessLinker extends Item {

    public static final String NBT_NETWORK = "networkId";
    public static final String NBT_MODE = "mode";

    public static final String MODE_GENERATE = "generate";
    public static final String MODE_CONSUME = "consume";

    public ItemWirelessLinker() {
        setRegistryName("omnitool", "wireless_linker");
        setTranslationKey("omnitool.wireless_linker");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
    }

    @Nullable
    public static UUID getNetworkId(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_NETWORK)) return null;
        try {
            return UUID.fromString(tag.getString(NBT_NETWORK));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void setNetworkId(ItemStack stack, @Nullable UUID id) {
        if (stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (id == null) {
            tag.removeTag(NBT_NETWORK);
        } else {
            tag.setString(NBT_NETWORK, id.toString());
        }
    }

    public static String getMode(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return MODE_GENERATE;
        String mode = stack.getTagCompound().getString(NBT_MODE);
        return MODE_CONSUME.equals(mode) ? MODE_CONSUME : MODE_GENERATE;
    }

    public static void setMode(ItemStack stack, String mode) {
        if (stack.isEmpty()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(NBT_MODE, MODE_CONSUME.equals(mode) ? MODE_CONSUME : MODE_GENERATE);
    }
}
