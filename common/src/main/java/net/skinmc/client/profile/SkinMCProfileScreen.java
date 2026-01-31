package net.skinmc.client.profile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Native Minecraft screen showing a SkinMC player profile.
 * Layout: top-left avatar, top-right username, below bio (wrapped), XP bar/text, country.
 */
public class SkinMCProfileScreen extends Screen {

    private static final int AVATAR_SIZE = 48;
    private static final int PADDING = 12;
    private static final int BIO_MAX_LINES = 3;
    private static final int XP_BAR_WIDTH = 120;
    private static final int XP_BAR_HEIGHT = 6;

    private final UUID playerUuid;
    private final String fallbackName;
    private PlayerProfile profile;
    private boolean loading = true;
    private boolean error = false;
    private boolean fetchStarted = false;
    private List<FormattedText> bioLines = List.of();

    public SkinMCProfileScreen(UUID playerUuid, String fallbackName) {
        super(Component.translatable("skinmc_mod.profile.title"));
        this.playerUuid = playerUuid;
        this.fallbackName = fallbackName != null ? fallbackName : "?";
    }

    public static void open(Player player) {
        if (player == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> mc.setScreen(new SkinMCProfileScreen(player.getUUID(), player.getName().getString())));
        }
    }

    private static final String PROFILE_URL = net.skinmc.SkinMCMod.BASE_URL + "/profile/";

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int bottom = height - 24;
        addRenderableWidget(Button.builder(Component.translatable("skinmc_mod.profile.open_website"), b -> openProfileInBrowser())
                .bounds(centerX - 105, bottom - 20, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(centerX + 5, bottom - 20, 100, 20).build());

        if (!fetchStarted && profile == null && !error) {
            fetchStarted = true;
            SkinMCProfileService.getInstance().fetchProfile(playerUuid)
                    .whenComplete((p, ex) -> Minecraft.getInstance().execute(() -> {
                        if (p != null) {
                            profile = p;
                            bioLines = wrapBio(profile.getBio());
                        } else {
                            error = true;
                        }
                        loading = false;
                    }));
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        super.render(gui, mouseX, mouseY, partialTick);

        int left = PADDING;
        int top = PADDING;

        if (loading) {
            gui.drawCenteredString(font, Component.translatable("skinmc_mod.profile.loading"), width / 2, height / 2 - 10, 0xFF_FF_FF_FF);
            return;
        }
        if (error) {
            gui.drawCenteredString(font, Component.translatable("skinmc_mod.profile.error"), width / 2, height / 2 - 10, 0xFF_FF_55_55);
            return;
        }
        if (profile == null) return;

        PlayerSkin skin = getSkinFor(playerUuid);
        if (skin != null) {
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(gui, skin, left, top, AVATAR_SIZE);
        }

        int textLeft = left + AVATAR_SIZE + PADDING;
        int textTop = top;
        String name = profile.getUsername().isEmpty() ? fallbackName : profile.getUsername();
        gui.drawString(font, name, textLeft, textTop, 0xFF_FF_FF_FF);
        int nameEndX = textLeft + font.width(name);
        if (profile.isVerified()) {
            gui.drawString(font, "\u2713", nameEndX + 4, textTop, 0xFF_55_FF_55, false);
            nameEndX += font.width("\u2713") + 4;
        }
        if (profile.isOnline()) {
            gui.drawString(font, Component.translatable("skinmc_mod.profile.online"), nameEndX + 4, textTop, 0xFF_55_FF_55, false);
        }
        if (!profile.getRole().isEmpty()) {
            gui.drawString(font, profile.getRole(), textLeft, textTop + font.lineHeight + 2, 0xFF_88_AA_FF, false);
        }

        int bioY = textTop + font.lineHeight + (profile.getRole().isEmpty() ? 4 : font.lineHeight + 6);
        for (int i = 0; i < Math.min(bioLines.size(), BIO_MAX_LINES); i++) {
            FormattedCharSequence seq = net.minecraft.locale.Language.getInstance().getVisualOrder(bioLines.get(i));
            gui.drawString(font, seq, textLeft, bioY + i * (font.lineHeight + 2), 0xFF_AA_AA_AA, false);
        }

        int xpY = bioY + BIO_MAX_LINES * (font.lineHeight + 2) + 8;
        int xpLabelX = textLeft;
        double progress = profile.getXpProgress();
        PlayerProfile.XpInfo xpInfo = profile.getXpInfo();
        if (xpInfo != null && xpInfo.getCurrentLevelId() > 0) {
            gui.drawString(font, Component.translatable("skinmc_mod.profile.level_xp", xpInfo.getCurrentLevelId(), xpInfo.getTotal()), xpLabelX, xpY, 0xFF_CC_CC_CC);
            if (xpInfo.getXpNeededForNextLevel() > 0) {
                gui.drawString(font, Component.translatable("skinmc_mod.profile.xp_to_next", xpInfo.getXpInCurrentLevel(), xpInfo.getXpNeededForNextLevel()), xpLabelX + XP_BAR_WIDTH + 6, xpY + font.lineHeight / 2 - 2, 0xFF_AA_AA_AA, false);
            }
        }
        int xpBarX = textLeft;
        int xpBarY = xpY + font.lineHeight + 2;
        int fill = (int) (XP_BAR_WIDTH * progress);
        gui.fill(xpBarX, xpBarY, xpBarX + XP_BAR_WIDTH, xpBarY + XP_BAR_HEIGHT, 0xFF_33_33_33);
        if (fill > 0) {
            gui.fill(xpBarX, xpBarY, xpBarX + fill, xpBarY + XP_BAR_HEIGHT, 0xFF_55_FF_55);
        }

        String countryDisplay = profile.getCountryDisplayName();
        if (!countryDisplay.isEmpty()) {
            int countryY = xpBarY + XP_BAR_HEIGHT + 6;
            gui.drawString(font, Component.translatable("skinmc_mod.profile.country", countryDisplay), textLeft, countryY, 0xFF_88_88_88);
        }
    }

    private void openProfileInBrowser() {
        String url = (profile != null && profile.getProfileUrl() != null && !profile.getProfileUrl().isBlank())
                ? profile.getProfileUrl()
                : PROFILE_URL + playerUuid;
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception e) {
            if (minecraft != null) {
                minecraft.getChatListener().handleSystemMessage(
                        Component.translatable("skinmc_mod.profile.open_website_failed"), false);
            }
        }
    }

    private static PlayerSkin getSkinFor(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return null;
        PlayerInfo info = conn.getPlayerInfo(uuid);
        return info != null ? info.getSkin() : null;
    }

    private List<FormattedText> wrapBio(String bio) {
        if (bio == null || bio.isBlank()) return List.of();
        String trimmed = bio.trim();
        if (trimmed.length() > 120) trimmed = trimmed.substring(0, 117) + "...";
        int maxWidth = width - PADDING * 2 - AVATAR_SIZE - PADDING;
        return font.getSplitter().splitLines(Component.literal(trimmed), maxWidth, Style.EMPTY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }
}
