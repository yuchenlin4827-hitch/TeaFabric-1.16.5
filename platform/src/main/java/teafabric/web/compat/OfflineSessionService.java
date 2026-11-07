package teafabric.web.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilSocialInteractionsService;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

/**
 * Offline-mode session service. The browser build has no RSA/JCA implementation to verify Mojang's
 * signed profile properties and cannot call the session servers (CORS), so it behaves like an
 * offline-mode client: no skins from the session server, no online-mode server joins.
 */
public final class OfflineSessionService implements MinecraftSessionService {
    @Override
    public void joinServer(GameProfile profile, String authenticationToken, String serverId)
            throws AuthenticationException {
        throw new AuthenticationException("Online-mode servers are not supported in the browser");
    }

    @Override
    public GameProfile hasJoinedServer(GameProfile user, String serverId, InetAddress address) {
        return user;
    }

    @Override
    public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(GameProfile profile,
            boolean requireSecure) {
        return Collections.emptyMap();
    }

    @Override
    public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
        return profile;
    }

    // ---- redirect targets (YggdrasilAuthenticationService call sites in Minecraft)

    public static MinecraftSessionService createMinecraftSessionService(YggdrasilAuthenticationService service) {
        return new OfflineSessionService();
    }

    public static YggdrasilSocialInteractionsService createSocialInteractionsService(
            YggdrasilAuthenticationService service, String accessToken) throws AuthenticationException {
        // Minecraft falls back to OfflineSocialInteractions on this exception.
        throw new AuthenticationException("Offline (browser) session");
    }
}
