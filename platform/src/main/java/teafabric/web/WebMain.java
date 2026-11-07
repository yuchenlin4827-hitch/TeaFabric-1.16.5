package teafabric.web;

/** Browser entry point. */
public final class WebMain {
    private WebMain() {
    }

    public static void main(String[] args) {
        net.minecraft.client.main.Main.main(new String[] {
            "--username", "Player", "--version", "1.16.5", "--accessToken", "0",
            "--gameDir", "/minecraft", "--assetsDir", "/minecraft/assets", "--assetIndex", "1.16"
        });
    }
}
