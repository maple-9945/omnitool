package com.example.modid.item;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ItemOmniTool extends Item {

    // ================= GT 工具模式 =================
    // 字符串必须与 GTCEu 的 gregtech.api.items.toolitem.ToolClasses 常量值完全一致：
    //   ToolClasses.WRENCH = "wrench"、SOFT_MALLET = "mallet"、SCREWDRIVER = "screwdriver"、
    //   CROWBAR = "crowbar"、HARD_HAMMER = "hammer"、WIRE_CUTTER = "wirecutter"
    // GT 的 BlockMachine#onBlockActivated 与维护仓的 fixProblemsWithTools 都是拿
    // Item#getToolClasses(stack) 的返回值去匹配这些字符串的。
    public static final String MODE_WRENCH = "wrench";
    public static final String MODE_MALLET = "mallet";
    public static final String MODE_SCREWDRIVER = "screwdriver";
    public static final String MODE_CROWBAR = "crowbar";
    public static final String MODE_HAMMER = "hammer";
    public static final String MODE_WIRECUTTER = "wirecutter";

    public static final String[] GT_MODES = new String[]{
            MODE_WRENCH, MODE_MALLET, MODE_SCREWDRIVER, MODE_CROWBAR, MODE_HAMMER, MODE_WIRECUTTER
    };

    public ItemOmniTool() {
        setRegistryName("omnitool", "omni_tool");
        setTranslationKey("omnitool.omni_tool");
        setMaxStackSize(1);
        setMaxDamage(0);
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return 10000.0F;
    }

    // ================= NBT：模式 =================
    public static String getMode(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return (tag != null && tag.hasKey("omniMode")) ? tag.getString("omniMode") : MODE_WRENCH;
    }

    public static void setMode(ItemStack stack, String mode) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString("omniMode", mode);
    }

    // 当前模式是否是 GT 认识的工具类型
    public static boolean isGtToolMode(String mode) {
        if (mode == null) return false;
        for (String m : GT_MODES) {
            if (m.equals(mode)) return true;
        }
        return false;
    }

    public static String getModeDisplayName(String mode) {
        if (MODE_WRENCH.equals(mode)) return "扳手 (Wrench)";
        if (MODE_MALLET.equals(mode)) return "软锤 (Soft Mallet)";
        if (MODE_SCREWDRIVER.equals(mode)) return "螺丝刀 (Screwdriver)";
        if (MODE_CROWBAR.equals(mode)) return "撬棍 (Crowbar)";
        if (MODE_HAMMER.equals(mode)) return "锻造锤 (Hammer)";
        if (MODE_WIRECUTTER.equals(mode)) return "剪线钳 (Wire Cutter)";
        return String.valueOf(mode);
    }

    // ================= NBT：精准采集 =================
    public static boolean isSilkTouch(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean("silkTouch");
    }

    public static void setSilkTouch(ItemStack stack, boolean silk) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean("silkTouch", silk);
    }

    // ================= 关键：把 NBT 模式暴露为 Forge 工具类 =================
    // 这是 GT 交互的入口：GTCEu 的 BlockMachine 在 onBlockActivated 里就是
    //     Set<String> toolClasses = stack.getItem().getToolClasses(stack);
    //     if (!toolClasses.isEmpty()) metaTileEntity.onToolClick(player, toolClasses, hand, hit);
    // 而维护仓的 fixProblemsWithTools 用 ToolHelper.isTool(stack, "wrench") 等，
    // ToolHelper.isTool(ItemStack, String...) 内部同样只读 Item#getToolClasses。
    // 所以只要这里返回当前模式字符串，GT 机器就能识别我们的工具，不需要任何反射。
    @Override
    public Set<String> getToolClasses(ItemStack stack) {
        String mode = getMode(stack);
        if (isGtToolMode(mode)) {
            return Collections.singleton(mode);
        }
        return Collections.emptySet();
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    // ================= Shift + 右键：只在对着空气时切换精准/时运 =================
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);

        if (playerIn.isSneaking()) {
            // 由客户端和服务端各自做一次射线检测；任何一边判定指向方块，就都不切换，
            // 把这次右键完整留给 GT 机器（BlockMachine）或其它方块逻辑。
            RayTraceResult ray = this.rayTrace(worldIn, playerIn, true);
            boolean aimingBlock = ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK;
            if (aimingBlock) {
                return new ActionResult<>(EnumActionResult.PASS, stack);
            }

            boolean currentSilk = isSilkTouch(stack);
            setSilkTouch(stack, !currentSilk);

            if (!worldIn.isRemote) {
                String modeText = !currentSilk ? "§b精准采集 (Silk Touch)" : "§d上限时运 (Fortune X)";
                playerIn.sendMessage(new TextComponentString("§e[全能工具] §f已切换附魔为: " + modeText));
            }
            playerIn.swingArm(handIn);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    // ================= 物品提示：显示当前模式 =================
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add("§7当前 GT 模式: §f" + getModeDisplayName(getMode(stack)));
        tooltip.add("§7附魔: §f" + (isSilkTouch(stack) ? "精准采集" : "时运 X"));
        tooltip.add("§8Shift+右键空气 切换精准/时运");
    }
}
