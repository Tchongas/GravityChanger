package me.andrew.gravitychanger.mixin.client;

import me.andrew.gravitychanger.GravityChangerMod;
import me.andrew.gravitychanger.accessor.EntityAccessor;
import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import me.andrew.gravitychanger.util.RotationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Unique private static final Logger gravitychanger$LOGGER = LogManager.getLogger("GravityChanger/RemotePlayers");
    @Unique private final Map<Integer, GravityUpdate> gravitychanger$pendingGravity = new HashMap<>();

    @Shadow @Final private MinecraftClient client;
    @Shadow private ClientWorld world;

    @Inject(method = "onPlayerSpawn", at = @At("TAIL"))
    private void gravitychanger$logPlayerSpawn(PlayerSpawnS2CPacket packet, CallbackInfo ci) {
        Object entity = this.world == null ? null : this.world.getEntityById(packet.getId());
        GravityUpdate update = this.gravitychanger$pendingGravity.remove(packet.getId());
        if (entity instanceof RotatableEntityAccessor rotatableEntity && update != null) {
            rotatableEntity.gravitychanger$setGravityDirection(update.direction(), update.initialGravity());
        }
        gravitychanger$LOGGER.warn("Remote player spawn id={} uuid={} pos=({}, {}, {}) entity={}", packet.getId(), packet.getPlayerUuid(), packet.getX(), packet.getY(), packet.getZ(), entity);
    }

    @Inject(method = "onEntitiesDestroy", at = @At("HEAD"))
    private void gravitychanger$logEntityRemoval(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        for (int entityId : packet.getEntityIds()) {
            Object entity = this.world == null ? null : this.world.getEntityById(entityId);
            if (entity instanceof PlayerEntity) {
                gravitychanger$LOGGER.warn("Remote player removal id={} entity={}", entityId, entity);
            }
        }
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void gravitychanger$receiveGravity(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (!GravityChangerMod.CHANNEL_GRAVITY.equals(packet.getChannel())) return;

        PacketByteBuf buf = packet.getData();
        int entityId = buf.readInt();
        Direction gravityDirection = buf.readEnumConstant(Direction.class);
        boolean initialGravity = buf.readBoolean();
        this.client.execute(() -> {
            Object entity = this.world == null ? null : this.world.getEntityById(entityId);
            if (entity instanceof RotatableEntityAccessor rotatableEntity) {
                rotatableEntity.gravitychanger$setGravityDirection(gravityDirection, initialGravity);
            } else {
                this.gravitychanger$pendingGravity.put(entityId, new GravityUpdate(gravityDirection, initialGravity));
            }
        });
        ci.cancel();
    }

    @Redirect(
            method = "onGameStateChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getEyeY()D",
                    ordinal = 0
            )
    )
    private double redirect_onGameStateChange_getEyeY_0(PlayerEntity playerEntity) {
        Direction gravityDirection = ((EntityAccessor) playerEntity).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return playerEntity.getEyeY();
        }

        return playerEntity.getCameraPosVec(1.0F).y;
    }

    @Redirect(
            method = "onGameStateChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getX()D",
                    ordinal = 0
            )
    )
    private double redirect_onGameStateChange_getX_0(PlayerEntity playerEntity) {
        Direction gravityDirection = ((EntityAccessor) playerEntity).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return playerEntity.getX();
        }

        return playerEntity.getCameraPosVec(1.0F).x;
    }

    @Redirect(
            method = "onGameStateChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getZ()D",
                    ordinal = 0
            )
    )
    private double redirect_onGameStateChange_getZ_0(PlayerEntity playerEntity) {
        Direction gravityDirection = ((EntityAccessor) playerEntity).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return playerEntity.getZ();
        }

        return playerEntity.getCameraPosVec(1.0F).z;
    }

    @Redirect(
            method = "onExplosion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;add(DDD)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_onExplosion_add_0(Vec3d vec3d, double x, double y, double z) {
        Direction gravityDirection = ((EntityAccessor) this.client.player).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d.add(x, y, z);
        }

        return vec3d.add(RotationUtil.vecWorldToPlayer(x, y, z, gravityDirection));
    }

    @Unique
    private record GravityUpdate(Direction direction, boolean initialGravity) {
    }
}
