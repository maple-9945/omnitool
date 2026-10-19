package com.example.modid.client.gui;

import com.example.modid.item.ItemOmniTool;
import com.example.modid.network.MessageSetMode;
import com.example.modid.network.OmniNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;

public class GuiOmniTool extends GuiScreen {

    private static final int BTN_MODE_BASE = 1;   // 1..6 对应 GT_MODES
    private static final int BTN_DONE = 100;
    private static final int BTN_WIDTH = 150;
    private static final int BTN_HEIGHT = 20;

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        // 6 个 GT 工具模式按钮，两列三行
        for (int i = 0; i < ItemOmniTool.GT_MODES.length; i++) {
            String mode = ItemOmniTool.GT_MODES[i];
            int col = i % 2;
            int row = i / 2;
            int x = centerX - BTN_WIDTH - 4 + col * (BTN_WIDTH + 8);
            int y = startY + row * (BTN_HEIGHT + 6);
            this.buttonList.add(new GuiButton(BTN_MODE_BASE + i, x, y, BTN_WIDTH, BTN_HEIGHT,
                    "模式: " + ItemOmniTool.getModeDisplayName(mode)));
        }

        this.buttonList.add(new GuiButton(BTN_DONE, centerX - 50, startY + 3 * (BTN_HEIGHT + 6) + 10,
                100, BTN_HEIGHT, "完成"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button.id == BTN_DONE) {
            this.mc.displayGuiScreen(null);
            return;
        }

        int index = button.id - BTN_MODE_BASE;
        if (index >= 0 && index < ItemOmniTool.GT_MODES.length) {
            String mode = ItemOmniTool.GT_MODES[index];

            // 客户端先改一份，GUI 与提示立即反映；真正落库由服务端完成并回同步
            ItemStack held = this.mc.player == null ? ItemStack.EMPTY : this.mc.player.getHeldItemMainhand();
            if (!held.isEmpty() && held.getItem() instanceof ItemOmniTool) {
                ItemOmniTool.setMode(held, mode);
                OmniNetwork.INSTANCE.sendToServer(new MessageSetMode(mode));
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // 打开 GUI 时不暂停游戏
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = "全能工具 · GT 交互模式";
        this.fontRenderer.drawStringWithShadow(title,
                this.width / 2 - this.fontRenderer.getStringWidth(title) / 2, this.height / 2 - 85, 0xFFFFFF);

        ItemStack held = this.mc.player == null ? ItemStack.EMPTY : this.mc.player.getHeldItemMainhand();
        String mode = held.isEmpty() ? "?" : ItemOmniTool.getMode(held);
        String current = "当前模式: " + TextFormatting.AQUA + ItemOmniTool.getModeDisplayName(mode);
        this.fontRenderer.drawStringWithShadow(current,
                this.width / 2 - this.fontRenderer.getStringWidth(current) / 2,
                this.height / 2 - 72, 0xAAAAAA);
    }
}
