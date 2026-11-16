package teafabric.game.worker;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import java.io.File;
import java.net.Proxy;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ServerResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.GameMode;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Vanilla's integrated server without the client it lives next to: in the browser build singleplayer worlds
 * run in a Web Worker, a second instance of the program, while the page renders. What IntegratedServer asks
 * the client (paused, view distance, the player's name) arrives as messages from the page.
 */
public final class WorkerServer extends MinecraftServer {
    private static final Logger LOGGER = LogManager.getLogger();

    private final String host;
    private volatile boolean pauseRequested;
    private volatile int viewDistance;
    private boolean paused;
    /** The host's player data, kept in level.dat as in singleplayer (IntegratedPlayerManager). */
    private NbtCompound userData;
    private volatile boolean hadPlayer;

    public WorkerServer(Thread thread, DynamicRegistryManager.Impl registryManager, LevelStorage.Session session,
            ResourcePackManager dataPackManager, ServerResourceManager serverResourceManager,
            SaveProperties saveProperties, MinecraftSessionService sessionService, GameProfileRepository profileRepo,
            UserCache userCache, WorldGenerationProgressListenerFactory progress, String host, int viewDistance) {
        super(thread, registryManager, session, saveProperties, dataPackManager, Proxy.NO_PROXY, Schemas.getFixer(),
                serverResourceManager, sessionService, profileRepo, userCache, progress);
        this.host = host;
        this.viewDistance = viewDistance;
        setServerName(host);
        setDemo(false);
        setPlayerManager(new WorkerPlayerManager(this, registryManager));
    }

    @Override
    protected boolean setupServer() {
        LOGGER.info("Starting worker minecraft server version " + SharedConstants.getGameVersion().getName());
        setOnlineMode(false);
        setPvpEnabled(true);
        setFlightEnabled(true);
        method_31400(); // the server's resource pack / properties defaults, as in IntegratedServer
        loadWorld();
        setMotd(getUserName() + " - " + getSaveProperties().getLevelName());
        return true;
    }

    @Override
    public void tick(BooleanSupplier shouldKeepTicking) {
        boolean wasPaused = paused;
        paused = pauseRequested && hadPlayer;
        if (!wasPaused && paused) {
            LOGGER.info("Saving and pausing game...");
            getPlayerManager().saveAllPlayerData();
            save(false, false, false);
        }
        if (getCurrentPlayerCount() > 0) {
            hadPlayer = true;
        }
        if (!paused) {
            super.tick(shouldKeepTicking);
            int distance = Math.max(2, viewDistance);
            if (distance != getPlayerManager().getViewDistance()) {
                LOGGER.info("Changing view distance to {}, from {}", distance, getPlayerManager().getViewDistance());
                getPlayerManager().setViewDistance(distance);
            }
        }
        // the page left the world: save and stop, as quitting singleplayer does
        if (hadPlayer && getCurrentPlayerCount() == 0 && isRunning()) {
            LOGGER.info("The player left; stopping the world");
            stop(false);
        }
    }

    void setPaused(boolean on) {
        pauseRequested = on;
    }

    void setViewDistance(int distance) {
        viewDistance = distance;
    }

    @Override
    public boolean shouldBroadcastRconToOps() {
        return true;
    }

    @Override
    public boolean shouldBroadcastConsoleToOps() {
        return true;
    }

    @Override
    public File getRunDirectory() {
        return new File(System.getProperty("user.dir", "/minecraft"));
    }

    @Override
    public boolean isDedicated() {
        return false;
    }

    @Override
    public int getRateLimit() {
        return 0;
    }

    @Override
    public boolean isUsingNativeTransport() {
        return false;
    }

    @Override
    public Optional<String> getModdedStatusMessage() {
        return Optional.of("Fabric (TeaFabric worker)");
    }

    @Override
    public boolean openToLan(GameMode gameMode, boolean cheatsAllowed, int port) {
        return false; // a page cannot accept connections
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public int getServerPort() {
        return -1;
    }

    @Override
    public boolean areCommandBlocksEnabled() {
        return true;
    }

    @Override
    public int getOpPermissionLevel() {
        return 4;
    }

    @Override
    public int getFunctionPermissionLevel() {
        return 2;
    }

    @Override
    public boolean isHost(GameProfile profile) {
        return profile.getName().equalsIgnoreCase(host);
    }

    /** No compression between the page and the worker: both ends are in the same browser. */
    @Override
    public int getNetworkCompressionThreshold() {
        return -1;
    }

    @Override
    public int adjustTrackingDistance(int initialDistance) {
        return initialDistance;
    }

    @Override
    public boolean syncChunkWrites() {
        return false;
    }

    /** Singleplayer: the host's data lives in level.dat ("Player"), others in playerdata/. */
    static final class WorkerPlayerManager extends PlayerManager {
        private final WorkerServer server;

        WorkerPlayerManager(WorkerServer server, DynamicRegistryManager.Impl registryManager) {
            super(server, registryManager, server.saveHandler, 8);
            this.server = server;
            setViewDistance(10);
        }

        @Override
        protected void savePlayerData(ServerPlayerEntity player) {
            if (server.isHost(player.getGameProfile())) {
                server.userData = player.writeNbt(new NbtCompound());
            }
            super.savePlayerData(player);
        }

        @Override
        public NbtCompound getUserData() {
            return server.userData;
        }
    }
}
