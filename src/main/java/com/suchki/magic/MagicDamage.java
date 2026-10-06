package com.suchki.magic;

import com.suchki.Suchki;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Виды магического урона (data/suchki/damage_type/*.json). Огонь — в теге is_fire, холод — is_freezing. */
public enum MagicDamage {
	FIRE("spell_fire"),
	FROST("spell_frost"),
	SHOCK("spell_shock"),
	HOLY("spell_holy"),
	DARK("spell_dark"),
	ARCANE("spell_arcane"),
	EARTH("spell_earth"),
	WIND("spell_wind"),
	WATER("spell_water"),
	VOID("spell_void"),
	BLOOD("spell_blood"),
	PSYCHIC("spell_psychic");

	/** Все виды магического урона (чтобы не давать за них опыт Воину/Лучнику). */
	public static final TagKey<DamageType> TAG = TagKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "spell"));

	public final ResourceKey<DamageType> key;

	MagicDamage(String path) {
		this.key = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Suchki.MOD_ID, path));
	}

	public DamageSource source(ServerLevel level, Entity direct, Entity caster) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), direct, caster);
	}
}
