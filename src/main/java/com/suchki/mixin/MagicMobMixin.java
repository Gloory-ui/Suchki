package com.suchki.mixin;

import com.suchki.magic.Minds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Иллюзии и призыв: моб не может выбрать запрещённую цель (страх, умиротворение, союзники, двойник). */
@Mixin(Mob.class)
public abstract class MagicMobMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void suchki$mindTarget(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (!self.level().isClientSide() && Minds.blockTarget(self, target)) ci.cancel();
	}
}
