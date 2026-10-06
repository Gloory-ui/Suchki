package com.suchki.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Двойник (Иллюзия): спрятать подпись «Манекен» под именем. */
@Mixin(Mannequin.class)
public interface MannequinInvoker {
	@Invoker("setHideDescription")
	void suchki$setHideDescription(boolean hide);
}
