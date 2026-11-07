package teafabric.web.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import teafabric.generated.Entrypoints;
import teafabric.web.vfs.WebFileSystem;

/**
 * Browser implementation of {@link FabricLoader}. Mod discovery, dependency resolution, mixin
 * application and remapping all happened at build time (see the baker); this class serves the
 * baked results: metadata from {@code teafabric/mods.json}, entrypoint instances from the
 * generated {@link Entrypoints} class and mod resources from the virtual file system.
 */
public final class WebFabricLoader implements FabricLoader {
    public static final WebFabricLoader INSTANCE = new WebFabricLoader();

    private final Map<String, WebModContainer> mods = new LinkedHashMap<>();
    private final Map<String, List<EntrypointContainer<Object>>> entrypointCache = new HashMap<>();
    private final WebObjectShare objectShare = new WebObjectShare();
    private String gameVersion = "1.16.5";
    private String loaderVersion = "?";
    private Object gameInstance;
    private boolean loaded;

    private WebFabricLoader() {
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path json = WebFileSystem.classpathRoot().resolve("teafabric/mods.json");
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(json), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            gameVersion = root.get("gameVersion").getAsString();
            loaderVersion = root.get("loaderVersion").getAsString();
            for (JsonElement e : root.getAsJsonArray("mods")) {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                Path modRoot = id.equals("minecraft") ? WebFileSystem.classpathRoot()
                        : WebFileSystem.classpathRoot().resolve("teafabric/mods/" + id);
                mods.put(id, new WebModContainer(new WebModMetadata(o), modRoot, this));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read baked mod list " + json, e);
        }
    }

    public String getLoaderVersion() {
        ensureLoaded();
        return loaderVersion;
    }

    public void startClient(File runDir, Object game) {
        setGameInstance(game);
        ensureLoaded();
        invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
        invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
    }

    public void startServer(File runDir, Object game) {
        setGameInstance(game);
        ensureLoaded();
        invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
    }

    public void setGameInstance(Object game) {
        if (gameInstance == null) {
            gameInstance = game;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<EntrypointContainer<Object>> entrypoints(String key) {
        ensureLoaded();
        List<EntrypointContainer<Object>> list = entrypointCache.get(key);
        if (list == null) {
            List<Object[]> raw = new ArrayList<>();
            Entrypoints.create(key, (List) raw);
            list = new ArrayList<>();
            for (Object[] entry : raw) {
                WebModContainer mod = mods.get((String) entry[0]);
                list.add(new WebEntrypointContainer<>(entry[1], mod));
            }
            entrypointCache.put(key, list);
        }
        return list;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (EntrypointContainer<Object> c : entrypoints(key)) {
            if (type.isInstance(c.getEntrypoint())) {
                result.add((T) c.getEntrypoint());
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> result = new ArrayList<>();
        for (EntrypointContainer<Object> c : entrypoints(key)) {
            if (type.isInstance(c.getEntrypoint())) {
                result.add((EntrypointContainer) c);
            }
        }
        return result;
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        RuntimeException failure = null;
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
            try {
                invoker.accept(c.getEntrypoint());
            } catch (RuntimeException e) {
                RuntimeException wrapped = new RuntimeException("Could not execute entrypoint stage '" + key
                        + "' due to errors, provided by '" + c.getProvider().getMetadata().getId() + "'", e);
                if (failure == null) {
                    failure = wrapped;
                } else {
                    failure.addSuppressed(wrapped);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    @Override
    public MappingResolver getMappingResolver() {
        return IntermediaryResolver.INSTANCE;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        ensureLoaded();
        WebModContainer mod = mods.get(id);
        if (mod == null) {
            for (WebModContainer m : mods.values()) {
                if (m.getMetadata().getProvides().contains(id)) {
                    return Optional.of(m);
                }
            }
        }
        return Optional.ofNullable(mod);
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        ensureLoaded();
        return Collections.unmodifiableCollection(new ArrayList<>(mods.values()));
    }

    Collection<WebModContainer> modsInternal() {
        ensureLoaded();
        return mods.values();
    }

    @Override
    public boolean isModLoaded(String id) {
        return getModContainer(id).isPresent();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return false;
    }

    @Override
    public EnvType getEnvironmentType() {
        return EnvType.CLIENT;
    }

    @Override
    public String getRawGameVersion() {
        ensureLoaded();
        return gameVersion;
    }

    @Override
    public Object getGameInstance() {
        return gameInstance;
    }

    @Override
    public Path getGameDir() {
        return Paths.get(WebFileSystem.GAME_DIR);
    }

    @Override
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    @Override
    public Path getConfigDir() {
        Path p = getGameDir().resolve("config");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            // reported on first use
        }
        return p;
    }

    @Override
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return new String[0];
    }

    /** Everything is baked in intermediary names, the production runtime namespace. */
    static final class IntermediaryResolver implements MappingResolver {
        static final IntermediaryResolver INSTANCE = new IntermediaryResolver();

        @Override
        public Collection<String> getNamespaces() {
            return Collections.singletonList("intermediary");
        }

        @Override
        public String getCurrentRuntimeNamespace() {
            return "intermediary";
        }

        @Override
        public String mapClassName(String namespace, String className) {
            return className;
        }

        @Override
        public String unmapClassName(String targetNamespace, String className) {
            return className;
        }

        @Override
        public String mapFieldName(String namespace, String owner, String name, String descriptor) {
            return name;
        }

        @Override
        public String mapMethodName(String namespace, String owner, String name, String descriptor) {
            return name;
        }
    }

    static final class WebObjectShare implements ObjectShare {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, List<BiConsumer<String, Object>>> waiting = new HashMap<>();

        @Override
        public synchronized Object get(String key) {
            return values.get(key);
        }

        @Override
        public synchronized void whenAvailable(String key, BiConsumer<String, Object> consumer) {
            Object v = values.get(key);
            if (v != null) {
                consumer.accept(key, v);
            } else {
                waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer);
            }
        }

        @Override
        public synchronized Object put(String key, Object value) {
            Object old = values.put(key, value);
            List<BiConsumer<String, Object>> list = waiting.remove(key);
            if (list != null) {
                for (BiConsumer<String, Object> c : list) {
                    c.accept(key, value);
                }
            }
            return old;
        }

        @Override
        public synchronized Object putIfAbsent(String key, Object value) {
            Object old = values.get(key);
            return old != null ? old : put(key, value);
        }

        @Override
        public synchronized Object remove(String key) {
            return values.remove(key);
        }
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    static List<String> strings(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                list.add(x.getAsString());
            }
        }
        return list;
    }
}
