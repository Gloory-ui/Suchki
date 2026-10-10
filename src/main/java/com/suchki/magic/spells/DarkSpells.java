package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.DARK;
import static com.suchki.magic.spells.SpellKit.DOWN;
import static com.suchki.magic.spells.SpellKit.UP;

import com.suchki.SuchkiConfig;
import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minds;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import com.suchki.mixin.MannequinInvoker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Тьма: смерть, кровь, проклятия и тени. От пронзающего луча-«обыденной смерти» до открытой территории,
 * где всё режется без конца. Свечение рисуется сложением цвета, поэтому «чёрное» здесь — глубокий фиолетовый и
 * багровый с дымом: настоящий чёрный цвет на таком рендере просто не виден.
 */
public final class DarkSpells {
	/** Фиолетовое свечение тьмы. */
	static final int VIOLET = 0x9A4DFF;
	/** Глубокий фиолетовый («чёрный» на светящемся рендере). */
	static final int DEEP = 0x4A1478;
	/** Бледный сиреневый — сердцевина лучей. */
	static final int PALE = 0xF4ECFF;
	/** Кровь. */
	static final int BLOOD = 0xC0304A;
	static final int CRIMSON = 0xE8203A;
	static final int BLOOD_LIGHT = 0xFFC8D0;

	/** Луч «обыденной смерти»: дальность и толщина. */
	static final double RAY = 40.0;
	/** Куда достаёт шаг тени до врага/блока (до своего призыва — на всю дальность заклинания). */
	static final double STEP_FOE = 64.0;

	/** Похищение жизни: состояние потока у каждого мага (нити, сколько тиков на каждой цели, что уже украдено). */
	private static final Map<UUID, Drain> DRAIN = new HashMap<>();
	/** Теневой обмен: следующий удар в ближнем бою в течение 2 с бьёт тьмой. */
	private static final Map<UUID, Cast> SHADOW_STRIKE = new HashMap<>();
	/** Кровотечение: сколько тиков ещё течёт кровь (одна зона на существо). */
	private static final Map<LivingEntity, int[]> BLEEDING = new WeakHashMap<>();
	private static boolean hooked;

	private DarkSpells() {}

	public static void register() {
		hook();

		// --- Ранг 1 ---

		Spell.of("shadow_bolt", DARK, 1, BOLT).mana(10).charge(10).cooldown(8).range(RAY).icon("ink_sac").circle(0.7F)
				.action(DarkSpells::deathRay)
				.register();

		// --- Ранг 2 ---

		Spell.of("drain_life", DARK, 2, CHANNEL).mana(12).cooldown(20).range(20).icon("fermented_spider_eye").beam()
				.loop(MagicSounds.LOOP_DRAIN)
				.channel(DarkSpells::drainTick)
				.register();

		Spell.of("curse", DARK, 2, BOLT).mana(20).charge(14).cooldown(60).range(48).anchor(Anchor.TARGET).icon("spider_eye")
				.action(DarkSpells::cursedSeal)
				.register();

		// --- Ранг 3 ---

		Spell.of("shadow_step", DARK, 3, SELF).mana(30).charge(14).cooldown(60).range(128).anchor(Anchor.TARGET).multicast(4)
				.icon("ender_pearl")
				.action(DarkSpells::shadowExchange)
				.register();

		Spell.of("darkness", DARK, 3, BOLT).mana(34).charge(20).cooldown(200).range(64).anchor(Anchor.TARGET).layers(2).circle(5.0F)
				.icon("sculk")
				.action(DarkSpells::moonlessHaze)
				.register();

		// --- Ранг 4 ---

		Spell.of("soul_harvest", DARK, 4, RITUAL).mana(60).charge(32).cooldown(500).range(12).layers(2).icon("wither_skeleton_skull")
				.action(c -> {
					if (c.copy > 0) {
						// Копия мультикаста — второй, вдвое слабый всплеск через полсекунды.
						Zones.later(c, 6, () -> soulHarvest(c.withPower(c.power * 0.5F)));
						return true;
					}
					SpellKit.release(c);
					soulHarvest(c);
					return true;
				})
				.register();

		// --- Ранг 5 ---

		Spell.of("grasp_heart", DARK, 5, BOLT).mana(90).charge(44).cooldown(600).range(48).icon("heart_of_the_sea")
				.action(DarkSpells::graspHeart)
				.register();

		Spell.of("black_coffin", DARK, 5, RITUAL).mana(100).charge(48).cooldown(720).range(96).anchor(Anchor.TARGET).layers(2)
				.circle(3.6F).icon("black_concrete").destroys()
				.action(DarkSpells::blackCoffin)
				.register();

		// --- Ранг 6 ---

		Spell.of("black_moonfang", DARK, 6, BOLT).mana(150).charge(64).cooldown(1800).range(96).layers(3).icon("netherite_sword")
				.destroys()
				.action(DarkSpells::moonfang)
				.register();

		// --- Ранг 7 ---

		Spell.of("malevolent_shrine", DARK, 7, RITUAL).mana(280).charge(120).cooldown(6000).range(24).layers(5).circle(4.5F)
				.icon("bone_block").destroys()
				.action(DarkSpells::malevolentShrine)
				.register();
	}

