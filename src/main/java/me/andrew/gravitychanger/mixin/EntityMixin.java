package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.GravityChangerMod;
import me.andrew.gravitychanger.accessor.EntityAccessor;
import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import me.andrew.gravitychanger.util.RotationUtil;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityAccessor {
    @Shadow private Vec3d pos;

    @Shadow private EntityDimensions dimensions;

    @Shadow private float standingEyeHeight;

    @Shadow public double prevX;

    @Shadow public double prevY;

    @Shadow public double prevZ;

    @Shadow public abstract double getX();

    @Shadow public abstract Vec3d getCameraPosVec(float tickDelta);

    @Shadow public abstract double getY();

    @Shadow public abstract double getZ();

    @Shadow public abstract void setPos(double x, double y, double z);

    @Shadow public World world;

    @Shadow protected boolean submergedInWater;

    @Shadow public abstract boolean isSubmergedIn(Tag<Fluid> fluidTag);

    @Shadow public boolean noClip;

    @Shadow public abstract Vec3d getVelocity();

    @Shadow public abstract boolean hasPassengers();

    @Shadow public abstract Box getBoundingBox();

    @Shadow public abstract Vec3d getPos();

    @Shadow @Final protected Random random;

    @Shadow public abstract boolean isConnectedThroughVehicle(Entity entity);

    @Shadow public abstract void addVelocity(double deltaX, double deltaY, double deltaZ);

    @Override
    public Direction gravitychanger$getAppliedGravityDirection() {
        return Direction.DOWN;
    }

    @Inject(
            method = "onTrackedDataSet",
            at = @At("RETURN")
    )
    private void inject_onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        if(this instanceof RotatableEntityAccessor rotatableEntityAccessor) {
            rotatableEntityAccessor.gravitychanger$onTrackedData(data);
        }
    }

    @Inject(
            method = "calculateBoundsForPose",
            at = @At("RETURN"),
            cancellable = true
    )
    private void gravitychanger$calculateBoundsForPose(EntityPose pose, CallbackInfoReturnable<Box> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return;

        Box playerBox = cir.getReturnValue().offset(this.pos.negate());
        cir.setReturnValue(RotationUtil.boxPlayerToWorld(playerBox, gravityDirection).offset(this.pos));
    }

    @Inject(method = "moveToBoundingBoxCenter", at = @At("HEAD"), cancellable = true)
    private void gravitychanger$moveToGravityCenter(CallbackInfo ci) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return;

        Box box = this.getBoundingBox();
        double x = (box.minX + box.maxX) / 2.0D;
        double y = (box.minY + box.maxY) / 2.0D;
        double z = (box.minZ + box.maxZ) / 2.0D;
        switch (gravityDirection) {
            case UP -> y = box.maxY;
            case NORTH -> z = box.minZ;
            case SOUTH -> z = box.maxZ;
            case WEST -> x = box.minX;
            case EAST -> x = box.maxX;
        }
        this.setPos(x, y, z);
        ci.cancel();
    }

    @ModifyVariable(method = "setBoundingBox", at = @At("HEAD"), argsOnly = true)
    private Box gravitychanger$rejectUprightBoundingBox(Box box) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return box;

        double height = this.dimensions.height;
        double axisLength = gravityDirection.getAxis().choose(box.getXLength(), box.getYLength(), box.getZLength());
        boolean wrongShape = Math.abs(axisLength - height) > 1.0E-5D;
        if (gravityDirection == Direction.UP) {
            wrongShape = Math.abs(box.minY - this.pos.y) < 1.0E-5D && Math.abs(box.maxY - this.pos.y) > 1.0E-5D;
        }
        return wrongShape ? this.gravitychanger$createBoundingBox(gravityDirection) : box;
    }

    @Inject(method = "calculateDimensions", at = @At("RETURN"))
    private void gravitychanger$rotateBoundingBox(CallbackInfo ci) {
        this.gravitychanger$updateBoundingBox();
    }

    private void gravitychanger$updateBoundingBox() {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN) return;

        ((Entity) (Object) this).setBoundingBox(this.gravitychanger$createBoundingBox(gravityDirection));
    }

    private Box gravitychanger$createBoundingBox(Direction gravityDirection) {
        double halfWidth = this.dimensions.width / 2.0D;
        Box playerBox = new Box(-halfWidth, 0.0D, -halfWidth, halfWidth, this.dimensions.height, halfWidth);
        return RotationUtil.boxPlayerToWorld(playerBox, gravityDirection).offset(this.pos);
    }

    @Inject(
            method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void gravitychanger$adjustMovementForCollisions(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if (gravityDirection == Direction.DOWN || movement.lengthSquared() == 0.0D) return;

        this.gravitychanger$updateBoundingBox();
        Entity entity = (Entity) (Object) this;
        Box box = this.getBoundingBox();
        List<VoxelShape> collisions = this.world.getCollisions(entity, box.stretch(movement), other -> true).toList();
        Vec3d playerMovement = RotationUtil.vecWorldToPlayer(movement, gravityDirection);
        double x = playerMovement.x;
        double y = playerMovement.y;
        double z = playerMovement.z;
        Direction directionX = RotationUtil.dirPlayerToWorld(Direction.EAST, gravityDirection);
        Direction directionY = RotationUtil.dirPlayerToWorld(Direction.UP, gravityDirection);
        Direction directionZ = RotationUtil.dirPlayerToWorld(Direction.SOUTH, gravityDirection);

        if (y != 0.0D) {
            y = VoxelShapes.calculateMaxOffset(directionY.getAxis(), box, collisions.stream(), y * directionY.getDirection().offset()) * directionY.getDirection().offset();
            if (y != 0.0D) box = box.offset(RotationUtil.vecPlayerToWorld(0.0D, y, 0.0D, gravityDirection));
        }

        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = VoxelShapes.calculateMaxOffset(directionZ.getAxis(), box, collisions.stream(), z * directionZ.getDirection().offset()) * directionZ.getDirection().offset();
            if (z != 0.0D) box = box.offset(RotationUtil.vecPlayerToWorld(0.0D, 0.0D, z, gravityDirection));
        }
        if (x != 0.0D) {
            x = VoxelShapes.calculateMaxOffset(directionX.getAxis(), box, collisions.stream(), x * directionX.getDirection().offset()) * directionX.getDirection().offset();
            if (!zFirst && x != 0.0D) box = box.offset(RotationUtil.vecPlayerToWorld(x, 0.0D, 0.0D, gravityDirection));
        }
        if (!zFirst && z != 0.0D) {
            z = VoxelShapes.calculateMaxOffset(directionZ.getAxis(), box, collisions.stream(), z * directionZ.getDirection().offset()) * directionZ.getDirection().offset();
        }

        cir.setReturnValue(RotationUtil.vecPlayerToWorld(x, y, z, gravityDirection));
    }

    @Inject(
            method = "getRotationVector(FF)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void inject_getRotationVector(CallbackInfoReturnable<Vec3d> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        cir.setReturnValue(RotationUtil.vecPlayerToWorld(cir.getReturnValue(), gravityDirection));
    }

    @Inject(
            method = "getVelocityAffectingPos",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_getVelocityAffectingPos(CallbackInfoReturnable<BlockPos> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        cir.setReturnValue(new BlockPos(this.pos.add(Vec3d.of(gravityDirection.getVector()).multiply(0.5000001D))));
    }

    @Inject(
            method = "getCameraPosVec",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_getCameraPosVec(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        Vec3d vec3d = RotationUtil.vecPlayerToWorld(0.0D, this.standingEyeHeight, 0.0D, gravityDirection);

        double d = MathHelper.lerp((double)tickDelta, this.prevX, this.getX()) + vec3d.x;
        double e = MathHelper.lerp((double)tickDelta, this.prevY, this.getY()) + vec3d.y;
        double f = MathHelper.lerp((double)tickDelta, this.prevZ, this.getZ()) + vec3d.z;
        cir.setReturnValue(new Vec3d(d, e, f));
    }

    @Inject(
            method = "getBrightnessAtEyes",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_getBrightnessAtEyes(CallbackInfoReturnable<Float> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        cir.setReturnValue(this.world.getBrightness(new BlockPos(this.getCameraPosVec(1.0F))));
    }

    @ModifyVariable(
            method = "move",
            at = @At("HEAD"),
            ordinal = 0
    )
    private Vec3d modify_move_Vec3d_0_0(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
    }

    @ModifyArg(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;multiply(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            ),
            index = 0
    )
    private Vec3d modify_move_multiply_0(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.maskPlayerToWorld(vec3d, gravityDirection);
    }

    @ModifyVariable(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 0
            ),
            ordinal = 0
    )
    private Vec3d modify_move_Vec3d_0_1(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }

    @ModifyVariable(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 0
            ),
            ordinal = 1
    )
    private Vec3d modify_move_Vec3d_1(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }

    @Inject(
            method = "getLandingPos",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_getLandingPos(CallbackInfoReturnable<BlockPos> cir) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        BlockPos blockPos = new BlockPos(RotationUtil.vecPlayerToWorld(0.0D, -0.20000000298023224D, 0.0D, gravityDirection).add(this.pos));
        // Probably not needed since these blocks only extend up
//        if (this.world.getBlockState(blockPos).isAir()) {
//            BlockPos blockPos2 = blockPos.offset(gravityDirection);
//            BlockState blockState = this.world.getBlockState(blockPos2);
//            if (blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS) || blockState.getBlock() instanceof FenceGateBlock) {
//                cir.setReturnValue(blockPos2);
//                return;
//            }
//        }

        cir.setReturnValue(blockPos);
    }

    @Inject(
            method = "spawnSprintingParticles",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_spawnSprintingParticles(CallbackInfo ci) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) return;

        ci.cancel();

        Vec3d floorPos = this.getPos().subtract(RotationUtil.vecPlayerToWorld(0.0D, 0.20000000298023224D, 0.0D, gravityDirection));

        BlockPos blockPos = new BlockPos(floorPos);
        BlockState blockState = this.world.getBlockState(blockPos);
        if (blockState.getRenderType() != BlockRenderType.INVISIBLE) {
            Vec3d particlePos = this.getPos().add(RotationUtil.vecPlayerToWorld((this.random.nextDouble() - 0.5D) * (double)this.dimensions.width, 0.1D, (this.random.nextDouble() - 0.5D) * (double)this.dimensions.width, gravityDirection));
            Vec3d playerVelocity = this.getVelocity();
            Vec3d particleVelocity = RotationUtil.vecPlayerToWorld(playerVelocity.x * -4.0D, 1.5D, playerVelocity.z * -4.0D, gravityDirection);
            this.world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState), particlePos.x, particlePos.y, particlePos.z, particleVelocity.x, particleVelocity.y, particleVelocity.z);
        }
    }

    @ModifyVariable(
            method = "updateMovementInFluid",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/entity/Entity;getVelocity()Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0
            ),
            ordinal = 1
    )
    private Vec3d modify_updateMovementInFluid_Vec3d_0(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
    }

    @ModifyArg(
            method = "updateMovementInFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;add(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 1
            ),
            index = 0
    )
    private Vec3d modify_updateMovementInFluid_add_0(Vec3d vec3d) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return vec3d;
        }

        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }

    @Inject(
            method = "pushAwayFrom",
            at = @At("HEAD"),
            cancellable = true
    )
    private void inject_pushAwayFrom(Entity entity, CallbackInfo ci) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        Direction otherGravityDirection = ((EntityAccessor) entity).gravitychanger$getAppliedGravityDirection();

        if(gravityDirection == Direction.DOWN && otherGravityDirection == Direction.DOWN) return;

        ci.cancel();

        if (!this.isConnectedThroughVehicle(entity)) {
            if (!entity.noClip && !this.noClip) {
                Vec3d entityOffset = entity.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter());

                {
                    Vec3d playerEntityOffset = RotationUtil.vecWorldToPlayer(entityOffset, gravityDirection);
                    double dx = playerEntityOffset.x;
                    double dz = playerEntityOffset.z;
                    double f = MathHelper.absMax(dx, dz);
                    if(f >= 0.009999999776482582D) {
                        f = Math.sqrt(f);
                        dx /= f;
                        dz /= f;
                        double g = 1.0D / f;
                        if(g > 1.0D) {
                            g = 1.0D;
                        }

                        dx *= g;
                        dz *= g;
                        dx *= 0.05000000074505806D;
                        dz *= 0.05000000074505806D;
                        if(!this.hasPassengers()) {
                            this.addVelocity(-dx, 0.0D, -dz);
                        }
                    }
                }

                {
                    Vec3d entityEntityOffset = RotationUtil.vecWorldToPlayer(entityOffset, otherGravityDirection);
                    double dx = entityEntityOffset.x;
                    double dz = entityEntityOffset.z;
                    double f = MathHelper.absMax(dx, dz);
                    if(f >= 0.009999999776482582D) {
                        f = Math.sqrt(f);
                        dx /= f;
                        dz /= f;
                        double g = 1.0D / f;
                        if(g > 1.0D) {
                            g = 1.0D;
                        }

                        dx *= g;
                        dz *= g;
                        dx *= 0.05000000074505806D;
                        dz *= 0.05000000074505806D;
                        if(!entity.hasPassengers()) {
                            entity.addVelocity(dx, 0.0D, dz);
                        }
                    }
                }
            }
        }
    }

    @Redirect(
            method = "doesNotCollide(DDD)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Box;offset(DDD)Lnet/minecraft/util/math/Box;",
                    ordinal = 0
            )
    )
    private Box redirect_doesNotCollide_offset_0(Box box, double x, double y, double z) {
        Direction gravityDirection = ((EntityAccessor) this).gravitychanger$getAppliedGravityDirection();
        if(gravityDirection == Direction.DOWN) {
            return box.offset(x, y, z);
        }

        return box.offset(RotationUtil.vecPlayerToWorld(x, y, z, gravityDirection));
    }
}
