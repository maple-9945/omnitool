package com.example.modid.proxy;

import com.example.modid.ModItems;
import com.example.modid.client.gui.GuiOmniTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent; // 注意这里
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public class ClientProxy {

    public static KeyBinding keyOpenGui;

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                ModItems.OMNI_TOOL, 0,
                new ModelResourceLocation("omnitool:omni_tool", "inventory")
        );

        // 注册 G 键
        if (keyOpenGui == null) {
            keyOpenGui = new KeyBinding("key.omnitool.open_gui", Keyboard.KEY_G, "key.categories.omnitool");
            ClientRegistry.registerKeyBinding(keyOpenGui);
        }
    }

    // 监听按键 (1.12.2 的写法是 KeyInputEvent)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (keyOpenGui.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            // 如果玩家手里拿着我们的工具
            if (mc.player != null && mc.player.getHeldItemMainhand().getItem() == ModItems.OMNI_TOOL) {
                mc.displayGuiScreen(new GuiOmniTool());
            }
        }
    }
}