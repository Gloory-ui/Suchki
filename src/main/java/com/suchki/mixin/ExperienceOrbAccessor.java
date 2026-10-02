package com.suchki.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
	@Accessor("age")
	int suchki$getAge();

	@Accessor("age")
	void suchki$setAge(int age);

	@Accessor("count")
	void suchki$setCount(int count);
}
