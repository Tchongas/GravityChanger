package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.GravityChangerMod;
import me.andrew.gravitychanger.accessor.EntityAccessor;
import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import me.andrew.gravitychanger.util.RotationUtil;
import net.minecraft.entity.*;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements EntityAccessor, RotatableEntityAccessor {
    @Shadow public abstract EntityDimensions getDimensions(EntityPose pose);

    @Shadow protected abstract boolean clipAtLedge();

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public Direction gravitychanger$getAppliedGravityDirection() {
        Entity vehicle = this.getVehicle();
        if(vehicle != null) {
            return ((EntityAccessor) vehicle).gravitychanger$getAppliedGravityDirection();
        }

        return this.gravitychanger$getGravityDirection();
    }

    @Override
    public void gravitychanger$onGravityChanged(Direction prevGravityDirection, boolean initialGravity) {
        Direction gravityDirection = this.gravitychanger$getGravityDirection();

        this.fallDistance = 0;

        this.calculateDimensions();

        if(!initialGravity) {
            // Adjust position to avoid suffocation in blocks when changing gravity
            EntityDimensions dimensions = this.getDimensions(this.getPose());
            Direction relativeDirection = RotationUtil.dirWorldToPlayer(gravityDirection, prevGravityDirection);
            Vec3d relativePosOffset = switch(relativeDirection) {
                case DOWN -> Vec3d.ZERO;
                case UP -> new Vec3d(0.0D, dimensions.height - 1.0E-6D, 0.0D);
                default -> Vec3d.of(relativeDirection.getVector()).multiply(dimensions.width / 2 - (gravityDirection.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0E-6D : 0.0D)).add(0.0D, dimensions.width / 2 - (prevGravityDirection.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0E-6D : 0.0D), 0.0D);
            };
            Vec3d newPosition = this.getPos().add(RotationUtil.vecPlayerToWorld(relativePosOffset, prevGravityDirection));
            this.updatePosition(newPosition.x, newPosition.y, newPosition.z);
            this.calculateDimensions();

            if((Object) this instanceof ServerPlayerEntity serverPlayerEntity) {
                serverPlayerEntity.networkHandler.syncWithPlayerPosition();
            }

            // Keep world velocity when changing gravity
            this.setVelocity(RotationUtil.vecWorldToPlayer(RotationUtil.vecPlayerToWorld(this.getVelocity(), prevGravityDirection), gravityDirection));

            // Keep world looking direction when changing gravity
            if(GravityChangerMod.config.keepWorldLook) {
                Vec2f worldAngles = RotationUtil.rotPlayerToWorld(this.getYaw(1.0F), this.getPitch(1.0F), prevGravityDirection);
                Vec2f newViewAngles = RotationUtil.rotWorldToPlayer(worldAngles.x, worldAngles.y, gravityDirection);
                this.yaw = newViewAngles.x;
                this.pitch = newViewAngles.y;
            }
        }
    }

    @Override
    public Direction gravitychanger$getTrackedGravityDirection() {
        return Direction.DOWN;
    }

    @Override
    public void gravitychanger$setTrackedGravityDirection(Direction gravityDirection) {
    }

    @Override
    public void gravitychanger$onTrackedData(TrackedData<?> data) {
    }

    @Inject(
            method = "readCustomDataFromTag",
            at = @At("RETURN")
    )
    private void inject_readCustomDataFromTag(CompoundTag nbt, CallbackInfo ci) {
        if(nbt.contains("GravityDirection", 3)) {
            Direction gravityDirection = Direction.byId(nbt.getInt("GravityDirection"));
            this.gravitychanger$setGravityDirection(gravityDirection, true);
        }
    }

    @Inject(
            method = "writeCustomDataToTag",
            at = @At("RETURN")
    )
    private void inject_writeCustomDataToTag(CompoundTag nbt, CallbackInfo ci) {
        nbt.putInt("GravityDirection", this.gravitychanger$getGravityDirection().getId());
    }

    @Redirect(
            method = "travel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            )
    )
    private Vec3d redirect_travel_getRotationVector_0(PlayerEntity playerEntity) {
        Direction gravityDirection = ((EntityAccessor) playerEntity).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return playerEntity.getRotationVector();
        }

        return RotationUtil.vecWorldToPlayer(playerEntity.getRotationVector(), gravityDirection);
    }

    @Redirect(
            method = "travel",
            at = @At(
                    value = "NEW",
                    target = "(DDD)Lnet/minecraft/util/math/BlockPos;",
                    ordinal = 0
            )
    )
    private BlockPos redirect_travel_new_0(double x, double y, double z) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return new BlockPos(x, y, z);
        }

        return new BlockPos(this.getPos().add(RotationUtil.vecPlayerToWorld(0.0D, 1.0D - 0.1D, 0.0D, gravityDirection)));
    }

    @Redirect(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/entity/ItemEntity",
                    ordinal = 0
            )
    )
    private ItemEntity redirect_dropItem_new_0(World world, double x, double y, double z, ItemStack stack) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return new ItemEntity(world, x, y, z, stack);
        }

        Vec3d vec3d = this.getCameraPosVec(1.0F).subtract(RotationUtil.vecPlayerToWorld(0.0D, 0.30000001192092896D, 0.0D, gravityDirection));

        return new ItemEntity(world, vec3d.x, vec3d.y, vec3d.z, stack);
    }

    @Redirect(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ItemEntity;setVelocity(DDD)V"
            )
    )
    private void redirect_dropItem_setVelocity(ItemEntity itemEntity, double x, double y, double z) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            itemEntity.setVelocity(x, y, z);
            return;
        }

        itemEntity.setVelocity(RotationUtil.vecPlayerToWorld(x, y, z, gravityDirection));
    }

    @Inject(
            method = "adjustMovementForSneaking",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gravitychanger$adjustMovementForSneaking(Vec3d movement, MovementType type, CallbackInfoReturnable<Vec3d> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return;
        if ((type != MovementType.SELF && type != MovementType.PLAYER) || !this.onGround || !this.clipAtLedge()) {
            cir.setReturnValue(movement);
            return;
        }

        Vec3d playerMovement = RotationUtil.vecWorldToPlayer(movement, gravityDirection);
        double x = playerMovement.x;
        double z = playerMovement.z;
        while (x != 0.0D && this.world.doesNotCollide(this, this.getBoundingBox().offset(RotationUtil.vecPlayerToWorld(x, -this.stepHeight, 0.0D, gravityDirection)))) {
            x = gravitychanger$reduceSneakingMovement(x);
        }
        while (z != 0.0D && this.world.doesNotCollide(this, this.getBoundingBox().offset(RotationUtil.vecPlayerToWorld(0.0D, -this.stepHeight, z, gravityDirection)))) {
            z = gravitychanger$reduceSneakingMovement(z);
        }
        while (x != 0.0D && z != 0.0D && this.world.doesNotCollide(this, this.getBoundingBox().offset(RotationUtil.vecPlayerToWorld(x, -this.stepHeight, z, gravityDirection)))) {
            x = gravitychanger$reduceSneakingMovement(x);
            z = gravitychanger$reduceSneakingMovement(z);
        }
        cir.setReturnValue(RotationUtil.vecPlayerToWorld(x, playerMovement.y, z, gravityDirection));
    }

    private static double gravitychanger$reduceSneakingMovement(double value) {
        if (value < 0.05D && value >= -0.05D) return 0.0D;
        return value > 0.0D ? value - 0.05D : value + 0.05D;
    }

    @ModifyArgs(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;",
                    ordinal = 0
            )
    )
    private void modify_tickMovement_expand_0(Args args) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        Vec3d vec3d = RotationUtil.maskPlayerToWorld(args.get(0), args.get(1), args.get(2), gravityDirection);
        args.set(0, vec3d.x);
        args.set(1, vec3d.y);
        args.set(2, vec3d.z);
    }

    @ModifyArgs(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;",
                    ordinal = 1
            )
    )
    private void modify_tickMovement_expand_1(Args args) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        Vec3d vec3d = RotationUtil.maskPlayerToWorld(args.get(0), args.get(1), args.get(2), gravityDirection);
        args.set(0, vec3d.x);
        args.set(1, vec3d.y);
        args.set(2, vec3d.z);
    }
}
