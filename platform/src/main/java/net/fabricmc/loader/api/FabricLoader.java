package net.fabricmc.loader.api;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import teafabric.web.fabric.WebFabricLoader;

/**
 * Shadows Fabric Loader's public {@code FabricLoader} API (same members as Fabric Loader 0.20.1)
 * so that {@link #getInstance()} returns the ahead-of-time browser implementation instead of
 * {@code FabricLoaderImpl}, which needs a class loader, jar scanning and reflection.
 */
public interface FabricLoader {
    static FabricLoader getInstance() {
        return WebFabricLoader.INSTANCE;
    }

    <T> List<T> getEntrypoints(String key, Class<T> type);

    <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

    <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker);

    ObjectShare getObjectShare();

    MappingResolver getMappingResolver();

    Optional<ModContainer> getModContainer(String id);

    Collection<ModContainer> getAllMods();

    boolean isModLoaded(String id);

    boolean isDevelopmentEnvironment();

    EnvType getEnvironmentType();

    String getRawGameVersion();

    Object getGameInstance();

    Path getGameDir();

    @Deprecated
    File getGameDirectory();

    Path getConfigDir();

    @Deprecated
    File getConfigDirectory();

    String[] getLaunchArguments(boolean sanitize);
}
