package com.suchki.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GrindstoneMenu.class)
public interface GrindstoneMenuAccessor {
	@Accessor("repairSlots")
	Container suchki$repairSlots();

	@Accessor("access")
	ContainerLevelAccess suchki$access();
}
