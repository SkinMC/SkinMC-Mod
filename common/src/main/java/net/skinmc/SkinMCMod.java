package net.skinmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkinMCMod {
    public static final String MOD_ID = "skinmc_mod";

    public static final String BASE_URL = "https://skinmc.net";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("SkinMC Mod initializing (cape override enabled)");
    }
}
