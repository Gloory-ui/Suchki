package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Лесоруб — быстрее рубит брёвна; Шахтёр и Странник — медленнее голодают. */
@Mixin(Player.class)
public abstract class SkillPlayerMixin {
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float suchki$woodSpeed(float speed, BlockState state) {
		if (!state.is(BlockTags.LOGS)) return speed;
		double bonus = Skills.perk((Player) (Object) this, Perk.WOOD_SPEED);
		return bonus > 0 ? (float) (speed * (1.0 + bonus)) : speed;
	}

	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float suchki$hungerSave(float exhaustion) {
		double save = Skills.perk((Player) (Object) this, Perk.HUNGER_SAVE);
		return save > 0 ? (float) (exhaustion * Math.max(0.1, 1.0 - save)) : exhaustion;
	}
}
