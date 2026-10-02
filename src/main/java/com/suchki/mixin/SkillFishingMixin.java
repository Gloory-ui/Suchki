package com.suchki.mixin;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Рыбак: больше удачи (сокровища) и быстрее клюёт. */
@Mixin(FishingRodItem.class)
public abstract class SkillFishingMixin {
	@ModifyArgs(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/FishingHook;<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V"))
	private void suchki$fishing(Args args) {
		Player player = args.get(0);
		int luck = args.get(2);
		int lure = args.get(3);
		args.set(2, luck + (int) Skills.perk(player, Perk.FISH_LUCK));
		args.set(3, lure + (int) Math.round(Skills.perk(player, Perk.FISH_LURE) * 20));
	}
}
