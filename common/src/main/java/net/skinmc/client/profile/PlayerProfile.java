package net.skinmc.client.profile;

import java.util.UUID;

/**
 * SkinMC profile data from the passport API (nested xp, country object, role, verified, etc.).
 */
public final class PlayerProfile {

    private final UUID uuid;
    private final String username;
    private final String avatarUrl;
    private final String bio;
    private final String role;
    private final String profileUrl;
    private final boolean isOnline;
    private final boolean verified;
    private final Country country;
    private final XpInfo xpInfo;

    public PlayerProfile(UUID uuid, String username, String avatarUrl, String bio,
                        String role, String profileUrl, boolean isOnline, boolean verified,
                        Country country, XpInfo xpInfo) {
        this.uuid = uuid;
        this.username = username != null ? username : "";
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.bio = bio != null ? bio : "";
        this.role = role != null ? role : "";
        this.profileUrl = profileUrl != null ? profileUrl : "";
        this.isOnline = isOnline;
        this.verified = verified;
        this.country = country;
        this.xpInfo = xpInfo;
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBio() { return bio; }
    public String getRole() { return role; }
    public String getProfileUrl() { return profileUrl; }
    public boolean isOnline() { return isOnline; }
    public boolean isVerified() { return verified; }
    public Country getCountry() { return country; }
    public XpInfo getXpInfo() { return xpInfo; }

    /** Total XP, or 0 if xp info absent. */
    public int getXpTotal() {
        return xpInfo != null ? xpInfo.getTotal() : 0;
    }

    /** Progress to next level in [0, 1], or 0 if unknown. */
    public double getXpProgress() {
        return xpInfo != null ? xpInfo.getProgressToNext() : 0.0;
    }

    /** Display string for country (name or code), or empty. */
    public String getCountryDisplayName() {
        if (country == null) return "";
        return country.getName() != null && !country.getName().isBlank()
                ? country.getName()
                : (country.getCode() != null ? country.getCode() : "");
    }

    public static final class Country {
        private final String code;
        private final String name;

        public Country(String code, String name) {
            this.code = code != null ? code : "";
            this.name = name != null ? name : "";
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    public static final class XpInfo {
        private final int total;
        private final int currentLevelId;
        private final int nextLevelId;
        private final int xpInCurrentLevel;
        private final int xpNeededForNextLevel;
        private final double progressToNext;

        public XpInfo(int total, int currentLevelId, int nextLevelId,
                      int xpInCurrentLevel, int xpNeededForNextLevel, double progressToNext) {
            this.total = Math.max(0, total);
            this.currentLevelId = Math.max(0, currentLevelId);
            this.nextLevelId = Math.max(0, nextLevelId);
            this.xpInCurrentLevel = Math.max(0, xpInCurrentLevel);
            this.xpNeededForNextLevel = Math.max(0, xpNeededForNextLevel);
            this.progressToNext = Math.max(0.0, Math.min(1.0, progressToNext));
        }

        public int getTotal() { return total; }
        public int getCurrentLevelId() { return currentLevelId; }
        public int getNextLevelId() { return nextLevelId; }
        public int getXpInCurrentLevel() { return xpInCurrentLevel; }
        public int getXpNeededForNextLevel() { return xpNeededForNextLevel; }
        public double getProgressToNext() { return progressToNext; }
    }
}
