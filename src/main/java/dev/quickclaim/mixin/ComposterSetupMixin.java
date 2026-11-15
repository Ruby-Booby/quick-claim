package dev.quickclaim.mixin;

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = {
            "net.mcreator.abyssaldecor.init.AbyssalDecorModCompostableItems",
            "net.mcreator.borninchaosv.init.BornInChaosV1ModCompostableItems",
            "net.mcreator.luminousnether.init.LuminousNetherModCompostableItems",
            "net.mcreator.unusualend.init.UnusualendModCompostableItems"
        },
        remap = false)
public abstract class ComposterSetupMixin {

    @Unique
    private static final ThreadLocal<Boolean> quickclaim$queuedSetup =
            ThreadLocal.withInitial(() -> false);

    @Shadow(remap = false)
    public static void addComposterItems(FMLCommonSetupEvent event) {
        throw new AssertionError();
    }

    @Inject(method = "addComposterItems", at = @At("HEAD"), cancellable = true, remap = false)
    private static void queueRegistration(FMLCommonSetupEvent event, CallbackInfo callback) {
        if (quickclaim$queuedSetup.get()) return;
        event.enqueueWork(
                () -> {
                    quickclaim$queuedSetup.set(true);
                    try {
                        addComposterItems(event);
                    } finally {
                        quickclaim$queuedSetup.remove();
                    }
                });
        callback.cancel();
    }
}
