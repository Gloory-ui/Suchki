package com.suchki.mixin;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Лучник: шанс не потратить стрелу (такую стрелу, как и при Бесконечности, нельзя подобрать). */
@Mixin(ProjectileWeaponItem.class)
public abstract class SkillArrowMixin {
	@ModifyVariable(method = "useAmmo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static boolean suchki$saveArrow(boolean free, ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
		if (free || !(shooter instanceof Player p) || p.level().isClientSide()) return free;
		boolean saved = Skills.roll(p, Perk.ARROW_SAVE);
		if (saved && p.level() instanceof net.minecraft.server.level.ServerLevel level) {
			com.suchki.fx.Fx.skillProc(level, p, p.getEyePosition().add(p.getLookAngle().scale(0.6)));
		}
		return saved;
	}
}
