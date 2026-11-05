package dev.quickclaim.mixin;

import dev.quickclaim.client.AtlasOverlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "pepjebs.mapatlases.client.AbstractAtlasWidget", remap = false)
public abstract class AtlasWidgetMixin {
    @Shadow protected double currentXCenter;
    @Shadow protected double currentZCenter;
    @Shadow protected int mapBlocksSize;
    @Shadow protected boolean rotatesWithPlayer;

    @Shadow
    protected abstract void applyScissors(GuiGraphics graphics, int x, int y, int x1, int y1);

    @Inject(method = "drawAtlas", at = @At("RETURN"), remap = false)
    private void drawClaims(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            Player player,
            float zoom,
            boolean borders,
            @Coerce Object type,
            int light,
            MapItemSavedData selected,
            CallbackInfo callback) {
        applyScissors(graphics, x, y, x + width, y + height);
        try {
            AtlasOverlay.draw(
                    this,
                    graphics,
                    x,
                    y,
                    width,
                    height,
                    currentXCenter,
                    currentZCenter,
                    width / (zoom * mapBlocksSize),
                    rotatesWithPlayer ? 180 - player.getYRot() : 0);
        } finally {
            graphics.disableScissor();
        }
    }
}
