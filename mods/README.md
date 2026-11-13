Put Fabric mod jars for Minecraft 1.16.5 here, then rebuild (`scripts/build-web.sh`).

Mods are baked in at build time: Fabric Loader and Mixin run during `gradle bake`, and TeaVM then
compiles the game together with the mods into one WebAssembly module. A built page cannot load a mod
jar on its own (there is no JVM or class loader in the browser), so adding or removing a mod always
means running the build again. Jars in this folder are git-ignored.
