package com.example.modid.client.gui;

import com.example.modid.client.WirelessClientData;
import com.example.modid.item.ItemWirelessLinker;
import com.example.modid.network.MessageWirelessAction;
import com.example.modid.network.OmniNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import java.io.IOException;

/**
 * 无线电网链接工具的独立 GUI：模式切换、创建网络、选择网络、清空绑定。
 */
public class GuiWirelessLinker extends GuiScreen {

    private static final int BTN_GENERATE = 0;
    private static final int BTN_CONSUME = 1;
    private static final int BTN_CLEAR = 2;
    private static final int BTN_CYCLE = 3;
    private static final int BTN_CREATE = 4;
    private static final int BTN_DONE = 100;

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int centerX = this.width / 2;
        int y = this.height / 2 - 20;
        int leftX = centerX - 102;
        int rightX = centerX + 2;

        this.buttonList.add(new GuiButton(BTN_GENERATE, leftX, y, 100, 20, "切换为发电模式"));
        this.buttonList.add(new GuiButton(BTN_CONSUME, rightX, y, 100, 20, "切换为供电模式"));
        this.buttonList.add(new GuiButton(BTN_CREATE, leftX, y + 26, 100, 20, "创建新网络"));
        this.buttonList.add(new GuiButton(BTN_CYCLE, rightX, y + 26, 100, 20, "选择网络"));
        this.buttonList.add(new GuiButton(BTN_CLEAR, centerX - 100, y + 52, 200, 20, "取消全部绑定"));
        this.buttonList.add(new GuiButton(BTN_DONE, centerX - 50, y + 78, 100, 20, "完成"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        switch (button.id) {
            case BTN_GENERATE:
                send(MessageWirelessAction.ACTION_SET_GENERATE);
                break;
            case BTN_CONSUME:
                send(MessageWirelessAction.ACTION_SET_CONSUME);
                break;
            case BTN_CREATE:
                send(MessageWirelessAction.ACTION_CREATE_NETWORK);
                break;
            case BTN_CYCLE:
                send(MessageWirelessAction.ACTION_CYCLE_NETWORK);
                break;
            case BTN_CLEAR:
                send(MessageWirelessAction.ACTION_CLEAR_BINDINGS);
                break;
            case BTN_DONE:
                this.mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    private void send(int action) {
        OmniNetwork.INSTANCE.sendToServer(new MessageWirelessAction(action));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;
        int top = this.height / 2 - 100;

        String title = "无线电网链接工具";
        this.fontRenderer.drawStringWithShadow(title,
                centerX - this.fontRenderer.getStringWidth(title) / 2, top, 0xFFFFFF);

        ItemStack held = this.mc.player == null ? ItemStack.EMPTY : this.mc.player.getHeldItemMainhand();
        String mode = ItemWirelessLinker.getMode(held);
        String modeText = "模式: " + (ItemWirelessLinker.MODE_CONSUME.equals(mode) ? "供电" : "发电");
        this.fontRenderer.drawStringWithShadow(modeText,
                centerX - this.fontRenderer.getStringWidth(modeText) / 2, top + 16, 0xAAAAAA);

        String id = "当前网络: " + WirelessClientData.shortId();
        this.fontRenderer.drawStringWithShadow(id,
                centerX - this.fontRenderer.getStringWidth(id) / 2, top + 32, 0x55FFFF);

        String energy = "存电: " + WirelessClientData.formatEnergy();
        this.fontRenderer.drawStringWithShadow(energy,
                centerX - this.fontRenderer.getStringWidth(energy) / 2, top + 48, 0xFFFFFF);
    }
}
