package com.example.modid.proxy;

import com.example.modid.ModItems;
import com.example.modid.client.ClientAccelRenderer;
import com.example.modid.client.gui.GuiOmniTool;
import com.example.modid.network.MessageAccelCancel;
import com.example.modid.network.MessageTimeAccel;
import com.example.modid.network.OmniNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "omnitool")
public class ClientProxy {

    public static KeyBinding keyOpenGui;      // G：打开配置（模式 + 倍率 + 永久加速/全部关闭）
    public static KeyBinding keyTimeAccel;    // V：开启 30 秒标记窗口 / 标记瞄准的方块
    public static KeyBinding keyAccelCancel;  // R：取消瞄准方块的加速（没瞄准则取消全部）

    private static boolean lastOpen;
    private static boolean lastAccel;
    private static boolean lastCancel;

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                ModItems.OMNI_TOOL, 0,
                new ModelResourceLocation("omnitool:omni_tool", "inventory")
        );

        if (keyOpenGui == null) {
            keyOpenGui = new KeyBinding("key.omnitool.open_gui", Keyboard.KEY_G, "key.categories.omnitool");
            ClientRegistry.registerKeyBinding(keyOpenGui);
        }
        if (keyTimeAccel == null) {
            keyTimeAccel = new KeyBinding("key.omnitool.time_accel", Keyboard.KEY_V, "key.categories.omnitool");
            ClientRegistry.registerKeyBinding(keyTimeAccel);
        }
        if (keyAccelCancel == null) {
            keyAccelCancel = new KeyBinding("key.omnitool.accel_cancel", Keyboard.KEY_R, "key.categories.omnitool");
            ClientRegistry.registerKeyBinding(keyAccelCancel);
        }
    }

    /**
     * 用 ClientTickEvent 轮询键盘/鼠标状态，而不是 InputEvent.KeyInputEvent + isPressed()。
     * 这样玩家在 Controls 里改键后立即生效；同时支持鼠标键（左/右/中/侧键），
     * 因为 1.12.2 的 KeyBinding 鼠标键用负数表示：keyCode = 鼠标按键号 - 100。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        boolean open = isDown(keyOpenGui);
        boolean accel = isDown(keyTimeAccel);
        boolean cancel = isDown(keyAccelCancel);

        boolean usable = mc.player != null && mc.currentScreen == null
                && mc.player.getHeldItemMainhand().getItem() == ModItems.OMNI_TOOL;

        if (usable) {
            if (open && !lastOpen) {
                mc.displayGuiScreen(new GuiOmniTool());
            }

            BlockPos target = lookedBlock(mc);

            if (accel && !lastAccel) {
                ClientAccelRenderer.beginWindow();
                if (target != null) {
                    ClientAccelRenderer.markFromClient(mc.world, target);
                }
                OmniNetwork.INSTANCE.sendToServer(
                        new MessageTimeAccel(ClientAccelRenderer.getMultiplier(), target));
            }

            if (cancel && !lastCancel) {
                OmniNetwork.INSTANCE.sendToServer(new MessageAccelCancel(target));
            }
        }

        lastOpen = open;
        lastAccel = accel;
        lastCancel = cancel;
    }

    private static boolean isDown(KeyBinding binding) {
        if (binding == null) return false;
        int code = binding.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        if (code < 0) {
            // 鼠标键：keyCode = button - 100（-100 左键，-99 右键，-98 中键，-97/-96 侧键）
            int button = code + 100;
            return button >= 0 && button < 8 && Mouse.isButtonDown(button);
        }
        return Keyboard.isKeyDown(code);
    }

    private static BlockPos lookedBlock(Minecraft mc) {
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            return mc.objectMouseOver.getBlockPos();
        }
        return null;
    }
}
