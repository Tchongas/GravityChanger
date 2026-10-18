package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.accessor.EntityAccessor;
import me.andrew.gravitychanger.util.RotationUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemUsageContext.class)
public abstract class ItemUsageContextMixin {
    @Shadow @Final protected PlayerEntity player;

    @Inject(method = "getPlayerYaw", at = @At("HEAD"), cancellable = true)
    private void gravitychanger$getPlayerYaw(CallbackInfoReturnable<Float> cir) {
        if (this.player == null) return;

        Direction gravityDirection = ((EntityAccessor) this.player).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return;
        cir.setReturnValue(RotationUtil.rotPlayerToWorld(this.player.getYaw(1.0F), this.player.getPitch(1.0F), gravityDirection).x);
    }
}
