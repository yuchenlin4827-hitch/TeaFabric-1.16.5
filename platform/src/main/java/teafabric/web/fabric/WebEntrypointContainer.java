package teafabric.web.fabric;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

final class WebEntrypointContainer<T> implements EntrypointContainer<T> {
    private final T entrypoint;
    private final ModContainer provider;

    WebEntrypointContainer(T entrypoint, ModContainer provider) {
        this.entrypoint = entrypoint;
        this.provider = provider;
    }

    @Override
    public T getEntrypoint() {
        return entrypoint;
    }

    @Override
    public ModContainer getProvider() {
        return provider;
    }
}
