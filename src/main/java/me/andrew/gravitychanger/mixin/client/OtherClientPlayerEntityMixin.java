package me.andrew.gravitychanger.mixin.client;

import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(OtherClientPlayerEntity.class)
public abstract class OtherClientPlayerEntityMixin implements RotatableEntityAccessor {
    private Direction gravitychanger$gravityDirection = Direction.DOWN;

    @Override
    public Direction gravitychanger$getGravityDirection() {
        return this.gravitychanger$gravityDirection;
    }

    @Override
    public void gravitychanger$setGravityDirection(Direction gravityDirection, boolean initialGravity) {
        if (this.gravitychanger$gravityDirection == gravityDirection) return;

        Direction previousGravity = this.gravitychanger$gravityDirection;
        this.gravitychanger$gravityDirection = gravityDirection;
        this.gravitychanger$onGravityChanged(previousGravity, initialGravity);
    }
}
