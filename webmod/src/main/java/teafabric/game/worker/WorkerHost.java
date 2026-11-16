package teafabric.game.worker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.netty.channel.ChannelPipeline;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.DecoderHandler;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.SizePrepender;
import net.minecraft.network.SplitterHandler;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.FileResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ServerResourceManager;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.WorldGenerationProgressLogger;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.UserCache;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.dynamic.RegistryOps;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import teafabric.game.WorkerBridge;

/**
 * The worker's side: starts and stops singleplayer worlds on the page's commands, the work the client does in
 * {@code MinecraftClient.startIntegratedServer} (data packs, level.dat, the server), and reports progress.
 *
 * <p>Commands (JSON): {@code start} (world, player, viewDistance; for a new world also level (level info,
 * NBT in base64) and generator (generator options, JSON), with the world folder's files), {@code pause} (on),
 * {@code viewDistance} (value). Status messages to the page: {@code loading} (text, percent), {@code ready},
 * {@code error} (text), {@code stopped}.
 */
public final class WorkerHost {
    private static final Logger LOGGER = LogManager.getLogger();
    private static WorkerServer server;
    private static boolean announcedReady;
    private static long lastProgressPost;
    /** A start that arrived while the previous world was still saving, with its files. */
    private static JsonObject deferredStart;
    private static java.util.List<Object[]> deferredFiles;

    private WorkerHost() {
    }

    /** Called by the worker's loop (platform) with each command. */
    public static void command(String json) {
        JsonObject msg = new JsonParser().parse(json).getAsJsonObject();
        String type = msg.get("t").getAsString();
        try {
            switch (type) {
                case "start":
                    if (server != null) {
                        // the previous world is still saving: start this one when it has stopped
                        deferredStart = msg;
                        deferredFiles = new java.util.ArrayList<>();
                        for (int i = 0; i < WorkerBridge.fileCount(); i++) {
                            deferredFiles.add(new Object[] {WorkerBridge.filePath(i), WorkerBridge.fileData(i)});
                        }
                        status("loading", "text", "Saving the previous world", "percent", -1);
                    } else {
                        start(msg, null);
                    }
                    break;
                case "pause":
                    if (server != null) {
                        server.setPaused(msg.get("on").getAsBoolean());
                    }
                    break;
                case "viewDistance":
                    if (server != null) {
                        server.setViewDistance(msg.get("value").getAsInt());
                    }
                    break;
                default:
                    LOGGER.warn("Unknown command from the page: {}", type);
            }
        } catch (Throwable t) {
            LOGGER.error("Command {} failed", type, t);
            status("error", "text", String.valueOf(t.getMessage() != null ? t.getMessage() : t));
        }
    }

    /** Called by the worker's loop every few milliseconds: watches the running server. */
    public static void poll() {
        WorkerServer s = server;
        if (s == null) {
            return;
        }
        if (!announcedReady && s.isLoading()) { // isLoading: loading has finished
            announcedReady = true;
            s.getNetworkIo().bindLocal(); // the platform accepts the page's connections on this binding
            status("ready");
        }
        if (s.isStopped()) {
            server = null;
            LOGGER.info("World stopped");
            status("stopped");
            if (deferredStart != null) {
                JsonObject msg = deferredStart;
                java.util.List<Object[]> files = deferredFiles;
                deferredStart = null;
                deferredFiles = null;
                try {
                    start(msg, files);
                } catch (Throwable t) {
                    LOGGER.error("Starting the world failed", t);
                    status("error", "text", String.valueOf(t.getMessage() != null ? t.getMessage() : t));
                }
            }
        }
    }

    /** Pipeline of an accepted connection: the protocol codecs before the connection's packet handler. */
    public static void addCodecs(ChannelPipeline pipeline) {
        pipeline.addBefore("packet_handler", "splitter", new SplitterHandler());
        pipeline.addBefore("packet_handler", "decoder", new DecoderHandler(NetworkSide.SERVERBOUND));
        pipeline.addBefore("packet_handler", "prepender", new SizePrepender());
        pipeline.addBefore("packet_handler", "encoder", new PacketEncoder(NetworkSide.CLIENTBOUND));
    }

