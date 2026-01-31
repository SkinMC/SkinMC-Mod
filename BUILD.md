# Building and running SkinMC Mod

## Build JARs for distribution

From the project root:

```bash
./gradlew clean build
```

Output JARs:

- **Fabric:** `fabric/build/libs/skinmc_mod-fabric-1.0.0.jar`
- **NeoForge:** `neoforge/build/libs/skinmc_mod-neoforge-1.0.0.jar`

Use these exact files when copying to a mods folder.

## If you get "non-static callback targets a static method"

The mixin callbacks are **static** in the source and in the built JAR. This error means the game is loading an **old** mod JAR that was built before the fix.

1. **Clean build**
   ```bash
   ./gradlew clean build
   ```

2. **Use the new JAR**
   - Remove **any** existing `skinmc_mod` JAR from your mods folder.
   - Copy **only** the new file:
     - Fabric: `fabric/build/libs/skinmc_mod-fabric-1.0.0.jar`
     - NeoForge: `neoforge/build/libs/skinmc_mod-neoforge-1.0.0.jar`
   - Do not use `-dev-shadow` or `-sources` JARs.

3. **If using Prism Launcher**
   - Update the mod in the instance: remove the old SkinMC mod, add the new JAR from the paths above (or replace the file in the instance’s `mods` folder).

4. **If using Run Client from the IDE**
   - Use **Build → Rebuild Project** (or run `./gradlew clean :fabric:build`), then run the Fabric/NeoForge client again so it picks up the latest build.
