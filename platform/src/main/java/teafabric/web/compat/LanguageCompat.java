package teafabric.web.compat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.class_2477;
import net.minecraft.class_310;
import net.minecraft.class_426;
import teafabric.web.log.LogSink;
import teafabric.web.vfs.WebAssets;

/**
 * Switching languages in the browser.
 *
 * <p>Translations other than English are not in assets.zip (47 MB in all): each is a placeholder, and
 * the chosen language's file is fetched from {@code lang/<hash>.json} when it is loaded. And choosing a
 * language reloads only the translations, not every resource: vanilla's full reload (textures, models,
 * sounds) takes a long time in a page, on slow devices much longer.
 */
public final class LanguageCompat {
    private LanguageCompat() {
    }

    /** Language.load(InputStream, BiConsumer) (redirect target): resolves placeholders first. */
    public static void method_29425(InputStream in, BiConsumer<String, String> consumer) {
        byte[] data;
        try {
            data = in.readAllBytes();
        } catch (IOException e) {
            LogSink.log(2, "WARN", "Language", "Cannot read translations", e);
            data = "{}".getBytes(StandardCharsets.UTF_8);
        }
        String hash = WebAssets.placeholderHash(data);
        if (hash != null) {
            byte[] real = WebAssets.fetch("lang/" + hash + ".json");
            if (real == null) {
                LogSink.log(2, "WARN", "Language", "Cannot download translations lang/" + hash + ".json", null);
                real = "{}".getBytes(StandardCharsets.UTF_8);
            }
            data = real;
        }
        class_2477.method_29425(new ByteArrayInputStream(data), consumer);
    }

    /**
     * MinecraftClient.reloadResources() (redirect target). Called from the language screen, only the
     * language manager reloads; any other caller gets the full reload.
     */
    public static CompletableFuture<Void> method_1521(class_310 client) {
        if (client.field_1755 instanceof class_426) {
            client.method_1526().method_14491(client.method_1478());
            return CompletableFuture.completedFuture(null);
        }
        return client.method_1521();
    }
}
