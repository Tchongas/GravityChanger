package me.andrew.gravitychanger.mixin.client;

import me.andrew.gravitychanger.accessor.EntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true)
    private static void gravitychanger$disableInWallOverlay(PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        if (((EntityAccessor) player).gravitychanger$getAppliedGravityDirection() != Direction.DOWN) {
            cir.setReturnValue(null);
        }
    }
}
