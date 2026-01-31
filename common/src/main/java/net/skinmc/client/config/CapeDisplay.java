package net.skinmc.client.config;

public enum CapeDisplay {
    SKINMC_OVERRIDES("skinmc_mod.cape.skinmc_overrides"),
    DEFAULT_ONLY("skinmc_mod.cape.default_only"),
    SKINMC_ONLY("skinmc_mod.cape.skinmc_only");

    private final String translationKey;

    CapeDisplay(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean useSkinMCCapes() {
        return this != DEFAULT_ONLY;
    }

    public boolean hideDefaultWhenNoSkinMC() {
        return this == SKINMC_ONLY;
    }
}
