package net.skinmc.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.skinmc.SkinMCMod;

import java.net.URI;

/**
 * In-game menu to choose which cape to show: SkinMC overrides, default only, or SkinMC only.
 */
public class SkinMCConfigScreen extends Screen {

    private static final int MAX_CONTENT_WIDTH = 230;
    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_TO_CONTROL_GAP = 3;
    private static final int ROW_GAP = 10;
    private static final URI CAPES_URI = URI.create(SkinMCMod.BASE_URL + "/account/capes");

    private final Screen parent;
    private int contentLeft;
    private int capeDisplayLabelY;
    private int profileShortcutLabelY;

    public SkinMCConfigScreen(Screen parent) {
        super(Component.translatable("skinmc_mod.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 40);
        contentLeft = (width - contentWidth) / 2;
        int top = Math.max(20, (height - 150) / 2);

        capeDisplayLabelY = top + 32;
        int capeDisplayButtonY = capeDisplayLabelY + font.lineHeight + LABEL_TO_CONTROL_GAP;

        addRenderableWidget(CycleButton.<CapeDisplay>builder(
                        value -> Component.translatable(value.getTranslationKey()),
                        SkinMCConfig.getCapeDisplay()
                )
                .withValues(CapeDisplay.values())
                .displayOnlyValue()
                .create(contentLeft, capeDisplayButtonY, contentWidth, CONTROL_HEIGHT,
                        Component.translatable("skinmc_mod.config.cape_display"), (button, value) -> {
                    SkinMCConfig.setCapeDisplay(value);
                    SkinMCConfig.save();
                }));

        profileShortcutLabelY = capeDisplayButtonY + CONTROL_HEIGHT + ROW_GAP;
        int profileShortcutButtonY = profileShortcutLabelY + font.lineHeight + LABEL_TO_CONTROL_GAP;
        addRenderableWidget(CycleButton.onOffBuilder(SkinMCConfig.isEnableShiftRightClickPlayer())
                .displayOnlyValue()
                .create(contentLeft, profileShortcutButtonY, contentWidth, CONTROL_HEIGHT,
                        Component.translatable("skinmc_mod.config.shift_right_click_player"), (button, value) -> {
                    SkinMCConfig.setEnableShiftRightClickPlayer(value);
                    SkinMCConfig.save();
                }));

        int actionsY = profileShortcutButtonY + CONTROL_HEIGHT + 12;
        int actionGap = 6;
        int actionWidth = (contentWidth - actionGap) / 2;
        addRenderableWidget(Button.builder(Component.translatable("skinmc_mod.config.change_cape"), button -> openCapePage())
                .bounds(contentLeft, actionsY, actionWidth, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(contentLeft + actionWidth + actionGap, actionsY, contentWidth - actionWidth - actionGap, CONTROL_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(gui);
        gui.drawCenteredString(font, title, width / 2, Math.max(20, (height - 150) / 2), 0xFF_FF_FF_FF);
        gui.drawString(font, Component.translatable("skinmc_mod.config.cape_display"),
                contentLeft, capeDisplayLabelY, 0xFF_AA_AA_AA, false);
        gui.drawString(font, Component.translatable("skinmc_mod.config.shift_right_click_player"),
                contentLeft, profileShortcutLabelY, 0xFF_AA_AA_AA, false);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void openCapePage() {
        try {
            Util.getPlatform().openUri(CAPES_URI);
        } catch (Exception e) {
            if (minecraft != null) {
                minecraft.getChatListener().handleSystemMessage(
                        Component.translatable("skinmc_mod.profile.open_website_failed"), false);
            }
        }
    }

    @Override
    public void onClose() {
        SkinMCConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
