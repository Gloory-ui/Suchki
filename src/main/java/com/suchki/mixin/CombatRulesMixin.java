package com.suchki.mixin;

import com.suchki.enchant.ProtectionCurve;
import net.minecraft.world.damagesource.CombatRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Защитные чары без жёсткого потолка: до 20 очков — как в ванили (до 80%), дальше каждое очко
 * даёт всё меньше, снижение урона стремится к 100%, но не достигает его.
 */
@Mixin(CombatRules.class)
public abstract class CombatRulesMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("HEAD"), cancellable = true)
	private static void suchki$softCap(float damage, float protection, CallbackInfoReturnable<Float> cir) {
		if (protection > ProtectionCurve.VANILLA_CAP) {
			cir.setReturnValue(damage * (float) (1.0 - ProtectionCurve.reduction(protection)));
		}
	}
}
