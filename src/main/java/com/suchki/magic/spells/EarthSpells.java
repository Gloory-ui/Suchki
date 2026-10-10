package com.suchki.magic.spells;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CastStyle;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Spells;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия «Земля»: каменная кожа, нарезное ядро, стены и мосты, трясина, землетрясение, каменный колосс, пасть
 * разлома, восстание титана и пробуждение великой горы.
 *
 * <p>Земля строит из временных блоков по своему учёту ({@link #swap}), а не через {@link Terrain#temp}: трясина и
 * разлом подменяют природные блоки (землю, камень, песок) даже при выключенном разрушении — и всегда возвращают их
 * на место; блок, сломанный игроком, просто возвращается без добычи. Настоящее разрушение (трещины, воронки,
 * щебень) — только через {@link Terrain}, оно само слушается настроек.
 */
public final class EarthSpells {
	/** Охра стихии, светлый песок, оранжевое свечение трещин, красные искры алхимии. */
	private static final int OCHRE = School.EARTH.color;
	private static final int SAND = School.EARTH.light;
	private static final int CRACK = 0xFF8A2A;
	private static final int ALCHEMY = 0xFF3B30;
	private static final Vec3 UP = SpellKit.UP;
	/** Метка каменного колосса (удары сотрясают землю). */
	private static final String COLOSSUS = "suchki.colossus";
	/** Сколько хрупких блоков ещё пробьёт каменное ядро: метка «suchki.shatter.N» на снаряде. */
	private static final String SHATTER = "suchki.shatter.";
	private static final Identifier GRIP = Identifier.fromNamespaceAndPath("suchki", "earth.stoneflesh.knockback");
	/** Ставим без обновления соседей: трава и факелы рядом не осыпаются, вода не течёт раньше времени. */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Высота и радиус великой горы. */
	private static final int MOUNTAIN_H = 30;
	private static final double MOUNTAIN_R = 12.0;
	/** Заклинания, при зарядке которых дрожит земля. */
	private static final Set<String> TREMORS = Set.of("earthquake", "titan_upheaval", "mountain_awakening");

	private EarthSpells() {}

	public static void register() {
		hooks();

		// --- Начальный ---

		Spell.of("stoneflesh", School.EARTH, 1, CastStyle.SELF).mana(12).charge(10).cooldown(20).range(6).icon("cobblestone")
				.anchor(Anchor.FEET).circle(1.2F).multicast(4)
				.action(EarthSpells::stoneflesh)
				.register();

		// --- Средний ---

		Spell.of("stone_cannon", School.EARTH, 2, CastStyle.BOLT).mana(18).charge(16).cooldown(30).range(96).icon("cobblestone")
				.model("cobblestone").size(0.42F).trail(Trail.STONE).destroys()
				.action(EarthSpells::stoneCannon)
				.hit(EarthSpells::stoneCannonHit)
				.register();

		Spell.of("earth_wall", School.EARTH, 2, CastStyle.RITUAL).mana(18).charge(14).cooldown(60).range(48).icon("stone_bricks")
				.anchor(Anchor.TARGET).circle(2.5F)
				.action(EarthSpells::earthWall)
				.register();

		// --- Продвинутый ---

		Spell.of("quagmire", School.EARTH, 3, CastStyle.RITUAL).mana(30).charge(22).cooldown(200).range(64).icon("mud")
				.anchor(Anchor.TARGET).circle(5.0F)
				.action(EarthSpells::quagmire)
				.register();

		// --- Святой ---

		Spell.of("earthquake", School.EARTH, 4, CastStyle.RITUAL).mana(56).charge(34).cooldown(400).range(16).icon("cracked_stone_bricks")
				.anchor(Anchor.FEET).layers(2).circle(5.0F).destroys()
				.action(EarthSpells::earthquake)
				.register();

		// --- Королевский ---

		Spell.of("iron_guardian", School.EARTH, 5, CastStyle.SUMMON).mana(100).charge(44).cooldown(1200).range(48).icon("carved_pumpkin")
				.anchor(Anchor.TARGET).layers(2).circle(4.0F)
				.action(EarthSpells::ironGuardian)
				.register();

		Spell.of("fissure", School.EARTH, 5, CastStyle.RITUAL).mana(100).charge(50).cooldown(900).range(64).icon("dripstone_block")
				.anchor(Anchor.LINE).layers(6).circle(1.5F).destroys()
				.action(EarthSpells::fissure)
				.register();

		// --- Императорский ---

		Spell.of("titan_upheaval", School.EARTH, 6, CastStyle.RITUAL).mana(170).charge(76).cooldown(2400).range(128).icon("pointed_dripstone")
				.anchor(Anchor.TARGET).layers(3).circle(12.0F).destroys()
				.action(EarthSpells::titanUpheaval)
				.register();

		// --- Божественный ---

		Spell.of("mountain_awakening", School.EARTH, 7, CastStyle.RITUAL).mana(280).charge(140).cooldown(7200).range(128).icon("heavy_core")
				.anchor(Anchor.TARGET).layers(5).circle(20.0F).destroys().noMulticast()
				.model("cobblestone").size(0.6F).trail(Trail.STONE)
				.action(EarthSpells::mountainAwakening)
				.hit(EarthSpells::boulderHit)
				.register();
	}

	// =====================================================================================================
	// Каменная кожа
	// =====================================================================================================

	/** Каменная кожа: +броня, +0,3 к устойчивости к отбрасыванию на 90 с; копии мультикаста покрывают союзников рядом. */
	private static boolean stoneflesh(Cast c) {
		LivingEntity who = c.caster;
		if (c.copy > 0) {
			List<LivingEntity> allies = c.alliesAround(Cast.center(c.caster), 6.0);
			allies.remove(c.caster);
			allies.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster)));
			if (c.copy - 1 >= allies.size()) return true;
			who = allies.get(c.copy - 1);
			MagicFx.circle(c.level, c.spell, who.position().add(0, 0.06, 0), UP, 1.0F, 8, 10, 10);
		}
		SpellKit.release(c);
		who.addEffect(new MobEffectInstance(MagicEffects.STONESKIN, c.duration(1800), c.power >= 1.2F ? 1 : 0), c.caster);
		stoneGrip(who);
		// Серая крошка ползёт по телу снизу вверх и твердеет облачком пыли.
		LivingEntity body = who;
		BlockParticleOption stone = dust(Blocks.STONE.defaultBlockState());
		SpellKit.repeat(c, 6, 2, i -> {
			Vec3 at = body.position().add(0, body.getBbHeight() * (i + 0.5) / 6.0, 0);
			MagicFx.ring(c.level, at, body.getBbWidth() * 0.7, stone, 10, -0.02);
			if (i == 5) {
				MagicFx.burst(c.level, Cast.center(body), ParticleTypes.POOF, 8, body.getBbWidth() * 0.5, 0.02);
				MagicFx.burst(c.level, Cast.center(body), MagicParticles.spark(SAND, 0.2F, 14), 10, 0.4, 0.04);
				MagicFx.sound(c.level, at, SoundEvents.STONE_PLACE, 0.8F, 0.6F);
			}
		});
		MagicFx.one(c.level, who.position().add(0, 0.06, 0), MagicParticles.ring(OCHRE, 1.4F, 12), 0, 1, 0);
		MagicFx.sound(c.level, who.position(), MagicSounds.impact(School.EARTH), 0.7F, 0.6F);
		MagicFx.sound(c.level, who.position(), SoundEvents.GRAVEL_BREAK, 0.8F, 0.5F);
		return true;
	}

	private static final Set<LivingEntity> GRIPPED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<LivingEntity, Long> SKIN_PROC = new WeakHashMap<>();

	/** Устойчивость к отбрасыванию, пока держится Каменная кожа (снимается в {@link #serverTick}). */
	private static void stoneGrip(LivingEntity e) {
		AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb == null) return;
		kb.addOrUpdateTransientModifier(new AttributeModifier(GRIP, 0.3, AttributeModifier.Operation.ADD_VALUE));
		GRIPPED.add(e);
	}

	// =====================================================================================================
	// Нарезное каменное ядро
	// =====================================================================================================

	/** Доля зарядки ядра: 0 — «готово», 1 — полный заряд посоха ученика (×1,75), выше — сильные посохи. */
	private static float charge(Cast c) {
		return Mth.clamp((c.power - 1.0F) / 0.75F, 0.0F, 1.5F);
	}

	private static boolean stoneCannon(Cast c) {
		SpellKit.release(c);
		float k = charge(c);
		float speed = 2.0F + 2.5F * Math.min(1.0F, k);
		SpellProjectile p = SpellProjectile.shoot(c, speed / (0.85F + 0.15F * c.power)).gravity(0.01)
				.life(Math.round(96.0F / speed) + 6);
		p.scale(1.0F + 0.35F * Math.min(1.0F, k));
		p.addTag(SHATTER + 4);
		if (k >= 0.8F) p.pierce(1);
		// Дульное кольцо и конус пыли.
		Vec3 dir = c.direction();
		Vec3 at = c.hand();
		MagicFx.shockwave(c.level, at.add(dir.scale(0.3)), dir, 0.15F, 1.1F, SAND, 8);
		BlockParticleOption stone = dust(Blocks.COBBLESTONE.defaultBlockState());
		RandomSource rnd = c.level.random;
		for (int i = 0; i < 14; i++) {
			Vec3 v = dir.scale(0.25 + rnd.nextDouble() * 0.25).add(jitter(rnd, 0.08), jitter(rnd, 0.08), jitter(rnd, 0.08));
			MagicFx.one(c.level, at, stone, v.x, v.y, v.z);
		}
		MagicFx.sound(c.level, at, MagicSounds.EXPLOSION, 0.45F, 1.7F);
		if (k >= 1.0F) Magic.shake(c.level, c.eye(), 0.15F, 6, 8);
		return true;
	}

	private static void stoneCannonHit(Cast c, SpellProjectile p, HitResult result) {
		float k = charge(c);
		Vec3 v = p.getDeltaMovement();
		Vec3 dir = v.lengthSqr() < 1.0E-6 ? c.look() : v.normalize();
		if (result instanceof EntityHitResult eh) {
			if (eh.getEntity() instanceof LivingEntity e && c.canHit(e) && !c.isAlly(e)) {
				float dmg = 8.0F * (1.0F + k) * c.bonus;
				if (k >= 0.8F) dmg *= armorPierce(e, dmg, 0.3F);
				c.hurt(e, dmg, MagicDamage.EARTH, p);
				Cast.knock(e, e.position().subtract(dir), 1.2, 0.3);
			}
			SpellKit.impact(c, result.getLocation(), 0.7F);
			MagicFx.burst(c.level, result.getLocation(), dust(Blocks.COBBLESTONE.defaultBlockState()), 14, 0.2, 0.15);
			return;
		}
		if (!(result instanceof BlockHitResult b)) return;
		BlockPos pos = b.getBlockPos();
		BlockState s = c.level.getBlockState(pos);
		int left = shatterLeft(p);
		if (left > 0 && cannonFragile(s, c.level, pos) && Terrain.shatter(c, Vec3.atCenterOf(pos), 0.5) > 0) {
			// Хрупкое (стекло, лёд, листва) — вдребезги, а ядро летит дальше.
			MagicFx.burst(c.level, Vec3.atCenterOf(pos), dust(s), 16, 0.3, 0.1);
			MagicFx.sound(c.level, b.getLocation(), SoundEvents.GLASS_BREAK, 0.8F, 0.9F);
			SpellProjectile next = SpellProjectile.spawn(c, b.getLocation().add(dir.scale(0.05)), v).gravity(0.01).life(40);
			next.scale(p.scale());
			next.addTag(SHATTER + (left - 1));
			if (k >= 0.8F) next.pierce(1);
			return;
		}
		Vec3 at = b.getLocation();
		MagicFx.burst(c.level, at, dust(s.isAir() ? Blocks.STONE.defaultBlockState() : s), 24, 0.25, 0.12);
		MagicFx.cloud(c.level, at, ParticleTypes.DUST_PLUME, 10, 0.3, 0.3, 0.3, 0.05);
		SpellKit.impact(c, at, 0.8F);
		MagicFx.sound(c.level, at, SoundEvents.STONE_BREAK, 1.0F, 0.6F);
		if (k >= 1.0F) Magic.shake(c.level, at, 0.15F, 6, 16);
	}

	private static int shatterLeft(Entity p) {
		for (String t : p.getTags()) {
			if (t.startsWith(SHATTER)) {
				try {
					return Integer.parseInt(t.substring(SHATTER.length()));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	/** Что ядро пробивает насквозь: стекло, лёд, листва, цветы, совсем мягкое (но не землю и песок). */
	private static boolean cannonFragile(BlockState s, ServerLevel level, BlockPos pos) {
		if (s.isAir()) return false;
		if (s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.ICE) || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS)) {
			return true;
		}
		float hard = s.getDestroySpeed(level, pos);
		return hard >= 0 && hard <= 0.5F && !earthy(s);
	}

	/** Множитель урона, как если бы у цели было на {@code part} меньше брони (формула брони Minecraft). */
	private static float armorPierce(LivingEntity e, float dmg, float part) {
		float armor = e.getArmorValue();
		if (armor <= 0) return 1.0F;
		float tough = (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		float full = 1.0F - armorCut(armor, tough, dmg);
		float less = 1.0F - armorCut(armor * (1.0F - part), tough, dmg);
		return full <= 0.01F ? 1.0F : less / full;
	}

	private static float armorCut(float armor, float tough, float dmg) {
		float t = 2.0F + tough / 4.0F;
		return Mth.clamp(armor - dmg / t, armor * 0.2F, 20.0F) / 25.0F;
	}

	// =====================================================================================================
	// Земляной бастион: стена, крепость, мост
	// =====================================================================================================

	/** Сколько стоят стена и крепость, сколько — мост. */
	private static final int WALL_LIFE = 300;
	private static final int BRIDGE_LIFE = 1200;

	private static boolean earthWall(Cast c) {
		// Копии мультикаста не строят своих стен — главная стена становится шире (ниже).
		if (c.copy > 0) return true;
		SpellKit.release(c);
		Targeting.Aim aim = c.target();
		int extra = c.copies - 1;
		Vec3 flat = flat(c.look());
		if (gap(c, aim)) return bridge(c, aim, flat, 3 + 3 * extra);
		if (c.caster.isShiftKeyDown()) return fortress(c, aim.ground(), 3 + extra);
		return wall(c, aim.ground(), flat, 7 + 7 * extra);
	}

	/** Прицел над пропастью, водой или лавой (или в пустоту) — строим мост, а не стену. */
	private static boolean gap(Cast c, Targeting.Aim aim) {
		if (aim.miss()) return true;
		Vec3 g = aim.ground();
		return !c.level.getFluidState(BlockPos.containing(g.x, g.y - 0.05, g.z)).isEmpty();
	}

	/** Стена 4 в высоту поперёк взгляда: ряды растут снизу вверх, стоят 15 с, потом осыпаются сверху. */
	private static boolean wall(Cast c, Vec3 ground, Vec3 flat, int width) {
		Vec3 along = new Vec3(-flat.z, 0, flat.x);
		List<BlockPos> bases = new ArrayList<>();
		int gy = Mth.floor(ground.y - 0.01);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < width; i++) {
			Vec3 p = ground.add(along.scale(i - (width - 1) / 2.0));
			int x = Mth.floor(p.x), z = Mth.floor(p.z);
			if (!seen.add(BlockPos.asLong(x, 0, z))) continue;
			BlockPos top = surface(c.level, x, gy + 3, z, 7);
			bases.add(top != null ? top : new BlockPos(x, gy, z));
		}
		return raise(c, bases, 4, ground, flat, false);
	}

	/** Кольцо-крепость 5×5 вокруг точки. */
	private static boolean fortress(Cast c, Vec3 ground, int height) {
		List<BlockPos> bases = new ArrayList<>();
		int cx = Mth.floor(ground.x), cz = Mth.floor(ground.z), gy = Mth.floor(ground.y - 0.01);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
				BlockPos top = surface(c.level, cx + dx, gy + 3, cz + dz, 7);
				bases.add(top != null ? top : new BlockPos(cx + dx, gy, cz + dz));
			}
		}
		return raise(c, bases, height, new Vec3(cx + 0.5, ground.y, cz + 0.5), Vec3.ZERO, true);
	}

	/**
	 * Поднять каменную кладку над столбцами {@code bases}: ряд за рядом (через 4 тика, за 10 тиков), существ из
	 * кладки — вбок, стоящих наверху — подбросить. Через {@link #WALL_LIFE} осыпается сверху вниз.
	 */
	private static boolean raise(Cast c, List<BlockPos> bases, int height, Vec3 center, Vec3 flat, boolean ring) {
		ServerLevel level = c.level;
		Batch b = new Batch(level);
		// Трещины бегут по будущей стене.
		for (BlockPos base : bases) {
			Vec3 top = Vec3.atBottomCenterOf(base.above());
			MagicFx.one(level, top.add(0, 0.05, 0), MagicParticles.spark(CRACK, 0.18F, 16), 0, 0.01, 0);
			MagicFx.burst(level, top, dust(level.getBlockState(base)), 3, 0.25, 0.05);
		}
		MagicFx.soundFar(level, center, MagicSounds.RUMBLE, 32, 1.1F);
		RandomSource rnd = level.random;
		for (int h = 0; h < height; h++) {
			int row = h;
			Zones.later(c, 4 + Math.round(h * 10.0F / Math.max(1, height - 1)), () -> {
				List<BlockPos> placed = new ArrayList<>();
				for (BlockPos base : bases) {
					BlockPos p = base.above(row + 1);
					float roll = rnd.nextFloat();
					BlockState mat = roll < 0.6F ? Blocks.STONE_BRICKS.defaultBlockState()
							: roll < 0.85F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
					if (swap(b, c.caster, p, mat, WALL_LIFE + 60, false)) placed.add(p);
				}
				for (BlockPos p : placed) MagicFx.burst(level, Vec3.atCenterOf(p), dust(level.getBlockState(p)), 3, 0.35, 0.08);
				shove(c, placed, center, flat, ring, row == height - 1);
				MagicFx.cloud(level, center.add(0, row + 0.5, 0), ParticleTypes.DUST_PLUME, 6 + 3 * bases.size() / 4,
						bases.size() * 0.25, 0.3, bases.size() * 0.25, 0.04);
				MagicFx.sound(level, center.add(0, row, 0), SoundEvents.STONE_PLACE, 1.2F, 0.6F + 0.1F * row);
				if (row == 0) {
					MagicFx.soundFar(level, center, MagicSounds.impact(School.EARTH), 32, 0.6F);
					Magic.shake(level, center, 0.3F, 8, 16);
				}
			});
		}
		Zones.later(c, 4 + WALL_LIFE - 20, () -> MagicFx.sound(level, center, SoundEvents.GRAVEL_BREAK, 1.0F, 0.6F));
		revertLater(c, b, 4 + WALL_LIFE, 8, true, 0);
		return true;
	}

	/** Вытолкнуть существ из только что поставленных блоков: вбок от стены (у крепости — наружу), стоящих сверху — вверх. */
	private static void shove(Cast c, List<BlockPos> placed, Vec3 center, Vec3 flat, boolean ring, boolean top) {
		if (placed.isEmpty()) return;
		AABB box = new AABB(placed.get(0));
		for (BlockPos p : placed) box = box.minmax(new AABB(p));
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.2, 1.2, 0.2), LivingEntity::isAlive)) {
			AABB bb = e.getBoundingBox();
			BlockPos hit = null;
			for (BlockPos p : placed) {
				if (bb.intersects(new AABB(p))) {
					hit = p;
					break;
				}
			}
			if (hit == null) {
				// Стоит прямо на макушке стены — подбросить.
				if (top && e.getY() >= box.maxY - 0.1 && e.getY() <= box.maxY + 0.6) SpellKit.launch(e, 0.8);
				continue;
			}
			if (e.getY() >= hit.getY() + 0.6) {
				e.teleportTo(e.getX(), hit.getY() + 1.0, e.getZ());
				SpellKit.launch(e, 0.8);
				continue;
			}
			Vec3 cell = Vec3.atBottomCenterOf(hit);
			Vec3 away;
			if (ring) {
				Vec3 d = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
				away = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
				// Свои остаются внутри крепости, чужие — снаружи.
				if (c.isAlly(e)) away = away.scale(-1);
			} else {
				double side = new Vec3(e.getX() - cell.x, 0, e.getZ() - cell.z).dot(flat);
				away = flat.scale(side > 0 ? 1 : -1);
			}
			Vec3 to = cell.add(away.scale(0.5 + e.getBbWidth() * 0.5 + 0.15));
			e.teleportTo(to.x, e.getY(), to.z);
			e.hurtMarked = true;
		}
	}

	/** Мост шириной {@code width} к точке прицела (до 48 блоков), растёт по 2 блока за тик, стоит 60 с. */
	private static boolean bridge(Cast c, Targeting.Aim aim, Vec3 flat, int width) {
		ServerLevel level = c.level;
		Vec3 start = c.caster.position();
		Vec3 d = new Vec3(aim.point().x - start.x, 0, aim.point().z - start.z);
		double len = Mth.clamp(d.length(), 4.0, 48.0);
		Vec3 dir = d.lengthSqr() < 0.01 ? flat : d.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		int y = Mth.floor(start.y - 0.01) - 1;
		int steps = Mth.ceil(len);
		Batch b = new Batch(level);
		RandomSource rnd = level.random;
		for (int t = 0; t * 2 < steps; t++) {
			int from = t * 2, to = Math.min(steps, from + 2);
			Zones.later(c, 1 + t, () -> {
				for (int s = from; s < to; s++) {
					Vec3 mid = start.add(dir.scale(s + 1.0));
					for (int w = 0; w < width; w++) {
						Vec3 p = mid.add(side.scale(w - (width - 1) / 2.0));
						BlockPos pos = new BlockPos(Mth.floor(p.x), y, Mth.floor(p.z));
						BlockState mat = rnd.nextFloat() < 0.8F ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
						if (swap(b, c.caster, pos, mat, BRIDGE_LIFE + 80, false) && rnd.nextInt(2) == 0) {
							// Красные искры алхимии прыгают по новым блокам.
							MagicFx.burst(level, Vec3.atCenterOf(pos).add(0, 0.55, 0), MagicParticles.spark(ALCHEMY, 0.18F, 10), 3, 0.3, 0.06);
						}
					}
					MagicFx.burst(level, mid.add(0, -0.4, 0), dust(Blocks.STONE_BRICKS.defaultBlockState()), 4, 0.6, 0.05);
				}
				if ((from / 2) % 3 == 0) MagicFx.sound(level, start.add(dir.scale(from)), SoundEvents.STONE_PLACE, 1.0F, 0.8F);
			});
		}
		MagicFx.soundFar(level, start, MagicSounds.RUMBLE, 32, 1.3F);
		// Осыпается с дальнего конца к магу — успеешь сойти.
		revertLater(c, b, BRIDGE_LIFE, 30, true, 0);
		return true;
	}

	// =====================================================================================================
	// Бездонная топь
	// =====================================================================================================

	private static final int MIRE_LIFE = 240;

	private static boolean quagmire(Cast c) {
		SpellKit.release(c);
		ServerLevel level = c.level;
		Vec3 at = c.aimGround();
		double r = c.copy > 0 ? 4.0 : 5.0;
		// Верхние два слоя земли, песка, камня по кругу — в грязь, от центра к краю за 10 тиков.
		int cx = Mth.floor(at.x), cz = Mth.floor(at.z), cy = Mth.floor(at.y - 0.01);
		int ri = Mth.ceil(r);
		List<BlockPos> tops = new ArrayList<>();
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				if (dx * dx + dz * dz > r * r) continue;
				BlockPos top = surface(level, cx + dx, cy + 2, cz + dz, 5);
				if (top != null) tops.add(top);
			}
		}
		tops.sort(Comparator.comparingDouble(p -> (p.getX() - cx) * (p.getX() - cx) + (p.getZ() - cz) * (p.getZ() - cz)));
		Batch b = new Batch(level);
		BlockState mud = Blocks.MUD.defaultBlockState();
		int n = tops.size();
		for (int k = 0; k < 5; k++) {
			int lo = n * k / 5, hi = n * (k + 1) / 5;
			Zones.later(c, k * 2, () -> {
				for (int i = lo; i < hi; i++) {
					BlockPos top = tops.get(i);
					for (int depth = 0; depth < 2; depth++) swap(b, c.caster, top.below(depth), mud, MIRE_LIFE + 60, true);
					if (i % 3 == 0) MagicFx.burst(level, Vec3.atCenterOf(top).add(0, 0.6, 0), dust(mud), 3, 0.3, 0.05);
				}
			});
		}
		// Три расходящиеся ряби и тяжёлый всплеск.
		for (int k = 1; k <= 3; k++) {
			float rr = (float) (r * k / 3.0);
			Zones.later(c, (k - 1) * 3, () -> MagicFx.shockwave(level, at.add(0, 0.12, 0), UP, rr * 0.4F, rr, OCHRE, 16));
		}
		MagicFx.soundFar(level, at, SoundEvents.MUD_BREAK, 32, 0.5F);
		MagicFx.soundFar(level, at, MagicSounds.impact(School.EARTH), 32, 0.55F);
		MagicFx.burst(level, at.add(0, 0.4, 0), dust(mud), 30, r * 0.4, 0.1);
		Map<LivingEntity, Integer> stuck = new WeakHashMap<>();
		Zones.add(c, at, r, MIRE_LIFE, (z, age) -> mireTick(c, at, r, age, stuck));
		Zones.later(c, MIRE_LIFE, () -> MagicFx.sound(level, at, SoundEvents.MUD_BRICKS_PLACE, 1.0F, 1.4F));
		// Высыхает от центра к краю.
		revertLater(c, b, MIRE_LIFE, 10, false, 0);
		return true;
	}

	/** Трясина каждые 2 тика: враги вязнут (3 с намертво, потом Замедление IV), мелкие тонут, свои чуть вязнут. */
	private static void mireTick(Cast c, Vec3 at, double r, int age, Map<LivingEntity, Integer> stuck) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		if (age % 4 == 0) {
			Vec3 p = SpellKit.inDisc(rnd, at, r * 0.9).add(0, 0.15, 0);
			MagicFx.burst(level, p, dust(Blocks.MUD.defaultBlockState()), 5, 0.15, 0.08);
			MagicFx.burst(level, p, ParticleTypes.BUBBLE_POP, 3, 0.2, 0.02);
			if (age % 24 == 0) MagicFx.sound(level, p, SoundEvents.MUD_FALL, 0.9F, 0.5F + rnd.nextFloat() * 0.2F);
		}
		if (age % 2 != 0) return;
		AABB box = new AABB(at, at).inflate(r, 2.5, r);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double dx = e.getX() - at.x, dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > r * r || e.getY() < at.y - 2.2 || e.getY() > at.y + 1.5) continue;
			if (c.isAlly(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 0, false, true), c.caster);
				continue;
			}
			if (!c.isFoe(e)) continue;
			if (boss(e) || !grounded(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1, false, true), c.caster);
				continue;
			}
			int first = stuck.computeIfAbsent(e, k -> age);
			boolean rooted = age - first < 60;
			// Корни отпускают ровно через 3 с: сильное замедление снимаем, иначе слабое его не перекроет.
			MobEffectInstance held = e.getEffect(MobEffects.SLOWNESS);
			if (!rooted && held != null && held.getAmplifier() > 3 && held.getDuration() <= 25) e.removeEffect(MobEffects.SLOWNESS);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, rooted ? 6 : 3, false, true), c.caster);
			Vec3 v = e.getDeltaMovement();
			double keep = rooted ? 0.0 : 0.5;
			e.setDeltaMovement(v.x * keep, Math.min(0.0, v.y), v.z * keep);
			e.hurtMarked = true;
			// Мелочь (ниже блока) тонет в грязи.
			if (e.getBbHeight() < 1.0F && (age - first) % 20 == 0) c.hurt(e, c.amount(1.0F), MagicDamage.EARTH);
			if (age % 10 == 0) MagicFx.burst(level, e.position().add(0, 0.1, 0), dust(Blocks.MUD.defaultBlockState()), 6, e.getBbWidth() * 0.5, 0.1);
		}
	}

	// =====================================================================================================
	// Рокот священной горы (землетрясение)
	// =====================================================================================================

	private static boolean earthquake(Cast c) {
		// Каждая копия мультикаста — ещё одна волна (+5 к радиусу) и +15% урона; всё делает главная.
		if (c.copy > 0) return true;
		SpellKit.release(c);
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		Vec3 center = c.caster.position();
		int extra = Math.min(3, c.copies - 1);
		int rings = 3 + extra;
		float dmg = c.amount(10.0F) * (1.0F + 0.15F * extra);
		boolean grief = Terrain.griefing(level);
		MagicFx.soundFar(level, center, MagicSounds.EXPLOSION, 64, 0.5F);
		MagicFx.soundFar(level, center, MagicSounds.RUMBLE, 64, 0.8F);
		MagicFx.shockwave(level, center.add(0, 0.1, 0), UP, 0.5F, 5.0F, SAND, 10);
		MagicFx.burst(level, center.add(0, 0.3, 0), new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundState(level, center)), 40, 1.5, 0.2);
		Magic.shake(level, center, 0.9F, 16, 24);
		// Трещины разбегаются от мага: при разрушении — настоящие расщелины, без него — каменные шипы.
		Batch spikes = new Batch(level);
		int n = 4 + rnd.nextInt(3);
		double a0 = rnd.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = a0 + i * Math.PI * 2 / n + jitter(rnd, 0.3);
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			double len = 10.0 + rnd.nextDouble() * 6.0;
			for (int s = 2; s <= len; s++) {
				Vec3 p = center.add(dir.scale(s));
				int step = s;
				Zones.later(c, s / 2, () -> {
					MagicFx.burst(level, p.add(0, 0.15, 0), dust(groundState(level, p)), 4, 0.3, 0.12);
					MagicFx.one(level, p.add(0, 0.08, 0), MagicParticles.spark(CRACK, 0.25F, 30), 0, 0.01, 0);
					if (!grief && step % 3 == 0) {
						BlockPos top = surface(level, Mth.floor(p.x), Mth.floor(center.y) + 2, Mth.floor(p.z), 6);
						if (top != null && !occupied(level, top.above(), 2)) spike(spikes, c.caster, top, 2, 200, Blocks.STONE.defaultBlockState());
					}
				});
			}
			if (grief) {
				for (int depth = 0; depth < 3; depth++) {
					Vec3 from = center.add(dir.scale(1.5)).add(0, -0.5 - depth, 0);
					Vec3 to = center.add(dir.scale(len)).add(0, -0.5 - depth, 0);
					Terrain.tunnel(c, from, to, 0.75, depth == 2 ? Terrain.Rim.RUBBLE : Terrain.Rim.NONE);
				}
			}
		}
		if (grief) Terrain.shatter(c, center, 16);
		for (int i = 0; i < rings; i++) {
			double r = 6.0 + 5.0 * i;
			double inner = i == 0 ? 0.5 : r - 5.0;
			int wave = i;
			Zones.later(c, 8 * i, () -> quakeRing(c, center, inner, r, dmg, grief, wave));
		}
		revertLater(c, spikes, 120 + 8, 6, true, 0);
		return true;
	}

	/** Одна ударная волна: бьёт всех на земле в радиусе (летящих — нет), пыль по кругу, выбоины при разрушении. */
	private static void quakeRing(Cast c, Vec3 center, double inner, double r, float dmg, boolean grief, int wave) {
		ServerLevel level = c.level;
		AABB box = new AABB(center, center).inflate(r, 3.0, r);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double dx = e.getX() - center.x, dz = e.getZ() - center.z;
			if (dx * dx + dz * dz > r * r || !c.isFoe(e) || !grounded(e)) continue;
			c.hurt(e, dmg, MagicDamage.EARTH);
			SpellKit.launch(e, 0.7);
			SpellKit.slow(c, e, 60, 1);
		}
		MagicFx.shockwave(level, center.add(0, 0.15, 0), UP, (float) inner, (float) r, OCHRE, 12);
		int points = (int) Math.min(72, r * 4);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points + wave * 0.3;
			Vec3 p = center.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
			MagicFx.one(level, p, dust(groundState(level, p)), Math.cos(a) * 0.15, 0.25, Math.sin(a) * 0.15);
		}
		MagicFx.soundFar(level, center, MagicSounds.RUMBLE, 48, 0.6F + 0.1F * wave);
		MagicFx.soundFar(level, center.add(0, 0, 0), MagicSounds.SHOCKWAVE, 32 + (float) r, 0.7F);
		Magic.shake(level, center, 0.6F, 14, 24);
		if (grief) {
			// Четверть поверхности на волне — выбоины щебня.
			RandomSource rnd = level.random;
			int holes = (int) (r / 2);
			for (int i = 0; i < holes; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				Vec3 p = center.add(Math.cos(a) * r, -0.5, Math.sin(a) * r);
				Terrain.crater(c, p, 1.1, 0.6, Terrain.Rim.RUBBLE);
			}
		}
	}

	// =====================================================================================================
	// Каменный колосс
	// =====================================================================================================

	private static final Map<Entity, ServerPlayer> OWNERS = new WeakHashMap<>();

	private static boolean ironGuardian(Cast c) {
		SpellKit.release(c);
		ServerLevel level = c.level;
		boolean small = c.copy > 0;
		// Колоссов не больше 1 (2 — при силе ×1,2) плюс по одному малому на копию: старые уходят в землю.
		if (c.copy == 0) dismissOld(c, (c.power >= 1.2F ? 2 : 1) - 1);
		Vec3 at = c.aimGround();
		BlockPos base = BlockPos.containing(at.x, at.y - 0.01, at.z);
		// Из земли вырывается каменный столб…
		Batch pillar = new Batch(level);
		for (int h = 1; h <= 5; h++) {
			int row = h;
			Zones.later(c, (h - 1) * 2, () -> {
				for (Direction d : new Direction[] {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
					if (row == 5 && d != null) continue;
					BlockPos p = (d == null ? base : base.relative(d)).above(row);
					swap(pillar, c.caster, p, row % 2 == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 80, false);
				}
				MagicFx.burst(level, Vec3.atCenterOf(base.above(row)), dust(Blocks.STONE.defaultBlockState()), 10, 0.8, 0.12);
				MagicFx.sound(level, Vec3.atCenterOf(base.above(row)), SoundEvents.STONE_PLACE, 1.2F, 0.5F + 0.08F * row);
			});
		}
		MagicFx.circle(level, c.spell, at.add(0, 0.08, 0), UP, small ? 2.0F : 3.0F, 2, 0.3F, 8, 14, 10);
		MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 48, 0.9F);
		Magic.shake(level, at, 0.5F, 12, 24);
		// …и рассыпается: из него поднимается колосс.
		Zones.later(c, 16, () -> {
			revert(pillar, 0, pillar.list.size(), 0);
			spawnColossus(c, at, small);
		});
		return true;
	}

	private static void spawnColossus(Cast c, Vec3 at, boolean small) {
		ServerLevel level = c.level;
		IronGolem g = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (g == null) return;
		g.setPlayerCreated(true);
		setBase(g, Attributes.SCALE, small ? 1.3 : 1.8);
		setBase(g, Attributes.MAX_HEALTH, base(g, Attributes.MAX_HEALTH) * 1.5);
		setBase(g, Attributes.ATTACK_DAMAGE, base(g, Attributes.ATTACK_DAMAGE) * 1.3);
		g.setHealth(g.getMaxHealth());
		g.setCustomName(c.spell.displayName());
		g.addTag(COLOSSUS);
		Vec3 spot = place(c, g, at);
		g.snapTo(spot.x, spot.y, spot.z, c.caster.getYRot(), 0);
		g.setYHeadRot(c.caster.getYRot());
		Minions.summon(c, g, Math.round(1800 * c.power));
		com.suchki.Suchki.LOGGER.info("DEBUG colossus at {} removed={} alive={} inLevel={} hp={}", spot, g.isRemoved(), g.isAlive(), c.level.getEntity(g.getUUID()) != null, g.getHealth());
		OWNERS.put(g, c.caster);
		float s = small ? 1.3F : 1.8F;
		MagicFx.pillar(level, spot, 1.2F * s, 5.0F * s, OCHRE, SAND, 20);
		MagicFx.shockwave(level, spot.add(0, 0.2, 0), UP, 0.5F, 4.0F * s, SAND, 14);
		MagicFx.cloud(level, spot.add(0, 1.0, 0), dust(Blocks.STONE.defaultBlockState()), 50, 1.0 * s, 1.5 * s, 1.0 * s, 0.15);
		MagicFx.cloud(level, spot.add(0, 0.5, 0), ParticleTypes.DUST_PLUME, 30, 1.2 * s, 0.4, 1.2 * s, 0.06);
		MagicFx.burst(level, spot.add(0, 2.4 * s, 0), MagicParticles.spark(0xFFB040, 0.35F, 30), 12, 0.3, 0.02);
		MagicFx.soundFar(level, spot, MagicSounds.EXPLOSION, 48, 0.6F);
		MagicFx.sound(level, spot, SoundEvents.IRON_GOLEM_REPAIR, 1.5F, 0.5F);
		MagicFx.sound(level, spot, SoundEvents.IRON_GOLEM_HURT, 1.2F, 0.4F);
		Magic.shake(level, spot, 0.6F, 12, 24);
	}

	/** Отпустить старых колоссов мага, оставив {@code keep} самых новых. */
	private static void dismissOld(Cast c, int keep) {
		List<IronGolem> mine = new ArrayList<>(c.level.getEntities(EntityType.IRON_GOLEM,
				g -> g.isAlive() && g.getTags().contains(COLOSSUS) && Minions.isOwnedBy(g, c.caster)));
		mine.sort(Comparator.comparingLong(g -> {
			Minions.Summon s = g.getAttached(Minions.SUMMON);
			return s == null ? 0L : s.expires();
		}));
		while (mine.size() > keep) {
			IronGolem g = mine.remove(0);
			MagicFx.burst(c.level, Cast.center(g), dust(Blocks.STONE.defaultBlockState()), 30, g.getBbWidth() * 0.5, 0.1);
			MagicFx.poof(c.level, g, OCHRE);
			g.discard();
		}
	}

	/** Свободное место для большого существа у точки (или рядом с магом). */
	private static Vec3 place(Cast c, Mob mob, Vec3 at) {
		for (int ring = 0; ring <= 3; ring++) {
			int dirs = ring == 0 ? 1 : 8;
			for (int i = 0; i < dirs; i++) {
				double a = Math.PI * 2 * i / dirs;
				for (int dy = 0; dy <= 3; dy++) {
					Vec3 p = at.add(Math.cos(a) * ring * 1.5, dy, Math.sin(a) * ring * 1.5);
					if (c.level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(p))) return p;
				}
			}
		}
		return c.caster.position();
	}

	private static boolean slamming;

	/** Удар колосса сотрясает землю: 6 урона земли всем врагам в 2,5 блока от цели. */
	private static void slam(ServerLevel level, IronGolem g, LivingEntity victim) {
		ServerPlayer owner = OWNERS.get(g);
		if (owner == null) {
			UUID id = Minions.ownerOf(g);
			owner = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		}
		Spell spell = Spells.get("iron_guardian");
		if (owner == null || spell == null) return;
		Cast c = new Cast(level, owner, spell, 1.0F, Magic.spellBonus(owner));
		slamming = true;
		try {
			for (LivingEntity e : c.foesAround(Cast.center(victim), 2.5)) {
				if (e != victim && e != g) c.hurt(e, c.amount(6.0F), MagicDamage.EARTH, g);
			}
		} finally {
			slamming = false;
		}
		Vec3 at = victim.position();
		MagicFx.shockwave(level, at.add(0, 0.15, 0), UP, 0.3F, 2.5F, OCHRE, 10);
		MagicFx.ring(level, at.add(0, 0.2, 0), 1.5, dust(groundState(level, at)), 16, 0.12);
		MagicFx.sound(level, at, MagicSounds.impact(School.EARTH), 1.0F, 0.6F);
	}

	// =====================================================================================================
	// Пасть разлома
	// =====================================================================================================

	/** Через сколько тиков после раскрытия разлом смыкается. */
	private static final int RIFT_OPEN = 80;

	private static boolean fissure(Cast c) {
		SpellKit.release(c);
		ServerLevel level = c.level;
		Vec3 flat = flat(c.look());
		// Копии идут параллельно главной трещине (±4 блока), а не к своим целям.
		Targeting.Aim main = c.copy == 0 ? c.target() : Targeting.aim(level, c.caster, c.eye(), c.look(), c.spell.range, Targeting.living(c.caster));
		Vec3 feet = c.caster.position();
		Vec3 end = main.ground();
		Vec3 d = new Vec3(end.x - feet.x, 0, end.z - feet.z);
		double dist = Mth.clamp(d.length(), 9.0, 64.0);
		Vec3 dir = d.lengthSqr() < 16 ? flat : d.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		double off = c.copy == 0 ? 0 : 4.0 * ((c.copy + 1) / 2) * (c.copy % 2 == 1 ? 1 : -1);
		Vec3 from = feet.add(dir.scale(3)).add(side.scale(off));
		Vec3 to = feet.add(dir.scale(dist)).add(side.scale(off));
		double fromY = feet.y, toY = main.miss() ? feet.y : end.y;
		double len = dist - 3;
		// Столбцы разлома: 3 в ширину, по земле (верх — первый твёрдый блок у линии).
		Map<Long, Integer> tops = new HashMap<>();
		List<List<BlockPos>> groups = new ArrayList<>();
		List<Boolean> edge = new ArrayList<>();
		for (int g = 0; g < 10; g++) groups.add(new ArrayList<>());
		List<BlockPos> edges = new ArrayList<>();
		for (double s = 0; s <= len; s += 0.7) {
			double t = s / len;
			Vec3 mid = from.add(dir.scale(s));
			int yRef = Mth.floor(Mth.lerp(t, fromY, toY) - 0.01);
			for (int w = -1; w <= 1; w++) {
				Vec3 p = mid.add(side.scale(w));
				int x = Mth.floor(p.x), z = Mth.floor(p.z);
				long key = BlockPos.asLong(x, 0, z);
				if (tops.containsKey(key)) continue;
				BlockPos top = surface(level, x, yRef + 4, z, 12);
				if (top == null) continue;
				tops.put(key, top.getY());
				groups.get(Math.min(9, (int) (t * 10))).add(top);
				if (w != 0) edges.add(top);
			}
		}
		if (tops.isEmpty()) return false;
		edge.clear();
		Batch b = new Batch(level);
		// Шесть кругов ложатся на землю один за другим, их связывают светящиеся трещины.
		Vec3 prev = null;
		for (int i = 0; i < 6; i++) {
			Vec3 p = from.add(dir.scale(len * (i + 0.5) / 6.0));
			Vec3 g = Targeting.ground(level, c.caster, new Vec3(p.x, Mth.lerp((i + 0.5) / 6.0, fromY, toY) + 3, p.z));
			Vec3 a = prev;
			Zones.later(c, i, () -> {
				MagicFx.circle(level, c.spell, g.add(0, 0.08, 0), UP, 1.5F, 5, 12, 12);
				if (a != null) MagicFx.line(level, a.add(0, 0.15, 0), g.add(0, 0.15, 0), MagicParticles.spark(CRACK, 0.22F, 40), 0.35);
			});
			prev = g;
		}
		AABB box = new AABB(from, to).inflate(2.5, 12, 2.5);
		Vec3 midLine = from.add(dir.scale(len / 2));
		// Земля раскалывается за 10 тиков от мага к цели.
		for (int g = 0; g < 10; g++) {
			List<BlockPos> group = groups.get(g);
			int wave = g;
			Zones.later(c, 3 + g, () -> openRift(c, b, group, tops, box, wave));
		}
		MagicFx.soundFar(level, midLine, MagicSounds.SHATTER, 96, 0.5F);
		MagicFx.soundFar(level, midLine, MagicSounds.EXPLOSION, 96, 0.6F);
		Magic.shake(level, midLine, 0.9F, 16, 48);
		for (int t = 20; t < RIFT_OPEN; t += 20) {
			Zones.later(c, 3 + t, () -> {
				MagicFx.soundFar(level, midLine, MagicSounds.RUMBLE, 64, 0.7F);
				for (int i = 0; i < 6; i++) {
					Vec3 p = from.add(dir.scale(level.random.nextDouble() * len)).add(0, 0.5, 0);
					MagicFx.cloud(level, p, new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()), 6, 1.0, 0.3, 1.0, 0);
				}
			});
		}
		Zones.later(c, 3 + 10 + RIFT_OPEN, () -> closeRift(c, b, tops, edges, box, from, dir, len));
		return true;
	}

	/** Раскрыть часть разлома: столбы земли до 8 вглубь — во временный воздух, враги над ним падают и вязнут. */
	private static void openRift(Cast c, Batch b, List<BlockPos> group, Map<Long, Integer> tops, AABB box, int wave) {
		ServerLevel level = c.level;
		BlockState air = Blocks.AIR.defaultBlockState();
		int life = RIFT_OPEN + 10 + 60;
		for (int i = 0; i < group.size(); i++) {
			BlockPos top = group.get(i);
			BlockState surfaceState = level.getBlockState(top);
			for (int y = top.getY() + 2; y >= top.getY() - 7; y--) {
				BlockPos p = new BlockPos(top.getX(), y, top.getZ());
				if (!level.getBlockState(p).isAir()) swap(b, c.caster, p, air, life, true);
			}
			if (i % 2 == 0) {
				Vec3 at = Vec3.atBottomCenterOf(top.above());
				MagicFx.cloud(level, at, dust(surfaceState.isAir() ? Blocks.DIRT.defaultBlockState() : surfaceState), 8, 0.5, 0.8, 0.5, 0.15);
				MagicFx.cloud(level, at, ParticleTypes.DUST_PLUME, 3, 0.4, 0.6, 0.4, 0.08);
			}
		}
		if (!group.isEmpty()) {
			BlockPos any = group.get(group.size() / 2);
			MagicFx.sound(level, Vec3.atCenterOf(any), SoundEvents.DEEPSLATE_BREAK, 1.5F, 0.5F + 0.03F * wave);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Integer top = tops.get(BlockPos.asLong(Mth.floor(e.getX()), 0, Mth.floor(e.getZ())));
			if (top == null || e.getY() > top + 3 || !c.isFoe(e)) continue;
			if (boss(e)) {
				// Большие твари не проваливаются: удар и выброс сразу.
				c.hurt(e, c.amount(20.0F), MagicDamage.EARTH);
				SpellKit.launch(e, 1.0);
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6, false, true), c.caster);
			e.setDeltaMovement(0, -0.8, 0);
			e.hurtMarked = true;
		}
	}

	/** Разлом смыкается: кто внутри — 34 урона и выброс наверх (своих выносит без урона), земля возвращается. */
	private static void closeRift(Cast c, Batch b, Map<Long, Integer> tops, List<BlockPos> edges, AABB box, Vec3 from, Vec3 dir, double len) {
		ServerLevel level = c.level;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Integer top = tops.get(BlockPos.asLong(Mth.floor(e.getX()), 0, Mth.floor(e.getZ())));
			if (top == null || e.getY() >= top + 0.99) continue;
			boolean foe = c.isFoe(e);
			if (foe) c.hurt(e, c.amount(boss(e) ? 20.0F : 34.0F), MagicDamage.EARTH);
			e.teleportTo(e.getX(), top + 1.05, e.getZ());
			if (e.isAlive()) SpellKit.launch(e, foe ? 1.0 : 0.4);
			e.resetFallDistance();
		}
		revert(b, 0, b.list.size(), 0);
		// Разрушение: края разлома остаются щебнем, камни летят в стороны.
		if (Terrain.griefing(level)) {
			RandomSource rnd = level.random;
			for (BlockPos p : edges) {
				if (rnd.nextFloat() >= 0.2F || !earthy(level.getBlockState(p)) || !level.mayInteract(c.caster, p)) continue;
				level.setBlock(p, rnd.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
			}
			for (int i = 0; i < 8 && !edges.isEmpty(); i++) {
				BlockPos p = edges.get(rnd.nextInt(edges.size())).above(2);
				if (!level.getBlockState(p).isAir() || !level.mayInteract(c.caster, p)) continue;
				FallingBlockEntity rock = FallingBlockEntity.fall(level, p, rnd.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState()
						: Blocks.GRAVEL.defaultBlockState());
				rock.disableDrop();
				rock.setDeltaMovement(jitter(rnd, 0.35), 0.5 + rnd.nextDouble() * 0.4, jitter(rnd, 0.35));
			}
		}
		for (double s = 0; s <= len; s += 3) {
			Vec3 p = from.add(dir.scale(s));
			Integer top = tops.get(BlockPos.asLong(Mth.floor(p.x), 0, Mth.floor(p.z)));
			Vec3 at = new Vec3(p.x, top == null ? p.y : top + 1.2, p.z);
			MagicFx.cloud(level, at, ParticleTypes.LARGE_SMOKE, 6, 0.8, 0.5, 0.8, 0.05);
			MagicFx.cloud(level, at, ParticleTypes.DUST_PLUME, 8, 1.0, 0.5, 1.0, 0.1);
			MagicFx.burst(level, at, dust(Blocks.STONE.defaultBlockState()), 10, 0.8, 0.2);
		}
		Vec3 mid = from.add(dir.scale(len / 2));
		MagicFx.soundFar(level, mid, MagicSounds.EXPLOSION_HUGE, 128, 0.7F);
		MagicFx.soundFar(level, mid, MagicSounds.RUMBLE, 96, 0.5F);
		Magic.shake(level, mid, 1.2F, 20, 48);
	}

	// =====================================================================================================
	// Восстание титана
	// =====================================================================================================

	private static boolean titanUpheaval(Cast c) {
		SpellKit.release(c);
		Vec3 at = c.aimGround();
		// Копии — малые эпицентры (два кольца) у следующих скоплений врагов, через 10 тиков друг за другом.
		boolean small = c.copy > 0;
		Zones.later(c, small ? 6 * c.copy : 0, () -> upheaval(c, at, small ? 2 : 3));
		return true;
	}

	private static void upheaval(Cast c, Vec3 at, int rings) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		double big = 4.0 * rings;
		// Три круга (r4/8/12) друг над другом, кольцо за кольцом, с геометрией преобразования и искрами алхимии.
		for (int k = 1; k <= rings; k++) {
			float r = 4.0F * k;
			float lift = 0.1F + 0.4F * (k - 1);
			Zones.later(c, (k - 1) * 3, () -> {
				MagicFx.circle(level, c.spell, at.add(0, lift, 0), UP, r, 8, 50, 16);
				MagicFx.ring(level, at.add(0, lift + 0.1, 0), r, MagicParticles.spark(ALCHEMY, 0.2F, 20), (int) (r * 4), 0.0);
			});
		}
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10 + rnd.nextDouble() * 0.3;
			Vec3 tip = at.add(Math.cos(a) * big, 0.12, Math.sin(a) * big);
			MagicFx.line(level, at.add(0, 0.12, 0), tip, MagicParticles.spark(CRACK, 0.2F, 50), 0.45);
		}
		MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 128, 0.6F);
		Magic.shake(level, at, 0.5F, 20, 32);
		Batch inner = new Batch(level);
		Batch outer = new Batch(level);
		for (int k = 1; k <= rings; k++) {
			int ring = k;
			Zones.later(c, 10 * k, () -> spikeRing(c, at, 4.0 * ring, ring == 1 ? inner : outer, ring));
		}
		Zones.later(c, 50, () -> collapse(c, at, rings == 3 ? 7.0 : 5.0, inner));
		// Шипы осыпаются, при разрушении часть остаётся щебнем.
		revertLater(c, outer, 110, 10, true, 0.15F);
	}

	/** Кольцо каменных копий радиуса {@code r}: 12 урона и подброс всем в ±1,5 блока от кольца. */
	private static void spikeRing(Cast c, Vec3 at, double r, Batch b, int k) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 2, 4, r + 2), LivingEntity::isAlive);
		for (LivingEntity e : near) {
			double d = Math.sqrt(sq(e.getX() - at.x) + sq(e.getZ() - at.z));
			if (Math.abs(d - r) > 1.5 || Math.abs(e.getY() - at.y) > 3.5 || !c.isFoe(e)) continue;
			c.hurt(e, c.amount(12.0F), MagicDamage.EARTH);
			if (!boss(e)) SpellKit.launch(e, 1.1);
		}
		int n = Math.max(8, (int) Math.round(Math.PI * 2 * r / 1.6));
		int cy = Mth.floor(at.y - 0.01);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + k * 0.37;
			double rr = r + jitter(rnd, 0.35);
			int x = Mth.floor(at.x + Math.cos(a) * rr), z = Mth.floor(at.z + Math.sin(a) * rr);
			BlockPos top = surface(level, x, cy + 3, z, 8);
			if (top == null) continue;
			int h = 2 + rnd.nextInt(4);
			if (occupied(near, top.above(), h)) continue;
			spike(b, c.caster, top, h, 400, rnd.nextBoolean() ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState());
			if (i % 2 == 0) {
				MagicFx.burst(level, Vec3.atCenterOf(top.above()), dust(Blocks.STONE.defaultBlockState()), 6, 0.4, 0.15);
				if (rnd.nextInt(3) == 0) MagicFx.burst(level, Vec3.atCenterOf(top.above(h)), MagicParticles.spark(ALCHEMY, 0.2F, 14), 4, 0.3, 0.06);
			}
		}
		MagicFx.shockwave(level, at.add(0, 0.2, 0), UP, (float) r - 1.0F, (float) r + 1.5F, OCHRE, 10);
		MagicFx.soundFar(level, at, MagicSounds.EXPLOSION, 96, 0.5F + 0.12F * k);
		MagicFx.sound(level, at, SoundEvents.POINTED_DRIPSTONE_LAND, 2.0F, 0.5F);
		Magic.shake(level, at, 0.7F + 0.2F * k, 10, 32);
	}

	/** Обвал в центре: 16 урона, тянет вниз, Замедление III; при разрушении — воронка, без него — временный щебень. */
	private static void collapse(Cast c, Vec3 at, double r, Batch inner) {
		ServerLevel level = c.level;
		revert(inner, 0, inner.list.size(), 0);
		for (LivingEntity e : c.foesAround(at.add(0, 1, 0), r)) {
			c.hurt(e, c.amount(16.0F), MagicDamage.EARTH);
			Vec3 d = at.subtract(e.position());
			Vec3 flat = new Vec3(d.x, 0, d.z);
			if (flat.lengthSqr() > 1.0E-4) flat = flat.normalize().scale(0.4);
			e.setDeltaMovement(flat.x, -0.8, flat.z);
			e.hurtMarked = true;
			SpellKit.slow(c, e, 60, 2);
		}
		MagicFx.dome(level, at, (float) r + 2.0F, OCHRE, SAND, 40);
		MagicFx.shockwave(level, at.add(0, 0.3, 0), UP, 1.0F, (float) r * 2.0F, SAND, 18);
		MagicFx.cloud(level, at.add(0, 6, 0), new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()), 60, r, 2, r, 0);
		MagicFx.cloud(level, at.add(0, 1, 0), ParticleTypes.CAMPFIRE_COSY_SMOKE, 30, r * 0.6, 0.5, r * 0.6, 0.02);
		MagicFx.burst(level, at.add(0, 0.5, 0), dust(Blocks.STONE.defaultBlockState()), 80, r * 0.5, 0.3);
		MagicFx.soundFar(level, at, MagicSounds.EXPLOSION_HUGE, 160, 0.7F);
		Magic.shake(level, at, 1.2F, 20, 32);
		if (Terrain.griefing(level)) {
			Terrain.crater(c, at, r, 5.0 / r, Terrain.Rim.RUBBLE);
		} else {
			Batch rubble = new Batch(level);
			RandomSource rnd = level.random;
			int cx = Mth.floor(at.x), cz = Mth.floor(at.z), cy = Mth.floor(at.y - 0.01);
			int ri = Mth.ceil(r - 1);
			for (int dx = -ri; dx <= ri; dx++) {
				for (int dz = -ri; dz <= ri; dz++) {
					if (dx * dx + dz * dz > ri * ri || rnd.nextFloat() > 0.45F) continue;
					BlockPos top = surface(level, cx + dx, cy + 2, cz + dz, 5);
					if (top == null) continue;
					BlockState s = switch (rnd.nextInt(3)) {
						case 0 -> Blocks.GRAVEL.defaultBlockState();
						case 1 -> Blocks.COBBLESTONE.defaultBlockState();
						default -> Blocks.COARSE_DIRT.defaultBlockState();
					};
					swap(rubble, c.caster, top, s, 260, true);
				}
			}
			revertLater(c, rubble, 200, 10, false, 0);
		}
	}

	// =====================================================================================================
	// Пробуждение великой горы
	// =====================================================================================================

	private static boolean mountainAwakening(Cast c) {
		SpellKit.release(c);
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		Vec3 at = c.aimGround();
		BlockPos base = BlockPos.containing(at.x, at.y - 0.01, at.z);
		MagicFx.soundFar(level, at, MagicSounds.RELEASE_ULTIMATE, 256, 0.8F);
		MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 192, 0.5F);
		// Пять кругов загораются от центра наружу, по земле бегут огненные трещины.
		for (int k = 0; k < 5; k++) {
			float r = 4.0F + 4.0F * k;
			float lift = 0.1F + 0.25F * k;
			Zones.later(c, k * 3, () -> MagicFx.circle(level, c.spell, at.add(0, lift, 0), UP, r, 8, 80, 20));
		}
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16 + rnd.nextDouble() * 0.2;
			MagicFx.line(level, at.add(0, 0.15, 0), at.add(Math.cos(a) * 20, 0.15, Math.sin(a) * 20), MagicParticles.spark(CRACK, 0.25F, 60), 0.5);
		}
		MagicFx.flash(level, at, 96, 0.5F, SAND, 12);
		Magic.shake(level, at, 1.0F, 30, 64);
		// Земля встаёт на дыбы: внутри — 40 урона, на краю — отбрасывает наружу.
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(20, 6, 20), LivingEntity::isAlive)) {
			double d = Math.sqrt(sq(e.getX() - at.x) + sq(e.getZ() - at.z));
			if (d > 20 || !c.isFoe(e)) continue;
			if (d <= MOUNTAIN_R) {
				c.hurt(e, c.amount(40.0F), MagicDamage.EARTH);
			} else {
				Cast.knock(e, at, 1.5, 0.4);
			}
		}
		// Зазубренный край горы: по 10° своя неровность.
		double[] jag = new double[36];
		for (int i = 0; i < jag.length; i++) jag[i] = jitter(rnd, 0.9);
		Batch m = new Batch(level);
		for (int y = 0; y < MOUNTAIN_H; y++) {
			int layer = y;
			Zones.later(c, 1 + y, () -> mountainLayer(c, m, base, layer, jag));
		}
		Vec3 peak = Vec3.atBottomCenterOf(base.above(MOUNTAIN_H + 1));
		Zones.later(c, 32, () -> {
			MagicFx.pillar(level, peak, 2.5F, 40.0F, OCHRE, SAND, 28);
			MagicFx.circle(level, c.spell, peak.add(0, 10, 0), SpellKit.DOWN, 8.0F, 3, 1.0F, 10, 20, 14);
			MagicFx.flash(level, peak, 128, 0.4F, SAND, 10);
			MagicFx.soundFar(level, peak, MagicSounds.PILLAR, 192, 0.6F);
		});
		// Склоны покрываются светящимися трещинами — гора вот-вот рухнет.
		Zones.later(c, 45, () -> {
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				Vec3 foot = Vec3.atBottomCenterOf(base.above()).add(Math.cos(a) * MOUNTAIN_R, 0.5, Math.sin(a) * MOUNTAIN_R);
				MagicFx.line(level, peak.add(0, -0.5, 0), foot, MagicParticles.spark(CRACK, 0.35F, 30), 0.6);
			}
			MagicFx.soundFar(level, peak, MagicSounds.SHATTER, 160, 0.5F);
			Magic.shake(level, at, 1.4F, 16, 64);
		});
		Zones.later(c, 60, () -> collapseMountain(c, at, base, m));
		return true;
	}

	/** Слой горы: диск радиуса 12·(1 − y/30) с неровным краем; существ на нём поднимает вместе с горой. */
	private static void mountainLayer(Cast c, Batch m, BlockPos base, int y, double[] jag) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		double r = MOUNTAIN_R * (1.0 - y / (double) MOUNTAIN_H);
		int yy = base.getY() + 1 + y;
		int ri = Mth.ceil(r + 1);
		int life = 60 + 40;
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > edgeOf(r, dx, dz, jag)) continue;
				BlockPos p = new BlockPos(base.getX() + dx, yy, base.getZ() + dz);
				swap(m, c.caster, p, rock(rnd, y, d >= r - 1.6), life, false);
				if (y == 0) {
					// Над низиной гора не висит в воздухе: подсыпаем до земли.
					for (int k = 1; k <= 8; k++) {
						BlockPos q = p.below(k);
						if (!soft(level.getBlockState(q))) break;
						swap(m, c.caster, q, Blocks.ANDESITE.defaultBlockState(), life, false);
					}
				}
			}
		}
		// Поднять всех, кто стоит на новом слое.
		AABB box = new AABB(base).inflate(MOUNTAIN_R + 1, 0, MOUNTAIN_R + 1).expandTowards(0, MOUNTAIN_H + 4, 0).inflate(0, 2, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double dx = e.getX() - (base.getX() + 0.5), dz = e.getZ() - (base.getZ() + 0.5);
			if (Math.sqrt(dx * dx + dz * dz) > edgeOf(r, Mth.floor(dx + 0.5), Mth.floor(dz + 0.5), jag)) continue;
			if (e.getY() >= yy + 1 || e.getY() < yy - 3) continue;
			e.teleportTo(e.getX(), yy + 1, e.getZ());
			e.resetFallDistance();
			if (c.isFoe(e) && !boss(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6, false, true), c.caster);
		}
		// Пыль по краю слоя, искры алхимии, скрежет камня.
		int points = Math.max(6, (int) (r * 2));
		BlockParticleOption dust = dust(rock(rnd, y, true));
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points + y * 0.4;
			Vec3 p = new Vec3(base.getX() + 0.5 + Math.cos(a) * (r + 0.6), yy + 0.5, base.getZ() + 0.5 + Math.sin(a) * (r + 0.6));
			MagicFx.one(level, p, dust, Math.cos(a) * 0.2, 0.15, Math.sin(a) * 0.2);
		}
		if (y % 3 == 0) {
			Vec3 at = new Vec3(base.getX() + 0.5, yy + 0.5, base.getZ() + 0.5);
			MagicFx.ring(level, at, r + 0.8, MagicParticles.spark(ALCHEMY, 0.25F, 16), Math.max(8, (int) (r * 3)), 0.05);
			MagicFx.cloud(level, at, ParticleTypes.DUST_PLUME, 20, r * 0.7, 0.5, r * 0.7, 0.08);
		}
		if (y % 6 == 0) {
			MagicFx.soundFar(level, Vec3.atCenterOf(base.above(y)), MagicSounds.RUMBLE, 192, 0.5F + 0.02F * y);
			MagicFx.soundFar(level, Vec3.atCenterOf(base.above(y)), SoundEvents.DEEPSLATE_BREAK, 64, 0.4F);
			Magic.shake(level, Vec3.atCenterOf(base), 0.8F, 14, 64);
		}
	}

	/** Радиус края горы в направлении (dx, dz): неровный, неровность меньше к вершине. */
	private static double edgeOf(double r, int dx, int dz, double[] jag) {
		double a = Math.atan2(dz, dx);
		int i = Math.floorMod((int) Math.floor(a / (Math.PI * 2) * jag.length), jag.length);
		return r + jag[i] * (r / MOUNTAIN_R) + 0.3;
	}

	/** Камень горы: у подножия андезит и булыжник, выше камень и глубинный сланец, на вершине снежная шапка. */
	private static BlockState rock(RandomSource rnd, int y, boolean shell) {
		if (y >= MOUNTAIN_H - 5 && shell) return Blocks.SNOW_BLOCK.defaultBlockState();
		float roll = rnd.nextFloat();
		if (y < 10) {
			return roll < 0.4F ? Blocks.ANDESITE.defaultBlockState() : roll < 0.8F ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		}
		if (y < 22) {
			return roll < 0.5F ? Blocks.STONE.defaultBlockState() : roll < 0.8F ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
		}
		return roll < 0.6F ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
	}

	/** Обвал горы: до 90 урона (к краю r20 — 40%), лавина валунов, купол пыли, ударная волна, воронка при разрушении. */
	private static void collapseMountain(Cast c, Vec3 at, BlockPos base, Batch m) {
		ServerLevel level = c.level;
		RandomSource rnd = level.random;
		AABB box = new AABB(base).inflate(20, 0, 20).expandTowards(0, MOUNTAIN_H + 6, 0).inflate(0, 4, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double d = Math.sqrt(sq(e.getX() - at.x) + sq(e.getZ() - at.z));
			if (d > 20) continue;
			if (c.isAlly(e)) {
				// Своих гора не роняет насмерть.
				e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, true), c.caster);
				continue;
			}
			if (!c.isFoe(e)) continue;
			float k = d <= MOUNTAIN_R ? 1.0F : (float) (1.0 - 0.6 * (d - MOUNTAIN_R) / (20 - MOUNTAIN_R));
			float dmg = c.amount(90.0F) * k;
			if (boss(e)) dmg = Math.min(dmg, 70.0F);
			if (e instanceof Player) dmg = Math.min(dmg, 30.0F);
			c.hurt(e, dmg, MagicDamage.EARTH);
			SpellKit.slow(c, e, 100, 3);
		}
		// Гора рушится сверху вниз за 10 тиков.
		revertLater(c, m, 0, 10, true, 0);
		// Лавина валунов с склонов.
		for (int i = 0; i < 32; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int y = rnd.nextInt(MOUNTAIN_H - 4);
			double r = MOUNTAIN_R * (1.0 - y / (double) MOUNTAIN_H);
			Vec3 from = new Vec3(base.getX() + 0.5 + Math.cos(a) * r, base.getY() + 1.5 + y, base.getZ() + 0.5 + Math.sin(a) * r);
			double out = 0.3 + rnd.nextDouble() * 0.6;
			Vec3 vel = new Vec3(Math.cos(a) * out, 0.2 + rnd.nextDouble() * 0.6, Math.sin(a) * out);
			SpellProjectile.spawn(c, from, vel).gravity(0.06).scale(1.5F + rnd.nextFloat() * 1.5F).life(100);
		}
		for (int y = 0; y < MOUNTAIN_H; y += 3) {
			Vec3 p = Vec3.atCenterOf(base.above(y + 1));
			double r = MOUNTAIN_R * (1.0 - y / (double) MOUNTAIN_H);
			MagicFx.cloud(level, p, dust(Blocks.STONE.defaultBlockState()), 40, r * 0.6, 1.0, r * 0.6, 0.3);
		}
		MagicFx.dome(level, at, 24.0F, OCHRE, SAND, 70);
		MagicFx.shockwave(level, at.add(0, 0.4, 0), UP, 2.0F, 40.0F, SAND, 30);
		MagicFx.sphere(level, at.add(0, 4, 0), 3.0F, 28.0F, OCHRE, SAND, 26);
		MagicFx.cloud(level, at.add(0, 2, 0), ParticleTypes.CAMPFIRE_COSY_SMOKE, 80, 14, 3, 14, 0.03);
		MagicFx.cloud(level, at.add(0, 1, 0), ParticleTypes.DUST_PLUME, 120, 16, 1.5, 16, 0.15);
		MagicFx.flash(level, at, 128, 0.7F, SAND, 16);
		MagicFx.soundFar(level, at, MagicSounds.EXPLOSION_HUGE, 256, 0.55F);
		MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 256, 0.4F);
		Magic.shake(level, at, 2.5F, 40, 80);
		Zones.later(c, 20, () -> MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 192, 0.45F));
		Zones.later(c, 45, () -> MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 128, 0.4F));
		// Разрушение: воронка r12 глубиной 8 и холмы щебня по краю.
		Zones.later(c, 14, () -> {
			if (!Terrain.griefing(level)) return;
			Terrain.crater(c, at, MOUNTAIN_R, 8.0 / MOUNTAIN_R, Terrain.Rim.RUBBLE);
			for (int i = 0; i < 28; i++) {
				double a = Math.PI * 2 * i / 28 + jitter(rnd, 0.1);
				double d = MOUNTAIN_R + 1.5 + rnd.nextDouble() * 3.0;
				int x = Mth.floor(at.x + Math.cos(a) * d), z = Mth.floor(at.z + Math.sin(a) * d);
				BlockPos top = surface(level, x, base.getY() + 4, z, 10);
				if (top == null) continue;
				int h = 1 + rnd.nextInt(3);
				for (int k = 1; k <= h; k++) {
					BlockPos p = top.above(k);
					if (!soft(level.getBlockState(p)) || !level.mayInteract(c.caster, p) || isTemp(level, p)) break;
					BlockState s = switch (rnd.nextInt(3)) {
						case 0 -> Blocks.ANDESITE.defaultBlockState();
						case 1 -> Blocks.GRAVEL.defaultBlockState();
						default -> Blocks.COBBLESTONE.defaultBlockState();
					};
					level.setBlock(p, s, Block.UPDATE_ALL);
				}
			}
		});
	}

	/** Валун обвала: пыль и стук; врага задевает на 6. */
	private static void boulderHit(Cast c, SpellProjectile p, HitResult result) {
		Vec3 at = result.getLocation();
		if (result instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e && c.isFoe(e)) {
			c.hurt(e, c.amount(6.0F), MagicDamage.EARTH, p);
		}
		MagicFx.burst(c.level, at, dust(Blocks.COBBLESTONE.defaultBlockState()), 14, 0.4, 0.15);
		MagicFx.cloud(c.level, at, ParticleTypes.DUST_PLUME, 4, 0.4, 0.2, 0.4, 0.05);
		if (c.level.random.nextInt(3) == 0) MagicFx.sound(c.level, at, SoundEvents.STONE_BREAK, 1.2F, 0.5F);
	}

	// =====================================================================================================
	// Временные блоки земли
	// =====================================================================================================

	/** Временный блок: что было, что поставили, до какого времени (запасной срок) и чьё. */
	private record Swap(ServerLevel level, BlockState original, BlockState placed, long until, Batch owner) {}

	/** Блоки одного заклинания: возвращаются вместе или по частям («осыпаются»). */
	private static final class Batch {
		final ServerLevel level;
		final List<BlockPos> list = new ArrayList<>();

		Batch(ServerLevel level) {
			this.level = level;
		}
	}

	private static final Map<GlobalPos, Swap> SWAPS = new HashMap<>();

	/**
	 * Поставить временный блок. Ставится в воздух, траву, цветы, снег, воду и лаву; если {@code natural} — ещё и
	 * вместо природных блоков (земля, песок, гравий, камень, руда), даже при выключенном разрушении: они вернутся.
	 * Хранилища, блоки с содержимым, неразрушимые и защищённые от игрока места не трогаются.
	 */
	private static boolean swap(Batch b, ServerPlayer caster, BlockPos pos, BlockState state, int ticks, boolean natural) {
		ServerLevel level = b.level;
		BlockPos p = pos.immutable();
		GlobalPos key = GlobalPos.of(level.dimension(), p);
		BlockState cur = level.getBlockState(p);
		Swap old = SWAPS.get(key);
		if (old != null) {
			if (cur == old.placed) {
				if (cur != state) level.setBlock(p, state, QUIET);
				SWAPS.put(key, new Swap(level, old.original, state, Math.max(old.until, level.getGameTime() + ticks), b));
				b.list.add(p);
				return true;
			}
			SWAPS.remove(key);
		}
		if (cur == state || Terrain.isTemp(p)) return false;
		boolean soft = soft(cur);
		if (state.isAir() && !cur.getFluidState().isEmpty()) return false;
		if (!soft && !(natural && earthy(cur) && !cur.hasBlockEntity() && cur.getDestroySpeed(level, p) >= 0)) return false;
		if (caster != null && !level.mayInteract(caster, p)) return false;
		BlockState original = !cur.getFluidState().isEmpty() && !cur.getFluidState().isSource() ? Blocks.AIR.defaultBlockState() : cur;
		level.setBlock(p, state, QUIET);
		SWAPS.put(key, new Swap(level, original, state, level.getGameTime() + ticks, b));
		b.list.add(p);
		return true;
	}

	/**
	 * Вернуть блоки партии с номерами [{@code lo}, {@code hi}) — с конца (что ставили последним, уходит первым).
	 * {@code rubble} — доля нижних блоков, что при разрушении остаётся щебнем навсегда.
	 */
	private static void revert(Batch b, int lo, int hi, float rubble) {
		ServerLevel level = b.level;
		int step = Math.max(1, (hi - lo) / 40);
		boolean grief = rubble > 0 && Terrain.griefing(level);
		for (int i = hi - 1; i >= lo; i--) {
			BlockPos p = b.list.get(i);
			GlobalPos key = GlobalPos.of(level.dimension(), p);
			Swap s = SWAPS.get(key);
			if (s == null || s.owner != b) continue;
			SWAPS.remove(key);
			BlockState now = level.getBlockState(p);
			if (!restorable(now, s)) continue;
			BlockState put = s.original;
			if (grief && s.original.isAir() && !now.isAir() && level.random.nextFloat() < rubble
					&& !SWAPS.containsKey(GlobalPos.of(level.dimension(), p.below())) && !level.getBlockState(p.below()).isAir()) {
				put = level.random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
			}
			level.setBlock(p, put, Block.UPDATE_ALL);
			if ((i - lo) % step == 0 && !now.isAir()) MagicFx.burst(level, Vec3.atCenterOf(p), dust(now), 4, 0.3, 0.05);
		}
	}

	/** Можно ли вернуть прежний блок: стоит наш блок, пусто (сломали, наш воздух) или натекла вода. */
	private static boolean restorable(BlockState now, Swap s) {
		return now == s.placed || now.isAir() || !now.getFluidState().isEmpty() && !now.getFluidState().isSource();
	}

	/**
	 * Через {@code delay} тиков вернуть партию за {@code ticks} тиков: {@code fromEnd} — с последних поставленных
	 * (стена осыпается сверху, мост — с дальнего конца), иначе — с первых (трясина высыхает от центра).
	 */
	private static void revertLater(Cast c, Batch b, int delay, int ticks, boolean fromEnd, float rubble) {
		Zones.later(c, delay, () -> {
			int n = b.list.size();
			int per = Math.max(1, Mth.ceil(n / (float) Math.max(1, ticks)));
			for (int k = 0; k * per < n; k++) {
				int lo = fromEnd ? Math.max(0, n - (k + 1) * per) : k * per;
				int hi = fromEnd ? n - k * per : Math.min(n, (k + 1) * per);
				Zones.later(c, k, () -> revert(b, lo, hi, rubble));
			}
		});
	}

	/** Сюда можно поставить временный блок без разрушения: воздух, трава, цветы, снег, огонь, вода, лава. */
	private static boolean soft(BlockState s) {
		return s.isAir() || s.canBeReplaced() || s.is(BlockTags.FIRE) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
				|| s.is(Blocks.SNOW);
	}

	/** Природная земля и камень: их земля магией сдвигает и возвращает. */
	private static boolean earthy(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD)
				|| s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.CLAY)
				|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA)
				|| s.is(BlockTags.NYLIUM) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.PACKED_MUD)
				|| s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.COBBLED_DEEPSLATE)
				|| s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
				|| s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES);
	}

	/** Каменный шип высотой {@code h} на столбце {@code top}: тело из камня, сверху острый сталагмит. */
	private static void spike(Batch b, ServerPlayer caster, BlockPos top, int h, int ticks, BlockState body) {
		for (int i = 1; i <= h; i++) {
			BlockState s;
			if (i == h) s = dripstone(DripstoneThickness.TIP);
			else if (i == h - 1 && h >= 3) s = dripstone(DripstoneThickness.FRUSTUM);
			else s = body;
			if (!swap(b, caster, top.above(i), s, ticks, false)) return;
		}
	}

	private static BlockState dripstone(DripstoneThickness thickness) {
		return Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
				.setValue(PointedDripstoneBlock.THICKNESS, thickness);
	}

	// --- Для самопроверки ---

	/** Сколько временных блоков земли стоит сейчас. */
	public static int tempCount() {
		return SWAPS.size();
	}

	public static boolean isTemp(ServerLevel level, BlockPos pos) {
		return SWAPS.containsKey(GlobalPos.of(level.dimension(), pos));
	}

	/** Вернуть все временные блоки земли сразу (остановка сервера, самопроверка). */
	public static void restoreAll() {
		List<Map.Entry<GlobalPos, Swap>> all = new ArrayList<>(SWAPS.entrySet());
		SWAPS.clear();
		// С конца: верхние блоки шипов и гор уходят раньше опор.
		for (int i = all.size() - 1; i >= 0; i--) {
			Swap s = all.get(i).getValue();
			BlockPos p = all.get(i).getKey().pos();
			if (restorable(s.level.getBlockState(p), s)) s.level.setBlock(p, s.original, Block.UPDATE_ALL);
		}
	}

	// =====================================================================================================
	// Сервер: срок временных блоков, каменная кожа, удары колосса, дрожь земли при зарядке
	// =====================================================================================================

	private static boolean hooked;
	private static final Map<UUID, Long> CHARGE_START = new HashMap<>();

	private static void hooks() {
		if (hooked) return;
		hooked = true;
		ServerTickEvents.END_SERVER_TICK.register(EarthSpells::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			restoreAll();
			CHARGE_START.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(EarthSpells::afterDamage);
		// Временный блок, сломанный игроком, возвращается к прежнему — без добычи.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
			if (!(world instanceof ServerLevel level)) return true;
			Swap s = SWAPS.remove(GlobalPos.of(level.dimension(), pos));
			if (s == null) return true;
			level.setBlock(pos, s.original, Block.UPDATE_ALL);
			MagicFx.burst(level, Vec3.atCenterOf(pos), dust(state), 10, 0.25, 0.05);
			return false;
		});
	}

	private static void serverTick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 == 0) {
			// Запасной срок: если отложенный возврат потерялся (зоны сброшены), блоки всё равно вернутся.
			if (!SWAPS.isEmpty()) {
				Iterator<Map.Entry<GlobalPos, Swap>> it = SWAPS.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<GlobalPos, Swap> e = it.next();
					Swap s = e.getValue();
					if (s.level.getGameTime() <= s.until + 100) continue;
					it.remove();
					if (restorable(s.level.getBlockState(e.getKey().pos()), s)) s.level.setBlock(e.getKey().pos(), s.original, Block.UPDATE_ALL);
				}
			}
			for (LivingEntity e : List.copyOf(GRIPPED)) {
				if (e.isAlive() && e.hasEffect(MagicEffects.STONESKIN)) continue;
				AttributeInstance kb = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (kb != null) kb.removeModifier(GRIP);
				GRIPPED.remove(e);
			}
		}
		if (now % 10 == 0) tremors(server, now);
	}

	/** Пока маг заряжает землетрясение, восстание титана или пробуждение горы, земля у круга дрожит всё сильнее. */
	private static void tremors(MinecraftServer server, int now) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String id = p.getAttached(Magic.CASTING);
			if (id == null || !TREMORS.contains(id)) {
				CHARGE_START.remove(p.getUUID());
				continue;
			}
			Spell s = Spells.get(id);
			if (s == null) continue;
			long start = CHARGE_START.computeIfAbsent(p.getUUID(), k -> (long) now);
			float f = Math.min(1.0F, (now - start) / (float) Math.max(1, s.chargeTicks));
			ServerLevel level = p.level();
			Vec3 at = s.anchor == Anchor.FEET ? p.position() : Targeting.aim(level, p, s.range).ground();
			float strength = s.tier >= 7 ? 0.3F + 0.7F * f : s.tier >= 6 ? 0.2F + 0.4F * f : 0.1F + 0.25F * f;
			Magic.shake(level, at, strength, 14, s.tier >= 7 ? 48 : 24);
			double r = s.circleRadius();
			// Камешки поднимаются над землёй, оседает пыль.
			MagicFx.cloud(level, at.add(0, 0.3, 0), dust(groundState(level, at)), (int) (6 + 18 * f), r * 0.6, 0.1, r * 0.6, 0.05);
			MagicFx.cloud(level, at.add(0, 0.2, 0), ParticleTypes.DUST_PLUME, (int) (2 + 6 * f), r * 0.5, 0.1, r * 0.5, 0.03);
			if ((now - start) % 40 == 0) MagicFx.soundFar(level, at, MagicSounds.RUMBLE, 32 + 8 * s.tier, 0.5F + 0.3F * f);
		}
	}

	private static void afterDamage(LivingEntity e, DamageSource source, float base, float taken, boolean blocked) {
		if (!(e.level() instanceof ServerLevel level)) return;
		// Каменная кожа: первый тяжёлый (больше 6) удар раз в 5 с слабее на 2.
		if (taken > 6.0F && e.isAlive() && e.hasEffect(MagicEffects.STONESKIN)) {
			long now = level.getGameTime();
			Long last = SKIN_PROC.get(e);
			if (last == null || now - last >= 100 || now < last) {
				SKIN_PROC.put(e, now);
				e.heal(2.0F);
				MagicFx.burst(level, Cast.center(e), dust(Blocks.STONE.defaultBlockState()), 12, 0.3, 0.1);
				MagicFx.sound(level, e.position(), SoundEvents.STONE_HIT, 0.9F, 0.7F);
			}
		}
		// Удар колосса сотрясает землю вокруг цели.
		if (!slamming && source.getEntity() instanceof IronGolem g && g.getTags().contains(COLOSSUS) && source.getDirectEntity() == g) {
			slam(level, g, e);
		}
	}

	// =====================================================================================================
	// Мелочи
	// =====================================================================================================

	private static BlockParticleOption dust(BlockState s) {
		return new BlockParticleOption(ParticleTypes.BLOCK, s);
	}

	/** Блок под точкой (для пыли нужного цвета); в воздухе — земля. */
	private static BlockState groundState(ServerLevel level, Vec3 p) {
		BlockState s = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5, p.z));
		return s.isAir() || !s.getFluidState().isEmpty() ? Blocks.DIRT.defaultBlockState() : s;
	}

	/** Верхний твёрдый блок столбца (x, z), если искать от {@code yTop} вниз не дальше {@code depth}; null — нет. */
	private static BlockPos surface(ServerLevel level, int x, int yTop, int z, int depth) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, yTop, z);
		for (int i = 0; i <= depth; i++) {
			BlockState s = level.getBlockState(m);
			if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, m).isEmpty() && !soft(s)) return m.immutable();
			m.move(0, -1, 0);
		}
		return null;
	}

	/** Стоит ли на земле (летящих и парящих волны не задевают). */
	private static boolean grounded(LivingEntity e) {
		if (e.onGround()) return true;
		if (e.isNoGravity() && !(e instanceof Mob m && m.isNoAi())) return false;
		BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.25, e.getZ());
		return !e.level().getBlockState(below).getCollisionShape(e.level(), below).isEmpty();
	}

	/** Боссы: урон получают, но не проваливаются, не вязнут и не взлетают. */
	private static boolean boss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	/** Занят ли столбец {@code h} блоков над {@code from} кем-то (чтобы не замуровать в шип). */
	private static boolean occupied(ServerLevel level, BlockPos from, int h) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(from).expandTowards(0, h - 1, 0), LivingEntity::isAlive).isEmpty();
	}

	private static boolean occupied(List<LivingEntity> near, BlockPos from, int h) {
		AABB column = new AABB(from).expandTowards(0, h - 1, 0);
		for (LivingEntity e : near) if (e.isAlive() && e.getBoundingBox().intersects(column)) return true;
		return false;
	}

	private static Vec3 flat(Vec3 look) {
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static double base(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a) {
		AttributeInstance i = e.getAttribute(a);
		return i == null ? 0 : i.getBaseValue();
	}

	private static void setBase(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v) {
		AttributeInstance i = e.getAttribute(a);
		if (i != null) i.setBaseValue(v);
	}

	private static double jitter(RandomSource rnd, double s) {
		return (rnd.nextDouble() - 0.5) * 2 * s;
	}

	private static double sq(double v) {
		return v * v;
	}
}
