package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.raytracer.RayTracer;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.Collections;
import java.util.Set;

/**
 * GT 机器交互拦截（扳手旋转 / 软锤开关 / 螺丝刀配置 / 撬棍 / 锤 / 剪线钳）。
 *
 * 依据来自 GTCEu 2.8.7 (#2901) 的字节码，而不是猜测：
 *   BlockMachine.onBlockActivated:
 *       Set<String> classes = stack.getItem().getToolClasses(stack);
 *       if (!classes.isEmpty()) {
 *           if (metaTileEntity.onToolClick(player, classes, hand, rayTrace)) {
 *               ToolHelper.damageItem(stack, player);
 *               ToolHelper.playToolSound(stack, player);
 *               return true;
 *           }
 *       }
 *       return metaTileEntity.onRightClick(player, hand, side, rayTrace);
 *
 *   MetaTileEntity.onToolClick(player, Set<String>, hand, CuboidRayTraceResult):
 *       按 set 里是否含 "screwdriver"/"mallet"/"wrench"/"crowbar"/"hammer"/"wirecutter"
 *       分别调用 onScrewdriverClick / onSoftMalletClick / onWrenchClick /
 *       onCrowbarClick / onHardHammerClick / onWireCutterClick。
 *
 * 因为 PlayerInteractEvent.RightClickBlock 在 PlayerInteractionManager 中先于
 * block.onBlockActivated 触发（取消后能跳过 BlockMachine），所以我们在这里取消原版流程，
 * 自己按同样的顺序调用 onToolClick -> onRightClick，避免重复执行。
 *
 * 额外好处：原版在潜行时会跳过 onBlockActivated，这条路径不受影响，潜行右键依然能操作机器。
 *
 * ==================== 时间加速隔离 ====================
 * 本类不再包含任何时间加速逻辑：右键永远不会触发/标记时间加速。
 * 时间加速只能由 V 键的网络包（MessageTimeAccel）在服务端触发。
 */
@Mod.EventBusSubscriber(modid = "omnitool")
public class GTInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        EntityPlayer player = event.getEntityPlayer();
        if (world == null || player == null) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ItemOmniTool)) return;

        // 只有模式是 GT 认识的工具类型才接管；否则把右键原样交还给方块
        String mode = ItemOmniTool.getMode(held);
        if (!ItemOmniTool.isGtToolMode(mode)) return;

        BlockPos pos = event.getPos();
        MetaTileEntity mte = GTUtility.getMetaTileEntity(world, pos);

        // 非 GT 机器：扳手模式下先看是否 EIO 方块；EIO 用自己的 ITool 判定 + 交互逻辑，
        // 这里必须原样放行，绝不能取消事件（否则 EIO 的旋转/拆装不会执行）。
        if (mte == null) {
            if (ItemOmniTool.MODE_WRENCH.equals(mode) && !isEnderIoBlock(world, pos)) {
                tryRotateBlock(world, pos, event);
            }
            return;
        }

        RayTraceResult trace = RayTracer.retraceBlock(world, player, pos);
        if (!(trace instanceof CuboidRayTraceResult)) return;

        CuboidRayTraceResult hit = (CuboidRayTraceResult) trace;
        // getToolClasses() 现在恒返回全部工具类（给掉落/维护仓判定用），
        // 但右键分派必须只传「当前 NBT 模式」，否则 MetaTileEntity.onToolClick
        // 会按 screwdriver -> mallet -> wrench ... 的顺序永远先命中 screwdriver。
        Set<String> toolClasses = Collections.singleton(mode);
        if (toolClasses.isEmpty()) return;

        EnumHand hand = event.getHand();

        // 与 BlockMachine 完全一致的分派顺序
        boolean handled = mte.onToolClick(player, toolClasses, hand, hit);
        if (!handled) {
            handled = mte.onRightClick(player, hand, hit.sideHit, hit);
        }

        if (handled) {
            ToolHelper.damageItem(held, player);
            ToolHelper.playToolSound(held, player);
        }

        // 无论 handled 与否都取消原版流程：否则 BlockMachine 会把同一次右键再执行一遍
        event.setCanceled(true);
        event.setCancellationResult(handled ? EnumActionResult.SUCCESS : EnumActionResult.PASS);
    }

    /**
     * 目标方块是否属于 Ender IO（按类名包名判断，避免把 EIO 编译期依赖带到运行期判断）。
     * EIO 的 BlockEio.onBlockActivated 会走 {@code ToolUtil.getEquippedTool(...)->ITool.canUse(...)}，
     * 所以只要不取消事件、把右键交还给 EIO，EIO 就会用自己的一套逻辑完成旋转/拆装。
     */
    private static boolean isEnderIoBlock(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().getClass().getName().startsWith("crazypants.enderio");
    }

    /**
     * 非 GT 方块的扳手旋转。
     *
     * <p>只调用原版 {@link Block#rotateBlock(World, BlockPos, EnumFacing)}：
     * 这是 1.12.2 所有“可旋转方块”（含多数 AE2 机器）统一实现的钩子。
     * 旋转在服务端执行，客户端由方块自身同步；成功则取消本次右键，避免同时打开 GUI。</p>
     *
     * <p>如果方块没实现该钩子（返回 false），这里不做任何处理、也不取消事件，
     * 让方块自己的 onBlockActivated 继续执行（例如 AE2 机器会按它自己的扳手逻辑处理，
     * 因为 ItemOmniTool 实现了 {@code IAEWrench}）。</p>
     */
    private static void tryRotateBlock(World world, BlockPos pos, PlayerInteractEvent.RightClickBlock event) {
        if (world.isRemote) return; // 旋转只由服务端执行，避免客户端预测再叠加服务器的旋转

        Block block = world.getBlockState(pos).getBlock();
        EnumFacing facing = event.getFace() == null ? EnumFacing.UP : event.getFace();

        if (block.rotateBlock(world, pos, facing)) {
            if (event.getEntityPlayer() != null) {
                event.getEntityPlayer().swingArm(event.getHand() == null ? EnumHand.MAIN_HAND : event.getHand());
            }
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }
}
