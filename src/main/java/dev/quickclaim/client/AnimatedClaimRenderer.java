package dev.quickclaim.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.quickclaim.ClaimBlock;
import dev.quickclaim.QuickClaim;
import dev.quickclaim.compat.AnimatedClaimBlock;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

final class AnimatedClaimRenderer implements BlockEntityRenderer<ClaimBlock.Entity> {
    private final GeoBlockRenderer<AnimatedClaimBlock> renderer =
            new GeoBlockRenderer<>(new Model());

    @Override
    public void render(
            ClaimBlock.Entity entity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        if (entity instanceof AnimatedClaimBlock animated) {
            renderer.render(animated, partialTick, pose, buffers, light, overlay);
        }
    }

    private static final class Model extends GeoModel<AnimatedClaimBlock> {
        private static final ResourceLocation GEOMETRY =
                ResourceLocation.fromNamespaceAndPath(QuickClaim.ID, "geo/claim_block.geo.json");
        private static final ResourceLocation TEXTURE =
                ResourceLocation.fromNamespaceAndPath(
                        QuickClaim.ID, "textures/block/claim_block.png");
        private static final ResourceLocation ANIMATION =
                ResourceLocation.fromNamespaceAndPath(
                        QuickClaim.ID, "animations/claim_block.animation.json");

        @Override
        public ResourceLocation getModelResource(AnimatedClaimBlock entity) {
            return GEOMETRY;
        }

        @Override
        public ResourceLocation getTextureResource(AnimatedClaimBlock entity) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(AnimatedClaimBlock entity) {
            return ANIMATION;
        }
    }
}
