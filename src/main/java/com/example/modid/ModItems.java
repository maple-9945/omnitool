package com.example.modid;

import com.example.modid.item.ItemOmniTool;
import com.example.modid.item.ItemWirelessLinker;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = "omnitool")
public class ModItems {
    public static final Item OMNI_TOOL = new ItemOmniTool();
    public static final Item WIRELESS_LINKER = new ItemWirelessLinker();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(OMNI_TOOL);
        event.getRegistry().register(WIRELESS_LINKER);
    }
}