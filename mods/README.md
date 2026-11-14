Put Fabric mod jars for Minecraft 1.16.5 here, then rebuild (`scripts/build-web.sh`).

Mods are baked in at build time: Fabric Loader and Mixin run during `gradle bake`, and TeaVM then
compiles the game together with the mods into one WebAssembly module. A built page cannot load a mod
jar on its own (there is no JVM or class loader in the browser), so adding or removing a mod always
means running the build again. Jars in this folder are git-ignored.

Tested optimization mods (all four together; the build adapts them to the browser automatically, see
"Optimization mods" in the main README):

* Sodium 0.2.0+build.4 (`sodium-fabric-mc1.16.5-0.2.0+build.4.jar`): renderer
* Lithium 0.6.6 (`lithium-fabric-mc1.16.5-0.6.6.jar`): game logic
* Phosphor 0.8.0 (`phosphor-fabric-mc1.16.2-0.8.0.jar`): lighting engine
* FerriteCore 2.1.1 (`ferritecore-2.1.1-fabric.jar`): memory use

Iris (shader packs) does not work in the browser build; the main README explains why.