	/** Удар в ближнем бою после теневого обмена: +6 урона тьмой (один раз). */
	private static void hook() {
		if (hooked) return;
		hooked = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (SHADOW_STRIKE.isEmpty() || !source.is(DamageTypes.PLAYER_ATTACK) || !(source.getEntity() instanceof ServerPlayer p)) return;
			Cast c = SHADOW_STRIKE.remove(p.getUUID());
			if (c == null || !c.isFoe(entity)) return;
			c.hurt(entity, c.amount(6.0F), MagicDamage.DARK);
			Vec3 at = Cast.center(entity);
			MagicFx.burst(c.level, at, ParticleTypes.SQUID_INK, 14, 0.3, 0.05);
			MagicFx.burst(c.level, at, MagicParticles.spark(VIOLET, 0.35F, 12), 12, 0.3, 0.08);
			MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 1.0F, 0.6F);
		});
	}

	// ==================================================================== Ранг 1

	/** Луч обыденной смерти: мгновенный луч до 40 блоков; первая цель — 5 и Иссушение, вторая — 3. */
	private static boolean deathRay(Cast c) {
		SpellKit.release(c);
		Vec3 from = c.hand();
		Vec3 dir = c.direction();
		Vec3 end = from.add(dir.scale(RAY));
		BlockHitResult wall = c.level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
		if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
		List<LivingEntity> hit = c.along(from, end, 0.35, c::isFoe);
		for (int i = 0; i < Math.min(2, hit.size()); i++) {
			LivingEntity e = hit.get(i);
			c.hurt(e, c.amount(i == 0 ? 5.0F : 3.0F), MagicDamage.DARK);
			if (i == 0) {
				e.addEffect(new MobEffectInstance(MobEffects.WITHER, c.duration(60), 0), c.caster);
				Cast.knock(e, from, 0.2, 0.05);
			}
			shards(c, Cast.center(e), i == 0 ? 1.0F : 0.6F);
		}
		// Белая сердцевина и фиолетовое свечение; копии залпа звучат чуть выше.
		MagicFx.beam(c.level, from, end, 0.35F, VIOLET, 0xFFFFFF, 5);
		MagicFx.line(c.level, from, end, MagicParticles.spark(PALE, 0.08F, 6), 1.2);
		if (hit.isEmpty() && wall.getType() != HitResult.Type.MISS) shards(c, end, 0.5F);
		MagicFx.sound(c.level, from, MagicSounds.IMPACT.get(School.LIGHTNING), 0.5F, 1.6F + 0.05F * c.copy);
		return true;
	}

	/** Чёрно-белые осколки в точке попадания. */
	private static void shards(Cast c, Vec3 at, float size) {
		MagicFx.burst(c.level, at, MagicParticles.spark(0xFFFFFF, 0.18F * size, 10), Math.round(10 * size), 0.15, 0.12);
		MagicFx.burst(c.level, at, ParticleTypes.SQUID_INK, Math.round(8 * size), 0.15, 0.08);
		MagicFx.burst(c.level, at, MagicParticles.spark(VIOLET, 0.3F * size, 12), Math.round(6 * size), 0.1, 0.05);
		MagicFx.sound(c.level, at, MagicSounds.SHATTER, 0.5F * size + 0.2F, 1.5F);
	}

	// ==================================================================== Ранг 2

	/** Состояние потока Похищения жизни. */
	private static final class Drain {
		final List<LivingEntity> tethers = new ArrayList<>();
		final Map<LivingEntity, Integer> ticksOn = new HashMap<>();
		final Set<LivingEntity> robbed = new HashSet<>();
	}

	/**
	 * Похищение жизни: багровые нити к цели под прицелом и ещё к двум врагам в конусе 40°; раз в полсекунды каждая
	 * нить тянет 2 здоровья, маг лечится на 60% отнятого (до 4 за раз), излишек — в поглощение (до 6). Через 3 с на
	 * одной цели нить крадёт её полезный эффект. Копии мультикаста не тянут сами — дают главной +1 нить и +0,5 урона.
	 */
	private static void drainTick(Cast c, int t) {
		if (c.copy > 0) return;
		UUID id = c.caster.getUUID();
		Drain d = DRAIN.get(id);
		if (d == null || t == 1) {
			d = new Drain();
			DRAIN.put(id, d);
		}
		int extra = c.copies - 1;
		if (t % 10 == 1 || d.tethers.isEmpty()) {
			d.tethers.clear();
			LivingEntity main = c.aimFoe();
			if (main != null) d.tethers.add(main);
			Vec3 eye = c.eye();
			Vec3 look = c.look();
			List<LivingEntity> cone = c.foesAround(eye, c.spell.range);
			cone.removeIf(e -> e == main || !Cast.inCone(e, eye, look, c.spell.range, 20) || !c.caster.hasLineOfSight(e));
			cone.sort(Comparator.comparingDouble(e -> -Cast.center(e).subtract(eye).normalize().dot(look)));
			for (LivingEntity e : cone) {
				if (d.tethers.size() >= 3 + extra) break;
				d.tethers.add(e);
			}
		}
		d.tethers.removeIf(e -> !e.isAlive());
		if (d.tethers.isEmpty()) return;
		Vec3 hand = c.hand();
		for (LivingEntity e : d.tethers) {
			Vec3 from = Cast.center(e);
			if (t % 5 == 1) MagicFx.helix(c.level, hand, from, 0.15F, CRIMSON, BLOOD_LIGHT, 6);
			if (t % 2 == 0) {
				// Капли крови текут по нити обратно в ладонь.
				Vec3 v = hand.subtract(from).scale(0.075);
				MagicFx.one(c.level, from, MagicParticles.spark(BLOOD, 0.22F, 13), v.x, v.y, v.z);
				MagicFx.one(c.level, from, new DustParticleOptions(0xA0102A, 1.0F), 0, 0, 0);
			}
		}
		if (t % 10 != 0) return;
		float taken = 0;
		for (LivingEntity e : d.tethers) {
			float before = e.getHealth();
			c.hurt(e, c.amount(2.0F + 0.5F * extra), MagicDamage.BLOOD);
			taken += Math.max(0.0F, before - e.getHealth());
			int on = d.ticksOn.merge(e, 10, Integer::sum);
			if (on >= 60 && d.robbed.add(e)) steal(c, e);
		}
		if (taken <= 0) return;
		float heal = Math.min(4.0F, taken * 0.6F);
		float room = c.caster.getMaxHealth() - c.caster.getHealth();
		c.heal(c.caster, heal);
		float overflow = heal - Math.max(0.0F, room);
		if (overflow > 0) {
			// Излишек — поглощение до 6 (эффект нужен, иначе поглощению негде держаться).
			float cur = c.caster.getAbsorptionAmount();
			if (cur < 6.0F) {
				c.caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, false, true), c.caster);
				c.caster.setAbsorptionAmount(Math.min(6.0F, cur + overflow));
			}
		}
		MagicFx.burst(c.level, hand, MagicParticles.spark(BLOOD, 0.3F, 10), 4, 0.1, 0.02);
	}

	/** Украсть один полезный эффект цели (не дольше 30 с). */
	private static void steal(Cast c, LivingEntity e) {
		for (MobEffectInstance fx : List.copyOf(e.getActiveEffects())) {
			if (fx.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) continue;
			e.removeEffect(fx.getEffect());
			int dur = fx.isInfiniteDuration() ? 600 : Math.min(600, fx.getDuration());
			c.caster.addEffect(new MobEffectInstance(fx.getEffect(), dur, fx.getAmplifier()), c.caster);
			MagicFx.helix(c.level, Cast.center(e), c.caster.getEyePosition(), 0.3F, VIOLET, PALE, 10);
			MagicFx.sound(c.level, c.caster.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
			MagicFx.sound(c.level, Cast.center(e), MagicSounds.VANISH, 0.8F, 0.6F);
			return;
		}
	}

	/** Проклятая печать: Проклятие (+25% урона), Слабость и Замедление на 15 с; при смерти печать перескакивает. */
	private static boolean cursedSeal(Cast c) {
		LivingEntity t = c.aimFoe();
		if (t == null) {
			Vec3 p = c.aimPoint();
			t = c.foesAround(p, 3.0).stream()
					.min(Comparator.comparingDouble((LivingEntity e) -> (e.hasEffect(MagicEffects.HEX) ? 100 : 0) + Cast.center(e).distanceTo(p)))
					.orElse(null);
		}
		if (t == null) return false;
		SpellKit.release(c);
		int ticks = c.duration(isBoss(t) ? 160 : 300);
		int amp = c.power >= 1.2F ? 1 : 0;
		LivingEntity[] cur = {t};
		hex(c, t, ticks, amp);
		Zones.add(c, Cast.center(t), 0, ticks, (z, age) -> {
			LivingEntity e = cur[0];
			if (e.isAlive()) {
				z.center = Cast.center(e);
				if (age % 20 == 0) {
					// Сигил пульсирует на спине.
					Vec3 back = e.position().add(0, e.getBbHeight() * 0.65, 0).subtract(e.getLookAngle().multiply(1, 0, 1).scale(0.35));
					MagicFx.one(c.level, back, MagicParticles.glyph(VIOLET, 0.3F, 16), 0, 0.01, 0);
				}
				return;
			}
			int left = ticks - age;
			Vec3 from = z.center;
			LivingEntity next = c.foesAround(from, 8.0).stream().filter(x -> x != e)
					.min(Comparator.comparingDouble((LivingEntity x) -> (x.hasEffect(MagicEffects.HEX) ? 100 : 0) + Cast.center(x).distanceTo(from)))
					.orElse(null);
			if (next == null || left < 10) {
				z.stop();
				return;
			}
			cur[0] = next;
			hex(c, next, left, amp);
			MagicFx.line(c.level, from, Cast.center(next), MagicParticles.spark(VIOLET, 0.16F, 12), 0.35);
			MagicFx.sound(c.level, from, MagicSounds.VANISH, 0.9F, 0.5F);
		});
		return true;
	}

	/** Наложить печать: эффекты и закрывающийся «глаз» над головой, который уходит в тело. */
	private static void hex(Cast c, LivingEntity t, int ticks, int amp) {
		t.addEffect(new MobEffectInstance(MagicEffects.HEX, ticks, amp), c.caster);
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), c.caster);
		t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0), c.caster);
		Vec3 head = t.position().add(0, t.getBbHeight() + 0.8, 0);
		MagicFx.circle(c.level, School.DARK, 2, VIOLET, PALE, head, UP, 1.0F, 1, 0, 8, 4, 8, -0.6F);
		Zones.later(c, 10, () -> {
			if (!t.isAlive()) return;
			MagicFx.circle(c.level, School.DARK, 2, DEEP, VIOLET, Cast.center(t), UP, 0.6F, 1, 0, 2, 4, 8, 0.8F);
			MagicFx.burst(c.level, Cast.center(t), ParticleTypes.SMOKE, 14, 0.3, 0.02);
		});
		MagicFx.burst(c.level, head, MagicParticles.spark(VIOLET, 0.25F, 14), 10, 0.4, 0.02);
		MagicFx.sound(c.level, Cast.center(t), MagicSounds.impactOf(c.spell), 1.0F, 0.6F);
		MagicFx.sound(c.level, Cast.center(t), SoundEvents.BELL_RESONATE, 0.5F, 0.5F);
	}

	// ==================================================================== Ранг 3

	/**
	 * Теневой обмен: за спину врага под прицелом (до 64), обмен местами со своим призывом (до 128) или к грани блока
	 * (до 64). Потом 2 с невидимости; следующий удар в ближнем бою за 2 с — ещё +6 тьмой. В пустоту шаг не делается.
	 * Мультикаст: на месте старта остаются теневые двойники, которые 2 с отвлекают врагов.
	 */
	private static boolean shadowExchange(Cast c) {
		if (c.copy > 0) return true;
		Vec3 start = c.caster.position();
		HitResult hit = c.aim(c.spell.range, Targeting.living(c.caster));
		Vec3 dest = null;
		LivingEntity foe = null;
		double dist = hit.getLocation().distanceTo(c.eye());
		if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l) {
			if (Minions.isOwnedBy(l, c.caster)) {
				// Свой призыв — меняемся местами.
				SpellKit.release(c);
				Vec3 there = l.position();
				pool(c, start);
				pool(c, there);
				l.teleportTo(start.x, start.y, start.z);
				c.teleport(there, VIOLET);
				afterStep(c, start, there);
				return true;
			}
			if (dist <= STEP_FOE) {
				foe = l;
				Vec3 at = l.position();
				Vec3 face = l.getLookAngle().multiply(1, 0, 1);
				if (face.lengthSqr() < 1.0E-4) face = c.look().multiply(-1, 0, -1);
				face = face.normalize();
				Vec3 side = new Vec3(-face.z, 0, face.x);
				double back = 0.9 + l.getBbWidth() * 0.5;
				for (Vec3 off : new Vec3[] {face.scale(-back), side.scale(back), side.scale(-back), face.scale(back)}) {
					dest = c.safeSpot(at.add(off));
					if (dest != null) break;
				}
			}
		}
		if (dest == null && hit instanceof BlockHitResult bh && hit.getType() != HitResult.Type.MISS && dist <= STEP_FOE) {
			dest = c.safeSpot(Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection())));
		}
		if (dest == null) return false;
		SpellKit.release(c);
		pool(c, start);
		pool(c, dest);
		c.teleport(dest, VIOLET);
		if (foe != null) {
			SHADOW_STRIKE.put(c.caster.getUUID(), c);
			Zones.later(c, 40, () -> SHADOW_STRIKE.remove(c.caster.getUUID(), c));
			// Смотрим на врага: удар в спину сразу.
			Vec3 to = Cast.center(foe).subtract(c.caster.getEyePosition());
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			c.caster.setYRot(yaw);
			c.caster.setYHeadRot(yaw);
		}
		afterStep(c, start, dest);
		return true;
	}

	/** Чёрная лужа-круг и дымные щупальца там, где маг уходит в тень и где выходит из неё. */
	private static void pool(Cast c, Vec3 feet) {
		MagicFx.circle(c.level, School.DARK, 3, DEEP, VIOLET, feet.add(0, 0.06, 0), UP, 1.2F, 1, 0, 3, 8, 10, 0.8F);
		MagicFx.cloud(c.level, feet.add(0, 0.6, 0), ParticleTypes.LARGE_SMOKE, 14, 0.35, 0.6, 0.35, 0.02);
		MagicFx.cloud(c.level, feet.add(0, 0.2, 0), ParticleTypes.SQUID_INK, 16, 0.5, 0.1, 0.5, 0.03);
	}

	private static void afterStep(Cast c, Vec3 start, Vec3 dest) {
		c.caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, c.duration(40), 0, false, false, true), c.caster);
		MagicFx.sound(c.level, start, MagicSounds.VANISH, 0.9F, 0.5F);
		MagicFx.sound(c.level, dest, MagicSounds.impactOf(c.spell), 0.8F, 0.7F);
		// Двойники-тени на месте старта (мультикаст).
		for (int i = 1; i < c.copies; i++) {
			Mannequin decoy = EntityType.MANNEQUIN.create(c.level, EntitySpawnReason.MOB_SUMMONED);
			if (decoy == null) continue;
			double a = Math.PI * 2 * i / Math.max(1, c.copies - 1);
			Vec3 at = c.copies == 2 ? start : start.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
			Vec3 safe = c.safeSpot(at);
			if (safe == null) safe = start;
			decoy.snapTo(safe.x, safe.y, safe.z, c.caster.getYRot(), 0);
			decoy.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(c.caster.getGameProfile()));
			((MannequinInvoker) decoy).suchki$setHideDescription(true);
			decoy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
			Minions.summon(c, decoy, 40);
			Vec3 from = safe;
			for (Mob m : c.level.getEntitiesOfClass(Mob.class, new AABB(from, from).inflate(16), c::isEnemy)) {
				Minds.apply(m, Minds.Mode.DECOY, 40, from, decoy);
			}
			MagicFx.cloud(c.level, safe.add(0, 1, 0), ParticleTypes.LARGE_SMOKE, 10, 0.3, 0.8, 0.3, 0.01);
		}
	}

	/**
	 * Безлунная мгла: облако тьмы радиусом 5 у прицела на 8 с. Каждые полсекунды враги внутри слепнут, сохнут,
	 * замедляются, забывают цель и не могут колдовать или стрелять (умиротворение), криперы гаснут. Кто вышел из
	 * облака — ещё 2 с бродит в растерянности.
	 */
	private static boolean moonlessHaze(Cast c) {
		SpellKit.release(c);
		Vec3 at = c.aimGround();
		double r = c.radius(5.0);
		int life = c.duration(160);
		MagicFx.circle(c.level, School.DARK, 3, DEEP, VIOLET, at.add(0, 0.1, 0), UP, (float) r, 2, 0.3F, 6, life - 12, 12, 0.08F);
		MagicFx.dome(c.level, at, (float) r, DEEP, VIOLET, life);
		MagicFx.line(c.level, c.hand(), at.add(0, 1, 0), MagicParticles.spark(VIOLET, 0.15F, 10), 0.5);
		MagicFx.soundFar(c.level, at, MagicSounds.impactOf(c.spell), 32, 0.5F);
		MagicFx.sound(c.level, at, MagicSounds.VANISH, 1.4F, 0.4F);
		Set<LivingEntity> inside = new HashSet<>();
		Zones.add(c, at, r, life, (z, age) -> {
			if (age % 2 == 0) {
				// Клубы мглы — полусфера с тусклыми искрами; край рябит.
				for (int i = 0; i < 4; i++) {
					Vec3 p = SpellKit.inDisc(c.level.random, at, r).add(0, c.level.random.nextDouble() * r * 0.8, 0);
					MagicFx.one(c.level, p, ParticleTypes.LARGE_SMOKE, 0, 0.01, 0);
					MagicFx.one(c.level, p, ParticleTypes.SQUID_INK, 0, 0.02, 0);
				}
				MagicFx.one(c.level, SpellKit.inDisc(c.level.random, at, r).add(0, 0.5 + c.level.random.nextDouble() * 2, 0),
						MagicParticles.spark(VIOLET, 0.2F, 18), 0, 0.01, 0);
			}
			if (age % 40 == 0) MagicFx.sound(c.level, at, MagicSounds.LOOP_VOID, 0.6F, 0.5F);
			if (age % 10 != 0) return;
			Set<LivingEntity> now = new HashSet<>(c.foesAround(at, r + 0.5));
			for (LivingEntity e : now) {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), c.caster);
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), c.caster);
				e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), c.caster);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), c.caster);
				if (e instanceof Mob m && !isBoss(m)) {
					// «Тишина»: умиротворённый моб не выбирает целью игроков — ни заклинаний, ни стрел.
					Minds.apply(m, Minds.Mode.CALM, 40, at, null);
					m.setTarget(null);
					if (m instanceof Creeper cr) cr.setSwellDir(-1);
				}
			}
			for (LivingEntity e : inside) {
				if (now.contains(e) || !e.isAlive()) continue;
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0), c.caster);
				if (e instanceof PathfinderMob pm && !isBoss(pm)) {
					Minds.apply(pm, Minds.Mode.CALM, 40, at, null);
					Vec3 away = DefaultRandomPos.getPos(pm, 6, 3);
					if (away != null) pm.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
				}
			}
			inside.clear();
			inside.addAll(now);
			if (age >= life - 10) MagicFx.sound(c.level, at, MagicSounds.VANISH, 1.0F, 0.7F);
		});
		return true;
	}

	// ==================================================================== Ранг 4

	/** Жатва душ: враги в радиусе 12 — 18 тьмой и Иссушение II; души текут в мага (лечение 2 за жертву, до 10). */
	private static void soulHarvest(Cast c) {
		Vec3 feet = c.caster.position();
		Vec3 chest = feet.add(0, 1.2, 0);
		double r = c.radius(12.0);
		int healed = 0;
		for (LivingEntity e : c.foesAround(chest, r)) {
			c.hurt(e, c.amount(18.0F), MagicDamage.DARK);
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, c.duration(120), 1), c.caster);
			Vec3 from = Cast.center(e);
			MagicFx.helix(c.level, from, chest, 0.25F, VIOLET, PALE, 14);
			Vec3 v = chest.subtract(from).scale(0.07);
			for (int i = 0; i < 4; i++) {
				MagicFx.one(c.level, from.add(0, i * 0.2, 0), ParticleTypes.SCULK_SOUL, v.x, v.y, v.z);
			}
			if (!e.isAlive()) {
				// Вырванная душа: опыта больше на половину.
				int xp = e.getExperienceReward(c.level, c.caster) / 2;
				if (xp > 0) ExperienceOrb.award(c.level, from, xp);
				MagicFx.sound(c.level, from, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
			}
			if (healed < 10) {
				c.heal(c.caster, 2.0F);
				healed += 2;
			}
		}
		if (healed > 0) {
			int amp = Math.min(4, healed / 2) - 1;
			c.caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, Math.max(0, amp)), c.caster);
		}
		MagicFx.circle(c.level, School.DARK, 4, DEEP, VIOLET, feet.add(0, 0.08, 0), UP, (float) r, 1, 0, 4, 6, 12, -0.5F);
		MagicFx.shockwave(c.level, feet.add(0, 0.3, 0), UP, 0.5F, (float) r, VIOLET, 14);
		MagicFx.shockwave(c.level, feet.add(0, 1.0, 0), UP, 0.5F, (float) r * 0.8F, CRIMSON, 18);
		MagicFx.burst(c.level, chest, ParticleTypes.SOUL, 20, 0.6, 0.05);
		MagicFx.soundFar(c.level, feet, MagicSounds.castOf(c.spell), 40, 0.5F);
		MagicFx.sound(c.level, feet, MagicSounds.GUST, 1.2F, 0.5F);
		MagicFx.sound(c.level, feet, SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.6F);
		Magic.shake(c.level, feet, 0.5F, 12, 24);
	}

	// ==================================================================== Ранг 5

	/**
	 * Длань, сжимающая сердце: над грудью цели бьётся алый сигил сердца, к нему тянется призрачная рука; через
	 * 6 тиков сердце раздавлено. Моб с ≤45 здоровья умирает, иначе 38 урона сквозь броню и 2 с оцепенения;
	 * боссы — 30, игроки — 14 и тошнота (без мгновенной смерти).
	 */
	private static boolean graspHeart(Cast c) {
		LivingEntity t = c.aimFoe();
		if (t == null) return false;
		SpellKit.release(c);
		Vec3 chest = t.position().add(0, t.getBbHeight() * 0.62, 0);
		Vec3 toCaster = c.eye().subtract(chest).normalize();
		MagicFx.circle(c.level, School.DARK, 5, CRIMSON, BLOOD_LIGHT, chest.add(toCaster.scale(t.getBbWidth() * 0.6 + 0.1)), toCaster, 0.7F,
				1, 0, 2, 5, 4, 1.5F);
		// Сердце бьётся всё чаще.
		for (int k : new int[] {0, 3, 5}) {
			Zones.later(c, k, () -> {
				MagicFx.sound(c.level, chest, SoundEvents.WARDEN_HEARTBEAT, 1.6F, 0.9F + 0.15F * k / 3.0F);
				MagicFx.burst(c.level, chest, MagicParticles.spark(CRIMSON, 0.4F, 6), 6, 0.08, 0.02);
			});
		}
		// Призрачная рука: пять «пальцев» сходятся к сердцу.
		Vec3 hand = c.hand();
		MagicFx.beam(c.level, hand, chest, 0.25F, BLOOD, BLOOD_LIGHT, 8);
		Vec3 side = toCaster.cross(UP);
		if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
		side = side.normalize();
		Vec3 up = side.cross(toCaster).normalize();
		for (int f = 0; f < 5; f++) {
			double a = Math.PI * 2 * f / 5;
			Vec3 tip = chest.add(side.scale(Math.cos(a) * 1.3)).add(up.scale(Math.sin(a) * 1.3)).add(toCaster.scale(0.6));
			Vec3 v = chest.subtract(tip).scale(1.0 / 6.0);
			for (int i = 0; i < 3; i++) {
				MagicFx.one(c.level, tip.add(v.scale(-i * 0.6)), MagicParticles.spark(i == 0 ? BLOOD_LIGHT : BLOOD, 0.22F, 8), v.x, v.y, v.z);
			}
		}
		Zones.later(c, 6, () -> crushHeart(c, t));
		return true;
	}

	private static void crushHeart(Cast c, LivingEntity t) {
		if (!t.isAlive()) return;
		Vec3 chest = t.position().add(0, t.getBbHeight() * 0.62, 0);
		boolean killed = false;
		if (t instanceof Player) {
			c.hurt(t, c.amount(14.0F), MagicDamage.VOID);
			t.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0), c.caster);
		} else if (isBoss(t)) {
			c.hurt(t, throughArmor(t, c.amount(30.0F)), MagicDamage.VOID);
			t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3), c.caster);
		} else if (t.getHealth() <= 45.0F * c.power * c.bonus) {
			float all = (t.getHealth() + t.getAbsorptionAmount()) * 4.0F + 100.0F;
			c.hurt(t, all, MagicDamage.VOID);
			killed = !t.isAlive();
		} else {
			c.hurt(t, throughArmor(t, c.amount(38.0F)), MagicDamage.VOID);
			stun(c, t, c.duration(40));
		}
		MagicFx.burst(c.level, chest, MagicParticles.spark(CRIMSON, 0.5F, 16), 30, 0.25, 0.25);
		MagicFx.burst(c.level, chest, new DustParticleOptions(0x8A0018, 1.6F), 40, 0.35, 0.1);
		MagicFx.burst(c.level, chest, ParticleTypes.DAMAGE_INDICATOR, 8, 0.3, 0.1);
		MagicFx.sphere(c.level, chest, 0.2F, 1.6F, CRIMSON, BLOOD_LIGHT, 8);
		MagicFx.flash(c.level, c.eye(), 3, 0.35F, CRIMSON, 8);
		MagicFx.soundFar(c.level, chest, MagicSounds.impactOf(c.spell), 32, 0.5F);
		MagicFx.sound(c.level, chest, SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
		MagicFx.sound(c.level, chest, MagicSounds.EXPLOSION, 0.7F, 0.6F);
		if (killed) {
			MagicFx.sound(c.level, chest, SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.7F);
			MagicFx.burst(c.level, chest, ParticleTypes.SCULK_SOUL, 10, 0.3, 0.06);
		}
		Zones.later(c, 14, () -> MagicFx.sound(c.level, chest, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 0.6F));
	}

	/**
	 * Чёрный гроб: короб 5×5×5 (стены из чёрного стекла с чёрными рёбрами) поднимается стена за стеной; кто внутри —
	 * скован. На 30-м тике 12 копий тьмы пронзают короб: 34 урона, Иссушение II, сильное замедление; на 40-м короб
	 * рассыпается. С разрушением — земля внутри раздавлена. Копии мультикаста — свой гроб на соседнем враге (−15%).
	 */
	private static boolean blackCoffin(Cast c) {
		SpellKit.release(c);
		Targeting.Aim aim = c.target();
		Vec3 base = aim.entity() instanceof LivingEntity l && c.isFoe(l) ? l.position() : c.aimGround();
		BlockPos o = BlockPos.containing(base.x, base.y + 0.05, base.z);
		Vec3 floor = Vec3.atBottomCenterOf(o);
		Vec3 center = floor.add(0, 2.0, 0);
		float k = Math.max(0.4F, 1.0F - 0.15F * c.copy);
		AABB box = new AABB(o.offset(-2, 0, -2).getCenter(), o.offset(2, 4, 2).getCenter()).inflate(0.5);
		MagicFx.circle(c.level, School.DARK, 5, DEEP, CRIMSON, floor.add(0, 0.07, 0), UP, 3.6F, 2, 0.25F, 6, 36, 10, 0.1F);
		MagicFx.cloud(c.level, floor.add(0, 0.5, 0), ParticleTypes.LARGE_SMOKE, 30, 1.6, 0.3, 1.6, 0.04);
		// Четыре стены и крышка — одна за другой.
		for (int w = 0; w < 5; w++) {
			int side = w;
			Zones.later(c, 2 * w, () -> coffinWall(c, o, side, 42 - 2 * side));
		}
		// Внутри — скованы (боссы — нет).
		Zones.add(c, center, 3, 30, (z, age) -> {
			for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, c::isFoe)) {
				if (isBoss(e)) continue;
				if (!e.hasEffect(MagicEffects.PARALYSIS)) e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, 31 - age, 0), c.caster);
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(0, Math.min(0, v.y), 0);
				e.hurtMarked = true;
			}
			if (age % 6 == 0) MagicFx.cloud(c.level, center, MagicParticles.spark(CRIMSON, 0.2F, 12), 6, 2.0, 2.0, 2.0, 0.0);
		});
		Zones.later(c, 30, () -> coffinSpears(c, o, center, box, k));
		Zones.later(c, 40, () -> {
			BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_STAINED_GLASS.defaultBlockState());
			MagicFx.cloud(c.level, center, glass, 120, 2.2, 2.2, 2.2, 0.3);
			MagicFx.burst(c.level, center, MagicParticles.spark(VIOLET, 0.4F, 16), 30, 2.0, 0.2);
			MagicFx.flash(c.level, center, 32, 0.35F, VIOLET, 8);
			MagicFx.shockwave(c.level, floor.add(0, 0.3, 0), UP, 1.0F, 8.0F, VIOLET, 12);
			MagicFx.soundFar(c.level, center, SoundEvents.GLASS_BREAK, 32, 0.6F);
			MagicFx.soundFar(c.level, center, MagicSounds.RUMBLE, 48, 0.8F);
		});
		return true;
	}

	/** Стена гроба: 0–3 — бока, 4 — крышка. Рёбра — чёрный бетон, грани — чёрное стекло. */
	private static void coffinWall(Cast c, BlockPos o, int side, int ticks) {
		BlockState glass = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
		BlockState edge = Blocks.BLACK_CONCRETE.defaultBlockState();
		for (int a = -2; a <= 2; a++) {
			for (int b = 0; b <= 4; b++) {
				int x, y, z;
				switch (side) {
					case 0 -> { x = a; y = b; z = -2; }
					case 1 -> { x = a; y = b; z = 2; }
					case 2 -> { x = -2; y = b; z = a; }
					case 3 -> { x = 2; y = b; z = a; }
					default -> { x = a; y = 4; z = b - 2; }
				}
				if (side < 4 && y == 4) continue;
				boolean rim = Math.abs(x) == 2 && Math.abs(z) == 2 || y == 4 && (Math.abs(x) == 2 || Math.abs(z) == 2);
				Terrain.temp(c, o.offset(x, y, z), rim ? edge : glass, ticks, false);
			}
		}
		Vec3 mid = Vec3.atCenterOf(o).add(side == 0 ? new Vec3(0, 1.5, -2) : side == 1 ? new Vec3(0, 1.5, 2)
				: side == 2 ? new Vec3(-2, 1.5, 0) : side == 3 ? new Vec3(2, 1.5, 0) : new Vec3(0, 4, 0));
		MagicFx.cloud(c.level, mid, MagicParticles.spark(CRIMSON, 0.25F, 14), 14, 1.2, 1.2, 1.2, 0.02);
		MagicFx.cloud(c.level, mid, ParticleTypes.LARGE_SMOKE, 8, 1.0, 1.0, 1.0, 0.02);
		MagicFx.sound(c.level, mid, MagicSounds.IMPACT.get(School.EARTH), 1.2F, 0.6F + 0.05F * side);
		Magic.shake(c.level, mid, 0.4F, 5, 16);
	}

	private static void coffinSpears(Cast c, BlockPos o, Vec3 center, AABB box, float k) {
		// 12 копий тьмы из стен и крышки — внутрь.
		for (int i = 0; i < 12; i++) {
			double a = Math.PI * 2 * i / 12;
			Vec3 from = i % 4 == 3 ? center.add(Math.cos(a) * 1.2, 2.4, Math.sin(a) * 1.2)
					: center.add(Math.cos(a) * 2.4, (i % 3 - 1) * 1.2, Math.sin(a) * 2.4);
			MagicFx.beam(c.level, from, center.add((c.level.random.nextDouble() - 0.5) * 0.6, 0, (c.level.random.nextDouble() - 0.5) * 0.6),
					0.12F, VIOLET, CRIMSON, 8);
		}
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, c::isFoe)) {
			c.hurt(e, c.amount(34.0F * k), MagicDamage.DARK);
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, c.duration(100), 1), c.caster);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 3), c.caster);
			MagicFx.burst(c.level, Cast.center(e), new DustParticleOptions(0x8A0018, 1.4F), 24, 0.3, 0.1);
		}
		MagicFx.burst(c.level, center, ParticleTypes.SQUID_INK, 40, 1.2, 0.1);
		MagicFx.soundFar(c.level, center, MagicSounds.castOf(c.spell), 48, 0.5F);
		MagicFx.sound(c.level, center, SoundEvents.TRIDENT_HIT, 1.5F, 0.5F);
		MagicFx.sound(c.level, center, MagicSounds.EXPLOSION, 1.0F, 0.6F);
		Magic.shake(c.level, center, 1.0F, 10, 32);
		// Земля внутри раздавлена (если разрешено разрушение).
		Terrain.crater(c, Vec3.atCenterOf(o), 2.6, 0.8, Terrain.Rim.SCULK);
	}

	// ==================================================================== Ранг 6

	/**
	 * Чёрный лунный клык: огромный чёрный серп с алой кромкой (ширина 12, высота 4) летит сквозь местность 3 блока
	 * за тик на 96 блоков: 48 урона (боссам 40, игрокам не больше 20), кровотечение 5 с, отброс. С разрушением —
	 * траншея 3×4 по земле на первые 64 блока, деревья на пути срезаны. Мультикаст ×2 — крест (второй серп стоймя,
	 * пересечение взрывается: 24 урона в радиусе 4), ×3/×4 — веер серпов.
	 */
	private static boolean moonfang(Cast c) {
		SpellKit.release(c);
		Vec3 dir = flat(c.copy > 0 ? c.direction() : c.look());
		boolean vertical = c.copies == 2 && c.copy == 1;
		if (c.copies >= 3 && c.copy > 0) dir = rotY(flat(c.look()), (c.copy % 2 == 1 ? 1 : -1) * 15.0 * ((c.copy + 1) / 2));
		Vec3 d = dir;
		Vec3 start = new Vec3(c.hand().x, c.caster.getY() + 1.6, c.hand().z).add(d.scale(1.0));
		Vec3 right = new Vec3(-d.z, 0, d.x);
		double halfW = vertical ? 2.0 : 6.0;
		double halfH = vertical ? 6.0 : 2.2;
		// Три круга у посоха вспыхивают один за другим — ствол удара.
		for (int i = 0; i < 3; i++) {
			MagicFx.circle(c.level, School.DARK, 6, i == 1 ? CRIMSON : DEEP, BLOOD_LIGHT, c.hand().add(c.look().scale(0.4 + 0.5 * i)), c.look(),
					1.0F + 0.5F * i, 1, 0, 2, 4, 10, 0.6F);
		}
		MagicFx.slash(c.level, start, d, vertical ? 5.0F : 6.5F, 96.0F, CRIMSON, DEEP, 32);
		MagicFx.flash(c.level, c.eye(), 24, 0.4F, CRIMSON, 8);
		MagicFx.soundFar(c.level, start, MagicSounds.RELEASE_HEAVY, 96, 0.7F);
		MagicFx.soundFar(c.level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 64, 0.5F);
		MagicFx.soundFar(c.level, start, MagicSounds.EXPLOSION, 64, 0.6F);
		Magic.shake(c.level, c.eye(), 2.0F, 16, 48);
		Set<LivingEntity> hit = new HashSet<>();
		boolean[] crossed = {false};
		Vec3[] lastGround = {null};
		boolean grief = c.spell.destroys && Terrain.griefing(c.level);
		Zones.add(c, start, halfW, 32, (z, age) -> {
			Vec3 prev = start.add(d.scale(3.0 * (age - 1)));
			Vec3 cur = start.add(d.scale(3.0 * age));
			z.center = cur;
			// Кто в полосе серпа между прошлым и нынешним положением.
			AABB band = new AABB(prev, cur).inflate(halfW + 1, halfH + 1, halfW + 1);
			for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, band, c::isFoe)) {
				if (hit.contains(e)) continue;
				Vec3 rel = Cast.center(e).subtract(prev);
				double along = rel.dot(d);
				if (along < -1.5 || along > 4.5) continue;
				if (Math.abs(rel.dot(right)) > halfW + e.getBbWidth() * 0.5) continue;
				if (Math.abs(rel.y) > halfH + e.getBbHeight() * 0.5) continue;
				hit.add(e);
				float dmg = isBoss(e) ? 40.0F : 48.0F;
				dmg = c.amount(dmg);
				if (e instanceof Player) dmg = Math.min(dmg, 20.0F);
				c.hurt(e, dmg, MagicDamage.DARK);
				bleed(c, e, c.duration(100));
				e.push(d.x * 1.5, 0.35, d.z * 1.5);
				e.hurtMarked = true;
				MagicFx.burst(c.level, Cast.center(e), new DustParticleOptions(0x8A0018, 1.5F), 24, 0.3, 0.15);
				if (vertical && !crossed[0]) {
					// Пересечение креста взрывается.
					crossed[0] = true;
					SpellKit.blast(c, Cast.center(e), 4.0, c.amount(24.0F), MagicDamage.DARK, 0, Terrain.Rim.NONE);
				}
			}
			// След: дым, алые искры по кромке, обломки земли.
			for (int i = -2; i <= 2; i++) {
				Vec3 p = vertical ? cur.add(0, i * halfH / 2.5, 0) : cur.add(right.scale(i * halfW / 2.5));
				MagicFx.one(c.level, p, ParticleTypes.LARGE_SMOKE, d.x * 0.1, 0.02, d.z * 0.1);
				MagicFx.one(c.level, p.add(0, vertical ? 0 : halfH * 0.6, 0), MagicParticles.spark(CRIMSON, 0.35F, 10), 0, 0, 0);
			}
			Vec3 g = Targeting.ground(c.level, c.caster, cur.add(0, 2.5, 0));
			if (age % 2 == 0 && g.y > cur.y - 8) {
				BlockState floor = c.level.getBlockState(BlockPos.containing(g.x, g.y - 0.5, g.z));
				if (!floor.isAir()) {
					MagicFx.cloud(c.level, g.add(0, 0.3, 0), new BlockParticleOption(ParticleTypes.BLOCK, floor), 16, 1.2, 0.3, 1.2, 0.2);
				}
			}
			if (age % 6 == 0) MagicFx.soundFar(c.level, cur, MagicSounds.GUST, 48, 0.5F);
			// Разрушение: траншея 3×4 по земле на первые 64 блока и срезанные деревья.
			if (grief && 3.0 * age <= 66 && g.y > cur.y - 8) {
				if (lastGround[0] != null) {
					Terrain.tunnel(c, lastGround[0].add(0, -1.6, 0), g.add(0, -1.6, 0), 1.6, Terrain.Rim.NONE);
				}
				lastGround[0] = g;
				cutTrees(c, prev, d, right, halfW, halfH, cur.y);
			}
			if (age == 31) MagicFx.soundFar(c.level, cur, MagicSounds.RUMBLE, 96, 0.7F);
		});
		return true;
	}

	/** Срезать брёвна и листву в полосе серпа (на всю его высоту). */
	private static void cutTrees(Cast c, Vec3 from, Vec3 d, Vec3 right, double halfW, double halfH, double y) {
		float drops = (float) SuchkiConfig.INSTANCE.magicDropChance;
		for (int s = 0; s < 3; s++) {
			for (double w = -halfW; w <= halfW; w += 1.0) {
				for (double h = -halfH; h <= halfH + 1; h += 1.0) {
					Vec3 p = from.add(d.scale(s)).add(right.scale(w));
					BlockPos pos = BlockPos.containing(p.x, y + h, p.z);
					BlockState st = c.level.getBlockState(pos);
					if (!(st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES))) continue;
					if (!Terrain.breakable(c.level, c.caster, pos, st, 1200)) continue;
					c.level.destroyBlock(pos, c.level.random.nextFloat() < drops);
				}
			}
		}
	}

	// ==================================================================== Ранг 7

	/**
	 * Расширение территории: Святилище злобы. Открытая территория радиусом 24 на 8 с (без барьера): за магом
	 * встаёт костяное святилище, каждые 4 тика всё живое внутри (кроме своих) режется — 1,5 урона кровью и
	 * кровотечение; раз в секунду самый крепкий враг получает «рассечение» (4 + 6% от его здоровья, до 20); в конце —
	 * огромный крест-разрез на 12. Игрокам — не больше 30 за всё время. Маг под Сопротивлением II. С разрушением —
	 * местность нарезается линиями разрезов, трава и листва скашиваются.
	 */
	private static boolean malevolentShrine(Cast c) {
		SpellKit.release(c);
		Vec3 feet = c.caster.position();
		double r = 24.0;
		int life = 160;
		MagicFx.soundFar(c.level, feet, MagicSounds.RELEASE_ULTIMATE, 160, 0.8F);
		MagicFx.soundFar(c.level, feet, MagicSounds.DOMAIN, 160, 0.7F);
		MagicFx.soundFar(c.level, feet, SoundEvents.BELL_RESONATE, 96, 0.5F);
		MagicFx.flash(c.level, feet, 64, 0.8F, CRIMSON, 20);
		Magic.shake(c.level, feet, 2.5F, 30, 64);
		// Пять кругов под магом — башней, по одному; земля краснеет до радиуса 24, небо — круг над территорией.
		for (int i = 0; i < 5; i++) {
			int n = i;
			Zones.later(c, 3 * i, () -> {
				MagicFx.circle(c.level, School.DARK, 7, n % 2 == 0 ? CRIMSON : DEEP, BLOOD_LIGHT, feet.add(0, 0.1 + 0.55 * n, 0), UP,
						4.5F - 0.6F * n, 1, 0, 4, life - 3 * n, 20, (n % 2 == 0 ? 1 : -1) * 0.15F);
				MagicFx.sound(c.level, feet, MagicSounds.CIRCLE_COMPLETE, 1.2F, 0.6F + 0.1F * n);
			});
		}
		MagicFx.circle(c.level, School.DARK, 7, CRIMSON, DEEP, feet.add(0, 0.07, 0), UP, (float) r, 1, 0, 24, life - 20, 30, 0.03F);
		MagicFx.circle(c.level, School.DARK, 7, DEEP, CRIMSON, feet.add(0, 32, 0), DOWN, (float) r + 8, 2, 1.5F, 30, life - 30, 30, -0.04F);
		MagicFx.shockwave(c.level, feet.add(0, 0.3, 0), UP, 1.0F, (float) r, CRIMSON, 30);
		MagicFx.shockwave(c.level, feet.add(0, 1.0, 0), UP, 1.0F, (float) r * 1.2F, DEEP, 40);
		Vec3 back = flat(c.look()).scale(-6.0);
		BlockPos shrine = BlockPos.containing(Targeting.ground(c.level, c.caster, feet.add(back).add(0, 3, 0)).add(0, 0.05, 0));
		buildShrine(c, shrine, life + 20);
		c.caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, life + 20, 1), c.caster);
		Map<LivingEntity, float[]> toPlayers = new HashMap<>();
		Vec3 look = flat(c.look());
		Zones.add(c, feet, r, life, (z, age) -> domainTick(c, feet, r, age, life, look, toPlayers));
		return true;
	}

	/** Святилище за спиной: костяные столбы, крыша из красного кирпича, алтарь из плачущего обсидиана. */
	private static void buildShrine(Cast c, BlockPos o, int ticks) {
		BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
		BlockState roof = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		for (int y = 0; y <= 5; y++) {
			int layer = y;
			Zones.later(c, 3 * y, () -> {
				int left = ticks - 3 * layer;
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
						BlockState s = null;
						if (layer == 0 && x == 0 && z == 0) s = Blocks.CRYING_OBSIDIAN.defaultBlockState();
						else if (layer <= 3 && corner) s = bone;
						else if (layer == 4) s = roof;
						else if (layer == 5 && (x == 0 || z == 0) && Math.abs(x) + Math.abs(z) <= 2) s = bone;
						if (s != null) Terrain.temp(c, o.offset(x, layer, z), s, left, false);
					}
				}
				Vec3 at = Vec3.atCenterOf(o.above(layer));
				MagicFx.cloud(c.level, at, MagicParticles.spark(CRIMSON, 0.3F, 14), 12, 2.0, 0.3, 2.0, 0.02);
				MagicFx.cloud(c.level, at, ParticleTypes.LARGE_SMOKE, 8, 2.0, 0.3, 2.0, 0.01);
				MagicFx.sound(c.level, at, SoundEvents.BONE_BLOCK_PLACE, 1.5F, 0.6F);
				MagicFx.sound(c.level, at, MagicSounds.IMPACT.get(School.EARTH), 0.8F, 0.5F);
			});
		}
	}

	private static void domainTick(Cast c, Vec3 feet, double r, int age, int life, Vec3 look, Map<LivingEntity, float[]> toPlayers) {
		var rnd = c.level.random;
		// Бесчисленные тонкие белые разрезы по всей территории.
		if (age % 2 == 0) {
			for (int i = 0; i < 3; i++) {
				Vec3 p = SpellKit.inDisc(rnd, feet, r).add(0, 0.3 + rnd.nextDouble() * 3.0, 0);
				Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, (rnd.nextDouble() - 0.5) * 0.6, rnd.nextDouble() - 0.5);
				MagicFx.slash(c.level, p, dir, 1.2F + rnd.nextFloat() * 1.8F, 3.0F + rnd.nextFloat() * 4.0F, 0xFFFFFF, CRIMSON, 5);
			}
		}
		if (age % 10 == 0) Magic.shake(c.level, feet, 0.35F, 12, r + 16);
		if (age % 40 == 0) MagicFx.soundFar(c.level, feet, MagicSounds.LOOP_STORM, 64, 0.5F);
		if (age % 4 == 0) {
			// «Разборка»: каждого внутри.
			List<LivingEntity> foes = c.foesAround(feet, r);
			if (foes.size() > 40) foes = foes.subList(0, 40);
			for (LivingEntity e : foes) {
				capped(c, e, c.amount(1.5F), toPlayers);
				bleed(c, e, 60);
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(CRIMSON, 0.2F, 8), 3, 0.3, 0.1);
			}
			for (int i = 0; i < 2; i++) {
				MagicFx.sound(c.level, SpellKit.inDisc(rnd, feet, r * 0.6), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.4F + rnd.nextFloat() * 0.5F);
			}
			// Разрезы по местности (если разрешено): шесть линий длиной 4, траву и листву скашивает.
			if (Terrain.griefing(c.level)) {
				for (int i = 0; i < 6; i++) {
					Vec3 p = SpellKit.inDisc(rnd, feet, r).add(0, -1.0 + rnd.nextDouble() * 5.0, 0);
					if (p.distanceTo(feet) < 4.0) continue;
					Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, (rnd.nextDouble() - 0.5) * 0.8, rnd.nextDouble() - 0.5).normalize();
					Terrain.tunnel(c, p, p.add(dir.scale(4.0)), 0.5, Terrain.Rim.NONE);
					Terrain.shatter(c, p, 1.5);
				}
			}
		}
		if (age % 20 == 10) {
			// «Рассечение»: самому крепкому врагу — 4 + 6% его здоровья (до 20).
			c.foesAround(feet, r).stream().max(Comparator.comparingDouble(LivingEntity::getMaxHealth)).ifPresent(e -> {
				float dmg = c.amount(Math.min(20.0F, 4.0F + 0.06F * e.getMaxHealth()));
				capped(c, e, dmg, toPlayers);
				Vec3 at = Cast.center(e);
				MagicFx.slash(c.level, at.subtract(look.scale(3)), look, 2.5F, 6.0F, 0xFFFFFF, CRIMSON, 6);
				MagicFx.burst(c.level, at, new DustParticleOptions(0x8A0018, 1.6F), 30, 0.4, 0.15);
				MagicFx.sound(c.level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
				MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 1.2F, 0.7F);
			});
		}
		if (age == life - 20) {
			// Огромный крест-разрез через всю территорию.
			Vec3 a = rotY(look, 45);
			Vec3 b = rotY(look, -45);
			MagicFx.slash(c.level, feet.add(0, 1.5, 0).subtract(a.scale(r)), a, 14.0F, (float) r * 2, 0xFFFFFF, CRIMSON, 12);
			MagicFx.slash(c.level, feet.add(0, 1.5, 0).subtract(b.scale(r)), b, 14.0F, (float) r * 2, 0xFFFFFF, CRIMSON, 12);
			MagicFx.flash(c.level, feet, 64, 0.9F, CRIMSON, 14);
			MagicFx.soundFar(c.level, feet, SoundEvents.WARDEN_SONIC_BOOM, 128, 0.6F);
			MagicFx.soundFar(c.level, feet, MagicSounds.EXPLOSION_HUGE, 160, 0.8F);
			Magic.shake(c.level, feet, 3.0F, 24, 64);
			for (LivingEntity e : c.foesAround(feet, r)) {
				capped(c, e, c.amount(12.0F), toPlayers);
				MagicFx.burst(c.level, Cast.center(e), new DustParticleOptions(0x8A0018, 1.8F), 30, 0.4, 0.2);
			}
		}
		if (age == life - 1) {
			MagicFx.soundFar(c.level, feet, MagicSounds.RUMBLE, 128, 0.5F);
			MagicFx.shockwave(c.level, feet.add(0, 0.3, 0), UP, (float) r, 2.0F, CRIMSON, 20);
		}
	}

	/** Урон с пределом для игроков (30 за всю территорию). */
	private static void capped(Cast c, LivingEntity e, float dmg, Map<LivingEntity, float[]> toPlayers) {
		if (e instanceof Player) {
			float[] got = toPlayers.computeIfAbsent(e, k -> new float[1]);
			dmg = Math.min(dmg, 30.0F - got[0]);
			if (dmg <= 0) return;
			got[0] += dmg;
		}
		c.hurt(e, dmg, MagicDamage.BLOOD);
	}

	// ==================================================================== Общее

	/** Боссы: урон получают, но не оцепенение и не мгновенную смерть. */
	static boolean isBoss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	/**
	 * Урон «сквозь броню»: у магических видов урона нет пометки «мимо брони», поэтому урон заранее увеличивается
	 * ровно настолько, сколько срежет броня цели.
	 */
	static float throughArmor(LivingEntity e, float dmg) {
		float armor = e.getArmorValue();
		if (armor <= 0) return dmg;
		float tough = (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		float eff = Mth.clamp(armor - dmg / (2.0F + tough / 4.0F), armor * 0.2F, 20.0F);
		float k = 1.0F - eff / 25.0F;
		return dmg / Math.max(0.2F, k);
	}

	/** Оцепенение («остановленное время»): ни шагу, ни удара; тело замирает в воздухе (только падает). */
	static void stun(Cast c, LivingEntity e, int ticks) {
		if (isBoss(e)) return;
		e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, ticks, 0), c.caster);
		Zones.add(c, Cast.center(e), 0, ticks, (z, age) -> {
			if (!e.isAlive()) {
				z.stop();
				return;
			}
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(0, Math.min(0, v.y), 0);
			if (age % 6 == 0) {
				MagicFx.one(c.level, e.position().add(0, e.getBbHeight() + 0.3, 0), MagicParticles.glyph(CRIMSON, 0.25F, 10), 0, 0.01, 0);
			}
		});
	}

	/** Кровотечение: 1 урон кровью в секунду и капли, пока не истечёт (повторное — продлевает). */
	static void bleed(Cast c, LivingEntity e, int ticks) {
		int[] left = BLEEDING.get(e);
		if (left != null) {
			left[0] = Math.max(left[0], ticks);
			return;
		}
		int[] l = {ticks};
		BLEEDING.put(e, l);
		Zones.add(c, Cast.center(e), 0, 20 * 60, (z, age) -> {
			if (!e.isAlive() || --l[0] <= 0) {
				BLEEDING.remove(e);
				z.stop();
				return;
			}
			if (age % 5 == 0) {
				MagicFx.one(c.level, e.position().add(0, e.getBbHeight() * 0.6, 0), new DustParticleOptions(0x8A0018, 1.0F), 0, -0.05, 0);
			}
			if (age % 20 == 0) c.hurt(e, 1.0F, MagicDamage.BLOOD);
		});
	}

	/** Направление по горизонтали. */
	static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Повернуть вектор вокруг вертикали на {@code deg} градусов. */
	static Vec3 rotY(Vec3 v, double deg) {
		double a = Math.toRadians(deg);
		double cos = Math.cos(a), sin = Math.sin(a);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	/** Для самопроверки: под действием ли кровотечения существо. */
	public static boolean bleeding(Entity e) {
		return e instanceof LivingEntity l && BLEEDING.containsKey(l);
	}
}
