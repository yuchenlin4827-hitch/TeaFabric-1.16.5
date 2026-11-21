package teafabric.game.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.dynamic.RegistryReadingOps;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import teafabric.game.WorkerBridge;

/**
 * Singleplayer with the world in a Web Worker (the page's side). Playing or creating a world sends it to the
 * worker, shows its progress, and then joins the worker's server like a multiplayer server; the page thread is
 * left to the client (rendering, input), the worker runs the server ticks and world generation in parallel.
 */
public final class WorkerWorlds {
    private static final Logger LOGGER = LogManager.getLogger();
    /** The address boot.js connects to the worker (its socket registry). */
    public static final String ADDRESS = "ws://teafabric.worker/";

    /** A worker world is being played (from "play" until the player leaves it). */
    private static boolean active;
    private static boolean sawWorld;
    private static boolean pauseSent;
    private static int viewDistanceSent = -1;

    private WorkerWorlds() {
    }

    /** Whether worlds run in the worker (it can be used); otherwise vanilla's integrated server runs them. */
    public static boolean enabled() {
        return WorkerBridge.available();
    }

    public static boolean active() {
        return active;
    }

    /** {@code MinecraftClient.startIntegratedServer(String)}: an existing world. */
    public static void play(MinecraftClient client, String dir) {
        begin(client);
        JsonObject msg = message(client, dir);
        WorkerBridge.send(msg.toString());
    }

    /** {@code MinecraftClient.createWorld}: a new world; its settings go along (rebuilt in the worker). */
    public static void create(MinecraftClient client, String dir, LevelInfo info,
            DynamicRegistryManager.Impl registry, GeneratorOptions generator) {
        begin(client);
        try {
            JsonObject msg = message(client, dir);
            JsonElement generatorJson = GeneratorOptions.CODEC
                    .encodeStart(RegistryReadingOps.of(JsonOps.INSTANCE, registry), generator)
                    .getOrThrow(false, e -> LOGGER.error("World generation settings: {}", e));
            msg.addProperty("generator", generatorJson.toString());
            NbtCompound level = new NbtCompound();
            level.putString("LevelName", info.getLevelName());
            level.putInt("GameType", info.getGameMode().getId());
            level.putBoolean("hardcore", info.isHardcore());
            level.putByte("Difficulty", (byte) info.getDifficulty().getId());
            level.putBoolean("allowCommands", info.areCommandsAllowed());
            level.put("GameRules", info.getGameRules().toNbt());
            DataPackSettings.CODEC.encodeStart(NbtOps.INSTANCE, info.getDataPackSettings()).result()
                    .ifPresent(t -> level.put("DataPacks", t));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(level, out);
            msg.addProperty("level", Base64.getEncoder().encodeToString(out.toByteArray()));
            // the world folder as Create World left it (data packs chosen on its screen)
            File gameDir = client.runDirectory;
            addFiles(gameDir, new File(new File(gameDir, "saves"), dir));
            WorkerBridge.send(msg.toString());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not hand the new world to the worker", e);
            WorkerLoadingScreen.fail(client, String.valueOf(e.getMessage()));
        }
    }

    private static void begin(MinecraftClient client) {
        active = true;
        sawWorld = false;
        pauseSent = false;
        viewDistanceSent = client.options.viewDistance;
        client.openScreen(new WorkerLoadingScreen());
    }

    private static JsonObject message(MinecraftClient client, String dir) {
        JsonObject msg = new JsonObject();
        msg.addProperty("t", "start");
        msg.addProperty("world", dir);
        msg.addProperty("player", client.getSession().getUsername());
        msg.addProperty("viewDistance", client.options.viewDistance);
        return msg;
    }

    private static void addFiles(File gameDir, File f) throws IOException {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                addFiles(gameDir, c);
            }
        } else if (f.isFile()) {
            String path = gameDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            WorkerBridge.addFile(path, Files.readAllBytes(f.toPath()));
        }
    }

    /** Every client tick: pause and view distance to the worker, the end of a worker world. */
    public static void tick(MinecraftClient client) {
        String path;
        while ((path = WorkerBridge.takeFile()) != null) {
            // level.dat of a world the worker created or saved: for the world list (the worker keeps the world)
            File f = new File(client.runDirectory, path);
            try {
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), WorkerBridge.takenFileData());
            } catch (IOException e) {
                LOGGER.warn("Could not write {} from the world worker", path, e);
            }
        }
        if (!active) {
            return;
        }
        if (client.world != null) {
            sawWorld = true;
        } else if (sawWorld && !(client.currentScreen instanceof WorkerLoadingScreen)) {
            active = false; // left the world: the worker saves and stops it (unpaused, it sees the player leave)
            if (pauseSent) {
                pauseSent = false;
                WorkerBridge.send("{\"t\":\"pause\",\"on\":false}");
            }
            return;
        }
        boolean paused = client.world != null && client.currentScreen != null && client.currentScreen.isPauseScreen();
        if (paused != pauseSent) {
            pauseSent = paused;
            WorkerBridge.send("{\"t\":\"pause\",\"on\":" + paused + "}");
        }
        if (client.options.viewDistance != viewDistanceSent) {
            viewDistanceSent = client.options.viewDistance;
            WorkerBridge.send("{\"t\":\"viewDistance\",\"value\":" + viewDistanceSent + "}");
        }
    }
}
