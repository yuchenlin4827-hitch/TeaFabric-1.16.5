package teafabric.web.fabric;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

final class WebModContainer implements ModContainer {
    private final WebModMetadata metadata;
    private final Path root;
    private final WebFabricLoader loader;

    WebModContainer(WebModMetadata metadata, Path root, WebFabricLoader loader) {
        this.metadata = metadata;
        this.root = root;
        this.loader = loader;
    }

    @Override
    public ModMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<Path> getRootPaths() {
        return Collections.singletonList(root);
    }

    @Override
    public ModOrigin getOrigin() {
        return new ModOrigin() {
            @Override
            public Kind getKind() {
                return metadata.parent != null ? Kind.NESTED : Kind.PATH;
            }

            @Override
            public List<Path> getPaths() {
                return getRootPaths();
            }

            @Override
            public String getParentModId() {
                return metadata.parent;
            }

            @Override
            public String getParentSubLocation() {
                return metadata.parent == null ? null : "baked";
            }

            @Override
            public String toString() {
                return "baked:" + metadata.getId();
            }
        };
    }

    @Override
    public Optional<ModContainer> getContainingMod() {
        return metadata.parent == null ? Optional.empty() : loader.getModContainer(metadata.parent);
    }

    @Override
    public Collection<ModContainer> getContainedMods() {
        List<ModContainer> list = new ArrayList<>();
        for (WebModContainer m : loader.modsInternal()) {
            if (metadata.getId().equals(m.metadata.parent)) {
                list.add(m);
            }
        }
        return list;
    }

    @Override
    @Deprecated
    public Path getRootPath() {
        return root;
    }

    @Override
    @Deprecated
    public Path getPath(String file) {
        return root.resolve(file.startsWith("/") ? file.substring(1) : file);
    }

    @Override
    public String toString() {
        return metadata.getId() + " " + metadata.getVersion().getFriendlyString();
    }
}
