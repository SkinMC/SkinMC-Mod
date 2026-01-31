package net.skinmc.client.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game menu to choose which cape to show: SkinMC overrides, default only, or SkinMC only.
 */
public class SkinMCConfigScreen extends Screen {

    private final Screen parent;
    private CycleButton<CapeDisplay> capeDisplayButton;

    public SkinMCConfigScreen(Screen parent) {
        super(Component.translatable("skinmc_mod.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        LinearLayout layout = LinearLayout.vertical().spacing(10);

        capeDisplayButton = CycleButton.<CapeDisplay>builder(value -> Component.translatable(value.getTranslationKey()))
                .withValues(CapeDisplay.values())
                .withInitialValue(SkinMCConfig.getCapeDisplay())
                .create(0, 0, 210, 20, Component.translatable("skinmc_mod.config.cape_display"), (button, value) -> {
                    SkinMCConfig.setCapeDisplay(value);
                    SkinMCConfig.save();
                });
        layout.addChild(capeDisplayButton);

        CycleButton<Boolean> shiftRightClickButton = CycleButton.onOffBuilder(SkinMCConfig.isEnableShiftRightClickPlayer())
                .create(0, 0, 210, 20, Component.translatable("skinmc_mod.config.shift_right_click_player"), (button, value) -> {
                    SkinMCConfig.setEnableShiftRightClickPlayer(value);
                    SkinMCConfig.save();
                });
        layout.addChild(shiftRightClickButton);

        layout.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(210).build());

        layout.arrangeElements();
        layout.setPosition(this.width / 2 - 105, this.height / 2 - layout.getHeight() / 2);
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    public void onClose() {
        SkinMCConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
