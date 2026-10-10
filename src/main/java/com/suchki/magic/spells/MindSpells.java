package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.MIND;
import static com.suchki.magic.spells.SpellKit.UP;

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
import com.suchki.magic.Spells;
import com.suchki.magic.Zones;
import com.suchki.mixin.MannequinInvoker;
import com.suchki.mixin.MobAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Разум: взор мудреца, жажда убийства, колыбельная, невидимость, безумие, власть над душами, зеркальные двойники,
 * массовый ужас, лунный кошмар и совершенный гипноз. Вся магия разума — урон {@link MagicDamage#PSYCHIC} и
 * состояния мобов из {@link Minds} (страх, умиротворение, бешенство, двойник); боссов не подчиняет и не усыпляет —
 * им достаются слабость, замедление и урон.
 */
public final class MindSpells {
	static final int PINK = School.MIND.color;
	static final int PALE = School.MIND.light;
	static final int VIOLET = 0x9B5CFF;
	static final int CYAN = 0x7FE8FF;
	static final int MAGENTA = 0xC0187A;
	static final int CRIMSON = 0xD0123A;
	static final int NIGHT = 0x1A0710;
	static final int GOLD = 0xFFD36B;

	// --- Взор Мудреца ---
	static final int EYE_VISION = 4800;
	static final double EYE_R = 32.0;
	static final double EYE_PER_COPY = 16.0;
	static final double EYE_MAX_R = 96.0;
	static final int EYE_GLOW = 600;
	static final int EYE_SWEEP = 10;
	static final int EYE_PULSE = 40;
	static final int EYE_HUD = 200;
	static final double EYE_HUD_RANGE = 64.0;
	// --- Жажда Убийства ---
	static final double FEAR_RANGE = 32.0;
	static final double FEAR_R = 4.0;
	static final int FEAR_T = 160;
	// --- Колыбельная ---
	static final double CALM_RANGE = 40.0;
	static final double CALM_R = 5.0;
	static final int CALM_T = 400;
	static final int SLEEP_T = 160;
	static final float SLEEP_MAX_HP = 30.0F;
	static final float WAKE_MAX = 8.0F;
	// --- Пустое Место ---
	static final int INVIS_T = 600;
	static final int REVEAL_T = 40;
	static final double INVIS_R = 32.0;
	// --- Безумие ---
	static final double FRENZY_RANGE = 48.0;
	static final double FRENZY_R = 5.0;
	static final int FRENZY_T = 300;
	// --- Глас Владыки Душ ---
	static final double DOM_RANGE = 48.0;
	static final double DOM_R = 4.0;
	static final int DOM_T = 1200;
	static final int DOM_T_STRONG = 1800;
	static final float DOM_RESIST = 10.0F;
	// --- Зеркальная Луна ---
	static final int MIRROR_T = 300;
	static final float MIRROR_HP = 20.0F;
	static final double MIRROR_R = 16.0;
	static final float SHARD_DMG = 4.0F;
	// --- Массовая Истерия ---
	static final double HYST_R = 16.0;
	static final double HYST_PER_COPY = 6.0;
	static final float HYST_DMG = 12.0F;
	static final int HYST_T = 240;
	// --- Лунный Кошмар ---
	static final double MOON_RANGE = 96.0;
	static final double MOON_R = 6.0;
	static final int MOON_STUN = 120;
	static final float MOON_PULSE = 4.0F;
	static final int MOON_EVERY = 12;
	static final float MOON_SIDE = 15.0F;
	// --- Совершенный Гипноз ---
	static final double HYP_R = 40.0;
	static final int HYP_BUILD = 10;
	static final int HYP_WAVE = 20;
	static final int HYP_T = 200;
	static final float HYP_PULSE = 4.0F;
	static final float HYP_FINALE = 30.0F;
	static final int HYP_COPIES = 6;

	private MindSpells() {}

	public static void register() {
		// --- Ранг 1 — Начальный ---
		Spell.of("night_eye", MIND, 1, SELF).anchor(Anchor.FEET).mana(10).charge(8).cooldown(40).range(EYE_R).multicast(4)
				.icon("golden_carrot")
				.action(MindSpells::sageEye)
				.register();
		Spell.of("fear", MIND, 1, BOLT).anchor(Anchor.TARGET).mana(12).charge(12).cooldown(40).range(FEAR_RANGE).icon("goat_horn")
				.action(MindSpells::killingIntent)
				.register();

		// --- Ранг 2 — Средний ---
		Spell.of("calm", MIND, 2, BOLT).anchor(Anchor.TARGET).mana(20).charge(14).cooldown(120).range(CALM_RANGE).icon("pink_petals")
				.action(MindSpells::lullaby)
				.register();
		Spell.of("invisibility", MIND, 2, SELF).anchor(Anchor.FEET).mana(24).charge(16).cooldown(300).range(INVIS_R).multicast(4)
				.icon("glass")
				.action(MindSpells::emptySpace)
				.register();

		// --- Ранг 3 — Продвинутый ---
		Spell.of("frenzy", MIND, 3, BOLT).anchor(Anchor.TARGET).mana(36).charge(20).cooldown(300).range(FRENZY_RANGE).icon("red_dye")
				.action(MindSpells::frenzy)
				.register();

		// --- Ранг 4 — Святой ---
		Spell.of("dominate", MIND, 4, BOLT).anchor(Anchor.TARGET).layers(2).mana(62).charge(34).cooldown(600).range(DOM_RANGE)
				.icon("golden_helmet")
				.action(MindSpells::dominate)
				.register();
		Spell.of("mirror_image", MIND, 4, SELF).anchor(Anchor.FEET).layers(2).mana(52).charge(24).cooldown(400).range(MIRROR_R)
				.multicast(4).icon("armor_stand")
				.action(MindSpells::mirrorMoon)
				.register();

		// --- Ранг 5 — Королевский ---
		Spell.of("mass_hysteria", MIND, 5, RITUAL).anchor(Anchor.FEET).layers(2).mana(90).charge(40).cooldown(900).range(HYST_R)
				.icon("echo_shard")
				.action(MindSpells::massHysteria)
				.register();

		// --- Ранг 6 — Императорский ---
		Spell.of("moonlit_nightmare", MIND, 6, BOLT).anchor(Anchor.TARGET).layers(3).mana(145).charge(70).cooldown(1600)
				.range(MOON_RANGE).icon("ender_eye")
				.action(MindSpells::moonlitNightmare)
				.register();

		// --- Ранг 7 — Божественный ---
		Spell.of("perfect_hypnosis", MIND, 7, RITUAL).anchor(Anchor.FEET).layers(5).circle(3.0F).mana(260).charge(130)
				.cooldown(6000).range(HYP_R).icon("amethyst_shard")
				.action(MindSpells::perfectHypnosis)
				.register();
	}

	// =====================================================================================================
	// Ранг 1
	// =====================================================================================================

	/**
	 * Взор Мудреца: ночное зрение, волна сканирования подсвечивает всех вокруг (невидимые проявляются), каждые 2 с
	 * «эн»-импульс отмечает новых; у цели в прицеле — разбор: здоровье, броня, атака, слабости. Копии мультикаста
	 * только расширяют обзор (+16 блоков за копию).
	 */
	private static boolean sageEye(Cast c) {
		if (c.copy > 0) return true;
		SpellKit.release(c);
		ServerPlayer p = c.caster;
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, c.duration(EYE_VISION), 0, false, false, true), p);
		double r = Math.min(EYE_MAX_R, EYE_R + EYE_PER_COPY * (c.copies - 1));
		Vec3 feet = p.position();
		// Круг «моргает» глазом, по земле уходит волна сканирования.
		MagicFx.circle(c.level, School.MIND, 1, PINK, CYAN, feet.add(0, 0.06, 0), UP, 1.5F, 1, 0, 2, 4, 8, 1.2F);
		MagicFx.shockwave(c.level, feet.add(0, 0.3, 0), UP, 0.5F, (float) r, CYAN, EYE_SWEEP + 4);
		MagicFx.one(c.level, p.getEyePosition().add(c.look().scale(0.6)), MagicParticles.rune(CYAN, 0.45F, 14),
				c.look().x, c.look().y, c.look().z);
		MagicFx.burst(c.level, p.getEyePosition(), MagicParticles.mote(CYAN, 0.12F, 20), 12, 0.3, 0.03);
		MagicFx.sound(c.level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.9F);
		MagicFx.sound(c.level, feet, MagicSounds.impactOf(c.spell), 0.5F, 1.8F);
		LivingEntity focus = c.aimLiving(EYE_HUD_RANGE, e -> e instanceof LivingEntity l && l != p && l.isAlive());
		Set<LivingEntity> marked = new HashSet<>();
		int glow = c.duration(EYE_GLOW);
		Zones.add(c, feet, r, glow, (z, age) -> {
			if (!p.isAlive() || p.isRemoved()) {
				z.stop();
				return;
			}
			z.center = p.position();
			if (age <= EYE_SWEEP) {
				if (age % 2 == 0) mark(c, z.center, r * Math.min(1.0, (age + 2) / (double) EYE_SWEEP), glow - age, marked);
			} else if (age % EYE_PULSE == 0) {
				// «Эн»: тихий импульс, новые в радиусе тоже подсвечиваются.
				MagicFx.shockwave(c.level, z.center.add(0, 0.2, 0), UP, 0.5F, (float) r, VIOLET, 12);
				mark(c, z.center, r, glow - age, marked);
			}
			if (focus != null && age <= EYE_HUD && age % 40 == 0 && focus.isAlive()) p.displayClientMessage(analysis(focus), true);
			if (age % 6 == 0 && age < EYE_SWEEP + 30) {
				MagicFx.one(c.level, p.getEyePosition().add(0, 0.25, 0), MagicParticles.spark(CYAN, 0.08F, 10), 0, 0.02, 0);
			}
		});
		return true;
	}

	/** Подсветить существ в радиусе (по вертикали ±16): свечение, невидимые проявляются. */
	private static void mark(Cast c, Vec3 center, double r, int ticks, Set<LivingEntity> marked) {
		AABB box = new AABB(center, center).inflate(r, 16, r);
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (e == c.caster || e instanceof Mannequin) continue;
			double dx = e.getX() - center.x, dz = e.getZ() - center.z;
			if (dx * dx + dz * dz > r * r) continue;
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(20, ticks), 0, false, false), c.caster);
			if (!c.isAlly(e) && e.hasEffect(MobEffects.INVISIBILITY)) {
				e.removeEffect(MobEffects.INVISIBILITY);
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(CYAN, 0.2F, 14), 10, 0.3, 0.03);
			}
			if (marked.add(e)) MagicFx.burst(c.level, e.getEyePosition().add(0, 0.5, 0), MagicParticles.mote(c.isAlly(e) ? 0x7CFF8A
					: c.isEnemy(e) ? 0xFF4A4A : GOLD, 0.12F, 16), 3, 0.15, 0.01);
		}
	}

	/** Разбор существа для панели над хотбаром: имя, здоровье, броня, атака, слабости. */
	public static Component analysis(LivingEntity e) {
		MutableComponent t = Component.empty().append(e.getDisplayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
		t.append(Component.literal(String.format(Locale.ROOT, "  ❤ %.0f/%.0f", e.getHealth(), e.getMaxHealth()))
				.withStyle(ChatFormatting.RED));
		t.append(Component.literal("  ◈ " + e.getArmorValue()).withStyle(ChatFormatting.GRAY));
		AttributeInstance atk = e.getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) t.append(Component.literal(String.format(Locale.ROOT, "  ⚔ %.1f", atk.getValue())).withStyle(ChatFormatting.GOLD));
		if (Cast.isUndead(e)) t.append(Component.literal("  ☀✦").withStyle(ChatFormatting.YELLOW));
		if (e.fireImmune()) t.append(Component.literal("  ♨✗").withStyle(ChatFormatting.DARK_RED));
		return t;
	}

	/**
	 * Жажда Убийства: волна давления у прицела — мобы в радиусе 4 бегут от мага 8 с и не нападают. Боссы — только
	 * замедление и слабость на 3 с.
	 */
	private static boolean killingIntent(Cast c) {
		SpellKit.release(c);
		Vec3 at = focus(c);
		Vec3 ground = c.aimGround();
		double r = c.radius(FEAR_R);
		int t = c.duration(FEAR_T);
		List<LivingEntity> scared = new ArrayList<>();
		for (LivingEntity e : c.foesAround(at, r + 0.5)) {
			if (e instanceof Mob m && !isBoss(m)) {
				Minds.apply(m, Minds.Mode.FEAR, t, c.caster.position(), null);
				scared.add(e);
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), c.caster);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), c.caster);
			}
		}
		// Тёмная рябь и волна дыма, у напуганных — дрожь.
		mindWave(c, ground, r, MAGENTA, PINK);
		MagicFx.ring(c.level, ground.add(0, 0.4, 0), r * 0.5, ParticleTypes.LARGE_SMOKE, 18, 0.18);
		MagicFx.one(c.level, ground.add(0, 2.2, 0), MagicParticles.rune(MAGENTA, 0.8F, 20), 0, 1, 0);
		MagicFx.sound(c.level, ground, MagicSounds.VANISH, 1.0F, 0.45F);
		MagicFx.sound(c.level, ground, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 1.0F);
		Zones.later(c, 14, () -> MagicFx.sound(c.level, ground, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.5F, 0.6F));
		tremble(c, scared, Math.min(t, 80));
		return true;
	}

	// =====================================================================================================
	// Ранг 2
	// =====================================================================================================

	/**
	 * Колыбельная Счастливых Дней: розовый туман у прицела — мобы в радиусе 5 забывают бой на 20 с, а слабые (до 30
	 * здоровья) засыпают на 8 с; первый удар по спящему наносит ещё +50% (до 8) уроном разума и будит его.
	 * Хранители и эндермены не поддаются, боссы — только тошнота.
	 */
	private static boolean lullaby(Cast c) {
		SpellKit.release(c);
		Vec3 at = focus(c);
		Vec3 ground = c.aimGround();
		double r = c.radius(CALM_R);
		int t = c.duration(CALM_T);
		int sleep = c.duration(SLEEP_T);
		for (LivingEntity e : c.foesAround(at, r + 0.5)) {
			if (e instanceof Warden || e instanceof EnderMan) {
				MagicFx.burst(c.level, e.getEyePosition(), MagicParticles.spark(PALE, 0.2F, 10), 6, 0.3, 0.02);
				MagicFx.sound(c.level, e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.6F);
				continue;
			}
			if (!(e instanceof Mob m) || isBoss(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0), c.caster);
				continue;
			}
			Minds.apply(m, Minds.Mode.CALM, t, c.caster.position(), null);
			MagicFx.burst(c.level, m.getEyePosition().add(0, 0.4, 0), ParticleTypes.HEART, 2, 0.2, 0);
			if (m.getMaxHealth() <= SLEEP_MAX_HP) sleep(c, m, sleep);
		}
		// Пастельный купол тумана с пузырями снов и лепестками.
		MagicFx.dome(c.level, ground, (float) r, PINK, GOLD, 40);
		MagicFx.cloud(c.level, ground.add(0, 1.0, 0), ParticleTypes.CHERRY_LEAVES, 30, r * 0.5, 0.8, r * 0.5, 0.01);
		MagicFx.cloud(c.level, ground.add(0, 0.8, 0), MagicParticles.mote(PINK, 0.25F, 40), 20, r * 0.45, 0.5, r * 0.45, 0.01);
		MagicFx.line(c.level, c.hand(), at, MagicParticles.spark(GOLD, 0.12F, 12), 0.5);
		MagicFx.sound(c.level, ground, MagicSounds.impactOf(c.spell), 1.0F, 1.3F);
		for (int i = 0; i < 3; i++) {
			float pitch = new float[] {1.2F, 1.5F, 1.0F}[i];
			Zones.later(c, 4 * i, () -> MagicFx.sound(c.level, ground, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, pitch));
		}
		Zones.add(c, ground, r, 40, (z, age) -> {
			if (age % 3 == 0) {
				MagicFx.one(c.level, SpellKit.inDisc(c.level.random, ground, r).add(0, 0.3, 0), MagicParticles.mote(PALE, 0.3F, 30), 0, 0.03, 0);
			}
		});
		return true;
	}

	/** Сон: моб замирает (паралич); первый удар по нему добавляет урон разума и будит. */
	private static void sleep(Cast c, Mob m, int ticks) {
		m.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, ticks, 0, false, false, true), c.caster);
		m.setTarget(null);
		float[] hp = {m.getHealth()};
		Zones.add(c, m.position(), 1, ticks, (z, age) -> {
			if (!m.isAlive() || !m.hasEffect(MagicEffects.PARALYSIS)) {
				z.stop();
				return;
			}
			if (m.getHealth() < hp[0] - 0.01F) {
				float lost = hp[0] - m.getHealth();
				z.stop();
				m.removeEffect(MagicEffects.PARALYSIS);
				c.hurt(m, Math.min(WAKE_MAX, lost * 0.5F), MagicDamage.PSYCHIC);
				MagicFx.burst(c.level, m.getEyePosition(), MagicParticles.spark(PALE, 0.25F, 12), 10, 0.3, 0.05);
				MagicFx.sound(c.level, m.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.6F);
				return;
			}
			hp[0] = m.getHealth();
			if (age % 20 == 0) MagicFx.one(c.level, m.getEyePosition().add(0.3, 0.5, 0), ParticleTypes.NOTE, 0.2 + 0.1 * (age / 20 % 5), 0, 0);
			if (age % 12 == 0) MagicFx.one(c.level, m.getEyePosition().add(0, 0.4, 0), MagicParticles.mote(PALE, 0.15F, 24), 0, 0.02, 0);
		});
	}

	/**
	 * Пустое Место: невидимость на 30 с; мобы в 32 блоках, что гнались за магом, теряют цель. Удар или зарядка
	 * заклинания 3+ ранга выдаёт мага на 2 с, потом он снова исчезает. Копии мультикаста прячут по союзнику рядом.
	 */
	private static boolean emptySpace(Cast c) {
		SpellKit.release(c);
		if (c.copy > 0) {
			LivingEntity ally = c.alliesAround(Cast.center(c.caster), 4.0).stream()
					.filter(e -> e != c.caster && !e.hasEffect(MobEffects.INVISIBILITY))
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster))).orElse(null);
			if (ally != null) {
				ally.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, c.duration(INVIS_T), 0, false, false, true), c.caster);
				dissolve(c, ally);
			}
			return true;
		}
		ServerPlayer p = c.caster;
		int life = c.duration(INVIS_T);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, life, 0, false, false, true), p);
		dissolve(c, p);
		loseTrack(c, p, Math.min(60, life));
		LivingEntity[] lastMob = {p.getLastHurtMob()};
		int[] lastTs = {p.getLastHurtMobTimestamp()};
		int[] reveal = {0};
		Zones.add(c, p.position(), INVIS_R, life, (z, age) -> {
			if (!p.isAlive() || p.isRemoved()) {
				z.stop();
				return;
			}
			z.center = p.position();
			boolean attacked = p.getLastHurtMob() != lastMob[0] || p.getLastHurtMobTimestamp() != lastTs[0];
			lastMob[0] = p.getLastHurtMob();
			lastTs[0] = p.getLastHurtMobTimestamp();
			String id = p.getAttached(Magic.CASTING);
			Spell casting = id == null ? null : Spells.get(id);
			boolean loud = casting != null && casting != c.spell && casting.tier >= 3;
			if (attacked || loud) {
				if (reveal[0] == 0) {
					// Иллюзия дрогнула: маг проступает сквозь мерцание.
					p.removeEffect(MobEffects.INVISIBILITY);
					MagicFx.burst(c.level, Cast.center(p), MagicParticles.spark(PINK, 0.2F, 10), 12, 0.4, 0.04);
					MagicFx.sound(c.level, p.position(), MagicSounds.GLITCH, 0.6F, 1.4F);
				}
				reveal[0] = REVEAL_T;
				return;
			}
			if (reveal[0] > 0) {
				if (--reveal[0] == 0 && life - age > 1) {
					p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, life - age, 0, false, false, true), p);
					dissolve(c, p);
				}
				return;
			}
			if (age > 2 && !p.hasEffect(MobEffects.INVISIBILITY)) {
				z.stop();
				return;
			}
			if (age % 20 == 0) loseTrack(c, p, 60);
			if (age % 5 == 0) {
				// Едва заметное марево там, где стоит маг.
				MagicFx.one(c.level, p.position().add((c.level.random.nextDouble() - 0.5) * 0.6, c.level.random.nextDouble() * 1.8,
						(c.level.random.nextDouble() - 0.5) * 0.6), MagicParticles.mote(PALE, 0.06F, 10), 0, 0.01, 0);
			}
			if (age == life - 1) MagicFx.sound(c.level, p.position(), MagicSounds.VANISH, 0.5F, 1.6F);
		});
		return true;
	}

	/** Мобы, что гнались за магом, теряют цель. */
	private static void loseTrack(Cast c, LivingEntity who, int ticks) {
		for (Mob m : c.level.getEntitiesOfClass(Mob.class, who.getBoundingBox().inflate(INVIS_R), m -> m.getTarget() == who)) {
			if (isBoss(m)) continue;
			Minds.apply(m, Minds.Mode.CALM, ticks, who.position(), null);
			m.setTarget(null);
			MagicFx.burst(c.level, m.getEyePosition().add(0, 0.5, 0), ParticleTypes.SMOKE, 3, 0.15, 0.01);
		}
	}

	/** Растворение в воздухе снизу вверх: мерцающие искры и марево. */
	private static void dissolve(Cast c, LivingEntity e) {
		for (int i = 0; i < 4; i++) {
			int k = i;
			Zones.later(c, i * 2, () -> {
				Vec3 at = e.position().add(0, 0.45 * k + 0.1, 0);
				MagicFx.ring(c.level, at, 0.45, MagicParticles.spark(k % 2 == 0 ? PINK : PALE, 0.12F, 12), 10, 0.02);
				MagicFx.burst(c.level, at, ParticleTypes.WITCH, 3, 0.25, 0.01);
			});
		}
		MagicFx.sound(c.level, e.position(), MagicSounds.GLITCH, 0.7F, 1.6F);
		MagicFx.sound(c.level, e.position(), MagicSounds.VANISH, 0.8F, 1.2F);
	}

	// =====================================================================================================
	// Ранг 3
	// =====================================================================================================

	/**
	 * Безумие: мобы в радиусе 5 у прицела 15 с дерутся друг с другом и бьют на 20% сильнее. Боссы — тошнота и
	 * блуждание 3 с.
	 */
	private static boolean frenzy(Cast c) {
		SpellKit.release(c);
		Vec3 at = focus(c);
		Vec3 ground = c.aimGround();
		double r = c.radius(FRENZY_R);
		int t = c.duration(FRENZY_T);
		List<LivingEntity> mad = new ArrayList<>();
		for (LivingEntity e : c.foesAround(at, r + 0.5)) {
			if (!(e instanceof Mob m)) continue;
			if (isBoss(m)) {
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0), c.caster);
				wander(c, m, 60);
				continue;
			}
			Minds.apply(m, Minds.Mode.FRENZY, t, c.caster.position(), null);
			rage(c, m, 0.2, t);
			mad.add(m);
			MagicFx.burst(c.level, m.getEyePosition().add(0, 0.4, 0), MagicParticles.spark(CRIMSON, 0.2F, 12), 8, 0.25, 0.04);
		}
		mindWave(c, ground, r, CRIMSON, PINK);
		MagicFx.one(c.level, ground.add(0, 2.4, 0), MagicParticles.rune(CRIMSON, 1.0F, 24), 0, 1, 0);
		MagicFx.sound(c.level, ground, MagicSounds.impactOf(c.spell), 1.0F, 0.7F);
		MagicFx.sound(c.level, ground, MagicSounds.GLITCH, 1.0F, 0.8F);
		MagicFx.sound(c.level, ground, SoundEvents.RAVAGER_ROAR, 0.6F, 1.5F);
		// Красные вспышки-глитчи над головами безумцев.
		Zones.add(c, ground, r, Math.min(t, 100), (z, age) -> {
			if (age % 6 != 0) return;
			for (LivingEntity e : mad) {
				if (e.isAlive()) MagicFx.one(c.level, e.getEyePosition().add(0, 0.6, 0), MagicParticles.spark(CRIMSON, 0.18F, 6), 0, 0.03, 0);
			}
		});
		return true;
	}

	// =====================================================================================================
	// Ранг 4
	// =====================================================================================================

	/**
	 * Глас Владыки Душ: моб под прицелом и ещё до двух в 4 блоках от него. Не сильнее 60 + 2·уровень мага здоровья —
	 * становятся слугами на 60 с (90 при полной силе): защищают мага и бьют его врагов на 20% сильнее, держатся
	 * рядом, не взрываются у хозяина. Удар мага по слуге снимает чары. Сильные — 10 урона разума, тошнота и
	 * блуждание 6 с; боссы — «На колени!»: замедление IV и слабость II на 3 с. Слуг не больше 3 (с 40 уровня — 5),
	 * мультикаст даёт +2 места за копию; новый слуга отпускает самого старого.
	 */
	private static boolean dominate(Cast c) {
		Vec3 at = focus(c);
		List<LivingEntity> victims = new ArrayList<>();
		if (c.target().entity() instanceof LivingEntity l && c.isFoe(l)) victims.add(l);
		Vec3 around = victims.isEmpty() ? at : Cast.center(victims.get(0));
		List<LivingEntity> near = c.foesAround(around, DOM_R + 0.5);
		near.sort(Comparator.comparingDouble(e -> Cast.center(e).distanceToSqr(around)));
		for (LivingEntity e : near) {
			if (victims.size() >= 3) break;
			if (!victims.contains(e)) victims.add(e);
		}
		Vec3 ground = c.aimGround();
		if (victims.isEmpty()) {
			MagicFx.burst(c.level, ground.add(0, 1, 0), MagicParticles.spark(VIOLET, 0.2F, 10), 10, 0.5, 0.03);
			MagicFx.sound(c.level, ground, MagicSounds.FIZZLE, 0.8F, 1.0F);
			return false;
		}
		SpellKit.release(c);
		int ticks = c.strong() ? DOM_T_STRONG : DOM_T;
		int limit = 60 + 2 * Magic.mageLevel(c.caster);
		int slots = (Magic.mageLevel(c.caster) >= 40 ? 5 : 3) + 2 * (c.copies - 1);
		MagicFx.circle(c.level, School.MIND, 4, VIOLET, GOLD, ground.add(0, 0.12, 0), UP, (float) DOM_R + 1, 2, 0.4F, 4, 20, 12, 0.3F);
		MagicFx.sound(c.level, ground, MagicSounds.RELEASE_HEAVY, 1.4F, 0.6F);
		MagicFx.soundFar(c.level, ground, MagicSounds.impactOf(c.spell), 40, 0.6F);
		for (LivingEntity e : victims) {
			Vec3 head = e.getEyePosition().add(0, 0.9, 0);
			Vec3 face = c.eye().subtract(head);
			// Цепь света от руки к жертве и алое око над её головой.
			MagicFx.beam(c.level, c.hand(), Cast.center(e), 0.08F, VIOLET, GOLD, 12);
			MagicFx.one(c.level, head, MagicParticles.rune(CRIMSON, 0.5F, 30), face.x, face.y, face.z);
			MagicFx.shockwave(c.level, Cast.center(e), UP, 0.3F, 2.2F, VIOLET, 10);
			MagicFx.sound(c.level, e.position(), SoundEvents.CHAIN_PLACE, 1.0F, 0.7F);
			if (isBoss(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), c.caster);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), c.caster);
				MagicFx.shockwave(c.level, e.position().add(0, 0.1, 0), UP, 0.5F, 4F, GOLD, 12);
			} else if (e instanceof Mob m && m.getMaxHealth() <= limit) {
				charm(c, m, ticks, slots);
			} else {
				c.hurt(e, c.amount(DOM_RESIST), MagicDamage.PSYCHIC);
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0), c.caster);
				if (e instanceof Mob m) wander(c, m, 120);
				MagicFx.burst(c.level, head, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 14, 0.3, 0.1);
				MagicFx.sound(c.level, e.position(), SoundEvents.GLASS_BREAK, 1.0F, 1.3F);
			}
		}
		return true;
	}

	/** Слуга «Гласа Владыки»: что вернуть, когда чары спадут. */
	private static final class Charm {
		final Mob mob;
		final List<WrappedGoal> targets;
		final Goal follow;
		final Component name;
		final boolean nameShown;
		boolean over;

		Charm(Mob mob, List<WrappedGoal> targets, Goal follow, Component name, boolean nameShown) {
			this.mob = mob;
			this.targets = targets;
			this.follow = follow;
			this.name = name;
			this.nameShown = nameShown;
		}
	}

	/** Слуги каждого мага, от старых к новым. */
	private static final Map<UUID, Deque<Charm>> CHARMS = new HashMap<>();

	/** Сколько слуг сейчас у мага (для самопроверки). */
	public static int servants(ServerPlayer p) {
		Deque<Charm> list = CHARMS.get(p.getUUID());
		if (list == null) return 0;
		list.removeIf(ch -> ch.over || !ch.mob.isAlive());
		return list.size();
	}

	private static void charm(Cast c, Mob m, int ticks, int slots) {
		Deque<Charm> list = CHARMS.computeIfAbsent(c.caster.getUUID(), k -> new ArrayDeque<>());
		list.removeIf(ch -> ch.over || !ch.mob.isAlive());
		while (list.size() >= slots) release(list.pollFirst(), false);
		MobAccessor acc = (MobAccessor) m;
		List<WrappedGoal> saved = new ArrayList<>(acc.suchki$targetSelector().getAvailableGoals());
		acc.suchki$targetSelector().removeAllGoals(g -> true);
		acc.suchki$targetSelector().addGoal(1, new ServantTargetGoal(m, c.caster));
		Goal follow = new FollowMasterGoal(m, c.caster);
		acc.suchki$goalSelector().addGoal(2, follow);
		Component base = m.getName().copy();
		Charm ch = new Charm(m, saved, follow, m.getCustomName(), m.isCustomNameVisible());
		list.addLast(ch);
		// Метка призванного: союзник для всех заклинаний и миксина целей; срок с запасом — снимаем чары сами.
		m.setAttached(Minions.SUMMON, new Minions.Summon(c.caster.getUUID(), c.level.getGameTime() + ticks + 200, VIOLET));
		Minds.clear(m);
		m.setTarget(null);
		m.setPersistenceRequired();
		m.setCustomName(Component.translatableWithFallback("suchki.mind.servant", "Слуга %s", base)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		m.setCustomNameVisible(true);
		m.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), c.caster);
		rage(c, m, 0.2, ticks);
		MagicFx.burst(c.level, m.getEyePosition().add(0, 0.6, 0), ParticleTypes.HEART, 4, 0.3, 0.02);
		MagicFx.one(c.level, m.getEyePosition().add(0, 1.1, 0), MagicParticles.rune(PINK, 0.4F, 40), 0, 1, 0);
		int hurtAt = m.getLastHurtByMobTimestamp();
		LivingEntity hurtBy = m.getLastHurtByMob();
		Zones.add(c, m.position(), 1, ticks, (z, age) -> {
			if (ch.over || !m.isAlive() || m.isRemoved()) {
				ch.over = true;
				z.stop();
				return;
			}
			z.center = m.position();
			// Маг ударил слугу — чары рвутся.
			if (m.getLastHurtByMob() == c.caster && (hurtBy != c.caster || m.getLastHurtByMobTimestamp() != hurtAt)) {
				release(ch, true);
				z.stop();
				return;
			}
			if (m instanceof Creeper cr && cr.distanceToSqr(c.caster) < 25) cr.setSwellDir(-1);
			if (age % 20 == 0) MagicFx.one(c.level, m.getEyePosition().add(0, 0.7, 0), MagicParticles.spark(VIOLET, 0.14F, 14), 0, 0.02, 0);
			if (age >= ticks - 1) {
				release(ch, false);
				z.stop();
			}
		});
	}

	/** Чары спали: моб снова сам по себе (цели, имя, свечение, сила — как были). */
	private static void release(Charm ch, boolean broken) {
		if (ch == null || ch.over) return;
		ch.over = true;
		Mob m = ch.mob;
		if (!m.isAlive() || m.isRemoved()) return;
		MobAccessor acc = (MobAccessor) m;
		acc.suchki$targetSelector().removeAllGoals(g -> true);
		for (WrappedGoal w : ch.targets) acc.suchki$targetSelector().addGoal(w.getPriority(), w.getGoal());
		acc.suchki$goalSelector().removeGoal(ch.follow);
		m.removeAttached(Minions.SUMMON);
		m.setCustomName(ch.name);
		m.setCustomNameVisible(ch.nameShown);
		m.removeEffect(MobEffects.GLOWING);
		calmDown(m, null);
		m.setTarget(null);
		m.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0));
		if (m.level() instanceof net.minecraft.server.level.ServerLevel level) {
			MagicFx.burst(level, m.getEyePosition(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PURPLE_STAINED_GLASS.defaultBlockState()),
					12, 0.3, 0.08);
			MagicFx.sound(level, m.position(), broken ? SoundEvents.GLASS_BREAK : MagicSounds.VANISH, 0.9F, broken ? 1.1F : 1.4F);
		}
	}

	/** Цель слуги: кто ударил хозяина, кого ударил хозяин, иначе ближайший монстр рядом. */
	static final class ServantTargetGoal extends TargetGoal {
		private final ServerPlayer master;
		private LivingEntity candidate;
		private int wait;

		ServantTargetGoal(Mob mob, ServerPlayer master) {
			super(mob, false);
			this.master = master;
			setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--wait > 0) return false;
			wait = 10;
			LivingEntity attacker = master.getLastHurtByMob();
			if (attacker != null && master.tickCount - master.getLastHurtByMobTimestamp() < 200 && valid(attacker)) {
				candidate = attacker;
				return true;
			}
			LivingEntity victim = master.getLastHurtMob();
			if (victim != null && master.tickCount - master.getLastHurtMobTimestamp() < 200 && valid(victim)) {
				candidate = victim;
				return true;
			}
			candidate = null;
			double best = 16 * 16;
			for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16),
					o -> o instanceof Enemy && valid(o))) {
				double d = other.distanceToSqr(mob);
				if (d < best) {
					best = d;
					candidate = other;
				}
			}
			return candidate != null;
		}

		private boolean valid(LivingEntity e) {
			return e.isAlive() && e != mob && !Cast.allyOf(master, e) && !Minions.isSummon(e) && canAttack(e, TargetingConditions.DEFAULT);
		}

		@Override
		public void start() {
			mob.setTarget(candidate);
			super.start();
		}
	}

	/** Слуга держится в 12 блоках от хозяина, отставших (дальше 24) переносит к нему. */
	static final class FollowMasterGoal extends Goal {
		private final Mob mob;
		private final ServerPlayer master;
		private int recalc;

		FollowMasterGoal(Mob mob, ServerPlayer master) {
			this.mob = mob;
			this.master = master;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return mob.getTarget() == null && master.isAlive() && master.level() == mob.level() && mob.distanceToSqr(master) > 144;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.getTarget() == null && master.isAlive() && master.level() == mob.level() && mob.distanceToSqr(master) > 16;
		}

		@Override
		public void tick() {
			if (--recalc > 0) return;
			recalc = 10;
			if (mob.distanceToSqr(master) > 24 * 24) {
				mob.teleportTo(master.getX(), master.getY(), master.getZ());
				mob.getNavigation().stop();
			} else {
				mob.getNavigation().moveTo(master, 1.2);
			}
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}
	}

	/**
	 * Зеркальная Луна: два двойника с обликом и снаряжением мага слева и справа на 15 с (20 здоровья каждый); враги в
	 * 16 блоках бьют их. Разбитый двойник разлетается осколками: слепота 2 с и 4 урона разума в радиусе 3. Маг на
	 * 3 с невидим. Копия мультикаста — ещё один двойник.
	 */
	private static boolean mirrorMoon(Cast c) {
		SpellKit.release(c);
		int life = c.duration(MIRROR_T);
		Vec3 feet = c.caster.position();
		Vec3 right = c.right();
		List<Vec3> spots = new ArrayList<>();
		if (c.copy == 0) {
			spots.add(feet.add(right.scale(-1.6)));
			spots.add(feet.add(right.scale(1.6)));
		} else {
			double side = c.copy % 2 == 1 ? -1 : 1;
			spots.add(feet.add(right.scale(side * (1.6 + (c.copy + 1) / 2))).add(c.look().multiply(1, 0, 1).scale(-0.8)));
		}
		MagicFx.circle(c.level, School.MIND, 4, PINK, PALE, feet.add(0, 0.06, 0), UP, 2.2F, 2, 0.3F, 3, 10, 12, 0.6F);
		MagicFx.sound(c.level, feet, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 1.0F);
		MagicFx.sound(c.level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
		for (Vec3 spot : spots) {
			Mannequin decoy = reflection(c, spot, MIRROR_HP, life);
			if (decoy == null) continue;
			MagicFx.line(c.level, Cast.center(c.caster), Cast.center(decoy), MagicParticles.spark(PALE, 0.14F, 12), 0.25);
			watchMirror(c, decoy, life);
		}
		if (c.copy == 0) c.caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true), c.caster);
		retarget(c, MIRROR_R, life);
		return true;
	}

	/** Двойники мага (по магам) — враги распределяются по всем живым двойникам. */
	private static final Map<UUID, List<Mannequin>> MIRRORS = new HashMap<>();

	/** Отражение мага: манекен с его обликом, снаряжением и именем; призван на {@code life} тиков. */
	private static Mannequin reflection(Cast c, Vec3 near, float hp, int life) {
		Mannequin decoy = EntityType.MANNEQUIN.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (decoy == null) return null;
		Vec3 at = c.safeSpot(near);
		if (at == null) at = c.caster.position();
		decoy.snapTo(at.x, at.y, at.z, c.caster.getYRot(), c.caster.getXRot());
		decoy.setYHeadRot(c.caster.getYHeadRot());
		decoy.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(c.caster.getGameProfile()));
		((MannequinInvoker) decoy).suchki$setHideDescription(true);
		decoy.setCustomName(c.caster.getName());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
				decoy.setItemSlot(slot, c.caster.getItemBySlot(slot).copy());
			}
		}
		AttributeInstance max = decoy.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) max.setBaseValue(hp);
		decoy.setHealth(hp);
		Minions.summon(c, decoy, life);
		MagicFx.burst(c.level, at.add(0, 1, 0), MagicParticles.spark(PINK, 0.3F, 18), 16, 0.4, 0.04);
		MagicFx.burst(c.level, at.add(0, 1, 0), ParticleTypes.END_ROD, 6, 0.3, 0.02);
		List<Mannequin> list = MIRRORS.computeIfAbsent(c.caster.getUUID(), k -> new ArrayList<>());
		list.removeIf(m -> !m.isAlive() || m.isRemoved());
		list.add(decoy);
		return decoy;
	}

	/** Двойник повторяет взмахи мага; разбитый — разлетается осколками. */
	private static void watchMirror(Cast c, Mannequin decoy, int life) {
		boolean[] swung = {c.caster.swinging};
		Zones.add(c, decoy.position(), 3, life + 5, (z, age) -> {
			if (decoy.isDeadOrDying()) {
				shatter(c, decoy);
				z.stop();
				return;
			}
			if (decoy.isRemoved()) {
				z.stop();
				return;
			}
			if (c.caster.swinging && !swung[0]) decoy.swing(InteractionHand.MAIN_HAND);
			swung[0] = c.caster.swinging;
			if (age % 10 == 0) MagicFx.one(c.level, Cast.center(decoy).add((c.level.random.nextDouble() - 0.5) * 0.6, 0,
					(c.level.random.nextDouble() - 0.5) * 0.6), MagicParticles.mote(PALE, 0.08F, 12), 0, 0.02, 0);
		});
	}

	/** Осколки зеркала: слепота и урон разума вокруг. */
	private static void shatter(Cast c, LivingEntity decoy) {
		Vec3 at = Cast.center(decoy);
		for (LivingEntity e : c.foesAround(at, 3.0)) {
			c.hurt(e, c.amount(SHARD_DMG), MagicDamage.PSYCHIC);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), c.caster);
		}
		MagicFx.burst(c.level, at, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 30, 0.4, 0.15);
		MagicFx.burst(c.level, at, MagicParticles.spark(PALE, 0.25F, 14), 16, 0.4, 0.12);
		MagicFx.sphere(c.level, at, 0.3F, 3.0F, PINK, PALE, 8);
		MagicFx.sound(c.level, at, SoundEvents.GLASS_BREAK, 1.3F, 0.8F);
		MagicFx.sound(c.level, at, MagicSounds.SHATTER, 0.8F, 1.3F);
		if (!decoy.isRemoved()) decoy.discard();
	}

	/** Враги рядом с магом бьют его двойников (по кругу, всех живых двойников мага). */
	private static void retarget(Cast c, double r, int ticks) {
		List<Mannequin> list = MIRRORS.getOrDefault(c.caster.getUUID(), List.of()).stream().filter(m -> m.isAlive() && !m.isRemoved()).toList();
		if (list.isEmpty()) return;
		int i = 0;
		for (Mob m : c.level.getEntitiesOfClass(Mob.class, c.caster.getBoundingBox().inflate(r), m -> c.isFoe(m) && !isBoss(m))) {
			Mannequin d = list.get(i++ % list.size());
			Minds.apply(m, Minds.Mode.DECOY, ticks, d.position(), d);
		}
	}

	// =====================================================================================================
	// Ранг 5
	// =====================================================================================================

	/**
	 * Массовая Истерия: волна ужаса расходится от мага на 16 блоков (+6 за копию): 12 урона разума, затем бегство и
	 * слабость на 12 с; примерно треть вместо бегства сходит с ума и бьёт своих. Боссы — слабость II и замедление II
	 * на 6 с.
	 */
	private static boolean massHysteria(Cast c) {
		if (c.copy > 0) return true;
		SpellKit.release(c);
		Vec3 feet = c.caster.position();
		double r = c.radius(HYST_R) + HYST_PER_COPY * (c.copies - 1);
		int t = c.duration(HYST_T);
		int wave = 14;
		MagicFx.circle(c.level, School.MIND, 5, MAGENTA, PINK, feet.add(0, 0.08, 0), UP, 4.0F, 2, 0.4F, 3, 20, 14, -0.4F);
		MagicFx.circle(c.level, School.MIND, 5, NIGHT, CRIMSON, feet.add(0, 0.1, 0), UP, (float) Math.min(r, 16), 1, 0, wave, 6, 14, 0.2F);
		MagicFx.flash(c.level, feet, (float) r + 8, 0.35F, MAGENTA, 10);
		MagicFx.soundFar(c.level, feet, MagicSounds.RELEASE_HEAVY, 64, 0.6F);
		MagicFx.soundFar(c.level, feet, MagicSounds.GLITCH, 48, 0.7F);
		MagicFx.sound(c.level, feet, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 1.3F);
		Magic.shake(c.level, feet, 0.8F, 14, (float) r + 8);
		Set<LivingEntity> done = new HashSet<>();
		Zones.add(c, feet, r, wave + 1, (z, age) -> {
			double front = r * Math.min(1.0, (age + 1) / (double) wave);
			// Искажённая волна: дым, крики-глифы, тёмные искры.
			if (age % 2 == 0) {
				MagicFx.shockwave(c.level, feet.add(0, 0.3, 0), UP, (float) Math.max(0.5, front - 1.5), (float) front + 0.5F,
						age % 4 == 0 ? MAGENTA : NIGHT, 6);
				MagicFx.ring(c.level, feet.add(0, 0.5, 0), front, ParticleTypes.LARGE_SMOKE, (int) (8 + front * 2), 0.05);
				MagicFx.ring(c.level, feet.add(0, 1.2, 0), front, MagicParticles.spark(PINK, 0.2F, 12), (int) (6 + front), 0.02);
			}
			if (age % 4 == 1) MagicFx.sound(c.level, feet, MagicSounds.GLITCH, 0.6F, 0.6F + age * 0.04F);
			for (LivingEntity e : c.foesAround(feet.add(0, 1, 0), front)) {
				if (done.add(e)) terrify(c, e, feet, t);
			}
		});
		Zones.later(c, wave, () -> MagicFx.soundFar(c.level, feet, MagicSounds.VANISH, 40, 0.5F));
		return true;
	}

	/** Ужас истерии: урон разума, затем бегство со слабостью или (каждый третий в среднем) безумие. */
	private static void terrify(Cast c, LivingEntity e, Vec3 from, int t) {
		c.hurt(e, c.amount(HYST_DMG), MagicDamage.PSYCHIC);
		MagicFx.burst(c.level, e.getEyePosition().add(0, 0.4, 0), MagicParticles.spark(MAGENTA, 0.22F, 12), 8, 0.3, 0.05);
		MagicFx.burst(c.level, e.getEyePosition(), ParticleTypes.SMOKE, 6, 0.25, 0.02);
		if (isBoss(e)) {
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), c.caster);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), c.caster);
			return;
		}
		if (!(e instanceof Mob m)) return;
		if (c.level.random.nextFloat() < 0.3F) {
			Minds.apply(m, Minds.Mode.FRENZY, t, from, null);
			rage(c, m, 0.2, t);
		} else {
			Minds.apply(m, Minds.Mode.FEAR, t, from, null);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 0), c.caster);
		}
	}

	// =====================================================================================================
	// Ранг 6
	// =====================================================================================================

	/**
	 * Лунный Кошмар: цель под прицелом (до 96 блоков) заперта под алой луной — 6 с оцепенения, 40 урона разума
	 * (по 4 каждые 0,6 с), потом бегство и слабость II на 10 с. До 4 врагов в 6 блоках от неё — 3 с оцепенения и
	 * 15 урона. Боссы цепенеют лишь на 1 с, урон получают полностью.
	 */
	private static boolean moonlitNightmare(Cast c) {
		LivingEntity primary = c.target().entity() instanceof LivingEntity l && c.isFoe(l) ? l : null;
		if (primary == null) {
			Vec3 p = c.aimPoint();
			primary = c.foesAround(p, MOON_R).stream().min(Comparator.comparingDouble(e -> Cast.center(e).distanceToSqr(p))).orElse(null);
		}
		Vec3 ground = c.aimGround();
		if (primary == null) {
			MagicFx.burst(c.level, ground.add(0, 1, 0), MagicParticles.spark(CRIMSON, 0.2F, 10), 10, 0.5, 0.03);
			MagicFx.sound(c.level, ground, MagicSounds.FIZZLE, 0.8F, 0.8F);
			return false;
		}
		SpellKit.release(c);
		LivingEntity target = primary;
		Vec3 pc = Cast.center(target);
		Vec3 toCaster = c.eye().subtract(pc).multiply(1, 0, 1);
		if (toCaster.lengthSqr() < 1.0E-4) toCaster = c.look().reverse();
		toCaster = toCaster.normalize();
		boolean boss = isBoss(target);
		int stun = boss ? 20 : c.duration(MOON_STUN);
		// Мир «переключается»: алая луна за спиной цели, чёрные кресты пригвождают её, вокруг — красно-чёрная мгла.
		Vec3 moon = pc.subtract(toCaster.scale(4.0)).add(0, 2.5, 0);
		MagicFx.flash(c.level, c.eye(), 6, 0.45F, CRIMSON, 6);
		MagicFx.burst(c.level, c.eye().add(c.look().scale(0.3)), MagicParticles.spark(CRIMSON, 0.12F, 10), 6, 0.1, 0.01);
		MagicFx.circle(c.level, School.MIND, 6, CRIMSON, NIGHT, moon, toCaster, 4.5F, 3, 0.3F, 8, MOON_STUN, 20, 0.05F);
		MagicFx.core(c.level, moon, 2.6F, 4.2F, NIGHT, CRIMSON, MOON_STUN + 10);
		MagicFx.barrier(c.level, pc.add(0, -0.5, 0), 3.5F, NIGHT, CRIMSON, stun);
		MagicFx.dome(c.level, ground, (float) MOON_R, NIGHT, CRIMSON, 40);
		Vec3 side = new Vec3(-toCaster.z, 0, toCaster.x);
		MagicFx.beam(c.level, pc.add(0, 2.4, 0), pc.add(0, -1.2, 0), 0.25F, NIGHT, CRIMSON, stun);
		MagicFx.beam(c.level, pc.add(side.scale(1.6)).add(0, 0.6, 0), pc.add(side.scale(-1.6)).add(0, 0.6, 0), 0.25F, NIGHT, CRIMSON, stun);
		MagicFx.soundFar(c.level, pc, MagicSounds.TIMESTOP, 64, 1.4F);
		MagicFx.sound(c.level, pc, MagicSounds.GLITCH, 1.2F, 0.6F);
		MagicFx.sound(c.level, c.caster.position(), SoundEvents.ENDERMAN_STARE, 0.8F, 0.6F);
		stun(c, target, stun);
		SpellKit.repeat(c, 10, MOON_EVERY, i -> {
			if (!target.isAlive()) return;
			c.hurt(target, c.amount(MOON_PULSE), MagicDamage.PSYCHIC);
			MagicFx.burst(c.level, target.getEyePosition(), MagicParticles.spark(CRIMSON, 0.22F, 10), 6, 0.3, 0.04);
			MagicFx.sound(c.level, target.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.8F + 0.04F * i);
			if (i % 3 == 0) MagicFx.sound(c.level, target.position(), MagicSounds.GLITCH, 0.5F, 0.5F);
		});
		Zones.later(c, stun, () -> {
			if (!target.isAlive()) return;
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), c.caster);
			if (target instanceof Mob m && !boss) Minds.apply(m, Minds.Mode.FEAR, 200, c.caster.position(), null);
			MagicFx.burst(c.level, target.getEyePosition(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_STAINED_GLASS.defaultBlockState()),
					20, 0.4, 0.1);
			MagicFx.sound(c.level, target.position(), SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
		});
		// Остальные рядом — короткий кошмар.
		List<LivingEntity> others = c.foesAround(pc, MOON_R);
		others.remove(target);
		others.sort(Comparator.comparingDouble(e -> Cast.center(e).distanceToSqr(pc)));
		for (LivingEntity e : others.subList(0, Math.min(4, others.size()))) {
			stun(c, e, isBoss(e) ? 20 : c.duration(MOON_STUN / 2));
			c.hurt(e, c.amount(MOON_SIDE), MagicDamage.PSYCHIC);
			MagicFx.beam(c.level, pc, Cast.center(e), 0.1F, NIGHT, CRIMSON, 10);
			MagicFx.one(c.level, e.getEyePosition().add(0, 0.8, 0), MagicParticles.rune(CRIMSON, 0.4F, 30), 0, 1, 0);
		}
		return true;
	}

	/** Оцепенение: паралич и никакого движения, кроме падения. */
	private static void stun(Cast c, LivingEntity e, int ticks) {
		e.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, ticks, 0, false, true, true), c.caster);
		if (e instanceof Mob m) m.setTarget(null);
		Zones.add(c, e.position(), 1, ticks, (z, age) -> {
			if (!e.isAlive()) {
				z.stop();
				return;
			}
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(0, Math.min(0, v.y), 0);
			if (age % 8 == 0) MagicFx.one(c.level, e.getEyePosition().add(0, 0.5, 0), MagicParticles.spark(CRIMSON, 0.12F, 10), 0, 0.02, 0);
		});
	}

	// =====================================================================================================
	// Ранг 7
	// =====================================================================================================

	/**
	 * Совершенный Гипноз: пять кругов собираются у ног, зеркальный купол расходится на 40 блоков. Враги внутри 10 с
	 * в иллюзии: дерутся друг с другом (на 50% сильнее) и с шестью призрачными двойниками мага, которые возрождаются,
	 * и получают по 4 урона разума каждые 0,5 с. Боссы — тошнота, слабость II и тот же урон. В конце зеркало
	 * трескается: 30 урона разума и замедление III на 3 с; двойники разлетаются осколками.
	 */
	private static boolean perfectHypnosis(Cast c) {
		SpellKit.release(c);
		ServerPlayer p = c.caster;
		Vec3 feet = p.position();
		double r = Math.min(48.0, c.radius(HYP_R));
		int end = HYP_BUILD + HYP_WAVE + HYP_T;
		// Тихий «тинь» и огромный обратный вдох.
		MagicFx.soundFar(c.level, feet, MagicSounds.TIMESTOP, 96, 1.6F);
		MagicFx.soundFar(c.level, feet, MagicSounds.RISER_LONG, 128, 0.8F);
		Magic.shake(c.level, feet, 1.2F, 30, 64);
		// Круги собираются один за другим: лепестки, глаза, зеркальная луна в середине.
		float[] radii = {6, 9, 12, 14};
		for (int i = 0; i < radii.length; i++) {
			int k = i;
			Zones.later(c, i * 3, () -> {
				MagicFx.circle(c.level, School.MIND, 7, k % 2 == 0 ? PINK : VIOLET, PALE, feet.add(0, 0.05 + 0.03 * k, 0), UP, radii[k], 1, 0,
						4, end - k * 3, 30, (k % 2 == 0 ? 0.05F : -0.05F));
				MagicFx.soundFar(c.level, feet, MagicSounds.CIRCLE_BIG, 64, 1.2F - 0.1F * k);
			});
		}
		Zones.later(c, 6, () -> {
			MagicFx.circle(c.level, School.MIND, 7, PALE, PINK, feet.add(0, 18, 0), SpellKit.DOWN, 10.0F, 2, 1.0F, 8, end, 30, 0.03F);
			MagicFx.core(c.level, feet.add(0, 18, 0), 2.0F, 3.6F, PALE, PINK, end);
		});
		Set<LivingEntity> victims = new LinkedHashSet<>();
		List<Mob> decoyed = new ArrayList<>();
		List<Mannequin> copies = new ArrayList<>();
		int[] spawned = {0};
		Zones.add(c, feet, r, end + 1, (z, age) -> {
			if (age == HYP_BUILD) {
				// Зеркальная волна и двойники.
				MagicFx.sphere(c.level, feet, 1.0F, (float) r, PINK, PALE, HYP_WAVE);
				MagicFx.flash(c.level, feet, (float) r + 16, 0.6F, PALE, 14);
				MagicFx.soundFar(c.level, feet, MagicSounds.RELEASE_ULTIMATE, 128, 1.0F);
				MagicFx.soundFar(c.level, feet, MagicSounds.DOMAIN, 128, 1.0F);
				for (int i = 0; i < HYP_COPIES; i++) spawnCopy(c, feet, i, end - age, copies, spawned);
			}
			if (age >= HYP_BUILD && age <= HYP_BUILD + HYP_WAVE) {
				double front = r * (age - HYP_BUILD + 1) / (double) HYP_WAVE;
				if (age % 2 == 0) {
					MagicFx.shockwave(c.level, feet.add(0, 0.4, 0), UP, (float) Math.max(0.5, front - 2), (float) front, PALE, 6);
					for (LivingEntity e : c.foesAround(feet.add(0, 1, 0), Math.min(r, front))) {
						if (victims.add(e)) enthrall(c, e, victims.size(), end - age, decoyed, copies);
					}
				}
			}
			if (age == HYP_BUILD + HYP_WAVE) MagicFx.barrier(c.level, feet, (float) r, PINK, PALE, end - age);
			if (age > HYP_BUILD && age < end) {
				if (age % 2 == 0) {
					Vec3 pt = SpellKit.inDisc(c.level.random, feet, r * 0.8);
					MagicFx.one(c.level, pt.add(0, 0.5 + c.level.random.nextDouble() * 3, 0), ParticleTypes.CHERRY_LEAVES, 0, 0.05, 0);
					MagicFx.one(c.level, pt.add(0, 1.0, 0), MagicParticles.mote(PALE, 0.2F, 30), 0, 0.04, 0);
				}
				if (age % 10 == 0 && age >= HYP_BUILD + HYP_WAVE) {
					for (LivingEntity e : victims) {
						if (!e.isAlive()) continue;
						c.hurt(e, c.amount(HYP_PULSE), MagicDamage.PSYCHIC);
						MagicFx.one(c.level, e.getEyePosition().add(0, 0.6, 0), MagicParticles.spark(PINK, 0.18F, 10), 0, 0.03, 0);
					}
				}
				if (age % 20 == 0) keepIllusion(c, feet, end - age, decoyed, copies, spawned);
				if (age % 40 == 0) MagicFx.soundFar(c.level, feet, MagicSounds.LOOP_CHANT, 48, 0.7F);
			}
			if (age == end) {
				shatterDomain(c, feet, r, victims, copies);
				z.stop();
			}
		});
		return true;
	}

	/** Жертва гипноза: свечение; моб — бешенство (+50%) или охота на двойника; босс — тошнота и слабость. */
	private static void enthrall(Cast c, LivingEntity e, int n, int ticks, List<Mob> decoyed, List<Mannequin> copies) {
		e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), c.caster);
		MagicFx.one(c.level, e.getEyePosition().add(0, 0.9, 0), MagicParticles.rune(PINK, 0.4F, 30), 0, 1, 0);
		if (isBoss(e) || !(e instanceof Mob m)) {
			e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ticks, 0), c.caster);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1), c.caster);
			return;
		}
		rage(c, m, 0.5, ticks);
		List<Mannequin> alive = copies.stream().filter(LivingEntity::isAlive).toList();
		if (n % 2 == 0 && !alive.isEmpty()) {
			Mannequin d = alive.get(n / 2 % alive.size());
			Minds.apply(m, Minds.Mode.DECOY, ticks, d.position(), d);
			decoyed.add(m);
		} else {
			Minds.apply(m, Minds.Mode.FRENZY, ticks, c.caster.position(), null);
		}
	}

	/** Призрачный двойник мага (1 здоровья) на кольце вокруг него. */
	private static void spawnCopy(Cast c, Vec3 feet, int i, int life, List<Mannequin> copies, int[] spawned) {
		if (spawned[0] >= 24) return;
		double a = Math.PI * 2 * i / HYP_COPIES + c.level.random.nextDouble() * 0.4;
		double d = 6 + c.level.random.nextDouble() * 3;
		Mannequin m = reflection(c, feet.add(Math.cos(a) * d, 0, Math.sin(a) * d), 1.0F, life);
		if (m == null) return;
		m.addEffect(new MobEffectInstance(MobEffects.GLOWING, life, 0, false, false));
		copies.add(m);
		spawned[0]++;
	}

	/** Разбитые двойники возрождаются, охотники за ними получают новую цель. */
	private static void keepIllusion(Cast c, Vec3 feet, int left, List<Mob> decoyed, List<Mannequin> copies, int[] spawned) {
		for (int i = 0; i < copies.size(); i++) {
			Mannequin m = copies.get(i);
			if (m.isAlive() && !m.isRemoved()) continue;
			if (m.isDeadOrDying()) MagicFx.burst(c.level, Cast.center(m), new BlockParticleOption(ParticleTypes.BLOCK,
					Blocks.GLASS.defaultBlockState()), 16, 0.3, 0.1);
			if (!m.isRemoved()) m.discard();
			copies.remove(i--);
		}
		while (copies.size() < HYP_COPIES && spawned[0] < 24) spawnCopy(c, feet, spawned[0], left, copies, spawned);
		if (copies.isEmpty()) return;
		for (int i = 0; i < decoyed.size(); i++) {
			Mob m = decoyed.get(i);
			if (m.isAlive() && Minds.modeOf(m) != Minds.Mode.DECOY) {
				Mannequin d = copies.get(i % copies.size());
				Minds.apply(m, Minds.Mode.DECOY, left, d.position(), d);
			}
		}
	}

	/** Зеркало трескается: финальный урон, замедление, осколки, гром стекла. */
	private static void shatterDomain(Cast c, Vec3 feet, double r, Set<LivingEntity> victims, List<Mannequin> copies) {
		for (LivingEntity e : victims) {
			if (!e.isAlive()) continue;
			c.hurt(e, c.amount(HYP_FINALE), MagicDamage.PSYCHIC);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), c.caster);
			e.removeEffect(MobEffects.GLOWING);
			if (e instanceof Mob m) Minds.clear(m);
			calmDown(e, null);
			MagicFx.burst(c.level, e.getEyePosition(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_STAINED_GLASS.defaultBlockState()),
					16, 0.4, 0.15);
		}
		for (Mannequin m : copies) {
			if (m.isRemoved()) continue;
			MagicFx.burst(c.level, Cast.center(m), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 24, 0.4, 0.15);
			m.discard();
		}
		copies.clear();
		MagicFx.sphere(c.level, feet, (float) r, (float) r * 1.15F, PALE, 0xFFFFFF, 16);
		MagicFx.shockwave(c.level, feet.add(0, 0.5, 0), UP, 1.0F, (float) r * 1.2F, PINK, 18);
		MagicFx.flash(c.level, feet, (float) r + 24, 0.9F, 0xFFFFFF, 16);
		for (int i = 0; i < 24; i++) {
			Vec3 pt = SpellKit.inDisc(c.level.random, feet, r).add(0, 2 + c.level.random.nextDouble() * r * 0.5, 0);
			MagicFx.burst(c.level, pt, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 12, 1.2, 0.2);
		}
		MagicFx.soundFar(c.level, feet, MagicSounds.SHATTER, 128, 0.5F);
		MagicFx.soundFar(c.level, feet, MagicSounds.EXPLOSION_HUGE, 96, 1.4F);
		MagicFx.sound(c.level, feet, SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
		Magic.shake(c.level, feet, 2.0F, 24, 96);
	}

	// =====================================================================================================
	// Общее
	// =====================================================================================================

	static boolean isBoss(Entity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden;
	}

	/** Центр действия у прицела: существо под прицелом или земля под прицелом (на уровне тел). */
	private static Vec3 focus(Cast c) {
		Entity e = c.target().entity();
		return e != null ? Cast.center(e) : c.aimGround().add(0, 1.0, 0);
	}

	/** Волна по разуму: искры от руки к цели, кольцо, мерцание и ведьмины искры. */
	private static void mindWave(Cast c, Vec3 ground, double r, int color, int light) {
		MagicFx.line(c.level, c.hand(), ground.add(0, 1, 0), MagicParticles.spark(color, 0.12F, 10), 0.5);
		MagicFx.shockwave(c.level, ground.add(0, 0.2, 0), UP, 0.3F, (float) r * 1.2F, color, 12);
		MagicFx.one(c.level, ground.add(0, 0.1, 0), MagicParticles.ring(light, (float) r, 14), 0, 1, 0);
		MagicFx.burst(c.level, ground.add(0, 1, 0), MagicParticles.mote(color, 0.12F, 22), 16, r * 0.4, 0.02);
		MagicFx.burst(c.level, ground.add(0, 1, 0), ParticleTypes.WITCH, 10, r * 0.3, 0.02);
	}

	/** Дрожь напуганных: тёмные искры у головы. */
	private static void tremble(Cast c, List<LivingEntity> who, int ticks) {
		if (who.isEmpty()) return;
		Zones.add(c, Cast.center(who.get(0)), 1, ticks, (z, age) -> {
			if (age % 5 != 0) return;
			for (LivingEntity e : who) {
				if (!e.isAlive()) continue;
				MagicFx.one(c.level, e.getEyePosition().add((c.level.random.nextDouble() - 0.5) * 0.5, 0.3, (c.level.random.nextDouble() - 0.5) * 0.5),
						MagicParticles.spark(MAGENTA, 0.1F, 8), 0, 0.02, 0);
			}
		});
	}

	/** Блуждание в тумане: моб забывает цель и бредёт куда попало. */
	private static void wander(Cast c, Mob m, int ticks) {
		Minds.apply(m, Minds.Mode.CALM, ticks, m.position(), null);
		if (!(m instanceof PathfinderMob pm)) return;
		Zones.add(c, m.position(), 1, ticks, (z, age) -> {
			if (!pm.isAlive()) {
				z.stop();
				return;
			}
			if (age % 20 == 0) {
				Vec3 to = DefaultRandomPos.getPos(pm, 6, 3);
				if (to != null) pm.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
			}
		});
	}

	/** Ярость: урон ближнего боя +{@code bonus} (доля) на {@code ticks} тиков. */
	private static final Identifier RAGE = Identifier.fromNamespaceAndPath("suchki", "mind_rage");
	private static final Map<LivingEntity, Object> RAGE_TOKEN = new WeakHashMap<>();

	private static void rage(Cast c, LivingEntity e, double bonus, int ticks) {
		AttributeInstance a = e.getAttribute(Attributes.ATTACK_DAMAGE);
		if (a == null) return;
		a.addOrUpdateTransientModifier(new AttributeModifier(RAGE, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		Object token = new Object();
		RAGE_TOKEN.put(e, token);
		Zones.later(c, ticks, () -> calmDown(e, token));
	}

	/** Снять ярость (если {@code token} не null — только если её не обновило более позднее заклинание). */
	private static void calmDown(LivingEntity e, Object token) {
		if (token != null && RAGE_TOKEN.get(e) != token) return;
		RAGE_TOKEN.remove(e);
		AttributeInstance a = e.getAttribute(Attributes.ATTACK_DAMAGE);
		if (a != null) a.removeModifier(RAGE);
	}

	/** Есть ли на существе ярость разума (для самопроверки). */
	public static boolean enraged(LivingEntity e) {
		AttributeInstance a = e.getAttribute(Attributes.ATTACK_DAMAGE);
		return a != null && a.getModifier(RAGE) != null;
	}
}
