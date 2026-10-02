package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.SuchkiConfig;
import com.suchki.enchant.Ench;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique
	private static final EquipmentSlot[] SUCHKI_ARMOR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Невесомость VIII: падение ниже безопасной высоты не наносит урона. */
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void suchki$featherFalling(double distance, float multiplier, DamageSource source,
			CallbackInfoReturnable<Boolean> cir) {
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;
		LivingEntity self = (LivingEntity) (Object) this;
		if (distance < cfg.featherFallingSafeHeight
				&& Ench.level(self, Enchantments.FEATHER_FALLING, self.getItemBySlot(EquipmentSlot.FEET))
						>= cfg.featherFallingPerkLevel) {
			cir.setReturnValue(false);
		}
	}

	/** Защита X: −2,5% урона за каждый такой предмет брони (полный комплект — −10%). */
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float suchki$protectionPerk(float damage, DamageSource source) {
		if (damage <= 0.0F || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return damage;
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;
		LivingEntity self = (LivingEntity) (Object) this;
		int pieces = 0;
		for (EquipmentSlot slot : SUCHKI_ARMOR) {
			if (Ench.level(self, Enchantments.PROTECTION, self.getItemBySlot(slot)) >= cfg.protectionPerkLevel) pieces++;
		}
		return pieces == 0 ? damage : damage * (float) Math.max(0.0, 1.0 - cfg.protectionPerkPerPiece * pieces);
	}
}
