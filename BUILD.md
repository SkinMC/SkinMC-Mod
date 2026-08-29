# Building and running SkinMC Mod

## Prerequisites

- JDK 25
- An internet connection for the first build

The Gradle wrapper downloads the pinned Gradle version automatically. If JDK 25 is not your
system default, point `JAVA_HOME` at it for the build:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew clean build
```

## Build JARs for distribution

From the project root:

```bash
./gradlew clean build
```

Output JARs (version comes from `gradle.properties` → `mod_version`):

- **Fabric:** `fabric/build/libs/skinmc_mod-fabric-<version>.jar`
- **NeoForge:** `neoforge/build/libs/skinmc_mod-neoforge-<version>.jar`

Use these exact files when copying to a mods folder. Do **not** distribute the `-raw` or
`-sources` JARs; the unsuffixed JARs contain the transformed common code required at runtime.

## Making a release (both Fabric and NeoForge)

1. **Set the version** in `gradle.properties`:

   ```properties
   mod_version = 2.3.0   # e.g. 2.3.0, 2.3.1, 2.4.0
   ```

2. **Build both platforms** from the project root:

   ```bash
   ./gradlew clean build
   ```

3. **Collect the release JARs** (no `-raw` or `-sources`):

   - `fabric/build/libs/skinmc_mod-fabric-<version>.jar`
   - `neoforge/build/libs/skinmc_mod-neoforge-<version>.jar`

4. **Create the release** (e.g. on GitHub or your download page):

   - Create a new release / tag (e.g. `v2.3.0`).
   - In the release notes, say which file is for Fabric and which for NeoForge.
   - Attach **both** JARs so users can download the one that matches their loader.

   Example release text:

   ```text
   - **Fabric:** `skinmc_mod-fabric-2.3.0.jar` — use with Fabric loader.
   - **NeoForge:** `skinmc_mod-neoforge-2.3.0.jar` — use with NeoForge loader.
   ```

5. **(Optional)** Commit the version bump before or after the release:
   ```bash
   git add gradle.properties
   git commit -m "Release 2.3.0"
   git tag v2.3.0
   git push origin main --tags
   ```

## Upgrading Minecraft after 26.2

1. **`gradle.properties`**
   - `minecraft_version` = the new Minecraft version
   - `fabric_api_version` = a Fabric API build for that Minecraft version
   - `neoforge_version` = a NeoForge build for that Minecraft version
   - Optionally: `architectury_api_version`, `fabric_loader_version`, `modmenu_version` if newer versions are required

2. **`fabric/src/main/resources/fabric.mod.json`**
   - Update the Minecraft and minimum loader/API ranges

3. **`neoforge/src/main/resources/META-INF/neoforge.mods.toml`**
   - Update the Minecraft, NeoForge, and Architectury version ranges

4. **Build toolchain**
   - Minecraft 26.x ships unobfuscated binaries, so keep Architectury Loom's no-remap plugin
   - Update Loom, Gradle, and the Java release only when the target Minecraft version requires it

5. **`README.md`**
   - Update the **Requirements** section: Minecraft version

6. **Build and test**
   ```bash
   ./gradlew clean build
   ```
   Then launch both `:fabric:runClient` and `:neoforge:runClient` to validate mixins and loader metadata.
