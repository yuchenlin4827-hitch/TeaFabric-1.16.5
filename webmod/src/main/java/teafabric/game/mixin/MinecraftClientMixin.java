package teafabric.game.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import teafabric.game.client.WorkerWorlds;

/** Singleplayer worlds start in the Web Worker instead of an integrated server on the page's thread. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "startIntegratedServer(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void teafabric$playInWorker(String dir, CallbackInfo ci) {
        if (WorkerWorlds.enabled()) {
            WorkerWorlds.play((MinecraftClient) (Object) this, dir);
            ci.cancel();
        }
    }

    @Inject(method = "createWorld", at = @At("HEAD"), cancellable = true)
    private void teafabric$createInWorker(String dir, LevelInfo info, DynamicRegistryManager.Impl registry,
            GeneratorOptions generator, CallbackInfo ci) {
        if (WorkerWorlds.enabled()) {
            WorkerWorlds.create((MinecraftClient) (Object) this, dir, info, registry, generator);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void teafabric$syncWorker(CallbackInfo ci) {
        WorkerWorlds.tick((MinecraftClient) (Object) this);
    }
}
