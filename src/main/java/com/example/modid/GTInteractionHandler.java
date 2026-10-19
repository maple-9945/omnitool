package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.raytracer.RayTracer;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
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
 *   BlockMachine.func_180639_a(id est onBlockActivated):
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
        if (mte == null) return; // 不是 GT 机器（方块不是 BlockMachine 承载的 MetaTileEntity）

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
}
