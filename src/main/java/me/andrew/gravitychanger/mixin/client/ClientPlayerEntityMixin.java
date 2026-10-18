package me.andrew.gravitychanger.mixin.client;

import com.mojang.authlib.GameProfile;
import me.andrew.gravitychanger.accessor.EntityAccessor;
import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity implements RotatableEntityAccessor {
    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    private Direction gravitychanger$gravityDirection = Direction.DOWN;

    @Override
    public Direction gravitychanger$getGravityDirection() {
        if(this.gravitychanger$gravityDirection == null) {
            return Direction.DOWN;
        }

        return this.gravitychanger$gravityDirection;
    }

    @Override
    public void gravitychanger$setGravityDirection(Direction gravityDirection, boolean initialGravity) {
        if(this.gravitychanger$gravityDirection == gravityDirection) return;

        Direction prevGravityDirection = this.gravitychanger$gravityDirection;
        this.gravitychanger$gravityDirection = gravityDirection;
        this.gravitychanger$onGravityChanged(prevGravityDirection, initialGravity);
    }

    @Override
    public void gravitychanger$onTrackedData(TrackedData<?> data) {
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void gravitychanger$disableVanillaPushOut(double x, double y, double z, CallbackInfo ci) {
        if (((EntityAccessor) this).gravitychanger$getAppliedGravityDirection() != Direction.DOWN) {
            ci.cancel();
        }
    }
}
