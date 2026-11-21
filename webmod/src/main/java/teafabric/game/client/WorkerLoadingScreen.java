package teafabric.game.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import teafabric.game.WorkerBridge;

/** While the worker loads a world: its progress; then the connection to it. */
public final class WorkerLoadingScreen extends Screen {
    private String text = "Starting the world";
    private int percent = -1;

    public WorkerLoadingScreen() {
        super(new TranslatableText("connect.joining"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        String json;
        while ((json = WorkerBridge.takeStatus()) != null) {
            JsonObject s = new JsonParser().parse(json).getAsJsonObject();
            switch (s.get("t").getAsString()) {
                case "loading":
                    text = s.has("text") ? s.get("text").getAsString() : text;
                    percent = s.has("percent") ? s.get("percent").getAsInt() : -1;
                    break;
                case "ready":
                    client.openScreen(new ConnectScreen(new SelectWorldScreen(new TitleScreen()), client,
                            WorkerWorlds.ADDRESS, 0));
                    return;
                case "error":
                    fail(client, s.has("text") ? s.get("text").getAsString() : "unknown error");
                    return;
                default:
                    break;
            }
        }
    }

    static void fail(MinecraftClient client, String message) {
        client.openScreen(new NoticeScreen(() -> client.openScreen(new SelectWorldScreen(new TitleScreen())),
                new TranslatableText("selectWorld.futureworld.error.title"), new LiteralText(message)));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 30, 0xFFFFFF);
        Text line = new LiteralText(percent >= 0 ? text + ": " + percent + "%" : text + "...");
        drawCenteredText(matrices, textRenderer, line, width / 2, height / 2 - 10, 0xA0A0A0);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
