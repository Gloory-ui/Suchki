package com.suchki.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к целям ИИ моба — чтобы сделать призванных союзниками. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("goalSelector")
	GoalSelector suchki$goalSelector();

	@Accessor("targetSelector")
	GoalSelector suchki$targetSelector();
}
