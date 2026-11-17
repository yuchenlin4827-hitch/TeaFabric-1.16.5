package teafabric.game.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import teafabric.game.client.WorkerWorlds;

/** A world in the worker is singleplayer for the game menu: "Save and Quit to Title", back to the title. */
@Mixin(GameMenuScreen.class)
abstract class GameMenuScreenMixin {
    @Redirect(method = {"initWidgets", "method_19836"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isInSingleplayer()Z"))
    private boolean teafabric$workerIsSingleplayer(MinecraftClient client) {
        return client.isInSingleplayer() || WorkerWorlds.active();
    }
}
