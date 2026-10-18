package me.andrew.gravitychanger.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void gravitychanger$preventPlacementInsidePlayer(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        PlayerEntity player = context.getPlayer();
        if (player == null) return;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        VoxelShape blockShape = state.getCollisionShape(world, pos, ShapeContext.of(player)).offset(pos.getX(), pos.getY(), pos.getZ());
        if (VoxelShapes.matchesAnywhere(blockShape, VoxelShapes.cuboid(player.getBoundingBox()), BooleanBiFunction.AND)) {
            cir.setReturnValue(false);
        }
    }
}
