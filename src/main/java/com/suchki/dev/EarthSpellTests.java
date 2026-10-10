package com.suchki.dev;

import static com.suchki.dev.SpellTestKit.arena;
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
import static com.suchki.dev.SpellTestKit.spell;

import com.suchki.magic.Magic;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.Minions;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Terrain;
import com.suchki.magic.spells.EarthSpells;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Проверки заклинаний стихии земли: каменная кожа, ядро, стена/крепость/мост, трясина, землетрясение, колосс,
 * разлом, восстание титана и великая гора. Временные блоки земли — свои ({@link EarthSpells#tempCount}), поэтому после
 * каждой проверки они возвращаются явно.
 */
final class EarthSpellTests {
	private EarthSpellTests() {}

	static void addTo(Map<String, DevTests.Test> tests) {
		tests.put("Магия: Земля — Каменная Кожа", EarthSpellTests::stoneflesh);
		tests.put("Магия: Земля — Нарезное Каменное Ядро", EarthSpellTests::stoneCannon);
		tests.put("Магия: Земля — Земляной Бастион", EarthSpellTests::earthWall);
		tests.put("Магия: Земля — Бездонная Топь", EarthSpellTests::quagmire);
		tests.put("Магия: Земля — Рокот Священной Горы", EarthSpellTests::earthquake);
		tests.put("Магия: Земля — Каменный Колосс", EarthSpellTests::colossus);
		tests.put("Магия: Земля — Пасть Разлома", EarthSpellTests::fissure);
		tests.put("Магия: Земля — Восстание Титана", EarthSpellTests::titanUpheaval);
		tests.put("Магия: Земля — Пробуждение Великой Горы", EarthSpellTests::mountain);
	}

	// --- Помощники ---

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	/** Наклон взгляда, чтобы прицел упал на землю в {@code d} блоках впереди. */
	private static float pitchTo(double d) {
		return (float) Math.toDegrees(Math.atan2(1.62, d));
	}

	/** Убрать мишени, снаряды, колоссов и все временные блоки вокруг площадки. */
	private static void tidy(ServerLevel level, BlockPos at, FakePlayer p) {
		clean();
		EarthSpells.restoreAll();
		p.setShiftKeyDown(false);
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(64), e -> !(e instanceof Player)).forEach(Entity::discard);
	}

	/** Сколько блоков в коробке [a, b] подходит под условие. */
	private static int count(ServerLevel level, BlockPos a, BlockPos b, Predicate<BlockState> what) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(a, b)) if (what.test(level.getBlockState(p))) n++;
		return n;
	}

	/** Залить коробку [a, b] камнем (земля под площадкой, чтобы было что раскалывать). */
	private static void stone(ServerLevel level, BlockPos a, BlockPos b) {
		for (BlockPos p : BlockPos.betweenClosed(a, b)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
	}

	private static int amp(LivingEntity e) {
		MobEffectInstance i = e.getEffect(MobEffects.SLOWNESS);
		return i == null ? -1 : i.getAmplifier();
	}

	private static List<IronGolem> colossi(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(IronGolem.class, new AABB(at).inflate(32), g -> g.isAlive() && g.getTags().contains("suchki.colossus"));
	}

	private static int projectiles(ServerLevel level, BlockPos at, int r) {
		return level.getEntitiesOfClass(SpellProjectile.class, new AABB(at).inflate(r)).size();
	}

	// --- Ранг 1 ---

	private static void stoneflesh(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 80);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Magic.castNow(p, spell("stoneflesh"), 1.0F);
			MobEffectInstance skin = p.getEffect(MagicEffects.STONESKIN);
			r.check(skin != null && skin.getDuration() >= 1700, "Каменная Кожа: эффект на 90 с ("
					+ (skin == null ? "нет" : skin.getDuration() + " тиков") + ")");
			double kb = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			r.check(kb >= 0.29, "Каменная Кожа: +0,3 к устойчивости к отбрасыванию (" + kb + ")");

			// Мультикаст ×2: вторая копия покрывает союзника (жителя) рядом.
			Mob v = dummy(level, EntityType.VILLAGER, at.offset(2, 0, 2));
			Magic.castNow(p, spell("stoneflesh"), 1.0F, 2);
			r.check(v.hasEffect(MagicEffects.STONESKIN), "Каменная Кожа ×2: копия покрывает союзника в 6 блоках");

			// Первый тяжёлый удар ослаблен на 2, следующий в течение 5 с — нет.
			v.hurtServer(level, level.damageSources().magic(), 10.0F);
			float first = lost(v);
			v.invulnerableTime = 0;
			v.hurtServer(level, level.damageSources().magic(), 10.0F);
			float second = lost(v) - first;
			r.check(first > 7.5F && first < 8.5F && second > 9.5F, "Каменная Кожа: первый удар 10 → 8, второй — полный (" + first + ", " + second + ")");
		} finally {
			tidy(level, at, p);
			p.removeAllEffects();
		}
	}

	// --- Ранг 2 ---

	private static void stoneCannon(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 81);
		FakePlayer p = mage(level, at);
		face(p, at, 0);
		try {
			Mob z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 6));
			Magic.hitNow(p, spell("stone_cannon"), hitOn(z), 1.0F);
			float low = lost(z);
			r.check(low >= 7.5F, "Ядро: без зарядки ~8 урона землёй (" + hp(z) + ")");
			z.invulnerableTime = 0;
			Magic.hitNow(p, spell("stone_cannon"), hitOn(z), 1.75F);
			float high = lost(z) - low;
			r.check(high >= 14.0F, "Ядро: полная зарядка ~16 урона (" + high + ")");
			z.discard();

			int before = projectiles(level, at, 16);
			Magic.castNow(p, spell("stone_cannon"), 1.0F);
			int one = projectiles(level, at, 16) - before;
			Magic.castNow(p, spell("stone_cannon"), 1.0F, 3);
			int three = projectiles(level, at, 16) - before - one;
			r.check(one == 1 && three == 3, "Ядро: одно ядро, мультикаст ×3 — три (" + one + ", " + three + ")");
			tidy(level, at, p);

			// Стекло на пути: при разрушении — вдребезги и ядро летит дальше, без разрушения — цело.
			BlockPos glass = at.offset(0, 1, 6);
			for (boolean on : new boolean[] {true, false}) {
				level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 2);
				int n0 = projectiles(level, at, 16);
				griefing(on, () -> {
					Magic.hitNow(p, spell("stone_cannon"), new BlockHitResult(Vec3.atCenterOf(glass).add(0, 0, -0.5), Direction.NORTH, glass, false), 1.0F);
					run(1);
				});
				boolean broken = level.getBlockState(glass).isAir();
				int flew = projectiles(level, at, 16) - n0;
				if (on) r.check(broken && flew == 1, "Ядро: разбивает стекло и летит дальше (" + broken + ", снарядов " + flew + ")");
				else r.check(!broken, "Ядро: без разрушения стекло цело");
				tidy(level, at, p);
			}
		} finally {
			tidy(level, at, p);
		}
	}

	private static void earthWall(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 82);
		FakePlayer p = mage(level, at);
		face(p, at, pitchTo(6));
		Predicate<BlockState> masonry = s -> s.is(Blocks.STONE_BRICKS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
				|| s.is(Blocks.CRACKED_STONE_BRICKS);
		try {
			// Стена 7×4 поперёк взгляда; существо в кладке выталкивается.
			Mob z = dummy(level, EntityType.ZOMBIE, at.offset(2, 0, 6));
			Magic.castNow(p, spell("earth_wall"), 1.0F);
			run(16);
			int wall = count(level, at.offset(-6, 0, 3), at.offset(6, 4, 9), masonry);
			r.check(wall >= 26 && wall <= 30, "Бастион: стена 7×4 (" + wall + " блоков)");
			r.check(level.noCollision(z, z.getBoundingBox().deflate(0.05)), "Бастион: существо вытолкнуто из кладки");
			run(300);
			int left = count(level, at.offset(-6, 0, 3), at.offset(6, 4, 9), masonry);
			r.check(left == 0 && EarthSpells.tempCount() == 0, "Бастион: через 15 с стена осыпается (" + left + ", врем. " + EarthSpells.tempCount() + ")");
			tidy(level, at, p);

			// Мультикаст ×2 — стена вдвое шире.
			Magic.castNow(p, spell("earth_wall"), 1.0F, 2);
			run(16);
			int wide = count(level, at.offset(-10, 0, 2), at.offset(10, 4, 10), masonry);
			r.check(wide >= 50, "Бастион ×2: стена 14×4 (" + wide + " блоков)");
			tidy(level, at, p);

			// С Shift — кольцо-крепость 5×5 высотой 3.
			p.setShiftKeyDown(true);
			Magic.castNow(p, spell("earth_wall"), 1.0F);
			run(16);
			int fort = count(level, at.offset(-6, 0, 2), at.offset(6, 4, 10), masonry);
			r.check(fort >= 40 && fort <= 48, "Бастион с Shift: крепость 16 столбов × 3 (" + fort + " блоков)");
			tidy(level, at, p);

			// Над пропастью (прицел в небо) — мост шириной 3 на 60 с.
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = 2; dz <= 14; dz++) {
					for (int dy = -4; dy <= -1; dy++) level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
			face(p, at, -20);
			Magic.castNow(p, spell("earth_wall"), 1.0F);
			run(30);
			int bridge = count(level, at.offset(-1, -1, 2), at.offset(1, -1, 14), masonry);
			r.check(bridge >= 33, "Бастион: мост через пропасть (" + bridge + " блоков из 39)");
			run(1240);
			int gone = count(level, at.offset(-3, -1, 2), at.offset(3, -1, 14), masonry);
			r.check(gone == 0, "Бастион: мост осыпается через 60 с (" + gone + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 3 ---

	private static void quagmire(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 83);
		FakePlayer p = mage(level, at);
		face(p, at, pitchTo(7));
		try {
			griefing(false, () -> {
				Mob z = dummy(level, EntityType.ZOMBIE, at.offset(1, 0, 7));
				Mob bug = dummy(level, EntityType.SILVERFISH, at.offset(-2, 0, 8));
				Magic.castNow(p, spell("quagmire"), 1.0F);
				run(12);
				int mud = count(level, at.offset(-6, -2, 1), at.offset(6, -1, 13), s -> s.is(Blocks.MUD));
				r.check(mud >= 120, "Топь: земля радиусом 5 стала грязью даже без разрушения (" + mud + " блоков)");
				r.check(amp(z) >= 6, "Топь: враг намертво увяз (Замедление " + (amp(z) + 1) + ")");
				run(60);
				r.check(amp(z) == 3, "Топь: через 3 с — Замедление IV (" + (amp(z) + 1) + ")");
				r.check(hurt(bug), "Топь: мелочь тонет и получает урон (" + hp(bug) + ")");
				run(200);
				int left = count(level, at.offset(-6, -2, 1), at.offset(6, -1, 13), s -> s.is(Blocks.MUD));
				r.check(left == 0 && level.getBlockState(at.offset(0, -1, 7)).is(Blocks.STONE) && EarthSpells.tempCount() == 0,
						"Топь: через 12 с высыхает, земля вернулась (" + left + ")");
			});
		} finally {
			tidy(level, at, p);
		}
	}

	// --- Ранг 4 ---

	private static void earthquake(ServerLevel level, BlockPos o, DevTests.Report r) {
		for (boolean on : new boolean[] {true, false}) {
			BlockPos at = far(o, on ? 84 : 85);
			FakePlayer p = mage(level, at);
			arena(level, at, 22, 6);
			stone(level, at.offset(-18, -4, -18), at.offset(18, -1, 18));
			face(p, at, 0);
			try {
				griefing(on, () -> {
					Mob near = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 4));
					Mob mid = dummy(level, EntityType.ZOMBIE, at.offset(10, 0, 0));
					Mob edge = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, -15));
					Mob out = dummy(level, EntityType.ZOMBIE, at.offset(-20, 0, 0));
					int broken0 = Terrain.brokenTotal();
					Magic.castNow(p, spell("earthquake"), 1.0F);
					run(10);
					int temp = EarthSpells.tempCount();
					run(12);
					r.check(lost(near) >= 9.5F && lost(mid) >= 9.5F && lost(edge) >= 9.5F && !hurt(out),
							"Рокот: волны до 16 блоков бьют по 10, дальше никого (" + hp(near) + ", " + hp(mid) + ", " + hp(edge) + ", " + hp(out) + ")");
					r.check(amp(edge) == 1, "Рокот: Замедление II (" + (amp(edge) + 1) + ")");
					int broken = Terrain.brokenTotal() - broken0;
					if (on) {
						r.check(broken >= 20, "Рокот: с разрушением земля раскалывается трещинами (сломано " + broken + ")");
					} else {
						r.check(broken == 0 && temp > 0, "Рокот: без разрушения — только временные каменные шипы (сломано " + broken + ", шипов " + temp + ")");
						run(140);
						r.check(EarthSpells.tempCount() == 0, "Рокот: шипы уходят в землю (" + EarthSpells.tempCount() + ")");
					}
				});
			} finally {
				tidy(level, at, p);
			}
		}
	}

	// --- Ранг 5 ---

	private static void colossus(ServerLevel level, BlockPos o, DevTests.Report r) {
		BlockPos at = far(o, 86);
		FakePlayer p = mage(level, at);
		face(p, at, pitchTo(6));
		try {
			Magic.castNow(p, spell("iron_guardian"), 1.0F);
			run(10);
			int pillar = EarthSpells.tempCount();
			run(10);
			List<IronGolem> one = colossi(level, at);
			IronGolem g = one.isEmpty() ? null : one.get(0);
			r.check(pillar > 0 && EarthSpells.tempCount() == 0, "Колосс: каменный столп встаёт и рассыпается (" + pillar + " блоков)");
			r.check(one.size() == 1 && Minions.isOwnedBy(g, p), "Колосс: призван и подчиняется магу (" + one.size() + ")");
			if (g != null) {
				r.check(Math.abs(g.getAttributeValue(Attributes.SCALE) - 1.8) < 0.01 && g.getMaxHealth() >= 149.0F,
						"Колосс: ×1,8 и 150 здоровья (" + g.getAttributeValue(Attributes.SCALE) + ", " + g.getMaxHealth() + ")");
				// Удар колосса сотрясает землю: сосед цели тоже получает урон.
				Mob a = dummy(level, EntityType.ZOMBIE, at.offset(-4, 0, 3));
				Mob b = dummy(level, EntityType.ZOMBIE, at.offset(-5, 0, 4));
				a.hurtServer(level, level.damageSources().mobAttack(g), 5.0F);
				r.check(lost(b) >= 5.5F, "Колосс: удар сотрясает землю — 6 урона врагу рядом с целью (" + hp(b) + ")");
			}
			Magic.castNow(p, spell("iron_guardian"), 1.0F);
			run(20);
			r.check(colossi(level, at).size() == 1, "Колосс: второй призыв заменяет первого (" + colossi(level, at).size() + ")");

			// Мультикаст ×2 — большой и малый колосс.
			Magic.castNow(p, spell("iron_guardian"), 1.0F, 2);
			run(20);
			List<IronGolem> two = colossi(level, at);
			boolean small = two.stream().anyMatch(x -> Math.abs(x.getAttributeValue(Attributes.SCALE) - 1.3) < 0.01);
			r.check(two.size() == 2 && small, "Колосс ×2: большой и малый ×1,3 (" + two.size() + ")");
		} finally {
			tidy(level, at, p);
		}
	}

	private static void fissure(ServerLevel level, BlockPos o, DevTests.Report r) {
		for (boolean on : new boolean[] {true, false}) {
			BlockPos at = far(o, on ? 87 : 88);
			FakePlayer p = mage(level, at);
			arena(level, at, 24, 6);
			stone(level, at.offset(-7, -10, 1), at.offset(7, -1, 24));
			face(p, at, pitchTo(18));
			try {
				griefing(on, () -> {
					Mob z = dummy(level, EntityType.ZOMBIE, at.offset(0, 0, 10));
					int broken0 = Terrain.brokenTotal();
					// Без разрушения — заодно мультикаст ×3: параллельные трещины в 4 блоках.
					Magic.castNow(p, spell("fissure"), 1.0F, on ? 1 : 3);
					run(14);
					boolean open = level.getBlockState(at.offset(0, -1, 10)).isAir() && level.getBlockState(at.offset(0, -8, 10)).isAir()
							&& level.getBlockState(at.offset(1, -4, 12)).isAir();
					r.check(open, "Разлом: земля раскрылась на 3 в ширину и 8 в глубину" + (on ? "" : " (без разрушения)"));
					r.check(amp(z) >= 6, "Разлом: враг над трещиной проваливается и вязнет (" + (amp(z) + 1) + ")");
					if (!on) {
						boolean side = level.getBlockState(at.offset(4, -3, 10)).isAir() && level.getBlockState(at.offset(-4, -3, 10)).isAir();
						r.check(side, "Разлом ×3: параллельные трещины в 4 блоках по бокам");
					}
					// Враг на дне (манекен сам не падает — опускаем).
					z.teleportTo(z.getX(), at.getY() - 6, z.getZ());
					run(90);
					r.check(lost(z) >= 33.5F && z.getY() >= at.getY() - 0.1, "Разлом: смыкается — 34 урона и выброс наверх (" + hp(z) + ", y "
							+ String.format("%.1f", z.getY() - at.getY()) + ")");
					boolean closed = level.getBlockState(at.offset(0, -5, 10)).is(Blocks.STONE) && level.getBlockState(at.offset(0, -1, 10)).is(Blocks.STONE);
					int rubble = count(level, at.offset(-1, -1, 3), at.offset(1, -1, 19), s -> s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL));
					r.check(closed && EarthSpells.tempCount() == 0, "Разлом: земля вернулась на место (врем. " + EarthSpells.tempCount() + ")");
					if (on) r.check(rubble > 0, "Разлом: с разрушением края остаются щебнем (" + rubble + ")");
					else r.check(rubble == 0 && Terrain.brokenTotal() == broken0, "Разлом: без разрушения — ни щебня, ни поломок (" + rubble + ")");
				});
			} finally {
				tidy(level, at, p);
			}
		}
	}

	// --- Ранг 6 ---

	private static void titanUpheaval(ServerLevel level, BlockPos o, DevTests.Report r) {
		for (boolean on : new boolean[] {true, false}) {
			BlockPos at = far(o, on ? 89 : 90);
			FakePlayer p = mage(level, at);
			arena(level, at, 26, 8);
			stone(level, at.offset(-9, -7, 1), at.offset(9, -1, 19));
			face(p, at, pitchTo(10));
			try {
				griefing(on, () -> {
					BlockPos c = at.offset(0, 0, 10);
					Mob ring1 = dummy(level, EntityType.ZOMBIE, c.offset(4, 0, 0));
					Mob ring2 = dummy(level, EntityType.ZOMBIE, c.offset(0, 0, 8));
					Mob core = dummy(level, EntityType.ZOMBIE, c.offset(-1, 0, 1));
					int broken0 = Terrain.brokenTotal();
					Magic.castNow(p, spell("titan_upheaval"), 1.0F);
					run(12);
					r.check(lost(ring1) >= 11.5F && !hurt(ring2), "Титан: первое кольцо копий (r4) — 12 урона (" + hp(ring1) + ", " + hp(ring2) + ")");
					run(10);
					r.check(lost(ring2) >= 11.5F, "Титан: второе кольцо (r8) — 12 урона (" + hp(ring2) + ")");
					run(13);
					int spikes = EarthSpells.tempCount();
					r.check(spikes > 20, "Титан: из земли встают каменные копья (" + spikes + " блоков)");
					run(20);
					r.check(lost(core) >= 15.5F && amp(core) == 2, "Титан: обвал в центре — 16 урона и Замедление III (" + hp(core) + ", "
							+ (amp(core) + 1) + ")");
					int broken = Terrain.brokenTotal() - broken0;
					if (on) r.check(broken >= 50, "Титан: с разрушением — воронка в центре (сломано " + broken + ")");
					else r.check(broken == 0, "Титан: без разрушения ничего не сломано");
					run(260);
					r.check(EarthSpells.tempCount() == 0, "Титан: копья и щебень уходят (" + EarthSpells.tempCount() + ")");
					if (!on) r.check(level.getBlockState(c.below()).is(Blocks.STONE), "Титан: без разрушения земля в центре цела");
				});
			} finally {
				tidy(level, at, p);
			}
		}
	}

	// --- Ранг 7 ---

	private static void mountain(ServerLevel level, BlockPos o, DevTests.Report r) {
		for (boolean on : new boolean[] {true, false}) {
			BlockPos at = far(o, on ? 91 : 92);
			FakePlayer p = mage(level, at);
			BlockPos c = at.offset(0, 0, 24);
			arena(level, c, 22, 34);
			stone(level, c.offset(-13, -9, -13), c.offset(13, -1, 13));
			face(p, at, pitchTo(24));
			try {
				griefing(on, () -> {
					Mob core = dummy(level, EntityType.ZOMBIE, c.offset(1, 0, 1));
					Mob mid = dummy(level, EntityType.ZOMBIE, c.offset(16, 0, 0));
					Mob out = dummy(level, EntityType.ZOMBIE, c.offset(-21, 0, 0));
					int broken0 = Terrain.brokenTotal();
					Magic.castNow(p, spell("mountain_awakening"), 1.0F);
					r.check(lost(core) >= 39.5F, "Гора: земля встаёт на дыбы — 40 урона в радиусе 12 (" + hp(core) + ")");
					Vec3 push = mid.getDeltaMovement();
					r.check(Math.hypot(push.x, push.z) >= 0.5 && !hurt(mid), "Гора: дальних отбрасывает наружу");
					run(20);
					BlockPos inside = c.above(5);
					r.check(EarthSpells.isTemp(level, inside) && !level.getBlockState(inside).isAir() && EarthSpells.tempCount() > 1500,
							"Гора: растёт слой за слоем (" + EarthSpells.tempCount() + " блоков)");
					r.check(core.getY() > c.getY() + 10, "Гора: поднимает врагов на склонах (y +" + String.format("%.0f", core.getY() - c.getY()) + ")");
					run(45);
					r.check(!core.isAlive() || lost(core) >= 125.0F, "Гора: обвал — всего до 130 урона в центре (" + hp(core) + ")");
					r.check(lost(mid) >= 55.0F && !hurt(out), "Гора: на краю (16) ~63 урона, за 20 — никого (" + hp(mid) + ", " + hp(out) + ")");
					r.check(projectiles(level, c, 48) >= 20, "Гора: лавина валунов (" + projectiles(level, c, 48) + ")");
					run(30);
					int broken = Terrain.brokenTotal() - broken0;
					r.check(EarthSpells.tempCount() == 0 && level.getBlockState(c.above(5)).isAir(), "Гора: рухнула, временные блоки ушли ("
							+ EarthSpells.tempCount() + ")");
					if (on) r.check(broken >= 200 && level.getBlockState(c.below(3)).isAir(), "Гора: с разрушением — воронка r12 (сломано " + broken + ")");
					else r.check(broken == 0 && level.getBlockState(c.below()).is(Blocks.STONE), "Гора: без разрушения земля цела");
				});
			} finally {
				tidy(level, at, p);
			}
		}
	}
}