    /** files: the world's files (path, bytes) when they were kept from an earlier poll, else the bridge's. */
    private static void start(JsonObject msg, java.util.List<Object[]> files) throws Exception {
        if (server != null) {
            throw new IllegalStateException("A world is still running");
        }
        String dir = msg.get("world").getAsString();
        String player = msg.get("player").getAsString();
        int viewDistance = msg.has("viewDistance") ? msg.get("viewDistance").getAsInt() : 8;
        boolean create = msg.has("level");
        File gameDir = new File(System.getProperty("user.dir", "/minecraft"));
        File saves = new File(gameDir, "saves");
        status("loading", "text", "Reading world data", "percent", -1);
        if (create) {
            // a new world: nothing of an earlier world with the same folder name stays
            deleteTree(new File(saves, dir));
        }
        int count = files != null ? files.size() : WorkerBridge.fileCount();
        for (int i = 0; i < count; i++) {
            File f = new File(gameDir, files != null ? (String) files.get(i)[0] : WorkerBridge.filePath(i));
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), files != null ? (byte[]) files.get(i)[1] : WorkerBridge.fileData(i));
        }

        LevelStorage storage = LevelStorage.create(saves.toPath());
        LevelStorage.Session session = storage.createSession(dir);
        DynamicRegistryManager.Impl registry = DynamicRegistryManager.create();
        NbtCompound levelTag = create ? NbtIo.readCompressed(new ByteArrayInputStream(
                Base64.getDecoder().decode(msg.get("level").getAsString()))) : null;
        DataPackSettings requested;
        if (create) {
            requested = levelTag.contains("DataPacks")
                    ? DataPackSettings.CODEC.parse(NbtOps.INSTANCE, levelTag.get("DataPacks")).result()
                            .orElse(DataPackSettings.SAFE_MODE)
                    : DataPackSettings.SAFE_MODE;
        } else {
            requested = session.getDataPackSettings();
        }

        status("loading", "text", "Loading data packs", "percent", -1);
        warmUpPackMetadata();
        ResourcePackManager packs = new ResourcePackManager(new VanillaDataPackProvider(),
                new FileResourcePackProvider(session.getDirectory(WorldSavePath.DATAPACKS).toFile(),
                        ResourcePackSource.PACK_SOURCE_WORLD));
        DataPackSettings dataPacks = MinecraftServer.loadDataPacks(packs,
                requested == null ? DataPackSettings.SAFE_MODE : requested, false);
        CompletableFuture<ServerResourceManager> reload = ServerResourceManager.reload(packs.createResourcePacks(),
                CommandManager.RegistrationEnvironment.INTEGRATED, 2, Util.getMainWorkerExecutor(), Runnable::run);
        ServerResourceManager resources = reload.get();

        SaveProperties properties;
        if (create) {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, resources.getResourceManager(), registry);
            JsonElement generatorJson = new JsonParser().parse(msg.get("generator").getAsString());
            DataResult<GeneratorOptions> generator = GeneratorOptions.CODEC.parse(ops, generatorJson);
            GeneratorOptions options = generator.resultOrPartial(e -> LOGGER.error("World generation settings: {}", e))
                    .orElseThrow(() -> new IllegalStateException("Invalid world generation settings"));
            LevelInfo info = LevelInfo.fromDynamic(new Dynamic<>(NbtOps.INSTANCE, levelTag), dataPacks);
            properties = new LevelProperties(info, options, generator.lifecycle());
        } else {
            properties = session.readLevelProperties(
                    RegistryOps.of(NbtOps.INSTANCE, resources.getResourceManager(), registry), dataPacks);
            if (properties == null) {
                throw new IllegalStateException("Failed to load world");
            }
        }
        session.backupLevelDataFile(registry, properties);
        resources.loadRegistryTags();

        YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
        MinecraftSessionService sessionService = auth.createMinecraftSessionService();
        GameProfileRepository profiles = auth.createProfileRepository();
        UserCache userCache = new UserCache(profiles, new File(gameDir, MinecraftServer.USER_CACHE_FILE.getName()));
        SkullBlockEntity.setUserCache(userCache);
        SkullBlockEntity.setSessionService(sessionService);
        UserCache.setUseRemote(false);

        status("loading", "text", "Preparing spawn area", "percent", 0);
        announcedReady = false;
        SaveProperties props = properties;
        server = MinecraftServer.startServer(thread -> new WorkerServer(thread, registry, session, packs, resources,
                props, sessionService, profiles, userCache, Progress::new, player, viewDistance));
    }

    /**
     * The first read of a pack's metadata fails in this build (on the page it is the client's resource pack,
     * which then shows "Broken/missing pack.mcmeta"): read the vanilla data pack's once, logging why, so that
     * the data pack list finds it.
     */
    private static void warmUpPackMetadata() {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try (java.io.InputStream in = net.minecraft.resource.DefaultResourcePack.class
                    .getResourceAsStream("/pack.mcmeta")) {
                if (in == null) {
                    LOGGER.warn("pack.mcmeta is not on the class path");
                    return;
                }
                com.google.gson.JsonObject json = net.minecraft.util.JsonHelper.deserialize(
                        new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)));
                net.minecraft.resource.metadata.PackResourceMetadata meta =
                        net.minecraft.resource.metadata.PackResourceMetadata.READER.fromJson(
                                net.minecraft.util.JsonHelper.getObject(json, "pack"));
                LOGGER.info("Vanilla data pack metadata read (attempt {}): format {}", attempt, meta.getPackFormat());
                return;
            } catch (Throwable t) {
                LOGGER.warn("Reading pack.mcmeta failed (attempt {})", attempt, t);
            }
        }
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteTree(c);
            }
        }
        f.delete();
    }

    /** Sends {t: type, key: value, ...} to the page. */
    static void status(String type, Object... keyValues) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object v = keyValues[i + 1];
            if (v instanceof Number) {
                o.addProperty((String) keyValues[i], (Number) v);
            } else {
                o.addProperty((String) keyValues[i], String.valueOf(v));
            }
        }
        WorkerBridge.post(o.toString());
    }

    /** World generation progress (spawn area) to the page's loading screen, a few times a second. */
    private static final class Progress implements WorldGenerationProgressListener {
        private final WorldGenerationProgressLogger logger;

        Progress(int radius) {
            logger = new WorldGenerationProgressLogger(radius);
        }

        @Override
        public void start(ChunkPos spawnPos) {
            logger.start(spawnPos);
        }

        @Override
        public void setChunkStatus(ChunkPos pos, ChunkStatus status) {
            logger.setChunkStatus(pos, status);
            long now = System.currentTimeMillis();
            if (now - lastProgressPost > 250) {
                lastProgressPost = now;
                status("loading", "text", "Preparing spawn area", "percent", logger.getProgressPercentage());
            }
        }

        @Override
        public void stop() {
            logger.stop();
        }
    }
}
