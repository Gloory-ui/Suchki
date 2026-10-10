package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.clean;
import static com.suchki.dev.SpellTestKit.dummy;
import static com.suchki.dev.SpellTestKit.face;
import static com.suchki.dev.SpellTestKit.far;
import static com.suchki.dev.SpellTestKit.griefing;
import static com.suchki.dev.SpellTestKit.hitOn;
import static com.suchki.dev.SpellTestKit.hp;
import static com.suchki.dev.SpellTestKit.hurt;
import static com.suchki.dev.SpellTestKit.mage;
import static com.suchki.dev.SpellTestKit.run;
import static com.suchki.dev.SpellTestKit.solid;
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.spells.FireSpells;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Проверки заклинаний стихии огня: урон, поджог, метки, столп, метеоры, колесо, солнце, эксплозия и воронки. */
final class FireSpellTests {
	private FireSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Огонь — стрела и поток пламени", FireSpellTests::arrowAndStream);
		tests.put("Магия: Огонь — Огненная Длань: Вспышка", FireSpellTests::flashFireHand);
		tests.put("Магия: Огонь — Огненный Цветок", FireSpellTests::fireball);
		tests.put("Магия: Огонь — Огненный Столп", FireSpellTests::pillar);
		tests.put("Магия: Огонь — Звездопад Возмездия", FireSpellTests::meteors);
		tests.put("Магия: Огонь — Солнечное Колесо", FireSpellTests::sunWheel);
		tests.put("Магия: Огонь — Гелиогенезис", FireSpellTests::heliogenesis);
		tests.put("Магия: Огонь — Багровая Эксплозия", FireSpellTests::crimsonExplosion);
	}

	private static float lost(LivingEntity e, float before) {
		return before - e.getHealth();
	}

	/** Убрать мишени, снаряды и следы заклинаний вокруг площадки. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		clean();
		FireSpells.recover(p);
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	private static int projectiles(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(SpellProjectile.class, new AABB(at).inflate(16)).size();
	}

	// --- Ранг 1 ---

	private static void arrowAndStream(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 40);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Magic.hitNow(p, spell("firebolt"), hitOn(z), 1.0F);
			r.check(lost(z, z.getMaxHealth()) >= 5.0F && z.isOnFire(), "Огненная стрела: ~6 урона огнём и поджог (" + hp(z) + ")");
			z.discard();

			int before = projectiles(level, at);
			Magic.castNow(p, spell("firebolt"), 1.0F);
			int one = projectiles(level, at) - before;
			Magic.castNow(p, spell("firebolt"), 1.0F, 3);
			int three = projectiles(level, at) - before - one;
			r.check(one == 1 && three == 3, "Огненная стрела: одна стрела, мультикаст ×3 — три (" + one + ", " + three + ")");
			tidy(level, at, p);

			// Поток пламени: враг впереди горит, враг за спиной цел; снег на конце струи тает.
			Zombie front = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 4));
			Zombie back = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -4));
			for (int dx = -1; dx <= 1; dx++) level.setBlock(at.offset(dx, 0, 8), Blocks.SNOW.defaultBlockState(), 2);
			Magic.channelNow(p, spell("flames"), 40, 1.0F);
			r.check(lost(front, front.getMaxHealth()) >= 10.0F && front.isOnFire() && !hurt(back),
					"Поток пламени: 2 с — ~12 урона врагу в конусе и поджог, позади никого (" + hp(front) + ", " + hp(back) + ")");
			int snow = 0;
			for (int dx = -1; dx <= 1; dx++) if (level.getBlockState(at.offset(dx, 0, 8)).is(Blocks.SNOW)) snow++;
			r.check(snow < 3, "Поток пламени: снег на конце струи тает (осталось " + snow + " из 3)");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 2 ---

	private static void flashFireHand(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 41);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie a = dummy(level, EntityType.ZOMBIE, at.offset(-2, 0, 7));
			Zombie b = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 12));
			Zombie behind = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -6));
			Magic.castNow(p, spell("flash_fire_hand"), 1.0F);
			run(8);
			r.check(!hurt(a) && !hurt(b), "Огненная Длань: до взрыва меток урона нет");
			run(12);
			r.check(lost(a, a.getMaxHealth()) >= 8.0F && lost(b, b.getMaxHealth()) >= 8.0F && a.isOnFire() && b.isOnFire() && !hurt(behind),
					"Огненная Длань: через 0,6 с метки взрываются — по 9 урона двум целям в конусе, позади никого ("
							+ hp(a) + ", " + hp(b) + ", " + hp(behind) + ")");
			tidy(level, at, p);

			// Мультикаст ×2 — вторая волна по тем же меткам (×0,6).
			Zombie c = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 9));
			Magic.castNow(p, spell("flash_fire_hand"), 1.0F, 2);
			run(30);
			r.check(lost(c, c.getMaxHealth()) >= 13.0F, "Огненная Длань ×2: вторая волна взрывов (" + hp(c) + ", ждём ≈14)");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 3 ---

	private static void fireball(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 42);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			int before = projectiles(level, at);
			Magic.castNow(p, spell("fireball"), 1.0F, 3);
			r.check(projectiles(level, at) - before == 3, "Огненный Цветок ×3: три шара");
			tidy(level, at, p);

			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			BlockPos floor = at.offset(0, -1, 7);
			int solidOn = solid(level, floor, 2);
			griefing(true, () -> {
				Magic.hitNow(p, spell("fireball"), new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false), 1.0F);
				run(5);
			});
			r.check(lost(z, z.getMaxHealth()) >= 10.0F && z.isOnFire(), "Огненный Цветок: взрыв рядом — 10–16 урона и поджог (" + hp(z) + ")");
			int afterOn = solid(level, floor, 2);
			r.check(afterOn < solidOn, "Огненный Цветок: воронка при разрешённом разрушении (" + solidOn + " → " + afterOn + ")");

			BlockPos floor2 = at.offset(0, -1, -7);
			int solidOff = solid(level, floor2, 2);
			griefing(false, () -> {
				Magic.hitNow(p, spell("fireball"), new BlockHitResult(Vec3.atCenterOf(floor2).add(0, 0.5, 0), Direction.UP, floor2, false), 1.0F);
				run(5);
			});
			r.check(solid(level, floor2, 2) == solidOff, "Огненный Цветок: без разрушения блоки целы");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 4 ---

	private static void pillar(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 43);
		FakePlayer p = mage(level, at);
		face(p, at, 10);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			for (int dx = -2; dx <= 2; dx++) level.setBlock(at.offset(dx, -1, 10), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			float[] launch = {0};
			float[] afterEruption = {0};
			griefing(true, () -> {
				Magic.castNow(p, spell("pillar_of_fire"), 1.0F);
				run(6);
				r.check(!hurt(z), "Огненный Столп: пока горит предупреждающий круг, урона нет");
				run(6);
				launch[0] = (float) z.getDeltaMovement().y;
				afterEruption[0] = z.getHealth();
				run(40);
			});
			r.check(z.getMaxHealth() - afterEruption[0] >= 18.0F && launch[0] > 0.5F && z.isOnFire(),
					"Огненный Столп: 22 урона огнём, подброс и поджог (" + afterEruption[0] + ", скорость вверх " + launch[0] + ")");
			r.check(afterEruption[0] - z.getHealth() >= 5.0F, "Огненный Столп: внутри столпа жжёт и дальше (" + hp(z) + ")");
			int scorched = 0;
			for (int dx = -2; dx <= 2; dx++) if (level.getBlockState(at.offset(dx, -1, 10)).is(Blocks.COARSE_DIRT)) scorched++;
			r.check(scorched >= 3, "Огненный Столп: трава выжжена (" + scorched + " из 5)");
			tidy(level, at, p);

			for (int dx = -2; dx <= 2; dx++) level.setBlock(at.offset(dx, -1, 10), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			Zombie z2 = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			griefing(false, () -> {
				Magic.castNow(p, spell("pillar_of_fire"), 1.0F);
				run(20);
			});
			int grass = 0;
			for (int dx = -2; dx <= 2; dx++) if (level.getBlockState(at.offset(dx, -1, 10)).is(Blocks.GRASS_BLOCK)) grass++;
			r.check(grass == 5 && hurt(z2), "Огненный Столп: без разрушения трава цела, урон есть (" + grass + " из 5)");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 5 ---

	private static void meteors(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 44);
		FakePlayer p = mage(level, at);
		face(p, at, 10);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			BlockPos ground = at.offset(0, -1, 8);
			int before = solid(level, ground, 4);
			griefing(true, () -> {
				Magic.castNow(p, spell("meteor_shower"), 1.0F);
				run(60);
			});
			float mid = z.getHealth();
			griefing(true, () -> run(110));
			r.check(z.getMaxHealth() - z.getHealth() >= 30.0F && z.isOnFire(),
					"Звездопад: метеоры и великий метеор (≈36) бьют по цели (" + hp(z) + ", до великого " + mid + ")");
			int after = solid(level, ground, 4);
			r.check(after < before - 5, "Звездопад: воронки при разрешённом разрушении (" + before + " → " + after + ")");
			tidy(level, at, p);

			BlockPos at2 = far(o, 45);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 10);
			Zombie z2 = dummy(level, EntityType.ZOMBIE, at2.offset(0, 0, 8));
			BlockPos ground2 = at2.offset(0, -1, 8);
			int before2 = solid(level, ground2, 6);
			griefing(false, () -> {
				Magic.castNow(p2, spell("meteor_shower"), 1.0F);
				run(170);
			});
			r.check(solid(level, ground2, 6) == before2 && hurt(z2), "Звездопад: без разрушения блоки целы, урон есть");
			tidy(level, at2, p2);
		} finally {
			tidy(level, at, p);
		}
	}

	private static void sunWheel(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 46);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			BlockPos wall = at.offset(0, 2, 12);
			for (BlockPos b : BlockPos.betweenClosed(at.offset(-3, 0, 12), at.offset(3, 5, 13))) level.setBlock(b, Blocks.STONE.defaultBlockState(), 2);
			int before = solid(level, wall, 3);
			griefing(true, () -> {
				Magic.castNow(p, spell("sun_wheel"), 1.0F);
				run(60);
			});
			r.check(lost(z, z.getMaxHealth()) >= 25.0F && z.isOnFire(), "Солнечное Колесо: 28 урона огнём и поджог (" + hp(z) + ")");
			int after = solid(level, wall, 3);
			r.check(after < before - 4, "Солнечное Колесо: прожигает стену при разрешённом разрушении (" + before + " → " + after + ")");
			tidy(level, at, p);

			for (BlockPos b : BlockPos.betweenClosed(at.offset(-3, 0, 12), at.offset(3, 5, 13))) level.setBlock(b, Blocks.STONE.defaultBlockState(), 2);
			int before2 = solid(level, wall, 3);
			griefing(false, () -> {
				Magic.castNow(p, spell("sun_wheel"), 1.0F);
				run(60);
			});
			r.check(solid(level, wall, 3) == before2, "Солнечное Колесо: без разрушения стена цела (колесо взрывается о неё)");
			for (BlockPos b : BlockPos.betweenClosed(at.offset(-3, 0, 12), at.offset(3, 5, 13))) level.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
			tidy(level, at, p);

			// Мультикаст ×3: веер из трёх колёс, каждое находит свою цель.
			Zombie mid = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
			Zombie left = dummy(level, EntityType.ZOMBIE, at.offset(-3, 0, 9));
			Zombie right = dummy(level, EntityType.ZOMBIE, at.offset(3, 0, 9));
			griefing(false, () -> {
				Magic.castNow(p, spell("sun_wheel"), 1.0F, 3);
				run(20);
			});
			r.check(hurt(mid) && hurt(left) && hurt(right), "Солнечное Колесо ×3: три колеса веером — три цели ("
					+ hp(left) + ", " + hp(mid) + ", " + hp(right) + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 6 ---

	private static void heliogenesis(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 47);
		FakePlayer p = mage(level, at);
		face(p, at, 10);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			Zombie edge = dummy(level, EntityType.ZOMBIE, at.offset(12, 0, 8));
			BlockPos ground = at.offset(0, -1, 8);
			int before = solid(level, ground, 6);
			griefing(true, () -> {
				Magic.castNow(p, spell("heliogenesis"), 1.0F);
				run(40);
			});
			float hang = lost(z, z.getMaxHealth());
			r.check(z.isOnFire() && edge.isOnFire() && hang < 12.0F, "Гелиогенезис: висящее солнце поджигает всех в радиусе 14 (" + hp(z) + ")");
			griefing(true, () -> run(60));
			r.check(lost(z, z.getMaxHealth()) >= 45.0F, "Гелиогенезис: падение солнца — до 55 урона в центре (" + hp(z) + ")");
			r.check(hurt(edge) && lost(edge, edge.getMaxHealth()) < 30.0F, "Гелиогенезис: огненная грива достаёт до 16 блоков (" + hp(edge) + ")");
			int after = solid(level, ground, 6);
			r.check(after < before - 40, "Гелиогенезис: воронка радиусом 8 (" + before + " → " + after + ")");
			tidy(level, at, p);

			BlockPos at2 = far(o, 48);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 10);
			Zombie z2 = dummy(level, EntityType.ZOMBIE, at2.offset(0, 0, 8));
			BlockPos ground2 = at2.offset(0, -1, 8);
			int before2 = solid(level, ground2, 6);
			griefing(false, () -> {
				Magic.castNow(p2, spell("heliogenesis"), 1.0F);
				run(100);
			});
			r.check(solid(level, ground2, 6) == before2 && hurt(z2), "Гелиогенезис: без разрушения блоки целы, урон есть");
			tidy(level, at2, p2);
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 7 ---

	private static void crimsonExplosion(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 49);
		FakePlayer p = mage(level, at);
		face(p, at, 10);
		try {
			Zombie z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 8));
			BlockPos ground = at.offset(0, -1, 8);
			int before = solid(level, ground, 6);
			boolean[] drained = {false};
			float[] waiting = {0};
			griefing(true, () -> {
				Magic.castNow(p, spell("crimson_explosion"), 1.0F);
				drained[0] = Magic.mana(p) < 0.01F;
				run(45);
				waiting[0] = z.getHealth();
				run(10);
			});
			r.check(drained[0], "Багровая Эксплозия: вся мана сожжена");
			r.check(waiting[0] >= z.getMaxHealth(), "Багровая Эксплозия: до взрыва (круги ствола, вдох) урона нет");
			r.check(lost(z, z.getMaxHealth()) >= 80.0F && (z.isOnFire() || z.isDeadOrDying()), "Багровая Эксплозия: взрыв до 160 урона (" + hp(z) + ")");
			r.check(FireSpells.collapsed(p) && p.hasEffect(MobEffects.SLOWNESS) && p.hasEffect(MobEffects.WEAKNESS),
					"Багровая Эксплозия: маг без сил — замедление, слабость, без прыжка");
			int after = solid(level, ground, 6);
			r.check(after < before - 100, "Багровая Эксплозия: огромная воронка (" + before + " → " + after + ")");
			run(210);
			r.check(!FireSpells.collapsed(p), "Багровая Эксплозия: через 10 с маг встаёт");
			tidy(level, at, p);

			BlockPos at2 = far(o, 50);
			FakePlayer p2 = mage(level, at2);
			face(p2, at2, 10);
			Zombie z2 = dummy(level, EntityType.ZOMBIE, at2.offset(0, 0, 8));
			BlockPos ground2 = at2.offset(0, -1, 8);
			int before2 = solid(level, ground2, 6);
			griefing(false, () -> {
				Magic.castNow(p2, spell("crimson_explosion"), 1.0F);
				run(60);
			});
			r.check(solid(level, ground2, 6) == before2 && hurt(z2), "Багровая Эксплозия: без разрушения блоки целы, урон есть");
			tidy(level, at2, p2);
		} finally {
			tidy(level, at, p);
		}
	}
}
