package me.andrew.gravitychanger.mixin;

import me.andrew.gravitychanger.accessor.RotatableEntityAccessor;
import me.andrew.gravitychanger.accessor.ServerPlayerEntityAccessor;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow private List<ServerPlayerEntity> players;

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void gravitychanger$syncAfterJoin(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        for (ServerPlayerEntity currentPlayer : this.players) {
            ((ServerPlayerEntityAccessor) currentPlayer).gravitychanger$sendGravityPacket(((RotatableEntityAccessor) currentPlayer).gravitychanger$getGravityDirection(), true);
        }
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void gravitychanger$syncAfterRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity respawnedPlayer = cir.getReturnValue();
        ((ServerPlayerEntityAccessor) respawnedPlayer).gravitychanger$sendGravityPacket(((RotatableEntityAccessor) respawnedPlayer).gravitychanger$getGravityDirection(), true);
    }
}
