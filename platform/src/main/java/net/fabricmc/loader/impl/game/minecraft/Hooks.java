package net.fabricmc.loader.impl.game.minecraft;

import java.io.File;
import teafabric.web.fabric.WebFabricLoader;

/**
 * Shadows Fabric Loader's game hooks. The calls to {@link #startClient} and {@link #insertBranding}
 * are patched into Minecraft by Fabric's entrypoint patcher during baking; here they drive the
 * ahead-of-time entrypoint registry instead of reflection.
 */
public final class Hooks {
    public static final String INTERNAL_NAME = "net/fabricmc/loader/impl/game/minecraft/Hooks";
    public static final String FABRIC = "fabric";
    public static final String VANILLA = "vanilla";
    public static String appletMainClass;

    private Hooks() {
    }

    public static String insertBranding(String brand) {
        if (brand == null || brand.isEmpty()) {
            return FABRIC;
        }
        if (brand.equals(VANILLA)) {
            return FABRIC;
        }
        return brand.contains(FABRIC) ? brand : brand + "," + FABRIC;
    }

    public static void startClient(File runDir, Object gameInstance) {
        WebFabricLoader.INSTANCE.startClient(runDir, gameInstance);
    }

    public static void startServer(File runDir, Object gameInstance) {
        WebFabricLoader.INSTANCE.startServer(runDir, gameInstance);
    }

    public static void setGameInstance(Object gameInstance) {
        WebFabricLoader.INSTANCE.setGameInstance(gameInstance);
    }
}
