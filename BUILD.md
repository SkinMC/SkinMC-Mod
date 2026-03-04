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

## Upgrading Minecraft version (e.g. 1.21.4 → 1.21.5)

1. **`gradle.properties`**
   - `minecraft_version` = new MC version (e.g. `1.21.5`)
   - `fabric_api_version` = Fabric API for that MC version (e.g. [Modrinth](https://modrinth.com/mod/fabric-api) or [Fabric API releases](https://modrinth.com/mod/fabric-api/versions))
   - `neoforge_version` = NeoForge for that MC version (e.g. [NeoForge](https://neoforged.net/) or Maven)
   - Optionally: `architectury_api_version`, `fabric_loader_version`, `modmenu_version` if newer versions are required

2. **`fabric/src/main/resources/fabric.mod.json`**
   - In `depends`, set `"minecraft": "~1.21.x"` to the new version

3. **`neoforge/src/main/resources/META-INF/neoforge.mods.toml`**
   - `versionRange` for `minecraft`: `[1.21.x,)` → new version
   - `versionRange` for `neoforge`: `[21.x,)` → new NeoForge major version if it changed

4. **`build.gradle`** (root)
   - If Parchment mappings fail, update the Parchment line in `dependencies` (check [Parchment](https://parchmentmc.org/) or Maven for the correct zip for the new MC version)

5. **`README.md`**
   - Update the **Requirements** section: Minecraft version

6. **Build and test**
   ```bash
   ./gradlew clean build
   ```
   Fix any dependency or mapping errors (Architectury, Mod Menu, etc.) by bumping versions in `gradle.properties`.
