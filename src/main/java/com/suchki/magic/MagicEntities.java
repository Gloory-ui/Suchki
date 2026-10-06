package com.suchki.magic;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Сущности магии. */
public final class MagicEntities {
	private static final ResourceKey<EntityType<?>> PROJECTILE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Magic.id("spell_projectile"));

	public static final EntityType<SpellProjectile> SPELL_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, PROJECTILE_KEY,
			EntityType.Builder.<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
					.noLootTable().sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2)
					.build(PROJECTILE_KEY));

	private MagicEntities() {}

	static void init() {
		// Регистрация — в статических полях.
	}
}
