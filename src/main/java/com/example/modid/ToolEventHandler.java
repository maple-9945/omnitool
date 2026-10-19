package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = "omnitool")
public class ToolEventHandler {

    private static final int MAX_LEVEL = 127; // 附魔级别
    private static final int MAX_LIMIT = 1024; // 总上限

    // ================== 1. 磁力 + 时运 ==================
    @SubscribeEvent
    public static void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        EntityPlayer player = event.getHarvester();
        if (player == null) return;

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ItemOmniTool)) return;
        if (player.capabilities.isCreativeMode) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear(); // 清空原本的掉落

        IBlockState state = event.getState();
        Block block = state.getBlock();
        BlockPos pos = event.getPos();
        Random rand = new Random();

        if (ItemOmniTool.isSilkTouch(heldItem)) {
            // 精准采集：直接掉方块
            ItemStack silkDrop = block.getPickBlock(state, null, player.world, pos, player);
            drops.add(silkDrop.isEmpty() ? new ItemStack(block) : silkDrop);
        } else {
            // 模拟原版时运机制：先获取基础掉落，然后每级时运尝试增加 0~1 个额外物品
            List<ItemStack> normalDrops = block.getDrops(player.world, pos, state, 0);
            for (ItemStack baseDrop : normalDrops) {
                // 基础掉落物直接加入
                drops.add(baseDrop.copy());

                // 计算额外掉落数量
                int extra = rand.nextInt(MAX_LEVEL + 1);
                int total = baseDrop.getCount() + (baseDrop.getCount() * extra);

                // 限制不超过 1024
                int finalCount = Math.min(total, MAX_LIMIT);

                // 如果放大后数量有增加，再额外添加超出部分
                if (finalCount > baseDrop.getCount()) {
                    ItemStack extraDrop = baseDrop.copy();
                    extraDrop.setCount(finalCount - baseDrop.getCount());
                    drops.add(extraDrop);
                }
            }
        }

        // 磁力吸附逻辑
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop.isEmpty()) continue;
            if (player.addItemStackToInventory(drop)) {
                iterator.remove();
            }
        }

        // 经验也翻倍（原版经验算法直接传入 127 级）
        int exp = block.getExpDrop(state, player.world, pos, MAX_LEVEL);
        if (exp > 0) {
            player.world.spawnEntity(new EntityXPOrb(player.world, player.posX, player.posY, player.posZ, exp));
        }
    }

    // ================== 2. 背包 buff：飞行、无敌、夜视、防火 ==================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;

        boolean hasTool = false;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof ItemOmniTool) {
                hasTool = true;
                break;
            }
        }
        if (!hasTool && player.getHeldItemOffhand().getItem() instanceof ItemOmniTool) {
            hasTool = true;
        }

        if (hasTool) {
            player.capabilities.allowFlying = true;
            player.capabilities.disableDamage = true;
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 400, 0, false, false));
            player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 400, 0, false, false));
        } else if (!player.capabilities.isCreativeMode) {
            player.capabilities.allowFlying = false;
            player.capabilities.disableDamage = false;
            if (player.capabilities.isFlying) {
                player.capabilities.isFlying = false;
            }
        }
        if (!player.world.isRemote) {
            player.sendPlayerAbilities();
        }
    }

    // ================== 3. 秒杀 ==================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
            ItemStack heldItem = player.getHeldItemMainhand();

            if (!heldItem.isEmpty() && heldItem.getItem() instanceof ItemOmniTool) {
                EntityLivingBase target = event.getEntityLiving();
                if (target != player) {
                    event.setAmount(9999.0F);
                }
            }
        }
    }

    // ================== 4. 抢夺 127 级（暴力接管法，绝不丢失物品种类） ==================
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
            ItemStack heldItem = player.getHeldItemMainhand();

            if (!heldItem.isEmpty() && heldItem.getItem() instanceof ItemOmniTool) {
                List<EntityItem> drops = event.getDrops();
                Random rand = new Random();

                // 创建一个新列表，用来存放我们自己计算好并生成的新物品
                java.util.ArrayList<EntityItem> finalDrops = new java.util.ArrayList<>();
                EntityLivingBase entity = event.getEntityLiving();

                // 遍历原版系统计算出来的基础掉落物（此时它已经算好了铁锭和腐肉）
                for (EntityItem originalDrop : drops) {
                    ItemStack stack = originalDrop.getItem().copy();
                    int baseCount = stack.getCount();

                    // 抢夺 127 级计算
                    int extraMultiplier = rand.nextInt(MAX_LEVEL + 1);
                    int extraCount = baseCount * extraMultiplier;

                    // 计算总数量并限制在 1024
                    int totalCount = Math.min(baseCount + extraCount, MAX_LIMIT);

                    // 按 64 拆分成独立的物品组
                    while (totalCount > 0) {
                        int spawnCount = Math.min(totalCount, stack.getMaxStackSize());
                        ItemStack newStack = stack.copy();
                        newStack.setCount(spawnCount);

                        // 在原生物的位置生成新的掉落物
                        EntityItem newDrop = new EntityItem(entity.world,
                                entity.posX,
                                entity.posY + 0.5,
                                entity.posZ,
                                newStack);

                        // 随机散开速度
                        newDrop.motionX = (rand.nextDouble() - 0.5) * 0.5;
                        newDrop.motionY = rand.nextDouble() * 0.5;
                        newDrop.motionZ = (rand.nextDouble() - 0.5) * 0.5;

                        finalDrops.add(newDrop);
                        totalCount -= spawnCount;
                    }
                }

                // 1. 清空系统原本的（有问题的）掉落物列表
                drops.clear();
                // 2. 磁力：击杀掉落物优先飞进背包；背包塞不下的部分再作为实体掉落
                for (EntityItem drop : finalDrops) {
                    ItemStack dropStack = drop.getItem();
                    if (dropStack.isEmpty()) continue;
                    // addItemStackToInventory 会尽力塞入并修改 stack 数量；
                    // 返回 true 表示全部进包，false 时 stack 里剩下的是没塞进去的部分
                    if (player.addItemStackToInventory(dropStack)) {
                        continue;
                    }
                    if (!dropStack.isEmpty()) {
                        drops.add(drop);
                    }
                }
            }
        }
    }
}