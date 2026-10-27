package com.example.modid.item;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemOmniTool extends Item
        implements appeng.api.implementations.items.IAEWrench,
        crazypants.enderio.api.tool.ITool {

    // ================= GT 工具模式 =================
    // 字符串必须与 GTCEu 的 gregtech.api.items.toolitem.ToolClasses 常量值完全一致；
    // GT 的 BlockMachine 与维护仓都靠 Item#getToolClasses 的返回值识别工具。
    public static final String MODE_WRENCH = "wrench";
    public static final String MODE_MALLET = "mallet";
    public static final String MODE_SCREWDRIVER = "screwdriver";
    public static final String MODE_CROWBAR = "crowbar";
    public static final String MODE_HAMMER = "hammer";
    public static final String MODE_WIRECUTTER = "wirecutter";

    public static final String[] GT_MODES = new String[]{
            MODE_WRENCH, MODE_MALLET, MODE_SCREWDRIVER, MODE_CROWBAR, MODE_HAMMER, MODE_WIRECUTTER
    };

    // 工具永远同时具备这些 GT 工具类：机器/线缆的掉落与维护仓扫描都按工具类判定，
    // 只返回当前模式会漏掉其它判定（例如维护仓修不同故障、GT 的 pickaxe 判定）。
    public static final Set<String> ALL_TOOL_CLASSES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "wrench", "wirecutter", "pickaxe", "screwdriver", "mallet", "crowbar", "hammer")));

    // 时运/抢夺等级：不走事件，直接写进 NBT，交给原版附魔逻辑
    public static final int FORTUNE_LEVEL = 127;
    public static final int LOOTING_LEVEL = 127;

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

    // ================= 原版附魔：直接写 NBT =================
    /**
     * 把当前模式的附魔写进物品 NBT：
     *   精准模式 -> 精准采集 I（无时运）
     *   时运模式 -> 时运 CXXVII（127 级）
     * 两种模式都带 抢夺 CXXVII，杀人时的掉落由原版 EntityLivingBase.dropLoot 读取，
     * 不再需要 LivingDropsEvent。
     */
    public static void applyEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> desired = new HashMap<>();
        if (isSilkTouch(stack)) {
            desired.put(Enchantments.SILK_TOUCH, 1);
        } else {
            desired.put(Enchantments.FORTUNE, FORTUNE_LEVEL);
        }
        desired.put(Enchantments.LOOTING, LOOTING_LEVEL);

        // 不一致才写，避免每 tick 都改 NBT
        if (!desired.equals(EnchantmentHelper.getEnchantments(stack))) {
            EnchantmentHelper.setEnchantments(desired, stack);
        }
    }

    // 防止玩家用铁砧/砂轮把附魔洗掉：服务端每 tick 校验一次，客户端靠同步更新
    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (worldIn.isRemote) return;
        applyEnchantments(stack);
    }

    // ================= Forge 工具类（GT 交互 / 掉落 / 维护仓识别） =================
    // 不再按 NBT 模式返回单一类型：始终返回全部 GT 工具类，
    // 这样 BlockMachine/BlockPipe 的掉落判定、维护仓 fixProblemsWithTools 的扫描、
    // GT 的 pickaxe 判定都能通过。具体“当前模拟哪种工具”由 GTInteractionHandler
    // 在右键分派时单独只传当前模式，所以不会影响扳手/软锤等交互差异。
    @Override
    public Set<String> getToolClasses(ItemStack stack) {
        return ALL_TOOL_CLASSES;
    }

    // 兜底：Forge 的采集判定会先问 getHarvestLevel，返回 -1 时会退化到
    // inventory.canHarvestBlock()。这里直接给高等级，保证 GT 机器（wrench 1 级）等
    // “需要工具才掉落”的方块一定能通过 harvestBlock 检查。
    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, @Nullable EntityPlayer player, @Nullable IBlockState blockState) {
        return 5;
    }

    // ================= AE2 扳手兼容 =================
    // AE2 的机器/线缆用自己的 IAEWrench 判定“手里是不是扳手”。
    // 实现该接口后，扳手模式下的全能工具会被 AE2 当成正规扳手，
    // AE 机器旋转、AE 线缆拆卸都直接走 AE 自己的逻辑，不需要额外反射。
    @Override
    public boolean canWrench(ItemStack stack, EntityPlayer player, BlockPos pos) {
        return MODE_WRENCH.equals(getMode(stack));
    }

    // ================= Ender IO 扳手兼容 =================
    // EIO 的 BlockEio 走 ToolUtil.getEquippedTool(player, hand) -> ITool.canUse(hand, player, pos)，
    // 因此只要 Item 实现了 crazypants.enderio.api.tool.ITool，且 canUse 在扳手模式下返回 true，
    // EIO 的管道/机器旋转、拆装逻辑就会把全能工具当成 Yeta 扳手。
    @Override
    public boolean canUse(EnumHand hand, EntityPlayer player, BlockPos pos) {
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getHeldItem(hand);
        return !stack.isEmpty() && stack.getItem() == this && MODE_WRENCH.equals(getMode(stack));
    }

    @Override
    public void used(EnumHand hand, EntityPlayer player, BlockPos pos) {
        // EIO 在交互成功后调用；本工具无限耐久，无需额外处理（EIO 自己会播放音效/粒子）。
    }

    @Override
    public boolean shouldHideFacades(ItemStack stack, EntityPlayer player) {
        return MODE_WRENCH.equals(getMode(stack));
    }

    // 让物品始终带附魔光效
    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    // ================= Shift + 右键：只在对着空气时切换精准/时运 =================
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);

        if (playerIn.isSneaking()) {
            // 客户端和服务端各做一次射线检测；命中方块就不切换，把右键让给 GT 机器
            RayTraceResult ray = this.rayTrace(worldIn, playerIn, true);
            boolean aimingBlock = ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK;
            if (aimingBlock) {
                return new ActionResult<>(EnumActionResult.PASS, stack);
            }

            boolean currentSilk = isSilkTouch(stack);
            setSilkTouch(stack, !currentSilk);
            applyEnchantments(stack); // 立刻写入附魔 NBT

            if (!worldIn.isRemote) {
                String modeText = !currentSilk ? "§b精准采集 (Silk Touch)" : "§d上限时运 (Fortune 127)";
                playerIn.sendMessage(new TextComponentString("§e[全能工具] §f已切换附魔为: " + modeText));
            }
            playerIn.swingArm(handIn);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    // ================= 物品提示 =================
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add("§7当前 GT 模式: §f" + getModeDisplayName(getMode(stack)));
        tooltip.add("§7附魔: §f" + (isSilkTouch(stack) ? "精准采集" : "时运 127") + " §7+ §f抢夺 127");
        tooltip.add("§8Shift+右键空气 切换精准/时运");
    }
}
