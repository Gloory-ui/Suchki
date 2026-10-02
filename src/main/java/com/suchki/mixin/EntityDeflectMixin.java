package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.enchant.Ench;
import com.suchki.enchant.SuchkiEnchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Отражение (нагрудник): шанс 6% за уровень отбить снаряд прямо в того, кто его выпустил. */
@Mixin(Entity.class)
public abstract class EntityDeflectMixin {
	@Unique
	private static final ProjectileDeflection SUCHKI_BACK_TO_SHOOTER = (projectile, deflector, random) -> {
		Entity shooter = projectile.getOwner();
		double speed = Math.max(projectile.getDeltaMovement().length(), 1.0);
		if (shooter == null) {
			ProjectileDeflection.REVERSE.deflect(projectile, deflector, random);
			return;
		}
		Vec3 dir = shooter.getEyePosition().subtract(projectile.position()).normalize();
		projectile.setDeltaMovement(dir.scale(speed));
		projectile.needsSync = true;
	};

	@ModifyReturnValue(method = "deflection", at = @At("RETURN"))
	private ProjectileDeflection suchki$deflect(ProjectileDeflection original, Projectile projectile) {
		if (original != ProjectileDeflection.NONE || !((Object) this instanceof LivingEntity self)) return original;
		if (self.level().isClientSide() || projectile.getOwner() == self) return original;
		int level = Ench.level(self, SuchkiEnchantments.DEFLECT, self.getItemBySlot(EquipmentSlot.CHEST));
		if (level > 0 && self.getRandom().nextFloat() < 0.06F * level) return SUCHKI_BACK_TO_SHOOTER;
		return original;
	}
}
