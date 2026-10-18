package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.GravityChangerMod;
import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import me.andrew.gravitychanger.accessor.ServerPlayerEntityAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements RotatableEntityAccessor, ServerPlayerEntityAccessor {
    @Shadow public ServerPlayNetworkHandler networkHandler;

    private Direction gravitychanger$gravityDirection = Direction.DOWN;
    @Unique private boolean gravitychanger$initialSyncComplete;

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

        this.gravitychanger$sendGravityPacket(gravityDirection, initialGravity);

        Direction prevGravityDirection = this.gravitychanger$gravityDirection;
        this.gravitychanger$gravityDirection = gravityDirection;
        this.gravitychanger$onGravityChanged(prevGravityDirection, initialGravity);
    }

    @Override
    public void gravitychanger$onTrackedData(TrackedData<?> data) {

    }

    @Override
    public void gravitychanger$sendGravityPacket(Direction gravityDirection, boolean initialGravity) {
        ServerPlayerEntity gravityPlayer = (ServerPlayerEntity) (Object) this;
        if (gravityPlayer.getServer() == null) return;

        for (ServerPlayerEntity recipient : gravityPlayer.getServer().getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(gravityPlayer.getEntityId());
            buf.writeEnumConstant(gravityDirection);
            buf.writeBoolean(initialGravity);
            recipient.networkHandler.sendPacket(new CustomPayloadS2CPacket(GravityChangerMod.CHANNEL_GRAVITY, buf));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void gravitychanger$syncAfterTrackingStarts(CallbackInfo ci) {
        if (this.gravitychanger$initialSyncComplete) return;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getServer() == null || player.networkHandler == null) return;
        this.gravitychanger$initialSyncComplete = true;
        for (ServerPlayerEntity currentPlayer : player.getServer().getPlayerManager().getPlayerList()) {
            RotatableEntityAccessor gravity = (RotatableEntityAccessor) currentPlayer;
            Direction direction = gravity.gravitychanger$getGravityDirection();
            if (direction != Direction.DOWN) {
                ((ServerPlayerEntityAccessor) currentPlayer).gravitychanger$sendGravityPacket(direction, true);
            }
        }
    }

    @Inject(
            method = "teleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void inject_teleport_sendPacket_0(CallbackInfo ci) {
        Direction gravityDirection = this.gravitychanger$getGravityDirection();
        if(gravityDirection != Direction.DOWN && GravityChangerMod.config.resetGravityOnDimensionChange) {
            this.gravitychanger$setGravityDirection(Direction.DOWN, true);
        } else {
            this.gravitychanger$sendGravityPacket(gravityDirection, true);
        }
    }

    @Inject(
            method = "copyFrom",
            at = @At("TAIL")
    )
    private void inject_copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        if(GravityChangerMod.config.resetGravityOnRespawn) {
            ((RotatableEntityAccessor) oldPlayer).gravitychanger$setGravityDirection(Direction.DOWN, true);
        } else {
            this.gravitychanger$setGravityDirection(((RotatableEntityAccessor) oldPlayer).gravitychanger$getGravityDirection(), true);
        }
    }
}
