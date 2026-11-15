Put Fabric mod jars for Minecraft 1.16.5 here, then rebuild (`scripts/build-web.sh`).

Mods are baked in at build time: Fabric Loader and Mixin run during `gradle bake`, and TeaVM then
compiles the game together with the mods into one WebAssembly module. A built page cannot load a mod
jar on its own (there is no JVM or class loader in the browser), so adding or removing a mod always
means running the build again. Jars in this folder are git-ignored.

`scripts/get-mods.sh` downloads the tested set from Modrinth (each file checked against its SHA-512):

* every mod of **Fabulously Optimized 1.12.3**, its last pack for Minecraft 1.16.5: Sodium, Lithium,
  Phosphor, FerriteCore, Iris, Indium, Sodium Extra, Reese's Sodium Options, LazyDFU, Smooth Boot,
  Entity Culling, Dynamic FPS, Cull Leaves, LambDynamicLights, LambdaBetterGrass, Colormatic, CEM, Better
  Beds, Fast Chest, Mod Menu, Cloth Config and Cloth API, Architectury, Not Enough Crashes, Slight GUI
  Modifications, Borderless Mining, Capes, Custom Fog, Fabrishot, WI Zoom, Advancement Info, AntiGhost,
  No Fade, No Potion Offset, Tooltip Fix, Item Model Fix, Fast Open Links and Folders, YOSBR and Fabric
  Language Kotlin. Three are left out: Fabric API (the build already includes it), FerriteCore 2.1.0
  (2.1.1 is used instead) and Hydrogen (optional in the pack and off on Java 16 and later; it generates
  classes at run time);
* UI and helper mods: Roughly Enough Items, AppleSkin, WTHIT, Mouse Wheelie, BetterF3, ShulkerBoxTooltip.
  (Light Overlay was tried and left out: it calls its own Fabric code through MethodHandles looked up by
  names built at run time.)

Map mods (Xaero's, JourneyMap) are not included: their licences forbid redistributing modified copies,
which a compiled build is.

The build adapts what these mods rely on that a browser does not have, only when the mod is present
(see "Mods" in the main README): Architectury's platform calls, Not Enough Crashes' start-up (mapping
download, log rewriting, System.exit blocking), Main.main injections,
Sodium's worker threads, FerriteCore's run-time class definition, Slight GUI Modifications' Groovy
scripting, and more in `baker/src/main/java/teafabric/baker/ModCompat.java`.
