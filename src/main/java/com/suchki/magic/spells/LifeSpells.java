package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.LIFE;
import static com.suchki.magic.spells.SpellKit.DOWN;
import static com.suchki.magic.spells.SpellKit.UP;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Жизнь: исцеление, очищение и природа — от Мистической ладони новичка до Цветения Иггдрасиля, Мирового Древа,
 * что вырастает у прицела, поднимает своих и давит врагов корнями.
 *
 * <p>Деревья, родник, корни и цветы на камне — временные блоки: ставятся только в воздух и сами исчезают, поэтому
 * работают и без разрушения. Листва ставится только вплотную к веткам (расстояние 1) и сохраняется — так её
 * состояние не меняется и она гарантированно исчезает вместе с деревом.
 */
public final class LifeSpells {
	static final int GREEN = School.LIFE.color;
	static final int PALE = School.LIFE.light;
	static final int GOLD = 0xFFD24A;
	static final int GOLD_PALE = 0xFFF2B8;
	static final int PETAL = 0xFFB3D9;
	static final int SPRING = 0x7FE0FF;
	static final int BARK = 0x8A6038;

	/** Луговые цветы (без иссушающей розы). */
	private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
			Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.LILY_OF_THE_VALLEY,
			Blocks.BLUE_ORCHID};
	/** Пентатоника арфы для «глиссандо» луга (полутоны от основного тона). */
	private static final int[] HARP = {0, 2, 4, 7, 9, 12, 14};
	/** Сколько тиков павший питомец ждёт Иггдрасиль. */
	private static final int PET_MEMORY = 600;

	/** Под Печатью бессмертия: до какого игрового времени. При спасении — золотой взрыв, отбрасывающий врагов. */
	private static final Map<UUID, Long> SEALED = new HashMap<>();
	/** Мистическая ладонь: кого лечит каждая копия потока и сколько уже (снятие дебаффа после 3 с на одном). */
	private static final Map<String, Patient> PALM = new HashMap<>();
	/** Павшие питомцы последних 30 с — их возвращает Иггдрасиль. */
	private static final List<FallenPet> FALLEN = new ArrayList<>();

	private static final class Patient {
		final UUID id;
		int since;

		Patient(UUID id, int since) {
			this.id = id;
			this.since = since;
		}
	}

	private record FallenPet(UUID owner, ResourceKey<Level> dimension, EntityType<?> type, CompoundTag tag, long time) {}

	/** Блок временной постройки: где, что, на каком тике роста и сколько держится. */
	private record Piece(BlockPos pos, BlockState state, int tick, int keep) {}

	private LifeSpells() {}

	public static void register() {
		// --- Ранг 1: Начальный ---

		// Мистическая ладонь: поток лечит мага на 1 раз в 0,5 с (союзника под прицелом в 6 блоках — ×1,2);
		// 3 с на одном пациенте снимают один дебафф. Копии лечат ещё по одному раненому союзнику рядом.
		Spell.of("healing", LIFE, 1, CHANNEL).mana(9).cooldown(10).range(6).icon("glistering_melon_slice")
				.anchor(Anchor.HAND).loop(MagicSounds.LOOP_HEAL)
				.channel(LifeSpells::palm)
				.register();

		// Луг цветущих воспоминаний: волна цветов радиусом 6 (×сила, до 8), Регенерация I 8 с своим, звери идут
		// за магом 10 с, нежить замедляется. Копии — ещё луга по 6 блоков дальше по взгляду.
		Spell.of("flower_field", LIFE, 1, SELF).mana(8).charge(12).cooldown(40).range(8).icon("pink_petals")
				.anchor(Anchor.FEET).multicast(4)
				.action(LifeSpells::meadow)
				.register();

		// --- Ранг 2: Средний ---

		// Исцеление союзника: до 48 блоков, 9 здоровья и один дебафф долой; нежить под прицелом — 7 урона святостью.
		Spell.of("heal_other", LIFE, 2, BOLT).mana(18).charge(12).cooldown(20).range(48).icon("golden_carrot")
				.anchor(Anchor.TARGET)
				.action(LifeSpells::healOther)
				.register();

		// Очищение: все дебаффы, огонь и холод долой, 4 здоровья и 5 с защиты от яда, иссушения и голода.
		Spell.of("cure", LIFE, 2, SELF).mana(18).charge(12).cooldown(60).range(4).icon("milk_bucket")
				.anchor(Anchor.FEET).multicast(4)
				.action(LifeSpells::cure)
				.register();

		// --- Ранг 3: Продвинутый ---

		// Обновление: свои в радиусе 8 — Регенерация II 10 с и 3 здоровья; каждая копия — +4 к радиусу.
		Spell.of("rejuvenation", LIFE, 3, SELF).mana(32).charge(18).cooldown(200).range(8).icon("golden_apple")
				.anchor(Anchor.FEET).multicast(4)
				.action(LifeSpells::rejuvenation)
				.register();

		// --- Ранг 4: Святой ---

		// Священный родник: зона радиусом 6 на 12 с у прицела (сидя — у ног): 6 здоровья при входе, потом 2 в секунду
		// (×1,5 днём под небом), дебафф долой раз в 2 с; нежить — 4 урона в секунду; вражеские снаряды сгорают.
		Spell.of("healing_circle", LIFE, 4, RITUAL).mana(60).charge(30).cooldown(400).range(48).icon("glow_berries")
				.anchor(Anchor.TARGET).layers(2)
				.action(LifeSpells::wellspring)
				.register();

		// --- Ранг 5: Королевский ---

		// Печать бессмертия: 60 с смертельный удар оставляет половину здоровья (и отбрасывает врагов золотым взрывом).
		Spell.of("undying", LIFE, 5, SELF).mana(90).charge(40).cooldown(2400).range(16).icon("totem_of_undying")
				.anchor(Anchor.FEET).layers(2).multicast(4)
				.action(LifeSpells::undying)
				.register();

		// --- Ранг 6: Императорский ---

		// Явление дремучего леса: 18 деревьев за секунду в радиусе 12; корни держат врагов 5 с, стволы бьют на 12
		// и через 3 с давят на 24 с подбросом; свои — 4 здоровья и Регенерация II 10 с.
		Spell.of("deep_forest_bloom", LIFE, 6, RITUAL).mana(150).charge(66).cooldown(2000).range(96).icon("oak_sapling")
				.anchor(Anchor.TARGET).layers(2)
				.action(LifeSpells::deepForest)
				.register();

		// --- Ранг 7: Божественный ---

		// Цветение Иггдрасиля: Мировое Древо высотой 32 на минуту; свои в 32 блоках — полное исцеление, Регенерация III,
		// Поглощение IV, Печать бессмертия и 30% маны, павшие питомцы возвращаются; корни 7 раз бьют врагов по 14.
		Spell.of("yggdrasil_bloom", LIFE, 7, RITUAL).mana(270).charge(130).cooldown(7200).range(128).icon("flowering_azalea")
				.anchor(Anchor.TARGET).layers(5).circle(16).noMulticast()
				.action(LifeSpells::yggdrasil)
				.register();

		hooks();
	}

	// =====================================================================================================
	// Ранг 1
	// =====================================================================================================

	/** Мистическая ладонь: тик потока. */
	private static void palm(Cast c, int t) {
		String key = c.caster.getUUID() + "/" + c.copy;
		if (t == 1) PALM.remove(key);
		LivingEntity aimed = aimedAlly(c, c.spell.range);
		LivingEntity patient;
		if (c.copy == 0) {
			patient = aimed != null ? aimed : c.caster;
		} else {
			// Копия берёт следующего раненого союзника рядом (ближние первыми).
			List<LivingEntity> hurt = new ArrayList<>(c.alliesAround(Cast.center(c.caster), c.spell.range));
			hurt.removeIf(e -> e == c.caster || e == aimed || !wounded(e));
			hurt.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster)));
			patient = c.copy - 1 < hurt.size() ? hurt.get(c.copy - 1) : null;
		}
		if (patient == null) {
			PALM.remove(key);
			return;
		}
		if (t % 2 == 0) palmMotes(c, patient, t);
		Patient watch = PALM.get(key);
		if (watch == null || !watch.id.equals(patient.getUUID())) {
			watch = new Patient(patient.getUUID(), t);
			PALM.put(key, watch);
		}
		// Три секунды на одном пациенте — один дебафф вытянут.
		if (t - watch.since >= 60) {
			watch.since = t;
			if (cleanse(patient, 1) > 0) {
				Vec3 at = Cast.center(patient);
				MagicFx.burst(c.level, at, MagicParticles.spark(PALE, 0.3F, 16), 12, 0.35, 0.05);
				MagicFx.burst(c.level, at, ParticleTypes.WAX_OFF, 6, 0.3, 0.1);
				MagicFx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
			}
		}
		if (t % 10 != 0 || patient.getHealth() >= patient.getMaxHealth()) return;
		c.heal(patient, c.amount(patient == c.caster ? 1.0F : 1.2F));
		if (t % 20 == 0) MagicFx.burst(c.level, patient.getEyePosition().add(0, 0.4, 0), ParticleTypes.HEART, 1, 0.25, 0);
	}

	/** Зелёные искры спиралью текут из ладони в пациента; пациент чуть светится. */
	private static void palmMotes(Cast c, LivingEntity patient, int t) {
		if (patient == c.caster) {
			double a = t * 0.45;
			Vec3 at = c.caster.position().add(Math.cos(a) * 0.6, 0.1 + (t % 20) * 0.09, Math.sin(a) * 0.6);
			MagicFx.one(c.level, at, MagicParticles.mote(GREEN, 0.14F, 18), 0, 0.03, 0);
			MagicFx.one(c.level, at.add(-Math.cos(a) * 1.2, 0, -Math.sin(a) * 1.2), MagicParticles.mote(PALE, 0.1F, 14), 0, 0.03, 0);
			return;
		}
		Vec3 from = c.hand();
		Vec3 to = Cast.center(patient);
		Vec3 d = to.subtract(from);
		Vec3 dir = d.lengthSqr() < 1.0E-4 ? c.look() : d.normalize();
		Vec3 right = CircleLayout.right(dir);
		Vec3 up = CircleLayout.up(dir);
		for (int j = 0; j < 3; j++) {
			double f = (t * 0.06 + j / 3.0) % 1.0;
			double a = t * 0.5 + j * 2.1;
			Vec3 at = from.add(d.scale(f)).add(right.scale(Math.cos(a) * 0.25)).add(up.scale(Math.sin(a) * 0.25));
			MagicFx.one(c.level, at, MagicParticles.mote(j == 0 ? PALE : GREEN, 0.12F, 10), dir.x * 0.04, dir.y * 0.04, dir.z * 0.04);
		}
		if (t % 6 == 0) {
			MagicFx.cloud(c.level, to, MagicParticles.spark(GREEN, 0.2F, 14), 3, patient.getBbWidth() * 0.4, patient.getBbHeight() * 0.35,
					patient.getBbWidth() * 0.4, 0.01);
		}
	}

	/** Луг цветущих воспоминаний. */
	private static boolean meadow(Cast c) {
		SpellKit.release(c);
		Vec3 look = c.look();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		Vec3 feet = c.caster.position();
		Vec3 center = c.copy == 0 ? feet : Targeting.ground(c.level, c.caster, feet.add(flat.scale(6.0 * c.copy)).add(0, 1.5, 0));
		double r = Math.min(8.0, 6.0 * c.power);
		int regen = Math.min(320, 160 + 40 * (c.copies - 1));
		ServerLevel level = c.level;
		if (c.copy > 0) MagicFx.circle(level, c.spell, center.add(0, 0.07, 0), UP, 2.0F, 6, 10, 12);
		// Волна: за 10 тиков кольцами расходятся лепестки, и на траве распускаются цветы.
		for (int i = 0; i < 5; i++) {
			int step = i;
			Zones.later(c, step * 2, () -> {
				double r0 = r * step / 5.0;
				double r1 = r * (step + 1) / 5.0;
				int n = bloom(c, center, r0, r1, 0.45F, 0);
				MagicFx.ring(level, center.add(0, 0.3, 0), r1, ParticleTypes.CHERRY_LEAVES, (int) (8 + r1 * 4), 0.05);
				MagicFx.ring(level, center.add(0, 0.2, 0), r1, MagicParticles.mote(PETAL, 0.12F, 20), (int) (6 + r1 * 3), 0.02);
				if (n > 0) MagicFx.sound(level, center, SoundEvents.BONE_MEAL_USE, 0.5F, 1.2F + 0.1F * step);
			});
		}
		SpellKit.repeat(c, HARP.length, 1, i -> MagicFx.sound(level, center, SoundEvents.NOTE_BLOCK_HARP.value(), 0.7F,
				(float) (0.7 * Math.pow(2, HARP[i] / 12.0))));
		MagicFx.shockwave(level, center.add(0, 0.15, 0), UP, 0.3F, (float) r, PETAL, 12);
		MagicFx.sound(level, center, SoundEvents.CHERRY_LEAVES_PLACE, 1.0F, 0.9F);

		for (LivingEntity e : c.alliesAround(center.add(0, 1, 0), r)) {
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0), c.caster);
			weaken(e, MobEffects.POISON);
			weaken(e, MobEffects.WITHER);
			MagicFx.burst(level, Cast.center(e), MagicParticles.mote(PETAL, 0.14F, 20), 6, 0.4, 0.03);
		}
		for (LivingEntity e : c.foesAround(center.add(0, 1, 0), r)) {
			if (Cast.isUndead(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), c.caster);
		}
		// Мирные звери тянутся к магу 10 с.
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(center, center).inflate(r, 3, r),
				a -> a.isAlive() && !(a instanceof Enemy) && !Minions.isSummon(a));
		if (animals.size() > 12) animals = animals.subList(0, 12);
		List<Animal> herd = List.copyOf(animals);
		for (Animal a : herd) a.getNavigation().moveTo(c.caster, 1.1);
		if (!herd.isEmpty()) {
			Zones.add(c, center, r, 200, (z, age) -> {
				if (age % 20 != 0) return;
				for (Animal a : herd) {
					if (a.isAlive() && a.distanceToSqr(c.caster) > 9) a.getNavigation().moveTo(c.caster, 1.1);
				}
			});
		}
		// Лепестки и споры поднимаются ещё 3 с.
		Zones.add(c, center, r, 60, (z, age) -> {
			if (age % 3 != 0) return;
			Vec3 p = SpellKit.inDisc(level.random, center, r);
			MagicFx.one(level, p.add(0, 0.2, 0), ParticleTypes.CHERRY_LEAVES, 0, 0.04, 0);
			MagicFx.one(level, p.add(0, 0.5, 0), ParticleTypes.SPORE_BLOSSOM_AIR, 0, 0.03, 0);
			if (age == 45) MagicFx.sound(level, center, SoundEvents.AZALEA_LEAVES_PLACE, 0.6F, 1.3F);
		});
		return true;
	}

	// =====================================================================================================
	// Ранг 2
	// =====================================================================================================

	/** Исцеление союзника (или удар по нежити) у прицела. */
	private static boolean healOther(Cast c) {
		List<LivingEntity> list = healTargets(c);
		if (c.copy >= list.size()) return false;
		LivingEntity t = list.get(c.copy);
		SpellKit.release(c);
		Vec3 at = Cast.center(t);
		MagicFx.helix(c.level, c.hand(), at, 0.3F, GREEN, PALE, 12);
		if (!c.isAlly(t)) {
			c.hurt(t, c.amount(7.0F), MagicDamage.HOLY);
			MagicFx.burst(c.level, at, ParticleTypes.END_ROD, 14, 0.3, 0.06);
			SpellKit.impact(c, at, 1.0F);
			return true;
		}
		c.heal(t, c.amount(9.0F));
		cleanse(t, 1);
		t.clearFire();
		Vec3 feet = t.position();
		MagicFx.circle(c.level, c.spell, feet.add(0, 0.06, 0), UP, Math.max(0.9F, t.getBbWidth() * 1.4F), 8, 10, 12);
		MagicFx.shockwave(c.level, feet.add(0, 0.12, 0), UP, 0.2F, 2.4F, PALE, 12);
		for (int i = 0; i < 10; i++) {
			Vec3 p = feet.add((c.level.random.nextDouble() - 0.5) * t.getBbWidth() * 1.6, c.level.random.nextDouble() * 0.4,
					(c.level.random.nextDouble() - 0.5) * t.getBbWidth() * 1.6);
			MagicFx.one(c.level, p, MagicParticles.mote(i % 3 == 0 ? PALE : GREEN, 0.13F, 26), 0, 0.06, 0);
		}
		MagicFx.burst(c.level, t.getEyePosition().add(0, 0.4, 0), ParticleTypes.HEART, 3, 0.4, 0);
		MagicFx.sound(c.level, at, MagicSounds.impactOf(c.spell), 0.9F, 1.3F);
		MagicFx.sound(c.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.7F);
		return true;
	}

	/**
	 * Кого лечит «Исцеление союзника»: союзник или нежить под прицелом, затем союзники в 4 блоках от точки прицела
	 * (самые раненые первыми). Копия номер i берёт i-го.
	 */
	private static List<LivingEntity> healTargets(Cast c) {
		Targeting.Aim aim = Targeting.aim(c.level, c.caster, c.eye(), c.look(), c.spell.range, Targeting.living(c.caster));
		List<LivingEntity> out = new ArrayList<>();
		if (aim.entity() instanceof LivingEntity l && l != c.caster && (patient(c, l) || Cast.isUndead(l) && c.isFoe(l))) out.add(l);
		List<LivingEntity> near = new ArrayList<>(c.alliesAround(aim.point(), 4.0));
		near.removeIf(e -> e == c.caster || out.contains(e));
		near.sort(Comparator.comparingDouble((LivingEntity e) -> e.getHealth() - e.getMaxHealth())
				.thenComparingDouble(e -> e.distanceToSqr(aim.point())));
		out.addAll(near);
		return out;
	}

	/** Очищение себя (копии — ближайших союзников в 4 блоках). */
	private static boolean cure(Cast c) {
		LivingEntity t = c.caster;
		if (c.copy > 0) {
			List<LivingEntity> near = new ArrayList<>(c.alliesAround(Cast.center(c.caster), c.spell.range));
			near.remove(c.caster);
			near.sort(Comparator.comparingInt((LivingEntity e) -> -harmful(e)).thenComparingDouble(e -> e.getHealth() - e.getMaxHealth())
					.thenComparingDouble(e -> e.distanceToSqr(c.caster)));
			if (c.copy - 1 >= near.size()) return false;
			t = near.get(c.copy - 1);
		}
		SpellKit.release(c);
		purify(c, t, 4.0F, 100);
		return true;
	}

	/** Все дебаффы, огонь и холод долой, лечение и {@code immune} тиков защиты от яда, иссушения и голода. */
	private static void purify(Cast c, LivingEntity t, float heal, int immune) {
		int removed = cleanse(t, Integer.MAX_VALUE);
		t.clearFire();
		t.setTicksFrozen(0);
		c.heal(t, c.amount(heal));
		ServerLevel level = c.level;
		Vec3 mid = Cast.center(t);
		// Тёмные искры вытягиваются из тела и тают в свете.
		for (int i = 0; i < 8 + removed * 4; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			Vec3 p = mid.add(Math.cos(a) * 0.3, (level.random.nextDouble() - 0.5) * t.getBbHeight() * 0.8, Math.sin(a) * 0.3);
			MagicFx.one(level, p, MagicParticles.spark(0x1E2A1E, 0.22F, 14), Math.cos(a) * 0.08, 0.04, Math.sin(a) * 0.08);
		}
		Zones.later(c, 6, () -> MagicFx.burst(level, Cast.center(t), MagicParticles.spark(0xFFFFFF, 0.28F, 14), 14, 0.7, 0.03));
		MagicFx.one(level, t.position().add(0, 0.1, 0), MagicParticles.ring(PALE, 1.8F, 12), 0, 1, 0);
		MagicFx.burst(level, mid, MagicParticles.mote(GREEN, 0.14F, 20), 16, 0.4, 0.05);
		MagicFx.sound(level, mid, MagicSounds.impactOf(c.spell), 0.9F, 1.1F);
		MagicFx.sound(level, mid, SoundEvents.BELL_BLOCK, 0.4F, 1.8F);
		Zones.add(c, t.position(), 1, immune, (z, age) -> {
			if (!t.isAlive()) {
				z.stop();
				return;
			}
			boolean any = t.removeEffect(MobEffects.POISON);
			any |= t.removeEffect(MobEffects.WITHER);
			any |= t.removeEffect(MobEffects.HUNGER);
			if (any) MagicFx.burst(level, Cast.center(t), MagicParticles.spark(PALE, 0.25F, 12), 8, 0.3, 0.03);
			if (age % 8 == 0) MagicFx.one(level, t.position().add(0, 0.2, 0), MagicParticles.mote(PALE, 0.1F, 16), 0, 0.05, 0);
		});
	}

	// =====================================================================================================
	// Ранг 3
	// =====================================================================================================

	/** Обновление: регенерация всем своим вокруг. */
	private static boolean rejuvenation(Cast c) {
		SpellKit.release(c);
		double r = c.radius(8.0) + 4.0 * (c.copies - 1);
		Vec3 feet = c.caster.position();
		ServerLevel level = c.level;
		if (c.copy > 0) {
			// Каждая копия — ещё одна волна (радиус уже учтён у главной).
			MagicFx.shockwave(level, feet.add(0, 0.15, 0), UP, 1.0F, (float) r, GREEN, 16);
			MagicFx.ring(level, feet.add(0, 0.3, 0), r * 0.8, ParticleTypes.HAPPY_VILLAGER, (int) (r * 4), 0.0);
			return true;
		}
		ParticleOptions leaf = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | GREEN);
		for (LivingEntity e : c.alliesAround(feet.add(0, 1, 0), r)) {
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, c.duration(200), 1), c.caster);
			c.heal(e, c.amount(3.0F));
			Vec3 head = e.position().add(0, e.getBbHeight() + 1.4, 0);
			MagicFx.cloud(level, head, leaf, 6, 0.6, 0.2, 0.6, 0);
			MagicFx.burst(level, Cast.center(e), MagicParticles.mote(GREEN, 0.15F, 24), 8, 0.4, 0.04);
		}
		SpellKit.repeat(c, 3, 5, i -> MagicFx.shockwave(level, feet.add(0, 0.12, 0), UP, 0.5F, (float) r, i == 1 ? PALE : GREEN, 14));
		MagicFx.one(level, feet.add(0, 0.05, 0), MagicParticles.rune(GREEN, 2.2F, 30), 0, 1, 0);
		MagicFx.cloud(level, feet.add(0, 3.0, 0), leaf, 20, r * 0.5, 0.5, r * 0.5, 0);
		MagicFx.sound(level, feet, MagicSounds.impactOf(c.spell), 1.0F, 0.8F);
		MagicFx.sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
		SpellKit.repeat(c, 4, 3, i -> MagicFx.sound(level, feet, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.4F,
				(float) (1.0 * Math.pow(2, HARP[4 - i] / 12.0))));
		return true;
	}

	// =====================================================================================================
	// Ранг 4
	// =====================================================================================================

	/** Священный родник у прицела (сидя — у ног); копии — родники поменьше у других союзников рядом. */
	private static boolean wellspring(Cast c) {
		Vec3 center;
		double r;
		if (c.copy == 0) {
			center = c.caster.isShiftKeyDown() ? c.caster.position() : c.aimGround();
			r = c.radius(6.0);
		} else {
			Targeting.Aim main = Targeting.aim(c.level, c.caster, c.eye(), c.look(), c.spell.range, Targeting.living(c.caster));
			Vec3 g = main.ground();
			List<LivingEntity> others = new ArrayList<>(c.alliesAround(g.add(0, 1, 0), 16.0));
			others.removeIf(e -> e == c.caster || horizontal(e.position(), g) < 5.0);
			others.sort(Comparator.comparingDouble(e -> e.distanceToSqr(g)));
			if (c.copy - 1 < others.size()) {
				center = others.get(c.copy - 1).position();
			} else {
				double a = Math.PI * 2 * c.copy / c.copies;
				center = Targeting.ground(c.level, c.caster, g.add(Math.cos(a) * 9.0, 2.0, Math.sin(a) * 9.0));
			}
			r = c.radius(4.0);
		}
		SpellKit.release(c);
		spring(c, center, r, c.duration(240));
		return true;
	}

	/** Родник радиуса {@code r} на {@code life} тиков. */
	private static void spring(Cast c, Vec3 center, double r, int life) {
		ServerLevel level = c.level;
		MagicFx.circle(level, c.spell, center.add(0, 0.07, 0), UP, (float) r, 2, 0.3F, 14, life - 34, 20);
		MagicFx.circle(level, School.WATER, 3, SPRING, 0xE6FBFF, center.add(0, 0.1, 0), UP, (float) r * 0.35F, 1, 0, 10, life - 30, 20, -0.35F);
		MagicFx.pillar(level, center, 0.8F, 9.0F, GOLD, GOLD_PALE, 50);
		MagicFx.shockwave(level, center.add(0, 0.2, 0), UP, 0.4F, (float) r, PALE, 16);
		MagicFx.burst(level, center.add(0, 0.6, 0), ParticleTypes.SPLASH, 40, 0.5, 0.2);
		MagicFx.soundFar(level, center, MagicSounds.impactOf(c.spell), 48, 0.9F);
		MagicFx.sound(level, center, SoundEvents.PLAYER_SPLASH, 1.0F, 1.2F);
		MagicFx.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
		MagicFx.sound(level, center, SoundEvents.BELL_RESONATE, 0.6F, 1.5F);
		fountain(c, center, life);
		for (int i = 0; i < 3; i++) {
			int step = i;
			Zones.later(c, 2 * step, () -> bloom(c, center, r * step / 3.0, r * (step + 1) / 3.0, 0.3F, life + 40));
		}
		Set<UUID> inside = new HashSet<>();
		Set<UUID> greeted = new HashSet<>();
		int[] shimmer = {-100};
		AABB box = new AABB(center.x - r, center.y - 1, center.z - r, center.x + r, center.y + r, center.z + r);
		Zones.add(c, center, r, life, (z, age) -> {
			Vec3 mid = center.add(0, 1.0, 0);
			// Фонтан, искры, лепестки, журчание.
			if (age % 2 == 0) {
				MagicFx.cloud(level, center.add(0, 1.3, 0), ParticleTypes.FALLING_WATER, 3, 0.25, 0.4, 0.25, 0);
				MagicFx.cloud(level, center.add(0, 0.8, 0), ParticleTypes.SPLASH, 4, 0.2, 0.1, 0.2, 0.15);
			}
			if (age % 3 == 0) {
				Vec3 p = SpellKit.inDisc(level.random, center, r);
				MagicFx.one(level, p.add(0, 0.15, 0), MagicParticles.mote(age % 6 == 0 ? GOLD : GREEN, 0.13F, 40), 0, 0.04, 0);
			}
			if (age % 7 == 0) MagicFx.one(level, SpellKit.inDisc(level.random, center, r).add(0, 2.5, 0), ParticleTypes.CHERRY_LEAVES, 0, 0, 0);
			if (age % 40 == 1) MagicFx.sound(level, center, SoundEvents.WATER_AMBIENT, 0.7F, 1.2F);
			// Вражеские снаряды сгорают на границе.
			for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> hostileShot(c, pr))) {
				if (pr.position().distanceToSqr(center) > r * r) continue;
				MagicFx.burst(level, pr.position(), MagicParticles.spark(GOLD, 0.3F, 12), 10, 0.15, 0.06);
				MagicFx.burst(level, pr.position(), ParticleTypes.SMOKE, 4, 0.1, 0.02);
				pr.discard();
				if (age - shimmer[0] >= 10) {
					shimmer[0] = age;
					MagicFx.barrier(level, center, (float) r, GOLD, GOLD_PALE, 14);
					MagicFx.sound(level, center, MagicSounds.WARD_HIT, 0.8F, 1.3F);
				}
			}
			// Враги выталкиваются.
			if (age % 2 == 0) {
				for (LivingEntity e : c.enemiesAround(mid, r)) Cast.knock(e, center, 0.3, 0.0);
			}
			// Вход и выход своих: при входе — 6 здоровья, при выходе — Регенерация I на 10 с.
			if (age % 5 == 0 || age >= z.life) {
				Set<UUID> now = new HashSet<>();
				for (LivingEntity e : c.alliesAround(mid, r)) {
					now.add(e.getUUID());
					if (greeted.add(e.getUUID())) {
						c.heal(e, c.amount(6.0F));
						MagicFx.burst(level, Cast.center(e), MagicParticles.mote(GREEN, 0.15F, 20), 10, 0.4, 0.05);
						MagicFx.burst(level, e.getEyePosition().add(0, 0.4, 0), ParticleTypes.HEART, 2, 0.3, 0);
					}
				}
				if (age >= z.life) now.clear();
				for (UUID id : inside) {
					if (now.contains(id) || !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive()) continue;
					l.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), c.caster);
				}
				inside.clear();
				inside.addAll(now);
			}
			if (age % 10 == 0) {
				for (LivingEntity e : c.alliesAround(mid, r)) {
					boolean sun = level.isBrightOutside() && level.canSeeSky(BlockPos.containing(e.getEyePosition()));
					c.heal(e, c.amount(sun ? 1.5F : 1.0F));
					if (age % 40 == 0 && cleanse(e, 1) > 0) {
						MagicFx.burst(level, Cast.center(e), MagicParticles.spark(PALE, 0.28F, 14), 10, 0.3, 0.05);
					}
				}
			}
			// Нежить горит святой водой.
			if (age % 20 == 0) {
				for (LivingEntity e : c.foesAround(mid, r)) {
					if (!Cast.isUndead(e)) continue;
					c.hurt(e, c.amount(4.0F), MagicDamage.HOLY);
					SpellKit.slow(c, e, 40, 1);
					MagicFx.burst(level, Cast.center(e), ParticleTypes.END_ROD, 8, 0.3, 0.05);
				}
			}
		});
	}

	/** Чаша родника: восемь моховых плит вокруг и вода в середине (в плите вода стоит и не растекается). */
	private static void fountain(Cast c, Vec3 center, int life) {
		BlockPos base = BlockPos.containing(center.add(0, 0.1, 0));
		BlockState rim = Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) Terrain.temp(c, base.offset(dx, 0, dz), rim, life + 40, false);
			}
		}
		BlockPos below = base.below();
		if (!c.level.getBlockState(below).isFaceSturdy(c.level, below, Direction.UP)) return;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState side = c.level.getBlockState(base.relative(d));
			if (side.isAir() || side.canBeReplaced()) return;
		}
		Terrain.temp(c, base, rim.setValue(SlabBlock.WATERLOGGED, true), life + 20, false);
	}

	/** Снаряд врага (его хозяин — враг мага). */
	private static boolean hostileShot(Cast c, Projectile pr) {
		return pr.getOwner() instanceof LivingEntity l && c.isEnemy(l);
	}

	// =====================================================================================================
	// Ранг 5
	// =====================================================================================================

	/** Печать бессмертия: на себя или на союзника под прицелом; копии — ещё по союзнику в 8 блоках. */
	private static boolean undying(Cast c) {
		List<LivingEntity> marked = new ArrayList<>();
		LivingEntity aimed = aimedAlly(c, c.spell.range);
		marked.add(aimed != null ? aimed : c.caster);
		if (aimed != null) marked.add(c.caster);
		List<LivingEntity> near = new ArrayList<>(c.alliesAround(Cast.center(c.caster), 8.0));
		near.removeIf(marked::contains);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster)));
		marked.addAll(near);
		if (c.copy >= marked.size()) return false;
		SpellKit.release(c);
		seal(c, marked.get(c.copy), c.duration(1200));
		return true;
	}

	/** Печать бессмертия на {@code ticks}: Неугасимая жизнь, знак-ромб на лбу, золотой взрыв при спасении. */
	static void seal(Cast c, LivingEntity t, int ticks) {
		t.addEffect(new MobEffectInstance(MagicEffects.UNDYING, ticks, 0), c.caster);
		long now = c.level.getGameTime();
		SEALED.values().removeIf(until -> until < now);
		SEALED.put(t.getUUID(), now + ticks);
		ServerLevel level = c.level;
		Vec3 feet = t.position();
		MagicFx.circle(level, School.LIFE, 5, GOLD, GOLD_PALE, feet.add(0, 0.07, 0), UP, 1.6F, 2, 0.3F, 12, 16, 14, 0.35F);
		MagicFx.pillar(level, feet, 0.5F, 3.5F, GOLD, GOLD_PALE, 24);
		MagicFx.burst(level, Cast.center(t), ParticleTypes.TOTEM_OF_UNDYING, 30, 0.4, 0.3);
		MagicFx.burst(level, Cast.center(t), MagicParticles.spark(GOLD, 0.35F, 24), 24, 0.5, 0.05);
		MagicFx.sound(level, feet, SoundEvents.BELL_BLOCK, 0.9F, 1.2F);
		MagicFx.sound(level, feet, MagicSounds.impactOf(c.spell), 0.8F, 0.8F);
		Zones.add(c, feet, 0, ticks, (z, age) -> {
			if (!t.isAlive() || !t.hasEffect(MagicEffects.UNDYING)) {
				z.stop();
				return;
			}
			if (age % 10 != 0) return;
			Vec3 brow = t.getEyePosition().add(t.getViewVector(1.0F).scale(0.3)).add(0, 0.22, 0);
			MagicFx.one(level, brow, MagicParticles.spark(GOLD, 0.16F, 12), 0, 0, 0);
		});
	}

	/** Печать сработала: золотой «феникс» отбрасывает врагов в 4 блоках. */
	private static void sealBurst(ServerLevel level, LivingEntity saved) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, saved.getBoundingBox().inflate(4.0),
				e -> e != saved && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == saved))) {
			Cast.knock(e, saved.position(), 1.0, 0.35);
		}
		Vec3 c = Cast.center(saved);
		MagicFx.pillar(level, saved.position(), 0.9F, 7.0F, GOLD, GOLD_PALE, 30);
		MagicFx.shockwave(level, saved.position().add(0, 0.15, 0), UP, 0.5F, 4.5F, GOLD, 14);
		MagicFx.sphere(level, c, 0.4F, 4.0F, GOLD, GOLD_PALE, 12);
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			MagicFx.one(level, c.add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6), MagicParticles.spark(GOLD_PALE, 0.3F, 30), Math.cos(a) * 0.08, 0.2,
					Math.sin(a) * 0.08);
		}
		MagicFx.burst(level, c, ParticleTypes.END_ROD, 20, 0.4, 0.12);
		MagicFx.flash(level, c, 16, 0.4F, GOLD_PALE, 10);
		MagicFx.soundFar(level, c, SoundEvents.BELL_RESONATE, 48, 1.2F);
		MagicFx.soundFar(level, c, MagicSounds.PILLAR, 32, 1.3F);
	}

	// =====================================================================================================
	// Ранг 6
	// =====================================================================================================

	/** Явление дремучего леса у прицела (копии — лес поменьше над следующей группой врагов). */
	private static boolean deepForest(Cast c) {
		Vec3 center = c.aimGround();
		boolean main = c.copy == 0;
		double r = main ? 12.0 : 8.0;
		int life = 200;
		ServerLevel level = c.level;
		SpellKit.release(c);
		MagicFx.circle(level, c.spell, center.add(0, 0.07, 0), UP, (float) r, 2, 0.35F, 16, life - 36, 20);
		MagicFx.flash(level, center, 64, 0.35F, GREEN, 12);
		MagicFx.shockwave(level, center.add(0, 0.2, 0), UP, 1.0F, (float) r * 1.3F, GREEN, 18);
		MagicFx.cloud(level, center.add(0, 1.0, 0), ParticleTypes.HAPPY_VILLAGER, 40, r * 0.5, 0.6, r * 0.5, 0.05);
		MagicFx.soundFar(level, center, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 64, 0.5F);
		MagicFx.soundFar(level, center, MagicSounds.RUMBLE, 64, 0.8F);
		MagicFx.soundFar(level, center, MagicSounds.impactOf(c.spell), 64, 0.7F);
		Magic.shake(level, center, 1.4F, 24, 40);
		grove(c, center, r, main ? 18 : 10, 300);

		Set<UUID> grabbed = new HashSet<>();
		Set<UUID> blessed = new HashSet<>();
		List<LivingEntity> foes = prey(c, center, r);
		for (int i = 0; i < Math.min(24, foes.size()); i++) {
			grabbed.add(foes.get(i).getUUID());
			grab(c, foes.get(i), center);
		}
		ParticleOptions leaf = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | 0x4FA83A);
		Zones.add(c, center, r, life, (z, age) -> {
			if (age % 4 == 0) {
				Vec3 p = SpellKit.inDisc(level.random, center, r);
				MagicFx.cloud(level, p.add(0, 2.5, 0), leaf, 3, 0.8, 0.8, 0.8, 0);
				MagicFx.one(level, SpellKit.inDisc(level.random, center, r).add(0, 0.3, 0), MagicParticles.mote(GREEN, 0.13F, 30), 0, 0.05, 0);
			}
			if (age % 30 == 0) MagicFx.sound(level, center, age % 60 == 0 ? SoundEvents.AZALEA_LEAVES_PLACE : SoundEvents.WOOD_PLACE, 1.0F, 0.6F);
			// Своим в лесу — 4 здоровья и Регенерация II (каждому один раз).
			if (age == 10 || age % 40 == 0) {
				for (LivingEntity e : c.alliesAround(center.add(0, 1, 0), r)) {
					if (!blessed.add(e.getUUID())) continue;
					c.heal(e, c.amount(4.0F));
					e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), c.caster);
					MagicFx.burst(level, Cast.center(e), MagicParticles.mote(GREEN, 0.15F, 24), 10, 0.4, 0.05);
				}
			}
			// Новые враги в лесу: каждые 2 с корни хватают ещё двоих.
			if (age % 40 == 0) {
				int n = 0;
				for (LivingEntity e : prey(c, center, r)) {
					if (n >= 2) break;
					if (grabbed.add(e.getUUID())) {
						grab(c, e, center);
						n++;
					}
				}
			}
		});
		return true;
	}

	/** Кого хватает лес: враги в круге, ближние первыми (мирных зверей лес щадит). */
	private static List<LivingEntity> prey(Cast c, Vec3 center, double r) {
		List<LivingEntity> out = new ArrayList<>(c.foesAround(center.add(0, 1, 0), r));
		out.removeIf(e -> e instanceof Animal && !c.isEnemy(e));
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
		return out;
	}

	/** Корни хватают врага: держат 5 с; через 0,5 с ствол бьёт (12), через 3 с давит и подбрасывает (24). */
	private static void grab(Cast c, LivingEntity e, Vec3 center) {
		ServerLevel level = c.level;
		Zones.Zone hold = root(c, e, 100);
		MagicFx.helix(level, e.position(), e.position().add(0, e.getBbHeight() + 0.4, 0), 0.5F + e.getBbWidth() * 0.5F, GREEN, PALE, 40);
		MagicFx.line(level, center.add(0, 0.2, 0), e.position().add(0, 0.2, 0), dust(Blocks.ROOTED_DIRT), 0.8);
		MagicFx.sound(level, e.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.7F);
		Zones.later(c, 10, () -> {
			if (!e.isAlive()) return;
			c.hurt(e, c.amount(12.0F), MagicDamage.EARTH);
			MagicFx.burst(level, Cast.center(e), dust(Blocks.OAK_LOG), 16, 0.4, 0.1);
			MagicFx.sound(level, e.position(), SoundEvents.WOOD_BREAK, 1.0F, 0.6F);
		});
		Zones.later(c, 60, () -> {
			if (hold != null) hold.stop();
			if (!e.isAlive()) return;
			c.hurt(e, c.amount(24.0F), MagicDamage.EARTH);
			SpellKit.launch(e, 1.0);
			MagicFx.burst(level, Cast.center(e), dust(Blocks.OAK_LOG), 24, 0.5, 0.15);
			MagicFx.shockwave(level, e.position().add(0, 0.15, 0), UP, 0.3F, 2.5F, BARK, 10);
			MagicFx.sound(level, e.position(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.9F, 0.6F);
		});
	}

	/**
	 * Корни держат на месте {@code ticks} тиков (боссы — только Замедление III). Возвращает зону-хватку (её можно
	 * снять раньше) или null.
	 */
	static Zones.Zone root(Cast c, LivingEntity e, int ticks) {
		if (boss(e)) {
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 2), c.caster);
			return null;
		}
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false, true), c.caster);
		Vec3 at = e.position();
		ParticleOptions roots = dust(Blocks.MANGROVE_ROOTS);
		return Zones.add(c, at, 0.5, ticks, (z, age) -> {
			if (!e.isAlive()) {
				z.stop();
				return;
			}
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(0, Math.min(0, v.y), 0);
			Vec3 p = e.position();
			double dx = p.x - at.x, dz = p.z - at.z;
			if (dx * dx + dz * dz > 0.09) e.teleportTo(at.x, p.y, at.z);
			if (e instanceof ServerPlayer) e.hurtMarked = true;
			if (age % 10 == 1) MagicFx.cloud(c.level, at.add(0, 0.2, 0), roots, 4, e.getBbWidth() * 0.5, 0.1, e.getBbWidth() * 0.5, 0);
		});
	}

	/** Роща: {@code count} деревьев в круге, не на существах; держатся {@code ticks} тиков. */
	private static void grove(Cast c, Vec3 center, double r, int count, int ticks) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r + 3, 8, r + 3),
				LivingEntity::isAlive);
		List<BlockPos> spots = new ArrayList<>();
		for (int tries = 0; tries < count * 6 && spots.size() < count; tries++) {
			Vec3 p = SpellKit.inDisc(rnd, center, r * 0.92);
			BlockPos top = surface(level, BlockPos.containing(p.x, center.y + 0.1, p.z));
			if (top == null) continue;
			if (spots.stream().anyMatch(s -> s.distSqr(top) < 7)) continue;
			Vec3 axis = Vec3.atBottomCenterOf(top);
			if (near.stream().anyMatch(e -> horizontal(e.position(), axis) < 1.6 + e.getBbWidth() * 0.5)) continue;
			spots.add(top);
		}
		List<BlockPos> leaves = new ArrayList<>();
		for (BlockPos base : spots) {
			int height = 4 + rnd.nextInt(4);
			int delay = rnd.nextInt(12);
			BlockState log = Blocks.OAK_LOG.defaultBlockState();
			Zones.add(c, Vec3.atCenterOf(base), 0, delay + height + 2, (z, age) -> {
				int k = age - delay - 1;
				if (k < 0) return;
				if (k < height) {
					BlockPos p = base.above(k);
					if (!Terrain.temp(c, p, log, ticks + 20 + height - k, false)) {
						if (k > 0) crown(c, p.below(), ticks, leaves);
						z.stop();
						return;
					}
					MagicFx.burst(level, Vec3.atCenterOf(p), dust(Blocks.OAK_LOG), 6, 0.3, 0.05);
					if (k == 0) MagicFx.sound(level, Vec3.atCenterOf(p), SoundEvents.WOOD_PLACE, 0.8F, 0.6F);
				} else if (k == height) {
					crown(c, base.above(height - 1), ticks, leaves);
					MagicFx.sound(level, Vec3.atCenterOf(base.above(height)), SoundEvents.AZALEA_LEAVES_PLACE, 0.8F, 0.9F);
				}
			});
		}
		Zones.later(c, ticks + 60, () -> sweep(level, leaves));
	}

	/** Шапка дерева: крестовина веток на верхушке и листва вокруг (каждый лист касается ветки — не облетает). */
	private static void crown(Cast c, BlockPos top, int ticks, List<BlockPos> leaves) {
		List<BlockPos> logs = new ArrayList<>();
		logs.add(top);
		logs.add(top.below());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos arm = top.relative(d);
			if (Terrain.temp(c, arm, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis()), ticks + 20, false)) {
				logs.add(arm);
			}
		}
		BlockState leaf = leaf(Blocks.OAK_LEAVES);
		for (BlockPos l : logs) {
			for (Direction d : Direction.values()) {
				if (d == Direction.DOWN && l.equals(top.below())) continue;
				BlockPos q = l.relative(d);
				if (Terrain.temp(c, q, leaf, ticks + c.level.random.nextInt(8), false)) leaves.add(q);
			}
		}
		MagicFx.cloud(c.level, Vec3.atCenterOf(top), ParticleTypes.HAPPY_VILLAGER, 8, 1.2, 0.6, 1.2, 0);
	}

	// =====================================================================================================
	// Ранг 7
	// =====================================================================================================

	/** Цветение Иггдрасиля: кинематографичная шкала на 10 с, Древо стоит минуту. */
	private static boolean yggdrasil(Cast c) {
		ServerLevel level = c.level;
		Vec3 center = c.aimGround();
		BlockPos base = BlockPos.containing(center.add(0, 0.1, 0));
		Vec3 trunk = Vec3.atBottomCenterOf(base);
		int life = 1200;
		double reach = 32.0;
		SpellKit.release(c);

		// t0: удар выпуска — столп зелёного света, круги, тряска, треск ломающейся земли.
		MagicFx.soundFar(level, trunk, MagicSounds.RELEASE_ULTIMATE, 160, 1.0F);
		MagicFx.soundFar(level, trunk, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 96, 0.5F);
		MagicFx.soundFar(level, trunk, MagicSounds.RUMBLE, 96, 0.7F);
		MagicFx.soundFar(level, trunk, MagicSounds.PILLAR, 96, 0.8F);
		MagicFx.circle(level, c.spell, center.add(0, 0.07, 0), UP, 16.0F, 3, 0.5F, 20, 170, 30);
		MagicFx.pillar(level, trunk, 3.5F, 48.0F, GREEN, PALE, 60);
		MagicFx.shockwave(level, trunk.add(0, 0.3, 0), UP, 2.0F, 34.0F, GREEN, 24);
		MagicFx.flash(level, trunk, 128, 0.6F, PALE, 20);
		Magic.shake(level, trunk, 2.4F, 40, 96);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(trunk, trunk).inflate(3.0, 4.0, 3.0), LivingEntity::isAlive)) {
			if (horizontal(e.position(), trunk) < 2.4) Cast.knock(e, trunk, 0.9, 0.3);
		}

		// t0–40: растёт Древо, земля расцветает расширяющимся кругом.
		List<BlockPos> leaves = new ArrayList<>();
		Map<Integer, List<Piece>> plan = new HashMap<>();
		for (Piece p : worldTree(base, level.random, life)) plan.computeIfAbsent(p.tick(), k -> new ArrayList<>()).add(p);
		int growth = plan.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
		Zones.add(c, trunk, 16, growth, (z, age) -> {
			List<Piece> now = plan.get(age - 1);
			if (now != null) {
				for (Piece p : now) {
					if (Terrain.temp(c, p.pos(), p.state(), p.keep(), false) && p.state().getBlock() instanceof LeavesBlock) leaves.add(p.pos());
				}
			}
			if (age % 3 == 0) {
				Vec3 tip = trunk.add(0, Math.min(30, age), 0);
				MagicFx.burst(level, tip, dust(Blocks.OAK_WOOD), 12, 1.2, 0.1);
				MagicFx.burst(level, tip, MagicParticles.spark(GREEN, 0.4F, 20), 8, 1.5, 0.08);
			}
			if (age % 6 == 0) MagicFx.soundFar(level, trunk.add(0, age, 0), age % 12 == 0 ? SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR : SoundEvents.WOOD_BREAK,
					64, 0.45F + 0.01F * age);
			if (age <= 20 && age % 2 == 0) {
				double r1 = 16.0 * age / 20.0;
				bloom(c, center, r1 - 1.6, r1, 0.3F, life);
				MagicFx.ring(level, center.add(0, 0.3, 0), r1, MagicParticles.mote(GREEN, 0.15F, 24), (int) (12 + r1 * 3), 0.02);
			}
		});
		Zones.later(c, life + 120, () -> sweep(level, leaves));
		Zones.later(c, 8, () -> {
			MagicFx.circle(level, c.spell, trunk.add(0, 38, 0), DOWN, 14.0F, 3, 1.2F, 30, 150, 30);
			MagicFx.soundFar(level, trunk.add(0, 38, 0), MagicSounds.CIRCLE_BIG, 96, 0.8F);
		});

		// t20: всё живое вокруг исцеляется и получает защиту от смерти.
		Zones.later(c, 20, () -> {
			Vec3 crown = trunk.add(0, 26, 0);
			MagicFx.sphere(level, trunk.add(0, 2, 0), 2.0F, (float) reach, PALE, GOLD_PALE, 30);
			MagicFx.dome(level, trunk, (float) reach, GREEN, GOLD_PALE, 40);
			MagicFx.flash(level, trunk, 96, 0.45F, GOLD_PALE, 16);
			MagicFx.soundFar(level, trunk, SoundEvents.BELL_RESONATE, 96, 0.8F);
			MagicFx.soundFar(level, trunk, MagicSounds.DOMAIN, 96, 1.2F);
			MagicFx.sound(level, trunk, SoundEvents.BEACON_ACTIVATE, 2.0F, 1.2F);
			Set<UUID> owners = new HashSet<>();
			owners.add(c.caster.getUUID());
			int beams = 0;
			for (LivingEntity e : c.alliesAround(trunk.add(0, 1, 0), reach)) {
				blessAll(c, e);
				if (e instanceof ServerPlayer sp) owners.add(sp.getUUID());
				if (beams++ < 24) MagicFx.beam(level, crown, Cast.center(e), 0.15F, GOLD, GOLD_PALE, 14);
			}
			revivePets(c, trunk, owners);
		});

		// t20–140: корни бьют врагов каждую секунду; нежить ещё и горит.
		Map<UUID, Float> playerDamage = new HashMap<>();
		SpellKit.repeat(c, 7, 20, i -> Zones.later(c, 20, () -> rootPulse(c, trunk, reach, playerDamage, i)));
		Zones.add(c, trunk, reach, 200, (z, age) -> {
			if (age >= 20 && age % 3 == 0) {
				Vec3 p = SpellKit.inDisc(level.random, trunk, 10).add(0, 22 + level.random.nextInt(8), 0);
				MagicFx.one(level, p, MagicParticles.mote(GOLD, 0.16F, 60), 0, -0.06, 0);
				if (age % 6 == 0) MagicFx.one(level, p, ParticleTypes.FALLING_SPORE_BLOSSOM, 0, 0, 0);
			}
			if (age >= 20 && age <= 160 && age % 20 == 0) {
				for (LivingEntity e : c.foesAround(trunk, reach)) {
					if (!Cast.isUndead(e)) continue;
					c.hurt(e, c.amount(4.0F), MagicDamage.HOLY);
					MagicFx.burst(level, Cast.center(e), ParticleTypes.END_ROD, 6, 0.3, 0.05);
				}
			}
			if (age % 40 == 0) {
				MagicFx.sound(level, trunk.add(0, 20, 0), SoundEvents.AZALEA_LEAVES_PLACE, 2.0F, 0.7F);
				MagicFx.sound(level, trunk, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.4F);
				MagicFx.sound(level, trunk, MagicSounds.RUMBLE, 0.6F, 1.2F);
			}
			if (age == 180) {
				MagicFx.soundFar(level, trunk, SoundEvents.BELL_BLOCK, 64, 0.8F);
				MagicFx.soundFar(level, trunk, SoundEvents.AMETHYST_BLOCK_RESONATE, 48, 1.0F);
			}
		});
		return true;
	}

	/** Полное исцеление Иггдрасиля: здоровье, очищение, Регенерация III, Поглощение IV, Печать и 30% маны. */
	private static void blessAll(Cast c, LivingEntity e) {
		e.setHealth(e.getMaxHealth());
		cleanse(e, Integer.MAX_VALUE);
		e.clearFire();
		e.setTicksFrozen(0);
		e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 2), c.caster);
		e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 3), c.caster);
		seal(c, e, 600);
		if (e instanceof ServerPlayer sp && sp.hasAttached(Magic.MANA)) {
			float max = Magic.maxMana(sp);
			sp.setAttached(Magic.MANA, Math.min(max, Magic.mana(sp) + 0.3F * max));
		}
		MagicFx.burst(c.level, Cast.center(e), ParticleTypes.TOTEM_OF_UNDYING, 20, 0.4, 0.3);
	}

	/** Удар корней номер {@code i}: 14 урона землёй всем врагам в 32 блоках, хватка 2 с (игрокам — не больше 30 всего). */
	private static void rootPulse(Cast c, Vec3 trunk, double reach, Map<UUID, Float> playerDamage, int i) {
		ServerLevel level = c.level;
		List<LivingEntity> foes = c.foesAround(trunk, reach);
		foes.removeIf(e -> e instanceof Animal && !c.isEnemy(e));
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(trunk)));
		int n = 0;
		for (LivingEntity e : foes) {
			if (n++ >= 32) break;
			float dmg = c.amount(14.0F);
			if (e instanceof ServerPlayer) {
				float done = playerDamage.getOrDefault(e.getUUID(), 0.0F);
				dmg = Math.min(dmg, 30.0F - done);
				if (dmg <= 0) continue;
				playerDamage.put(e.getUUID(), done + dmg);
			}
			c.hurt(e, dmg, MagicDamage.EARTH);
			root(c, e, 40);
			BlockPos feet = e.blockPosition();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = feet.relative(d, 1 + Mth.ceil(e.getBbWidth() * 0.5 - 0.5));
				Terrain.temp(c, q, Blocks.MANGROVE_ROOTS.defaultBlockState(), 40, false);
				if (e.getBbHeight() > 1.2) Terrain.temp(c, q.above(), Blocks.MANGROVE_ROOTS.defaultBlockState(), 34, false);
			}
			MagicFx.helix(level, e.position(), e.position().add(0, e.getBbHeight() + 0.6, 0), 0.5F + e.getBbWidth() * 0.5F, GREEN, GOLD_PALE, 24);
			MagicFx.burst(level, e.position().add(0, 0.3, 0), dust(Blocks.ROOTED_DIRT), 16, 0.5, 0.15);
			if (n <= 8) MagicFx.line(level, trunk.add(0, 0.3, 0), e.position().add(0, 0.3, 0), dust(Blocks.ROOTED_DIRT), 1.5);
		}
		MagicFx.shockwave(level, trunk.add(0, 0.25, 0), UP, 3.0F, (float) reach, i % 2 == 0 ? GREEN : GOLD, 18);
		MagicFx.soundFar(level, trunk, MagicSounds.RUMBLE, 64, 0.8F + 0.05F * i);
		MagicFx.soundFar(level, trunk, SoundEvents.ROOTED_DIRT_BREAK, 48, 0.6F);
		if (!foes.isEmpty()) Magic.shake(level, trunk, 0.8F, 10, reach);
	}

	/**
	 * План Мирового Древа: ствол 3×3 высотой 28 с корневыми наплывами, 22 ветви с отростками, листва и светящиеся
	 * плоды вокруг веток. Тик роста: ствол по ярусу в тик, ветви — с 12-го тика, листва — через 2 тика после своей
	 * ветки. Исчезает сверху вниз: сначала листва, потом ветви, последним — ствол.
	 */
	private static List<Piece> worldTree(BlockPos base, RandomSource rnd, int life) {
		Map<BlockPos, Piece> logs = new LinkedHashMap<>();
		BlockState wood = Blocks.OAK_WOOD.defaultBlockState();
		for (int k = 0; k < 28; k++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = base.offset(dx, k, dz);
					logs.put(p, new Piece(p, wood, k, life + 40 + (27 - k)));
				}
			}
		}
		for (int k = 28; k < 32; k++) logs.put(base.above(k), new Piece(base.above(k), wood, k, life + 40));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState root = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
			for (int s = 2; s <= 4; s++) logs.put(base.relative(d, s), new Piece(base.relative(d, s), root, s - 2, life + 70));
			logs.put(base.relative(d, 2).above(), new Piece(base.relative(d, 2).above(), wood, 1, life + 70));
			BlockPos diag = base.relative(d, 2).relative(d.getClockWise(), 2);
			logs.put(diag, new Piece(diag, wood, 0, life + 70));
		}
		// Ветви: золотой угол по кругу, пять ярусов (нижние — длиннее и положе).
		Map<BlockPos, Piece> boughs = new LinkedHashMap<>();
		for (int i = 0; i < 22; i++) {
			int tier = i % 5;
			double a = i * 2.39996;
			double rise = 0.25 + 0.12 * tier;
			Vec3 dir = new Vec3(Math.cos(a), rise, Math.sin(a)).normalize();
			Vec3 from = Vec3.atCenterOf(base.above(14 + tier * 3)).add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
			int start = 12 + tier * 3;
			int length = 11 - tier;
			branch(boughs, logs, from, dir, length, start, life + 30, 0.3);
			Vec3 side = new Vec3(-Math.sin(a), rise * 0.5, Math.cos(a)).normalize();
			branch(boughs, logs, from.add(dir.scale(length * 0.55)), side, 3, start + (int) (length * 0.55), life + 30, 0);
			branch(boughs, logs, from.add(dir.scale(length * 0.85)), side.scale(-1).add(0, rise, 0).normalize(), 3,
					start + (int) (length * 0.85), life + 30, 0);
		}
		// Листва вокруг веток (и макушки): каждый лист касается ветки.
		Map<BlockPos, Piece> foliage = new LinkedHashMap<>();
		BlockState oak = leaf(Blocks.OAK_LEAVES);
		BlockState bloom = leaf(Blocks.FLOWERING_AZALEA_LEAVES);
		BlockState fruit = Blocks.SHROOMLIGHT.defaultBlockState();
		List<Piece> holders = new ArrayList<>(boughs.values());
		for (int k = 26; k < 32; k++) holders.add(logs.get(base.above(k)));
		for (Piece b : holders) {
			for (Direction d : Direction.values()) {
				BlockPos q = b.pos().relative(d);
				if (logs.containsKey(q) || boughs.containsKey(q) || foliage.containsKey(q)) continue;
				float roll = rnd.nextFloat();
				BlockState s = roll < 0.06F ? fruit : roll < 0.3F ? bloom : oak;
				foliage.put(q, new Piece(q, s, b.tick() + 2, life + rnd.nextInt(25)));
			}
		}
		List<Piece> out = new ArrayList<>(logs.values());
		out.addAll(boughs.values());
		out.addAll(foliage.values());
		return out;
	}

	/** Ветка из брёвен по направлению; {@code bare} — доля у основания без листвы (её отметит отсутствие в boughs). */
	private static void branch(Map<BlockPos, Piece> boughs, Map<BlockPos, Piece> trunk, Vec3 from, Vec3 dir, int length, int start, int keep,
			double bare) {
		Direction.Axis axis = Math.abs(dir.y) > Math.max(Math.abs(dir.x), Math.abs(dir.z)) ? Direction.Axis.Y
				: Math.abs(dir.x) > Math.abs(dir.z) ? Direction.Axis.X : Direction.Axis.Z;
		BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
		for (int s = 0; s <= length * 2; s++) {
			BlockPos p = BlockPos.containing(from.add(dir.scale(s * 0.5)));
			if (trunk.containsKey(p) || boughs.containsKey(p)) continue;
			int tick = start + s / 2;
			// Голый участок у ствола тоже бревно, но листва вокруг него не нужна — кладём его в ствол.
			if (s < length * 2 * bare) trunk.put(p, new Piece(p, log, tick, keep));
			else boughs.put(p, new Piece(p, log, tick, keep));
		}
	}

	// =====================================================================================================
	// Общие помощники
	// =====================================================================================================

	/** Свой, которого можно лечить: маг, игроки, питомцы, призванные, жители (не стойки и не манекены). */
	private static boolean patient(Cast c, Entity e) {
		return e instanceof LivingEntity l && l.isAlive() && c.isAlly(l) && !(l instanceof ArmorStand) && !(l instanceof Mannequin);
	}

	/** Союзник под прицелом (не сам маг) или null. */
	private static LivingEntity aimedAlly(Cast c, double range) {
		HitResult hit = Targeting.ray(c.level, c.caster, c.eye(), c.look(), Math.min(range, Magic.MAX_RANGE),
				e -> e != c.caster && !e.isSpectator() && patient(c, e));
		return hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity l ? l : null;
	}

	private static boolean wounded(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth() - 0.01F;
	}

	/** Сколько вредных эффектов на существе. */
	static int harmful(LivingEntity e) {
		int n = 0;
		for (MobEffectInstance i : e.getActiveEffects()) if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) n++;
		return n;
	}

	/** Снять до {@code max} вредных эффектов; возвращает, сколько снято. */
	static int cleanse(LivingEntity e, int max) {
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance i : e.getActiveEffects()) {
			if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(i.getEffect());
		}
		int n = Math.min(max, bad.size());
		for (int k = 0; k < n; k++) e.removeEffect(bad.get(k));
		return n;
	}

	/** Ослабить эффект на уровень (первый уровень — снять). */
	private static void weaken(LivingEntity e, Holder<MobEffect> effect) {
		MobEffectInstance i = e.getEffect(effect);
		if (i == null) return;
		e.removeEffect(effect);
		if (i.getAmplifier() > 0) {
			e.addEffect(new MobEffectInstance(effect, i.getDuration(), i.getAmplifier() - 1, i.isAmbient(), i.isVisible(), i.showIcon()));
		}
	}

	/** Боссы: жёсткий контроль (корни) на них не действует — только замедление. */
	private static boolean boss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static ParticleOptions dust(Block block) {
		return new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
	}

	/** Листва, что не облетает: сохранённая и вплотную к ветке (так её состояние не меняется). */
	private static BlockState leaf(Block block) {
		return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 1);
	}

	/** Первый воздух над землёй в столбце (±4 от высоты точки) или null. */
	private static BlockPos surface(ServerLevel level, BlockPos p) {
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos at = p.offset(0, dy, 0);
			BlockState below = level.getBlockState(at.below());
			if (level.getBlockState(at).isAir() && !below.isAir() && below.getFluidState().isEmpty()) return at;
		}
		return null;
	}

	/**
	 * Цветы на кольце от {@code r0} до {@code r1}: на траве и земле — цветок, на камне (только временно) — мох.
	 * {@code tempTicks} = 0 — цветы навсегда (луг), иначе временные. Ставятся только в воздух.
	 */
	private static int bloom(Cast c, Vec3 center, double r0, double r1, float chance, int tempTicks) {
		ServerLevel level = c.level;
		BlockPos o = BlockPos.containing(center.add(0, 0.1, 0));
		int ri = Mth.ceil(r1);
		int n = 0;
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < Math.max(0, r0) || d >= r1 || level.random.nextFloat() >= chance) continue;
				BlockPos top = surface(level, o.offset(dx, 0, dz));
				if (top == null) continue;
				BlockPos under = top.below();
				BlockState ground = level.getBlockState(under);
				BlockState put;
				if (ground.is(BlockTags.DIRT)) {
					put = FLOWERS[level.random.nextInt(FLOWERS.length)].defaultBlockState();
				} else if (tempTicks > 0 && ground.isFaceSturdy(level, under, Direction.UP)) {
					put = Blocks.MOSS_CARPET.defaultBlockState();
				} else {
					continue;
				}
				boolean ok;
				if (tempTicks > 0) {
					ok = Terrain.temp(c, top, put, tempTicks + level.random.nextInt(20), false);
				} else {
					ok = level.mayInteract(c.caster, top) && put.canSurvive(level, top) && level.setBlock(top, put, 3);
				}
				if (ok) {
					n++;
					MagicFx.burst(level, Vec3.atBottomCenterOf(top).add(0, 0.3, 0), ParticleTypes.HAPPY_VILLAGER, 2, 0.2, 0);
				}
			}
		}
		return n;
	}

	/** Убрать листву, которую временные блоки уже не вернут (кто-то сломал ветку и лист сменил состояние). */
	private static void sweep(ServerLevel level, List<BlockPos> leaves) {
		for (BlockPos p : leaves) {
			if (Terrain.isTemp(p)) continue;
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof LeavesBlock && s.getValue(LeavesBlock.PERSISTENT)
					&& (s.is(Blocks.OAK_LEAVES) || s.is(Blocks.FLOWERING_AZALEA_LEAVES))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	// =====================================================================================================
	// Печать и питомцы: события сервера
	// =====================================================================================================

	private static void hooks() {
		// Своя фаза раньше общей: эффект Неугасимой жизни снимается при спасении, а нам нужно успеть его увидеть.
		Identifier early = Identifier.fromNamespaceAndPath("suchki", "life_seal");
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(early, (entity, source, amount) -> {
			if (entity.level() instanceof ServerLevel level && entity.hasEffect(MagicEffects.UNDYING)
					&& SEALED.remove(entity.getUUID()) != null) {
				sealBurst(level, entity);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> rememberPet(entity));
	}

	/** Запомнить павшего питомца (на 30 с) — его сможет вернуть Иггдрасиль. */
	private static void rememberPet(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level) || Minions.isSummon(e) || !(e instanceof OwnableEntity own)) return;
		boolean tame = e instanceof TamableAnimal t ? t.isTame() : e instanceof AbstractHorse h && h.isTamed();
		if (!tame || own.getOwnerReference() == null) return;
		long now = level.getGameTime();
		FALLEN.removeIf(p -> now - p.time() > PET_MEMORY);
		if (FALLEN.size() >= 64) FALLEN.remove(0);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		e.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		tag.remove("UUID");
		FALLEN.add(new FallenPet(own.getOwnerReference().getUUID(), level.dimension(), e.getType(), tag, now));
	}

	/** Вернуть павших за 30 с питомцев хозяев {@code owners} к стволу. */
	private static int revivePets(Cast c, Vec3 trunk, Set<UUID> owners) {
		long now = c.level.getGameTime();
		int n = 0;
		Iterator<FallenPet> it = FALLEN.iterator();
		while (it.hasNext()) {
			FallenPet p = it.next();
			if (now - p.time() > PET_MEMORY) {
				it.remove();
				continue;
			}
			if (!owners.contains(p.owner()) || p.dimension() != c.level.dimension()) continue;
			it.remove();
			Entity e = EntityType.loadEntityRecursive(p.type(), p.tag(), c.level, EntitySpawnReason.LOAD, x -> x);
			if (!(e instanceof LivingEntity l)) continue;
			double a = Math.PI * 2 * n / 6.0;
			Vec3 spot = Targeting.ground(c.level, c.caster, trunk.add(Math.cos(a) * 3.5, 2.0, Math.sin(a) * 3.5));
			l.snapTo(spot.x, spot.y, spot.z, l.getYRot(), 0);
			l.setHealth(l.getMaxHealth());
			l.deathTime = 0;
			l.hurtTime = 0;
			l.removeAllEffects();
			l.clearFire();
			l.setDeltaMovement(Vec3.ZERO);
			if (c.level.addFreshEntity(l)) {
				n++;
				MagicFx.pillar(c.level, spot, 0.6F, 4.0F, GOLD, GOLD_PALE, 30);
				MagicFx.burst(c.level, Cast.center(l), ParticleTypes.TOTEM_OF_UNDYING, 30, 0.4, 0.3);
				MagicFx.sound(c.level, spot, SoundEvents.TOTEM_USE, 0.8F, 1.3F);
			}
		}
		return n;
	}
}
