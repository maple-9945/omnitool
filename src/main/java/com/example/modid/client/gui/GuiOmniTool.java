package com.example.modid.client.gui;

import com.example.modid.client.ClientAccelRenderer;
import com.example.modid.item.ItemOmniTool;
import com.example.modid.network.MessageAccelOptions;
import com.example.modid.network.MessageSetMode;
import com.example.modid.network.MessageTimeAccel;
import com.example.modid.network.OmniNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiOmniTool extends GuiScreen {

    private static final int BTN_MODE_BASE = 1;   // 1..6 对应 GT_MODES
    private static final int BTN_DONE = 100;
    private static final int BTN_APPLY_MULT = 200;
    private static final int BTN_PERMANENT = 201;
    private static final int BTN_CANCEL_ALL = 202;
    private static final int BTN_WIDTH = 150;
    private static final int BTN_HEIGHT = 20;

    private GuiTextField multiplierField;

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 加速倍率输入框（1~1024）
        this.multiplierField = new GuiTextField(0, this.fontRenderer,
                centerX - 50, centerY - 72, 100, 18);
        this.multiplierField.setMaxStringLength(4);
        this.multiplierField.setText(String.valueOf(ClientAccelRenderer.getMultiplier()));

        int startY = centerY - 40;

        for (int i = 0; i < ItemOmniTool.GT_MODES.length; i++) {
            String mode = ItemOmniTool.GT_MODES[i];
            int col = i % 2;
            int row = i / 2;
            int x = centerX - BTN_WIDTH - 4 + col * (BTN_WIDTH + 8);
            int y = startY + row * (BTN_HEIGHT + 6);
            this.buttonList.add(new GuiButton(BTN_MODE_BASE + i, x, y, BTN_WIDTH, BTN_HEIGHT,
                    "模式: " + ItemOmniTool.getModeDisplayName(mode)));
        }

        this.buttonList.add(new GuiButton(BTN_APPLY_MULT, centerX + 56, centerY - 72, 50, 18, "应用"));
        this.buttonList.add(new GuiButton(BTN_DONE, centerX - 50, startY + 3 * (BTN_HEIGHT + 6) + 10,
                100, BTN_HEIGHT, "完成"));
        this.buttonList.add(new GuiButton(BTN_PERMANENT, centerX - 100, centerY + 72, 200, BTN_HEIGHT,
                permanentLabel()));
        this.buttonList.add(new GuiButton(BTN_CANCEL_ALL, centerX - 100, centerY + 96, 200, BTN_HEIGHT,
                "关闭全部加速（整个存档）"));
    }

    private static String permanentLabel() {
        return "永久加速模式: " + (ClientAccelRenderer.isPermanentMode() ? "开" : "关");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button.id == BTN_DONE) {
            this.mc.displayGuiScreen(null);
            return;
        }

        if (button.id == BTN_APPLY_MULT) {
            applyMultiplier();
            return;
        }

        if (button.id == BTN_PERMANENT) {
            boolean enabled = !ClientAccelRenderer.isPermanentMode();
            ClientAccelRenderer.setPermanentMode(enabled);
            button.displayString = permanentLabel();
            OmniNetwork.INSTANCE.sendToServer(
                    new MessageAccelOptions(MessageAccelOptions.ACTION_PERMANENT, enabled));
            return;
        }

        if (button.id == BTN_CANCEL_ALL) {
            ClientAccelRenderer.clearLocal();
            OmniNetwork.INSTANCE.sendToServer(
                    new MessageAccelOptions(MessageAccelOptions.ACTION_CANCEL_ALL, false));
            return;
        }

        int index = button.id - BTN_MODE_BASE;
        if (index >= 0 && index < ItemOmniTool.GT_MODES.length) {
            String mode = ItemOmniTool.GT_MODES[index];

            ItemStack held = this.mc.player == null ? ItemStack.EMPTY : this.mc.player.getHeldItemMainhand();
            if (!held.isEmpty() && held.getItem() instanceof ItemOmniTool) {
                ItemOmniTool.setMode(held, mode);
                OmniNetwork.INSTANCE.sendToServer(new MessageSetMode(mode));
            }
        }
    }

    private void applyMultiplier() {
        int value;
        try {
            value = Integer.parseInt(this.multiplierField.getText().trim());
        } catch (NumberFormatException e) {
            value = 1024;
        }
        value = Math.max(1, Math.min(1024, value));
        this.multiplierField.setText(String.valueOf(value));
        ClientAccelRenderer.setMultiplier(value);
        OmniNetwork.INSTANCE.sendToServer(new MessageTimeAccel(value));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.multiplierField.textboxKeyTyped(typedChar, keyCode)) {
            if (keyCode == Keyboard.KEY_RETURN) {
                applyMultiplier();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.multiplierField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        String title = "全能工具 · GT 交互模式";
        this.fontRenderer.drawStringWithShadow(title,
                this.width / 2 - this.fontRenderer.getStringWidth(title) / 2, this.height / 2 - 118, 0xFFFFFF);

        ItemStack held = this.mc.player == null ? ItemStack.EMPTY : this.mc.player.getHeldItemMainhand();
        String mode = held.isEmpty() ? "?" : ItemOmniTool.getMode(held);
        String current = "当前模式: " + ItemOmniTool.getModeDisplayName(mode);
        this.fontRenderer.drawStringWithShadow(current,
                this.width / 2 - this.fontRenderer.getStringWidth(current) / 2, this.height / 2 - 102, 0xAAAAAA);

        String label = "加速倍率 (1-1024)";
        this.fontRenderer.drawStringWithShadow(label,
                this.width / 2 - 50, this.height / 2 - 84, 0xCCCCCC);

        this.multiplierField.drawTextBox();
    }
}
