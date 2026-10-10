package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.arena;
import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.fill;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hitOn;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.hurt;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Проверки заклинаний стихии воды и льда: урон холодом, замедление и промерзание, лёд под ногами и темницы
 * (временные блоки), снаряды и мультикаст, поток дракона (лава → обсидиан), глыба с неба и цунами.
 */
final class WaterSpellTests {
	private WaterSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Вода — Ледяной Шип", WaterSpellTests::iceShard);
		tests.put("Магия: Вода — Дыхание Глубин", WaterSpellTests::deepBreath);
		tests.put("Магия: Вода — Ледяной Клюв", WaterSpellTests::frostBeak);
		tests.put("Магия: Вода — Белая Луна", WaterSpellTests::frostNova);
		tests.put("Магия: Вода — Вальс Ледяных Копий", WaterSpellTests::frostWaltz);
		tests.put("Магия: Вода — Ледниковая Гряда", WaterSpellTests::glacierRidge);
		tests.put("Магия: Вода — Склеп Проклятого Хрусталя", WaterSpellTests::crystalPrison);
		tests.put("Магия: Вода — Рёв Владыки Пучины", WaterSpellTests::wyrmRoar);
		tests.put("Магия: Вода — Градовый Молот", WaterSpellTests::hailHammer);
		tests.put("Магия: Вода — Потоп Левиафана", WaterSpellTests::leviathan);
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** Убрать мишени, снаряды, эффекты мага и следы заклинаний. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p, int r) {
		clean();
		p.removeAllEffects();
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(r), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		tidy(level, at, p, 48);
	}

	private static int projectiles(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(SpellProjectile.class, new AABB(at).inflate(20)).size();
	}

	private static int amp(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		MobEffectInstance i = e.getEffect(effect);
		return i == null ? -1 : i.getAmplifier();
	}

	/** Сколько льда (плотного и синего) в кубе радиуса {@code r}. */
	private static int ice(ServerLevel level, BlockPos c, int r) {
		int n = 0;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			BlockState s = level.getBlockState(q);
			if (s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)) n++;
		}
		return n;
	}

	/** Лёд у ступней: сколько соседних по горизонтали блоков — временный плотный лёд. */
	private static int rootIce(ServerLevel level, LivingEntity e) {
		int n = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos q = e.blockPosition().relative(d);
			if (level.getBlockState(q).is(Blocks.PACKED_ICE) && Terrain.isTemp(q)) n++;
		}
		return n;
	}

	// --- Ранг 1 ---

	private static void iceShard(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 60);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Magic.hitNow(p, spell("ice_shard"), hitOn(z), 1.0F);
			r.check(lost(z) >= 5.0F && amp(z, MobEffects.SLOWNESS) >= 1 && z.getTicksFrozen() > 0,
					"Ледяной Шип: ~6 урона холодом, Замедление II и промерзание (" + hp(z) + ", холод " + z.getTicksFrozen() + ")");
			z.discard();

			// Попадание в воду — гладь вокруг схватывается льдом.
			BlockPos pool = at.offset(0, -1, 5);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(pool.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 2);
			Magic.hitNow(p, spell("ice_shard"), new BlockHitResult(Vec3.atCenterOf(pool).add(0, 0.5, 0), Direction.UP, pool, false), 1.0F);
			int frosted = 0;
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (level.getBlockState(pool.offset(dx, 0, dz)).is(Blocks.FROSTED_ICE)) frosted++;
			r.check(frosted >= 5, "Ледяной Шип: вода у места попадания замерзает (" + frosted + " из 9)");
			clean();
			r.check(level.getBlockState(pool).is(Blocks.WATER), "Ледяной Шип: лёд временный — снова вода");
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(pool.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);

			int before = projectiles(level, at);
			Magic.castNow(p, spell("ice_shard"), 1.0F);
			int one = projectiles(level, at) - before;
			Magic.castNow(p, spell("ice_shard"), 1.0F, 3);
			int three = projectiles(level, at) - before - one;
			r.check(one == 1 && three == 3, "Ледяной Шип: один шип, мультикаст ×3 — три (" + one + ", " + three + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void deepBreath(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 61);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Magic.castNow(p, spell("deep_breath"), 1.0F);
			MobEffectInstance wb = p.getEffect(MobEffects.WATER_BREATHING);
			r.check(wb != null && wb.getDuration() >= 3000 && p.hasEffect(MobEffects.DOLPHINS_GRACE) && p.hasEffect(MobEffects.CONDUIT_POWER),
					"Дыхание Глубин: 3 мин дыхания под водой, грация дельфина и сила проводника");
			p.removeAllEffects();

			// Мультикаст ×2: вторая копия дарит дыхание союзнику рядом.
			LivingEntity friend = dummy(level, EntityType.VILLAGER, at.offset(2, 0, 1));
			LivingEntity foe = dummy(level, EntityType.ZOMBIE, at.offset(-2, 0, 1));
			Magic.castNow(p, spell("deep_breath"), 1.0F, 2);
			r.check(p.hasEffect(MobEffects.WATER_BREATHING) && friend.hasEffect(MobEffects.WATER_BREATHING) && !foe.hasEffect(MobEffects.WATER_BREATHING),
					"Дыхание Глубин ×2: маг и союзник-житель дышат под водой, враг — нет");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 2 ---

	private static void frostBeak(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 62);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			int before = projectiles(level, at);
			Magic.castNow(p, spell("frost_beak"), 1.0F);
			int first = projectiles(level, at) - before;
			run(10);
			int all = projectiles(level, at) - before;
			r.check(first == 1 && all == 3, "Ледяной Клюв: копья за спиной летят по очереди — 1, затем 3 (" + first + ", " + all + ")");
			tidy(level, at, p);

			Magic.castNow(p, spell("frost_beak"), 1.0F, 2);
			run(10);
			int six = projectiles(level, at) - before;
			r.check(six == 6, "Ледяной Клюв ×2: шесть копий (" + six + ")");
			tidy(level, at, p);

			// Три копья в одну цель — ледяной взрыв сверху.
			z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			Magic.hitNow(p, spell("frost_beak"), hitOn(z), 1.0F);
			float one = lost(z);
			Magic.hitNow(p, spell("frost_beak"), hitOn(z), 1.0F);
			Magic.hitNow(p, spell("frost_beak"), hitOn(z), 1.0F);
			float three = lost(z);
			r.check(one >= 6.0F && three >= one * 3.3F && z.getTicksFrozen() > 0 && z.hasEffect(MobEffects.SLOWNESS),
					"Ледяной Клюв: 7 урона за копьё, три в цель — ещё ледяной взрыв (" + one + " → " + three + ", ждём ≈25)");

			BlockPos floor = at.offset(0, -1, 5);
			Magic.hitNow(p, spell("frost_beak"), new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false), 1.0F);
			r.check(level.getBlockState(floor.above()).is(Blocks.PACKED_ICE) && Terrain.isTemp(floor.above()),
					"Ледяной Клюв: промах вмерзает в землю временным ледяным шипом");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 3 ---

	private static void frostNova(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 63);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity near = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 4));
			LivingEntity mid = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -8));
			Magic.castNow(p, spell("frost_nova"), 1.0F);
			r.check(lost(near) >= 7.0F && amp(near, MobEffects.SLOWNESS) >= 2 && near.getTicksFrozen() > 0 && !hurt(mid),
					"Белая Луна: 9 урона холодом в радиусе 6, сильное замедление; дальше 6 — цел (" + hp(near) + ", " + hp(mid) + ")");
			r.check(rootIce(level, near) >= 3, "Белая Луна: ноги врага вмерзают в лёд (" + rootIce(level, near) + " из 4 блоков вокруг)");
			clean();
			r.check(rootIce(level, near) == 0, "Белая Луна: лёд у ног временный");
			near.setHealth(near.getMaxHealth());

			// ×3 — эхо-кольца шире (8 и 10): достают дальнего.
			Magic.castNow(p, spell("frost_nova"), 1.0F, 3);
			r.check(hurt(mid) && lost(near) >= 13.0F,
					"Белая Луна ×3: эхо-кольца бьют ещё раз и дотягиваются до 8 блоков (" + hp(near) + ", " + hp(mid) + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void frostWaltz(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 64);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			// Вражеская стрела, влетевшая в кольцо копий, разбивается вместе с копьём.
			Magic.castNow(p, spell("frost_waltz"), 1.0F);
			run(20);
			Entity arrow = DevTests.spawn(level, EntityType.ARROW, at);
			arrow.setPos(p.getX() + 1.8, p.getY() + 1.4, p.getZ());
			arrow.setDeltaMovement(0, 0, 0.8);
			run(2);
			r.check(arrow.isRemoved(), "Вальс: вражеская стрела у копья разбивается");
			r.check(Zones.active() > 0, "Вальс: кольцо копий держится");
			tidy(level, at, p);

			// Копья сами летят в ближайшего врага — раз в полсекунды, все шесть.
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 7));
			int before = projectiles(level, at);
			Magic.castNow(p, spell("frost_waltz"), 1.0F);
			run(12);
			int first = projectiles(level, at) - before;
			run(55);
			int all = projectiles(level, at) - before;
			r.check(first == 1 && all == 6, "Вальс: копьё в врага раз в 0,5 с, всего шесть (" + first + ", " + all + ")");

			Magic.hitNow(p, spell("frost_waltz"), hitOn(z), 1.0F);
			r.check(lost(z) >= 6.0F && amp(z, MobEffects.SLOWNESS) >= 1, "Вальс: копьё — 7 урона холодом и Замедление II (" + hp(z) + ")");
			tidy(level, at, p);

			// Конец вальса: оставшиеся копья разлетаются осколками.
			Magic.castNow(p, spell("frost_waltz"), 1.0F, 2);
			int zones = Zones.active();
			// Врага ставим только к концу, иначе копья заранее улетят в него.
			run(399);
			LivingEntity end = dummy(level, EntityType.ZOMBIE, at.offset(1, 0, 1));
			run(3);
			r.check(zones >= 2 && hurt(end), "Вальс ×2: два кольца; в конце копья разлетаются осколками (" + zones + ", " + hp(end) + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 4 ---

	private static void glacierRidge(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 65);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			int broken = Terrain.brokenTotal();
			int temps = Terrain.tempCount();
			griefing(true, () -> {
				Magic.castNow(p, spell("glacier_ridge"), 1.0F);
				run(20);
			});
			r.check(lost(z) >= 11.0F && amp(z, MobEffects.SLOWNESS) >= 3 && z.getTicksFrozen() >= 200,
					"Ледниковая Гряда: 14 урона холодом, вмерзание и Замедление IV (" + hp(z) + ")");
			int placed = Terrain.tempCount() - temps;
			r.check(placed >= 60 && ice(level, at.offset(0, 1, 6), 1) >= 3, "Ледниковая Гряда: вдоль линии встал ледник (" + placed + " блоков льда)");
			r.check(Terrain.brokenTotal() == broken, "Ледниковая Гряда: ничего не ломает даже с разрушением");
			r.check(!level.getBlockState(z.blockPosition()).is(Blocks.PACKED_ICE) && !level.getBlockState(z.blockPosition()).is(Blocks.BLUE_ICE),
					"Ледниковая Гряда: лёд не встаёт внутрь врага");
			clean();
			r.check(ice(level, at.offset(0, 1, 6), 1) == 0, "Ледниковая Гряда: лёд временный и тает");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 5 ---

	private static void crystalPrison(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 66);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity a = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 9));
			LivingEntity b = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 9));
			LivingEntity outside = dummy(level, EntityType.ZOMBIE, at.offset(-9, 0, 9));
			Magic.castNow(p, spell("cursed_crystal_prison"), 1.0F);
			run(8);
			r.check(!hurt(a) && !hurt(b), "Склеп: пока иней сходится к центру, урона нет");
			run(3);
			r.check(lost(a) >= 16.0F && lost(b) >= 16.0F && !hurt(outside),
					"Склеп: 20 урона холодом всем в радиусе 6, вне круга — никого (" + hp(a) + ", " + hp(b) + ", " + hp(outside) + ")");
			int iceA = ice(level, a.blockPosition().above(), 2);
			r.check(a.hasEffect(MagicEffects.PARALYSIS) && b.hasEffect(MagicEffects.PARALYSIS) && iceA >= 12,
					"Склеп: враги обездвижены в хрустальных темницах (" + iceA + " блоков льда у первого)");
			float hpA = a.getHealth();
			run(21);
			r.check(hpA - a.getHealth() >= 3.0F, "Склеп: в темнице 4 урона в секунду (" + hpA + " → " + a.getHealth() + ")");

			// Сильный удар по узнику — темница лопается (18 урона), соседняя трескается следом.
			hpA = a.getHealth();
			a.setHealth(a.getHealth() - 12.0F);
			run(1);
			r.check(hpA - 12.0F - a.getHealth() >= 14.0F && !a.hasEffect(MagicEffects.PARALYSIS) && ice(level, a.blockPosition().above(), 1) == 0,
					"Склеп: удар 10+ разбивает темницу — 18 урона холодом, лёд исчезает (" + hp(a) + ")");
			run(5);
			r.check(!b.hasEffect(MagicEffects.PARALYSIS) && ice(level, b.blockPosition().above(), 1) == 0,
					"Склеп: соседняя темница лопается следом");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void wyrmRoar(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 67);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			LivingEntity blaze = dummy(level, EntityType.BLAZE, at.offset(0, 0, 13));
			LivingEntity behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -5));
			z.igniteForSeconds(10);
			BlockPos lava = at.offset(1, 0, 6);
			level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
			BlockPos glass = at.offset(0, 1, 4);
			level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 2);
			level.setBlock(glass.above(), Blocks.GLASS.defaultBlockState(), 2);
			griefing(true, () -> {
				Magic.castNow(p, spell("abyssal_wyrm_roar"), 1.0F);
				run(8);
			});
			r.check(lost(z) >= 18.0F && !z.isOnFire() && amp(z, MobEffects.SLOWNESS) >= 1 && z.getDeltaMovement().z > 0.5 && !hurt(behind),
					"Рёв Пучины: 22 урона водой, гасит огонь, Замедление II и уносит потоком вперёд (" + hp(z) + ", толчок "
							+ String.format("%.2f", z.getDeltaMovement().z) + ")");
			r.check(lost(blaze) >= lost(z) * 1.7F, "Рёв Пучины: ифриту — двойной урон (" + hp(blaze) + " против " + hp(z) + ")");
			r.check(level.getBlockState(lava).is(Blocks.OBSIDIAN), "Рёв Пучины: лава на пути — в обсидиан");
			int left = (level.getBlockState(glass).is(Blocks.GLASS) ? 1 : 0) + (level.getBlockState(glass.above()).is(Blocks.GLASS) ? 1 : 0);
			r.check(left < 2, "Рёв Пучины: с разрушением поток сносит стекло на пути (осталось " + left + " из 2)");
			tidy(level, at, p);

			BlockPos at2 = far(o, 68);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 0);
			BlockPos glass2 = at2.offset(0, 1, 4);
			level.setBlock(glass2, Blocks.GLASS.defaultBlockState(), 2);
			level.setBlock(glass2.above(), Blocks.GLASS.defaultBlockState(), 2);
			LivingEntity z2 = dummy(level, EntityType.ZOMBIE, at2.offset(0, 0, 10));
			griefing(false, () -> {
				Magic.castNow(p2, spell("abyssal_wyrm_roar"), 1.0F);
				run(10);
			});
			r.check(level.getBlockState(glass2).is(Blocks.GLASS) && level.getBlockState(glass2.above()).is(Blocks.GLASS) && hurt(z2),
					"Рёв Пучины: без разрушения стекло цело, урон есть");
			tidy(level, at2, p2);
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 6 ---

	private static void hailHammer(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 69);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			fill(level, at.offset(0, -4, 9), 3);
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 9));
			LivingEntity rim = dummy(level, EntityType.ZOMBIE, at.offset(6, 0, 9));
			int broken = Terrain.brokenTotal();
			griefing(true, () -> {
				Magic.castNow(p, spell("hail_hammer"), 1.0F);
				run(45);
			});
			float snow = lost(z) + lost(rim);
			r.check(snow >= 6.0F && z.getTicksFrozen() > 0 && z.hasEffect(MobEffects.SLOWNESS),
					"Градовый Молот: снег из круга замедляет, на врагах расцветают ледяные цветы (" + snow + " урона)");
			int temps = Terrain.tempCount();
			griefing(true, () -> run(40));
			r.check(lost(z) >= 40.0F && hurt(rim), "Градовый Молот: глыба — 40 урона в центре и меньше на краю (" + hp(z) + ", " + hp(rim) + ")");
			r.check(z.hasEffect(MagicEffects.PARALYSIS) && !rim.hasEffect(MagicEffects.PARALYSIS),
					"Градовый Молот: в 4 блоках от удара враги вмерзают в темницы");
			r.check(Terrain.brokenTotal() > broken + 20, "Градовый Молот: с разрушением — воронка (" + (Terrain.brokenTotal() - broken) + " блоков)");
			r.check(Terrain.tempCount() > temps, "Градовый Молот: из воронки растут ледяные шипы");
			tidy(level, at, p);

			BlockPos at2 = far(o, 70);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 0);
			LivingEntity z2 = dummy(level, EntityType.ZOMBIE, at2.offset(0, 0, 9));
			int broken2 = Terrain.brokenTotal();
			griefing(false, () -> {
				Magic.castNow(p2, spell("hail_hammer"), 1.0F);
				run(85);
			});
			r.check(Terrain.brokenTotal() == broken2 && lost(z2) >= 40.0F, "Градовый Молот: без разрушения блоки целы, урон есть (" + hp(z2) + ")");
			tidy(level, at2, p2);
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 7 ---

	private static void leviathan(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 71);
		arena(level, at, 24, 8);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			LivingEntity z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 20));
			LivingEntity behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -6));
			BlockPos grass = at.offset(2, 0, 18);
			level.setBlock(grass.below(), Blocks.DIRT.defaultBlockState(), 2);
			level.setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), 2);
			int temps = Terrain.tempCount();
			griefing(true, () -> {
				Magic.castNow(p, spell("tidal_leviathan"), 1.0F);
				run(8);
			});
			r.check(!hurt(z), "Потоп Левиафана: пока волна поднимается, урона нет");
			griefing(true, () -> run(12));
			r.check(lost(z) >= 48.0F && amp(z, MobEffects.SLOWNESS) >= 2 && z.getDeltaMovement().z > 0.5 && !hurt(behind),
					"Потоп Левиафана: волна — 60 урона водой, Замедление III и унос; позади никого (" + hp(z) + ", " + hp(behind) + ")");
			r.check(Terrain.tempCount() - temps >= 20, "Потоп Левиафана: пройденная полоса затоплена (" + (Terrain.tempCount() - temps) + " блоков воды)");
			r.check(!level.getBlockState(grass).is(Blocks.SHORT_GRASS), "Потоп Левиафана: с разрушением волна сносит траву");
			float wave = lost(z);
			griefing(true, () -> run(42));
			r.check(z.hasEffect(MagicEffects.PARALYSIS) && ice(level, z.blockPosition().above(), 1) >= 8,
					"Потоп Левиафана: затопленная полоса мгновенно замерзает — враг во льду");
			griefing(true, () -> run(101));
			r.check(lost(z) - wave >= 20.0F && !z.hasEffect(MagicEffects.PARALYSIS),
					"Потоп Левиафана: оттепель — ещё 25 урона холодом, враг свободен (" + hp(z) + ")");
			tidy(level, at, p, 120);

			BlockPos at2 = far(o, 72);
			arena(level, at2, 24, 8);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 0);
			BlockPos grass2 = at2.offset(2, 0, 18);
			level.setBlock(grass2.below(), Blocks.DIRT.defaultBlockState(), 2);
			level.setBlock(grass2, Blocks.SHORT_GRASS.defaultBlockState(), 2);
			int broken = Terrain.brokenTotal();
			griefing(false, () -> {
				Magic.castNow(p2, spell("tidal_leviathan"), 1.0F);
				run(60);
			});
			r.check(level.getBlockState(grass2).is(Blocks.SHORT_GRASS) && Terrain.brokenTotal() == broken, "Потоп Левиафана: без разрушения трава цела");
			tidy(level, at2, p2, 120);
		} finally {
			tidy(level, at, p, 120);
		}
	}
}
