package com.suchki.mixin;

import com.suchki.magic.Minions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Призванные существа ничего не роняют при смерти (ни добычи, ни снаряжения, ни опыта). */
@Mixin(LivingEntity.class)
public abstract class MagicLivingMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void suchki$noSummonLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (Minions.isSummon((LivingEntity) (Object) this)) ci.cancel();
	}
}
