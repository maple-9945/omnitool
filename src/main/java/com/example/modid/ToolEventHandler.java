package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "omnitool")
public class ToolEventHandler {

    // ================== 1. 磁力吸附（时运/精准已改由 ItemOmniTool 的原版附魔实现） ==================
    // 只搬运掉落物，不修改 drops 内容，所以 GT 机器/线缆的原始掉落（含 NBT）不受影响。
    @SubscribeEvent
    public static void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        EntityPlayer player = event.getHarvester();
        if (player == null) return;

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ItemOmniTool)) return;
        if (player.capabilities.isCreativeMode) return;

        magnetize(player, event.getDrops());
    }

    private static void magnetize(EntityPlayer player, List<ItemStack> drops) {
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop.isEmpty()) continue;
            if (player.addItemStackToInventory(drop)) {
                iterator.remove();
            }
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
}
