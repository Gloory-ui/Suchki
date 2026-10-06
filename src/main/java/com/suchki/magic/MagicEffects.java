package com.suchki.magic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Свои эффекты магии: Каменная кожа, Паралич, Проклятие, Неугасимая жизнь. */
public final class MagicEffects {
	static final class Effect extends MobEffect {
		Effect(MobEffectCategory category, int color) {
			super(category, color);
		}

		Effect(MobEffectCategory category, int color, ParticleOptions particle) {
			super(category, color, particle);
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			// Паралич: мобы не ходят и не целятся.
			if (entity instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return this == PARALYSIS.value();
		}
	}

	/** +4 брони и +2 прочности брони за уровень. */
	public static final Holder<MobEffect> STONESKIN = register("stoneskin", new Effect(MobEffectCategory.BENEFICIAL, 0x8C8C8C)
			.addAttributeModifier(Attributes.ARMOR, Magic.id("effect.stoneskin.armor"), 4.0, AttributeModifier.Operation.ADD_VALUE)
			.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, Magic.id("effect.stoneskin.toughness"), 2.0,
					AttributeModifier.Operation.ADD_VALUE));
	/** Не двигается, не прыгает, не наносит урона. */
	public static final Holder<MobEffect> PARALYSIS = register("paralysis", new Effect(MobEffectCategory.HARMFUL, 0x7FB8D8)
			.addAttributeModifier(Attributes.MOVEMENT_SPEED, Magic.id("effect.paralysis.speed"), -1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.JUMP_STRENGTH, Magic.id("effect.paralysis.jump"), -1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.FLYING_SPEED, Magic.id("effect.paralysis.fly"), -1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.ATTACK_DAMAGE, Magic.id("effect.paralysis.attack"), -1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	/** Получает на 25% больше урона (за уровень). Считается в SkillCombatMixin. */
	public static final Holder<MobEffect> HEX = register("hex", new Effect(MobEffectCategory.HARMFUL, 0x5B1E8C));
	/** Смертельный удар отменяется один раз. */
	public static final Holder<MobEffect> UNDYING = register("undying", new Effect(MobEffectCategory.BENEFICIAL, 0xFFD24A));

	private MagicEffects() {}

	private static Holder<MobEffect> register(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Magic.id(name), effect);
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!entity.hasEffect(UNDYING) || !(entity.level() instanceof ServerLevel level)) return true;
			// Как тотем: снимаем все эффекты, оставляем половину здоровья, даём регенерацию и поглощение.
			entity.removeAllEffects();
			entity.setHealth(entity.getMaxHealth() * 0.5F);
			entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
			entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
			entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
			MagicFx.undyingSaved(level, entity);
			return false;
		});
	}

	/** Сколько дополнительного урона получает существо под Проклятием (0 — нет эффекта). */
	public static float hexBonus(LivingEntity entity) {
		MobEffectInstance hex = entity.getEffect(HEX);
		return hex == null ? 0.0F : 0.25F * (hex.getAmplifier() + 1);
	}
}
