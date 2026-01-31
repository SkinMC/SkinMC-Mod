# Building and running SkinMC Mod

## Build JARs for distribution

From the project root:

```bash
./gradlew clean build
```

Output JARs (version comes from `gradle.properties` → `mod_version`):

- **Fabric:** `fabric/build/libs/skinmc_mod-fabric-<version>.jar`
- **NeoForge:** `neoforge/build/libs/skinmc_mod-neoforge-<version>.jar`

Use these exact files when copying to a mods folder. Do **not** use `-dev-shadow` or `-sources` JARs.

## Making a release (both Fabric and NeoForge)

1. **Set the version** in `gradle.properties`:

   ```properties
   mod_version = 2.0.0   # e.g. 2.0.0, 2.0.1, 2.1.0
   ```

2. **Build both platforms** from the project root:

   ```bash
   ./gradlew clean build
   ```

3. **Collect the release JARs** (no `-dev-shadow` or `-sources`):

   - `fabric/build/libs/skinmc_mod-fabric-<version>.jar`
   - `neoforge/build/libs/skinmc_mod-neoforge-<version>.jar`

4. **Create the release** (e.g. on GitHub or your download page):

   - Create a new release / tag (e.g. `v2.0.0`).
   - In the release notes, say which file is for Fabric and which for NeoForge.
   - Attach **both** JARs so users can download the one that matches their loader.

   Example release text:

   ```text
   - **Fabric:** `skinmc_mod-fabric-2.0.0.jar` — use with Fabric loader.
   - **NeoForge:** `skinmc_mod-neoforge-2.0.0.jar` — use with NeoForge loader.
   ```

5. **(Optional)** Commit the version bump before or after the release:
   ```bash
   git add gradle.properties
   git commit -m "Release 2.0.0"
   git tag v2.0.0
   git push origin main --tags
   ```
