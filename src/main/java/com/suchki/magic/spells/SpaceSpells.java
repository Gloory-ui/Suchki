package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.SPACE;

import com.suchki.Suchki;
import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicRenderColors;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minds;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Пространство: телекинез, скачки и порталы, точки притяжения и отталкивания, изгнание в иное измерение,
 * остановка времени, разрез реальности и рождение каменной луны. Урон стихии — пустотой ({@link MagicDamage#VOID}).
 */
public final class SpaceSpells {
	/** Бирюза телекинеза и возвращения. */
	static final int TEAL = 0x3FD8D0;
	/** Бледно-фиолетовые двери. */
	static final int VIOLET = 0xB89CFF;
	static final int VIOLET_LIGHT = 0xF0E6FF;
	/** Лазурь — притяжение. */
	static final int BLUE = 0x2E6BFF;
	static final int BLUE_LIGHT = 0xC8DCFF;
	/** Багрянец — отталкивание. */
	static final int RED = 0xFF2238;
	static final int RED_LIGHT = 0xFFC2C8;
	/** Тёмная бирюза вихря измерений. */
	static final int SWIRL = 0x1F8C8C;
	/** Пустота за разрезом и ядро луны. */
	static final int ABYSS = 0x0A0418;
	/** Золото часов и серо-голубое остановленное время. */
	static final int CLOCK = 0xFFD27A;
	static final int STILL = 0x9AA8C8;

	private SpaceSpells() {}

	public static void register() {
		hooks();

		// --- Начальный ---
		Spell.of("telekinesis", SPACE, 1, CHANNEL).mana(6).cooldown(10).range(32).icon("lead").loop(MagicSounds.LOOP_VOID)
				.channel(SpaceSpells::telekinesis)
				.register();

		// --- Средний ---
		Spell.of("blink", SPACE, 2, SELF).anchor(Anchor.TARGET).circle(1.2F).mana(18).charge(12).cooldown(40).range(32).multicast(4)
				.icon("ender_pearl")
				.action(SpaceSpells::blink)
				.register();

		// --- Продвинутый ---
		Spell.of("recall", SPACE, 3, RITUAL).layers(2).mana(40).charge(28).cooldown(600).range(8).icon("recovery_compass")
				.action(SpaceSpells::recall)
				.register();
		Spell.of("door_crossing", SPACE, 3, BOLT).anchor(Anchor.TARGET).circle(1.8F).mana(38).charge(22).cooldown(200).range(128)
				.icon("oak_door")
				.action(SpaceSpells::doorCrossing)
				.register();

		// --- Святой ---
		Spell.of("lapse_blue", SPACE, 4, BOLT).anchor(Anchor.TARGET).layers(2).mana(55).charge(28).cooldown(240).range(96).destroys()
				.icon("heart_of_the_sea")
				.action(SpaceSpells::lapseBlue)
				.register();

		// --- Королевский ---
		Spell.of("reversal_red", SPACE, 5, BOLT).anchor(Anchor.HAND).layers(2).mana(95).charge(40).cooldown(600).range(64).destroys()
				.icon("redstone_block")
				.action(SpaceSpells::reversalRed)
				.register();
		Spell.of("kamui", SPACE, 5, BOLT).anchor(Anchor.TARGET).layers(2).circle(3.0F).mana(90).charge(44).cooldown(600).range(96)
				.icon("ender_eye")
				.action(SpaceSpells::kamui)
				.register();

		// --- Императорский ---
		Spell.of("time_stop", SPACE, 6, SELF).anchor(Anchor.FEET).layers(3).circle(3.5F).mana(170).charge(70).cooldown(3600).range(48)
				.multicast(4).icon("clock")
				.action(SpaceSpells::timeStop)
				.register();
		Spell.of("reality_slash", SPACE, 6, BOLT).anchor(Anchor.LINE).layers(7).circle(0.8F).mana(155).charge(64).cooldown(1800).range(64)
				.destroys().icon("amethyst_shard")
				.action(SpaceSpells::realitySlash)
				.register();

		// --- Божественный ---
		Spell.of("planetary_devastation", SPACE, 7, RITUAL).anchor(Anchor.SKY).layers(3).circle(12.0F).sky(24.0F).mana(300).charge(140)
				.cooldown(9000).range(128).destroys().icon("crying_obsidian")
				.action(SpaceSpells::planetaryDevastation)
				.register();
	}

	// =====================================================================================================
	// Длань Владыки (telekinesis): поток тянет предметы, держит мелкого моба, на отпускании — бросок
	// =====================================================================================================

	/** Хватка одного мага: кого держим, зона, которая ждёт отпускания, и когда поток кормили. */
	private static final class Grip {
		final List<LivingEntity> held = new ArrayList<>();
		long fedAt;
		Vec3 look = new Vec3(0, 0, 1);
	}

	private static final Map<UUID, Grip> GRIPS = new HashMap<>();

	private static void telekinesis(Cast c, int t) {
		// Копии мультикаста не тянут по отдельности: каждая добавляет главной хватке +1 моба и +2 к радиусу.
		if (c.copy > 0) return;
		UUID id = c.caster.getUUID();
		Grip g = GRIPS.get(id);
		if (g == null || t == 1) {
			if (g != null) release(c, g);
			g = new Grip();
			GRIPS.put(id, g);
			Grip grip = g;
			Zones.add(c, c.caster.position(), 0, 72000, (z, age) -> {
				// Поток кончился (маг отпустил ПКМ): бросаем всех, кого держали.
				boolean holding = c.spell.id.equals(c.caster.getAttached(Magic.CASTING)) && c.caster.isAlive()
						&& c.level.getGameTime() - grip.fedAt <= 2;
				if (!holding || GRIPS.get(id) != grip) {
					release(c, grip);
					if (GRIPS.get(id) == grip) GRIPS.remove(id);
					z.stop();
				}
			});
		}
		g.fedAt = c.level.getGameTime();
		Vec3 eye = c.eye();
		Vec3 look = c.look();
		g.look = look;
		double reach = c.spell.range;
		double radius = 3 + 2 * (c.copies - 1);

		// Предметы и опыт вдоль взгляда летят к магу.
		int pulled = 0;
		Vec3 end = eye.add(look.scale(reach));
		for (Entity e : c.level.getEntities(c.caster, new AABB(eye, end).inflate(radius), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			Vec3 to = e.position().subtract(eye);
			double along = to.dot(look);
			if (along < 0 || along > reach || to.subtract(look.scale(along)).lengthSqr() > radius * radius) continue;
			Vec3 pull = eye.subtract(e.position()).normalize().scale(0.45);
			e.setDeltaMovement(pull);
			e.hurtMarked = true;
			if (e instanceof ItemEntity item) item.setNoPickUpDelay();
			if (t % 3 == 0) MagicFx.one(c.level, e.position().add(0, 0.2, 0), MagicParticles.spark(TEAL, 0.15F, 10), 0, 0, 0);
			pulled++;
		}

		g.held.removeIf(e -> !e.isAlive() || e.level() != c.level || e.distanceToSqr(c.caster) > 48 * 48);
		// Предметов нет — невидимая рука хватает мелкое существо под прицелом (и ещё по одному за копию).
		if (g.held.isEmpty() && pulled == 0 && t % 2 == 0) {
			LivingEntity first = c.aimLiving(reach, e -> e instanceof LivingEntity l && grabbable(c, l));
			if (first != null) {
				g.held.add(first);
				for (LivingEntity extra : c.around(Cast.center(first), 4.0, l -> grabbable(c, l) && l != first)) {
					if (g.held.size() >= c.copies) break;
					g.held.add(extra);
				}
				for (LivingEntity e : g.held) {
					MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(TEAL, 0.3F, 14), 12, e.getBbWidth() * 0.5, 0.05);
					MagicFx.sound(c.level, e.position(), MagicSounds.TELEPORT, 0.6F, 1.8F);
				}
				MagicFx.sound(c.level, c.hand(), MagicSounds.GLITCH, 0.4F, 1.6F);
			}
		}
		// Держим в трёх блоках перед собой (несколько — рядком).
		Vec3 right = CircleLayout.right(look);
		int n = g.held.size();
		for (int i = 0; i < n; i++) {
			LivingEntity e = g.held.get(i);
			Vec3 spot = eye.add(look.scale(3.0)).add(right.scale((i - (n - 1) * 0.5) * 1.4)).subtract(0, e.getBbHeight() * 0.5, 0);
			Vec3 v = spot.subtract(e.position()).scale(0.4);
			if (v.length() > 1.0) v = v.normalize();
			e.setDeltaMovement(v);
			e.hurtMarked = true;
			e.resetFallDistance();
			if (t % 3 == 0) {
				MagicFx.one(c.level, Cast.center(e), MagicParticles.ring(TEAL, e.getBbWidth() + 0.5F, 8), look.x, look.y, look.z);
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.mote(MagicRenderColors.lighten(TEAL, 0.5F), 0.1F, 12), 3,
						e.getBbWidth() * 0.6, 0.01);
			}
		}
	}

	private static boolean grabbable(Cast c, LivingEntity l) {
		return l.getBbWidth() <= 1.0F && l.getMaxHealth() <= 30 && !(l instanceof Player) && c.canHit(l) && !c.isAlly(l) && !boss(l);
	}

	/** Бросок всех удерживаемых по взгляду; врезавшись в блок или существо, получают 4 урона пустотой. */
	private static void release(Cast c, Grip g) {
		if (g.held.isEmpty()) return;
		Vec3 look = c.caster.isAlive() ? c.look() : g.look;
		for (LivingEntity e : List.copyOf(g.held)) {
			if (!e.isAlive()) continue;
			e.setDeltaMovement(look.scale(1.8).add(0, 0.2, 0));
			e.hurtMarked = true;
			MagicFx.sound(c.level, e.position(), MagicSounds.GUST, 0.8F, 1.3F);
			Zones.add(c, e.position(), 0, 30, (z, age) -> {
				if (!e.isAlive()) {
					z.stop();
					return;
				}
				MagicFx.one(c.level, Cast.center(e), MagicParticles.spark(TEAL, 0.3F, 10), 0, 0, 0);
				if (age < 2) return;
				LivingEntity other = null;
				for (LivingEntity o : c.around(Cast.center(e), 1.5, o -> o != e && c.isFoe(o) && o.getBoundingBox().inflate(0.3).intersects(e.getBoundingBox()))) {
					other = o;
					break;
				}
				if (e.horizontalCollision || e.verticalCollision || other != null) {
					c.hurt(e, c.amount(4.0F), MagicDamage.VOID);
					if (other != null) c.hurt(other, c.amount(4.0F), MagicDamage.VOID);
					SpellKit.impact(c, Cast.center(e), 0.8F);
					z.stop();
				}
			});
		}
		g.held.clear();
	}

	// =====================================================================================================
	// Скачок Пространства (blink)
	// =====================================================================================================

	/** Откуда маг прыгнул в последний раз — там копии мультикаста оставляют двойников. */
	private static final Map<UUID, Vec3> DEPARTURES = new HashMap<>();

	private static boolean blink(Cast c) {
		if (c.copy > 0) return afterimage(c);
		double max = Math.min(32.0, 16.0 * c.power);
		Vec3 eye = c.eye();
		Targeting.Aim aim = c.target();
		Vec3 dir = aim.point().subtract(eye);
		double dist = dir.length();
		if (dist < 1.0E-3) return false;
		dir = dir.scale(1.0 / dist);
		Vec3 feet;
		if (dist > max) {
			feet = eye.add(dir.scale(max)).subtract(0, 1.0, 0);
		} else if (aim.entity() != null) {
			Vec3 flat = new Vec3(dir.x, 0, dir.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			feet = aim.entity().position().subtract(flat.scale(aim.entity().getBbWidth() * 0.5 + 0.9));
		} else if (aim.hit() instanceof BlockHitResult b && b.getType() != HitResult.Type.MISS) {
			feet = b.getDirection() == Direction.UP ? b.getLocation()
					: b.getLocation().add(Vec3.atLowerCornerOf(b.getDirection().getUnitVec3i()).scale(0.5)).subtract(0, 0.9, 0);
		} else {
			feet = aim.point();
		}
		Vec3 dest = standNear(c, feet);
		for (int back = 1; dest == null && back <= 8; back++) dest = standNear(c, feet.subtract(dir.scale(back * 0.75)));
		Vec3 from = c.caster.position();
		if (dest == null || dest.distanceTo(from) < 1.0) return false;
		DEPARTURES.put(c.caster.getUUID(), from);

		// Уход: круг у ног вспыхивает, маг рассыпается квадратиками; вдоль пути — тающие силуэты.
		MagicFx.circle(c.level, c.spell, from.add(0, 0.06, 0), SpellKit.UP, 1.2F, 3, 4, 10);
		MagicFx.cloud(c.level, from.add(0, 1.0, 0), MagicParticles.glyph(c.color(), 0.14F, 14), 24, 0.3, 0.6, 0.3, 0.04);
		Vec3 path = dest.subtract(from);
		for (int i = 1; i <= 4; i++) {
			Vec3 p = from.add(path.scale(i / 5.0)).add(0, 1.0, 0);
			MagicFx.cloud(c.level, p, MagicParticles.mote(c.lightColor(), 0.16F, 10 + 3 * i), 6, 0.2, 0.5, 0.2, 0.0);
		}
		Vec3 momentum = c.caster.getDeltaMovement();
		MagicFx.sound(c.level, from, MagicSounds.castOf(c.spell), 0.9F, 1.3F);
		c.teleport(dest, c.color());
		c.caster.setDeltaMovement(momentum.scale(0.5));
		c.caster.hurtMarked = true;
		// Полсекунды без урона от падения.
		SpellKit.repeat(c, 10, 1, n -> c.caster.resetFallDistance());
		MagicFx.shockwave(c.level, dest.add(0, 0.1, 0), SpellKit.UP, 0.3F, 2.6F, c.lightColor(), 10);
		MagicFx.circle(c.level, c.spell, dest.add(0, 0.06, 0), SpellKit.UP, 1.2F, 2, 6, 12);
		return true;
	}

	/** Где встать рядом с точкой: сама точка или ближайшее свободное место на ±3 блока по высоте. */
	private static Vec3 standNear(Cast c, Vec3 feet) {
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			Vec3 p = feet.add(0, dy, 0);
			if (!c.level.isLoaded(BlockPos.containing(p))) return null;
			if (c.level.noCollision(c.caster, c.caster.getDimensions(c.caster.getPose()).makeBoundingBox(p))) return p;
		}
		return null;
	}

	/** Копия мультикаста: двойник-послеобраз на месте ухода, перетягивает на себя врагов на 3 с. */
	private static boolean afterimage(Cast c) {
		Vec3 from = DEPARTURES.get(c.caster.getUUID());
		if (from == null) return true;
		Mannequin m = EntityType.MANNEQUIN.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (m == null) return true;
		Vec3 at = from.add(c.right().scale((c.copy - (c.copies) * 0.5) * 0.9));
		m.snapTo(at.x, at.y, at.z, c.caster.getYRot(), 0);
		m.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(c.caster.getGameProfile()));
		m.setGlowingTag(true);
		Minions.summon(c, m, 60);
		for (LivingEntity e : c.enemiesAround(at, 16)) {
			if (e instanceof Mob mob) Minds.apply(mob, Minds.Mode.DECOY, 60, at, m);
		}
		MagicFx.cloud(c.level, at.add(0, 1, 0), MagicParticles.glyph(c.color(), 0.12F, 16), 12, 0.3, 0.6, 0.3, 0.02);
		return true;
	}

	// =====================================================================================================
	// Метка Возвращения (recall)
	// =====================================================================================================

	/** Переносная метка (ставится с Shift): одна на мага, хранится в нём самом. */
	private static final AttachmentType<GlobalPos> MARK = AttachmentRegistry.<GlobalPos>builder()
			.persistent(GlobalPos.CODEC)
			.copyOnDeath()
			.buildAndRegister(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "recall_mark"));

	private static boolean recall(Cast c) {
		// Копии не переносят мага ещё раз: каждая забирает с собой одного союзника (см. ниже).
		if (c.copy > 0) return true;
		ServerPlayer p = c.caster;
		Vec3 here = p.position();
		if (p.isShiftKeyDown()) {
			p.setAttached(MARK, GlobalPos.of(c.level.dimension(), p.blockPosition()));
			MagicFx.circle(c.level, School.SPACE, 3, TEAL, 0xD8FFFA, here.add(0, 0.06, 0), SpellKit.UP, 1.4F, 1, 0, 10, 30, 14, 0.2F);
			MagicFx.burst(c.level, here.add(0, 0.3, 0), MagicParticles.glyph(TEAL, 0.25F, 30), 10, 0.5, 0.02);
			MagicFx.sound(c.level, here, MagicSounds.CIRCLE_COMPLETE, 0.9F, 1.4F);
			return true;
		}
		GlobalPos mark = p.getAttached(MARK);
		ServerLevel to;
		Vec3 dest;
		if (mark != null && mark.dimension() == c.level.dimension()) {
			to = c.level;
			dest = Vec3.atBottomCenterOf(mark.pos());
		} else {
			TeleportTransition home = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
			to = home.newLevel();
			dest = home.position();
		}
		if (to == c.level && dest.distanceToSqr(here) < 4) return false;
		// Союзники-игроки рядом (по одному за копию мультикаста).
		List<ServerPlayer> party = new ArrayList<>();
		for (ServerPlayer other : c.level.players()) {
			if (party.size() >= c.copies - 1) break;
			if (other != p && other.isAlive() && other.distanceToSqr(p) <= 16) party.add(other);
		}
		// Уход: вспышка, мага затягивает в круг под ногами.
		MagicFx.flash(c.level, here, 12, 0.35F, TEAL, 8);
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 6;
			Vec3 at = here.add(Math.cos(a) * 1.2, 2.2 - i * 0.09, Math.sin(a) * 1.2);
			MagicFx.one(c.level, at, MagicParticles.spark(TEAL, 0.25F, 14), 0, -0.12, 0);
		}
		MagicFx.burst(c.level, here.add(0, 1, 0), ParticleTypes.REVERSE_PORTAL, 50, 0.4, 0.3);
		MagicFx.shockwave(c.level, here.add(0, 0.1, 0), SpellKit.UP, 2.4F, 0.2F, TEAL, 10);
		MagicFx.soundFar(c.level, here, MagicSounds.TELEPORT, 32, 0.7F);
		move(p, to, dest);
		for (ServerPlayer friend : party) move(friend, to, dest.add(friend.position().subtract(here)));
		// Прибытие: такой же круг распускается у цели, тихий перезвон.
		MagicFx.circle(to, School.SPACE, 3, TEAL, 0xD8FFFA, dest.add(0, 0.06, 0), SpellKit.UP, 2.0F, 2, 0.3F, 8, 20, 14, 0.25F);
		MagicFx.burst(to, dest.add(0, 1, 0), ParticleTypes.PORTAL, 60, 0.4, 0.4);
		MagicFx.burst(to, dest.add(0, 1, 0), MagicParticles.spark(TEAL, 0.3F, 18), 24, 0.5, 0.06);
		MagicFx.sound(to, dest, MagicSounds.TELEPORT, 1.0F, 1.3F);
		MagicFx.sound(to, dest, MagicSounds.CIRCLE_COMPLETE, 0.8F, 1.6F);
		return true;
	}

	private static void move(ServerPlayer p, ServerLevel to, Vec3 dest) {
		if (to == p.level()) {
			p.teleportTo(dest.x, dest.y, dest.z);
		} else {
			p.teleport(new TeleportTransition(to, dest, Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
		}
		p.resetFallDistance();
	}

	// =====================================================================================================
	// Переход Дверей (door_crossing): связанные порталы
	// =====================================================================================================

	/**
	 * Портал: центр, «лицо» {@code n} и оси плоскости {@code u}, {@code v} ({@code u = v × n}); полуразмеры по осям.
	 * Плоский портал — ось {@code v} по горизонтали, проверка — эллипс.
	 */
	private record Portal(Vec3 center, Vec3 n, Vec3 u, Vec3 v, double hu, double hv) {
		static Portal of(Vec3 center, Vec3 n, Vec3 v, double hu, double hv) {
			return new Portal(center, n, v.cross(n).normalize(), v, hu, hv);
		}

		/** Насколько далеко тело выступает вдоль нормали портала. */
		double depth(Entity e) {
			return Math.abs(n.y) * e.getBbHeight() * 0.5 + (Math.abs(n.x) + Math.abs(n.z)) * e.getBbWidth() * 0.5;
		}

		AABB box() {
			double r = Math.max(hu, hv) + 1.5;
			return new AABB(center, center).inflate(r);
		}
	}

	private static final class Gate {
		final Portal a;
		final Portal b;
		Zones.Zone zone;
		final Map<UUID, Integer> passed = new HashMap<>();

		Gate(Portal a, Portal b) {
			this.a = a;
			this.b = b;
		}
	}

	private static final Map<UUID, Gate> GATES = new HashMap<>();

	private static boolean doorCrossing(Cast c) {
		// Копии мультикаста не открывают новых дверей: они делают одну пару больше и дольше.
		if (c.copy > 0) return true;
		UUID id = c.caster.getUUID();
		Gate open = GATES.remove(id);
		if (open != null && open.zone != null && open.zone.age() < open.zone.life) {
			// Повторный каст закрывает двери.
			closeGate(c, open);
			return true;
		}
		boolean big = c.copies > 1;
		double hw = big ? 1.5 : 1.0, height = big ? 4.0 : 3.0;
		Vec3 look = c.look();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Portal a;
		Portal b;
		int life;
		if (c.caster.isShiftKeyDown()) {
			// «Охотничьи угодья»: пол уходит из-под врага, он падает из двери в небе — снова и снова.
			LivingEntity foe = c.aimLiving(48, e -> e instanceof LivingEntity l && c.isFoe(l));
			if (foe == null) {
				Vec3 p = c.aimPoint(48);
				foe = c.foesAround(p, 3).stream().min((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p))).orElse(null);
			}
			if (foe == null || boss(foe)) return false;
			Vec3 ground = Targeting.ground(c.level, c.caster, foe.position().add(0, 0.5, 0));
			Vec3 sky = ground.add(0, 25, 0);
			for (int dy = 0; dy < 10 && !c.level.noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(sky.subtract(0, 2.5, 0))); dy++) {
				sky = sky.subtract(0, 1, 0);
			}
			a = Portal.of(ground.add(0, 0.05, 0), SpellKit.UP, new Vec3(0, 0, 1), hw + 0.3, hw + 0.3);
			b = Portal.of(sky, SpellKit.DOWN, new Vec3(0, 0, 1), hw + 0.3, hw + 0.3);
			life = 60;
		} else {
			Vec3 baseA = c.caster.position().add(flat.scale(2.0));
			Vec3 baseB = c.aimGround();
			if (!c.level.isLoaded(BlockPos.containing(baseB))) return false;
			baseB = freeDoor(c, baseB, flat, hw, height);
			if (baseB == null || baseB.distanceTo(baseA) < 4) return false;
			// Лицо первой двери смотрит на мага, второй — от него: вошёл вперёд — вышел дальше вперёд.
			a = Portal.of(baseA.add(0, height * 0.5, 0), flat.reverse(), SpellKit.UP, hw, height * 0.5);
			b = Portal.of(baseB.add(0, height * 0.5, 0), flat, SpellKit.UP, hw, height * 0.5);
			life = 300 + 100 * (c.copies - 1);
		}
		SpellKit.release(c);
		Gate gate = new Gate(a, b);
		GATES.put(id, gate);
		openFx(c, a, life);
		openFx(c, b, life);
		MagicFx.soundFar(c.level, a.center, MagicSounds.castOf(c.spell), 32, 0.8F);
		MagicFx.soundFar(c.level, b.center, MagicSounds.TELEPORT, 48, 0.6F);
		gate.zone = Zones.add(c, a.center, 0, life, (z, age) -> {
			gateTick(c, gate, age);
			if (age >= life) {
				if (GATES.get(id) == gate) GATES.remove(id);
				closeGate(c, gate);
			}
		});
		return true;
	}

	/** Свободное место 2×3 под дверь у точки (или на 1–2 блока выше); null — тесно. */
	private static Vec3 freeDoor(Cast c, Vec3 base, Vec3 flat, double hw, double height) {
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		for (int up = 0; up <= 2; up++) {
			Vec3 p = base.add(0, up, 0);
			boolean free = true;
			for (double l = -hw + 0.5; l <= hw - 0.5 + 1.0E-3 && free; l += 1.0) {
				for (int h = 0; h < Math.round(height) && free; h++) {
					BlockPos bp = BlockPos.containing(p.add(right.scale(l)).add(0, h + 0.5, 0));
					free = c.level.getBlockState(bp).getCollisionShape(c.level, bp).isEmpty();
				}
			}
			if (free) return p;
		}
		return null;
	}

	private static void openFx(Cast c, Portal p, int life) {
		float r = (float) Math.max(p.hu, p.hv);
		MagicFx.circle(c.level, School.SPACE, 3, VIOLET, VIOLET_LIGHT, p.center, p.n, r, 1, 0, 10, life, 10, 0.12F);
		MagicFx.circle(c.level, School.SPACE, 3, VIOLET, VIOLET_LIGHT, p.center, p.n.reverse(), r * 0.7F, 1, 0, 14, life, 10, -0.2F);
		MagicFx.shockwave(c.level, p.center, p.n, 0.3F, r * 1.6F, VIOLET_LIGHT, 12);
	}

	private static void gateTick(Cast c, Gate g, int age) {
		// Обод — овал с хроматическими сбоями, внутри звёзды; раз в 3 тика.
		if (age % 3 == 0) {
			for (Portal p : new Portal[] {g.a, g.b}) {
				for (int i = 0; i < 10; i++) {
					double t = Math.PI * 2 * i / 10 + age * 0.07;
					Vec3 at = p.center.add(p.u.scale(Math.cos(t) * p.hu)).add(p.v.scale(Math.sin(t) * p.hv));
					int col = i % 5 == 0 ? (age % 6 == 0 ? 0xFF4060 : 0x40F0FF) : VIOLET;
					MagicFx.one(c.level, at, MagicParticles.spark(col, 0.22F, 8), 0, 0, 0);
				}
				Vec3 star = p.center.add(p.u.scale((c.level.random.nextDouble() - 0.5) * p.hu * 1.4))
						.add(p.v.scale((c.level.random.nextDouble() - 0.5) * p.hv * 1.4));
				MagicFx.one(c.level, star, MagicParticles.mote(VIOLET_LIGHT, 0.1F, 14), 0, 0, 0);
				MagicFx.one(c.level, star, ParticleTypes.REVERSE_PORTAL, 0, 0, 0);
			}
		}
		if (age % 40 == 0) MagicFx.sound(c.level, g.a.center, MagicSounds.LOOP_VOID, 0.25F, 1.4F);
		pass(c, g, g.a, g.b, age);
		pass(c, g, g.b, g.a, age);
	}

	/** Всё, что пересекает плоскость {@code from}, выходит из {@code to} с повёрнутой скоростью. */
	private static void pass(Cast c, Gate g, Portal from, Portal to, int age) {
		for (Entity e : c.level.getEntities((Entity) null, from.box(), SpaceSpells::traveller)) {
			Integer last = g.passed.get(e.getUUID());
			if (last != null && age - last < 20) continue;
			Vec3 rel = Cast.center(e).subtract(from.center);
			double dn = rel.dot(from.n);
			double du = rel.dot(from.u);
			double dv = rel.dot(from.v);
			if (Math.abs(dn) > 0.35 + from.depth(e)) continue;
			if ((du * du) / (from.hu * from.hu) + (dv * dv) / (from.hv * from.hv) > 1.0) continue;
			double side = dn > 1.0E-3 ? 1 : dn < -1.0E-3 ? -1 : -Math.signum(e.getDeltaMovement().dot(from.n) + 1.0E-6);
			// Поворот (n, u, v) → (−n′, −u′, v′): вошёл в лицо одной двери — вышел из лица другой.
			Vec3 vel = map(e.getDeltaMovement(), from, to);
			Vec3 out = to.center.add(to.u.scale(-du)).add(to.v.scale(dv)).add(to.n.scale(side * (to.depth(e) + 0.6)));
			Vec3 feet = out.subtract(0, e.getBbHeight() * 0.5, 0);
			Vec3 face = map(Vec3.directionFromRotation(0, e.getYRot()), from, to);
			float yaw = new Vec3(face.x, 0, face.z).lengthSqr() > 1.0E-3 ? (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG) : e.getYRot();
			g.passed.put(e.getUUID(), age);
			if (e instanceof ServerPlayer sp) {
				sp.teleportTo(c.level, feet.x, feet.y, feet.z, Set.of(), yaw, sp.getXRot(), false);
			} else {
				e.teleportTo(feet.x, feet.y, feet.z);
				e.setYRot(yaw);
				if (e instanceof LivingEntity l) l.setYHeadRot(yaw);
			}
			// Бесконечное падение «охотничьих угодий» не копит урон сверх разумного.
			e.fallDistance = Math.min(e.fallDistance, 12.0);
			e.setDeltaMovement(vel);
			e.hurtMarked = true;
			for (Portal p : new Portal[] {from, to}) {
				MagicFx.shockwave(c.level, p.center, p.n, 0.4F, (float) Math.max(p.hu, p.hv) * 1.4F, VIOLET_LIGHT, 8);
				MagicFx.burst(c.level, p.center, MagicParticles.spark(VIOLET_LIGHT, 0.3F, 10), 10, 0.4, 0.08);
				MagicFx.flash(c.level, p.center, 6, 0.18F, VIOLET_LIGHT, 5);
			}
			MagicFx.sound(c.level, from.center, MagicSounds.TELEPORT, 0.7F, 1.5F);
			MagicFx.sound(c.level, to.center, MagicSounds.TELEPORT, 0.7F, 1.7F);
		}
	}

	private static Vec3 map(Vec3 vec, Portal from, Portal to) {
		double a = vec.dot(from.n), b = vec.dot(from.u), w = vec.dot(from.v);
		return to.n.scale(-a).add(to.u.scale(-b)).add(to.v.scale(w));
	}

	private static boolean traveller(Entity e) {
		if (!e.isAlive() || e.isPassenger() || e.isSpectator()) return false;
		return e instanceof LivingEntity || e instanceof Projectile || e instanceof ItemEntity || e instanceof ExperienceOrb
				|| e instanceof FallingBlockEntity || e instanceof PrimedTnt;
	}

	private static void closeGate(Cast c, Gate g) {
		if (g.zone != null) g.zone.stop();
		for (Portal p : new Portal[] {g.a, g.b}) {
			MagicFx.shockwave(c.level, p.center, p.n, (float) Math.max(p.hu, p.hv) * 1.8F, 0.1F, VIOLET, 8);
			MagicFx.burst(c.level, p.center, ParticleTypes.REVERSE_PORTAL, 30, 0.3, 0.2);
			MagicFx.sound(c.level, p.center, MagicSounds.VANISH, 0.8F, 0.6F);
		}
	}

	// =====================================================================================================
	// Лазурь (lapse_blue): точка притяжения
	// =====================================================================================================

	private static boolean lapseBlue(Cast c) {
		SpellKit.release(c);
		Targeting.Aim aim = c.target();
		Vec3 core = aim.entity() != null ? aim.point() : aim.point().add(0, 1.5, 0);
		int life = 80;
		MagicFx.circle(c.level, School.SPACE, 4, BLUE, BLUE_LIGHT, core, c.look().reverse(), 2.4F, 2, 0.4F, 8, life - 8, 10, -0.6F);
		MagicFx.core(c.level, core, 0.8F, 2.2F, ABYSS, BLUE_LIGHT, life);
		MagicFx.soundFar(c.level, core, MagicSounds.TELEPORT, 48, 0.5F);
		Zones.add(c, core, 10, life, (z, age) -> {
			// Притяжение: сильнее у ядра, боссов — втрое слабее.
			for (LivingEntity e : c.foesAround(core, 10)) {
				double d = Cast.center(e).distanceTo(core);
				double k = (0.04 + 0.12 * (1 - d / 10)) * (boss(e) ? 0.3 : 1.0);
				Cast.pull(e, core, k);
			}
			if (age % 2 == 0) {
				for (Entity e : c.level.getEntities(c.caster, new AABB(core, core).inflate(16), e -> e instanceof ItemEntity || e instanceof ExperienceOrb
						|| e instanceof Projectile pr && pr.getOwner() != c.caster)) {
					Vec3 to = core.subtract(e.position());
					double d = to.length();
					if (d < 0.3) continue;
					if (e instanceof Projectile) {
						if (d < 10) e.setDeltaMovement(e.getDeltaMovement().add(to.scale(0.1 / d)));
					} else {
						e.setDeltaMovement(to.scale(Math.min(0.5, d * 0.15) / d).add(0, 0.04, 0));
					}
					e.hurtMarked = true;
				}
				// Струйки частиц, текущие внутрь.
				for (int i = 0; i < 6; i++) {
					Vec3 off = randomDir(c).scale(4 + c.level.random.nextDouble() * 4);
					Vec3 v = off.reverse().scale(0.09);
					MagicFx.one(c.level, core.add(off), MagicParticles.spark(i % 2 == 0 ? BLUE : BLUE_LIGHT, 0.22F, 12), v.x, v.y, v.z);
				}
			}
			if (age % 10 == 0) {
				for (LivingEntity e : c.foesAround(core, 2.0)) {
					c.hurt(e, c.amount(3.0F), MagicDamage.VOID);
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3), c.caster);
				}
				MagicFx.shockwave(c.level, core, c.look(), 6.0F, 0.5F, BLUE_LIGHT, 10);
			}
			if (age % 20 == 0) MagicFx.sound(c.level, core, MagicSounds.LOOP_VOID, 1.0F, 0.6F + age / 200.0F);
			if (age >= life) lapseCollapse(c, core);
		});
		return true;
	}

	/** Схлопывание: удар в ядре, разлёт, всё притянутое падает у ядра. */
	private static void lapseCollapse(Cast c, Vec3 core) {
		for (LivingEntity e : c.foesAround(core, 6)) {
			if (Cast.center(e).distanceTo(core) <= 3) c.hurt(e, c.amount(12.0F), MagicDamage.VOID);
			Cast.knock(e, core, 1.2, 0.35);
		}
		for (Entity e : c.level.getEntities(c.caster, new AABB(core, core).inflate(16), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			e.setPos(core.x, core.y - 0.5, core.z);
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
		}
		MagicFx.sphere(c.level, core, 4.0F, 0.2F, BLUE, BLUE_LIGHT, 8);
		MagicFx.shockwave(c.level, core, SpellKit.UP, 0.5F, 9.0F, BLUE_LIGHT, 12);
		MagicFx.flash(c.level, core, 40, 0.45F, BLUE_LIGHT, 8);
		MagicFx.burst(c.level, core, MagicParticles.spark(BLUE_LIGHT, 0.4F, 16), 30, 0.6, 0.25);
		MagicFx.soundFar(c.level, core, MagicSounds.impactOf(c.spell), 48, 0.6F);
		MagicFx.soundFar(c.level, core, MagicSounds.SHOCKWAVE, 48, 1.2F);
		Magic.shake(c.level, core, 1.2F, 10, 32);
		Terrain.crater(c, core, 2.5, 1.0, Terrain.Rim.NONE);
	}

	// =====================================================================================================
	// Обращение: Багрянец (reversal_red): сфера отталкивания
	// =====================================================================================================

	private static boolean reversalRed(Cast c) {
		SpellKit.release(c);
		boolean main = c.copy == 0;
		float k = main ? 1.0F : 0.6F;
		Vec3 from = c.hand();
		Vec3 dir;
		if (main) {
			dir = c.look();
		} else {
			// Копии слетаются в ту же точку, что и главная сфера.
			Vec3 p = Targeting.aim(c.level, c.caster, c.eye(), c.look(), 64, Targeting.living(c.caster)).point();
			Vec3 d = p.subtract(from);
			dir = d.lengthSqr() < 1.0E-4 ? c.look() : d.normalize();
		}
		if (main) {
			MagicFx.soundFar(c.level, from, MagicSounds.RELEASE_HEAVY, 64, 1.0F);
			MagicFx.flash(c.level, from, 16, 0.3F, RED_LIGHT, 6);
			MagicFx.shockwave(c.level, from, dir, 0.3F, 3.0F, RED_LIGHT, 8);
		}
		double blastR = main ? 5 + (c.copies - 1) : 3;
		float blastDmg = main ? 20.0F : 12.0F;
		final Vec3 direction = dir;
		final class Orb {
			Vec3 pos = from;
			double flown;
			double rock;
			int hits;
			boolean done;
			final Set<UUID> struck = new HashSet<>();
		}
		Orb orb = new Orb();
		Zones.add(c, from, 0, 40, (z, age) -> {
			if (orb.done) return;
			Vec3 next = orb.pos.add(direction.scale(3.0));
			BlockHitResult bh = c.level.clip(new ClipContext(orb.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
			Vec3 stop = null;
			if (bh.getType() != HitResult.Type.MISS) {
				if (Terrain.griefing(c.level) && orb.rock < 24) {
					// Сквозь камень — тоннелем, пока не пройдёт 24 блока породы.
					Terrain.tunnel(c, orb.pos, next, 1.5, Terrain.Rim.NONE);
					orb.rock += next.distanceTo(bh.getLocation());
				} else {
					stop = bh.getLocation().subtract(direction.scale(0.5));
					next = stop;
				}
			}
			// Всех на пути — сметает и бьёт, каждый следующий слабее.
			for (LivingEntity e : c.along(orb.pos, next, 2.0, c::isFoe)) {
				if (!orb.struck.add(e.getUUID())) continue;
				c.hurt(e, c.amount(30.0F * k) * (float) Math.pow(0.8, orb.hits++), MagicDamage.VOID);
				double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				if (!boss(e)) {
					Vec3 push = direction.scale(3.0 * k * res);
					e.push(push.x, 0.5 * res, push.z);
					e.hurtMarked = true;
				}
				MagicFx.burst(c.level, Cast.center(e), MagicParticles.spark(RED_LIGHT, 0.4F, 12), 12, 0.3, 0.15);
			}
			// Сфера и её отталкивающие пульсы, багровый след.
			MagicFx.sphere(c.level, next, 0.5F * k, 1.1F * k, RED, RED_LIGHT, 3);
			MagicFx.line(c.level, orb.pos, next, MagicParticles.spark(RED, 0.3F * k, 10), 0.5);
			if (age % 3 == 0) MagicFx.shockwave(c.level, next, direction, 0.8F * k, 2.6F * k, RED_LIGHT, 6);
			if (age % 6 == 1) MagicFx.sound(c.level, next, MagicSounds.GUST, 1.2F, 0.5F);
			orb.flown += next.distanceTo(orb.pos);
			orb.pos = next;
			if (stop == null && (orb.flown >= 64 || orb.rock >= 24)) stop = next;
			if (stop != null || age >= 39) {
				orb.done = true;
				z.stop();
				repel(c, stop != null ? stop : next, blastR, blastDmg, main);
			}
		});
		return true;
	}

	/** Взрыв отталкивания: урон с затуханием, отброс прочь, багровый купол, кольцо, вспышка, воронка. */
	private static void repel(Cast c, Vec3 at, double r, float dmg, boolean main) {
		for (LivingEntity e : c.foesAround(at, r)) {
			double k = 1.0 - 0.5 * Math.min(1.0, Cast.center(e).distanceTo(at) / r);
			c.hurt(e, c.amount(dmg) * (float) k, MagicDamage.VOID);
			if (!boss(e)) Cast.knock(e, at, 2.5 * k, 0.5);
		}
		MagicFx.dome(c.level, at, (float) r, RED, RED_LIGHT, 22);
		MagicFx.sphere(c.level, at, 0.5F, (float) r * 1.2F, RED, RED_LIGHT, 12);
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), SpellKit.UP, 1.0F, (float) r * 2, RED_LIGHT, 14);
		MagicFx.flash(c.level, at, (float) r * 10, main ? 0.7F : 0.35F, RED_LIGHT, 10);
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION_EMITTER, main ? 2 : 1, r * 0.25, 0);
		MagicFx.burst(c.level, at, MagicParticles.spark(RED, 0.5F, 18), 40, r * 0.3, 0.3);
		MagicFx.soundFar(c.level, at, main ? MagicSounds.EXPLOSION_HUGE : MagicSounds.EXPLOSION, main ? 128 : 48, 0.9F);
		Magic.shake(c.level, at, main ? 3.0F : 1.5F, 16, r * 8);
		Terrain.crater(c, at, main ? 4.0 : 2.5, 0.75, Terrain.Rim.RUBBLE);
	}

	// =====================================================================================================
	// Вихрь Измерений (kamui): изгнание в иное измерение
	// =====================================================================================================

	private static boolean kamui(Cast c) {
		LivingEntity t = c.target().entity() instanceof LivingEntity l && c.isFoe(l) ? l : null;
		if (t == null && c.copy == 0) t = c.aimFoe();
		if (t == null) {
			Vec3 p = c.aimPoint();
			t = c.foesAround(p, 3).stream().min((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p))).orElse(null);
		}
		if (t == null) return false;
		SpellKit.release(c);
		LivingEntity target = t;
		boolean boss = boss(target);
		int hold = 30;
		Vec3 at0 = Cast.center(target);
		MagicFx.core(c.level, at0, 0.5F, 1.8F, ABYSS, SWIRL, hold + 10);
		MagicFx.tornado(c.level, target.position(), 1.4F + target.getBbWidth(), target.getBbHeight() + 1.0F, SWIRL, 0xBFFFF5, hold);
		MagicFx.soundFar(c.level, at0, MagicSounds.LOOP_VOID, 32, 0.5F);
		if (!boss) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, hold + 12, 4), c.caster);
		final class Swirl {
			boolean banish;
		}
		Swirl s = new Swirl();
		Zones.add(c, at0, 3, hold + 12, (z, age) -> {
			if (!target.isAlive()) {
				z.stop();
				return;
			}
			Vec3 at = Cast.center(target);
			if (!boss) {
				target.setDeltaMovement(Vec3.ZERO);
				target.hurtMarked = true;
			}
			if (age <= hold) {
				if (age % 3 == 0) c.hurt(target, c.amount(2.6F), MagicDamage.VOID);
				// Три рукава спирали сжимаются к точке.
				double r = 2.4 * (1.0 - age / (double) hold) + 0.3;
				for (int arm = 0; arm < 3; arm++) {
					double a = age * 0.55 + arm * Math.PI * 2 / 3;
					Vec3 p = at.add(Math.cos(a) * r, Math.sin(age * 0.3 + arm) * 0.4, Math.sin(a) * r);
					MagicFx.one(c.level, p, MagicParticles.spark(arm == 0 ? 0xB01020 : SWIRL, 0.3F, 8), -Math.sin(a) * 0.1, 0, Math.cos(a) * 0.1);
				}
				MagicFx.one(c.level, at, ParticleTypes.REVERSE_PORTAL, 0, 0, 0);
				// Снаряды рядом проглатывает искажение.
				if (age % 2 == 0) {
					for (Projectile pr : c.level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(3), pr -> pr.getOwner() != c.caster)) {
						MagicFx.burst(c.level, pr.position(), ParticleTypes.REVERSE_PORTAL, 6, 0.1, 0.05);
						pr.discard();
					}
				}
				if (age == hold) {
					MagicFx.flash(c.level, at, 24, 0.4F, 0x300818, 8);
					MagicFx.sound(c.level, at, MagicSounds.VANISH, 1.0F, 0.5F);
					if (boss) {
						z.stop();
						return;
					}
					s.banish = !(target instanceof Player) && (target.getHealth() <= target.getMaxHealth() * 0.3F || target.getMaxHealth() <= 24);
					if (!s.banish) {
						drop(c, target);
						z.stop();
					}
				}
				return;
			}
			// Изгнание: цель сжимается и исчезает; добыча и опыт падают к ногам мага.
			AttributeInstance scale = target.getAttribute(Attributes.SCALE);
			int k = age - hold;
			if (scale != null) scale.setBaseValue(Math.max(0.1, 1.0 - 0.09 * k));
			if (k >= 10) banish(c, target, scale);
		});
		return true;
	}

	private static void drop(Cast c, LivingEntity t) {
		Vec3 up = t.position();
		for (int dy = 8; dy >= 2; dy--) {
			Vec3 p = t.position().add(0, dy, 0);
			if (c.level.noCollision(t, t.getDimensions(t.getPose()).makeBoundingBox(p))) {
				up = p;
				break;
			}
		}
		MagicFx.burst(c.level, Cast.center(t), ParticleTypes.REVERSE_PORTAL, 20, 0.3, 0.2);
		t.teleportTo(up.x, up.y, up.z);
		t.setDeltaMovement(0, -0.2, 0);
		t.hurtMarked = true;
		MagicFx.burst(c.level, up.add(0, t.getBbHeight() * 0.5, 0), MagicParticles.spark(SWIRL, 0.3F, 14), 16, 0.4, 0.05);
		MagicFx.sound(c.level, up, MagicSounds.TELEPORT, 0.9F, 0.8F);
	}

	private static void banish(Cast c, LivingEntity t, AttributeInstance scale) {
		Vec3 feet = c.caster.position();
		MagicFx.burst(c.level, Cast.center(t), MagicParticles.spark(SWIRL, 0.25F, 12), 10, 0.1, 0.05);
		t.setInvisible(true);
		t.teleportTo(feet.x, feet.y, feet.z);
		c.hurt(t, t.getHealth() + 1000.0F, MagicDamage.VOID);
		if (t.isAlive()) t.kill(c.level);
		if (t.isAlive()) {
			// Не удалось (неуязвимость, тотем): возвращаем облик.
			t.setInvisible(false);
			if (scale != null) scale.setBaseValue(1.0);
		}
		MagicFx.burst(c.level, feet.add(0, 0.5, 0), MagicParticles.spark(SWIRL, 0.3F, 16), 12, 0.3, 0.05);
		MagicFx.sound(c.level, feet, MagicSounds.VANISH, 0.8F, 1.4F);
	}

	// =====================================================================================================
	// Мгновение, Застынь (time_stop)
	// =====================================================================================================

	/** Застывшее существо или предмет: где стоял, с какой скоростью летел, что вернуть, какие удары копятся. */
	private static final class Frozen {
		final Entity e;
		final Vec3 pos;
		final Vec3 vel;
		final boolean noAi;
		final boolean noGravity;
		final int fire;
		final int fuse;
		final int until;
		final List<Hit> hits = new ArrayList<>();

		Frozen(Entity e, int until) {
			this.e = e;
			this.pos = e.position();
			this.vel = e.getDeltaMovement();
			this.noAi = e instanceof Mob m && m.isNoAi();
			this.noGravity = e.isNoGravity();
			this.fire = e.getRemainingFireTicks();
			this.fuse = e instanceof PrimedTnt t ? t.getFuse() : 0;
			this.until = until;
		}
	}

	private record Hit(DamageSource source, float amount) {}

	private static final String TS = "suchki.time_stopped";
	private static final String TS_NOAI = "suchki.time_stopped.noai";
	private static final String TS_NOGRAV = "suchki.time_stopped.nograv";
	private static final Map<Entity, Frozen> FROZEN = new HashMap<>();
	/** Пока true — накопленный урон проходит. */
	private static boolean resuming;
	private static boolean hooked;

	/** Обработчики остановленного времени: удары копятся, при остановке сервера и загрузке всё оттаивает. */
	private static void hooks() {
		if (hooked) return;
		hooked = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (resuming) return true;
			Frozen f = FROZEN.get(entity);
			if (f == null) return true;
			f.hits.add(new Hit(source, amount));
			if (entity.level() instanceof ServerLevel level) {
				MagicFx.burst(level, Cast.center(entity), MagicParticles.spark(CLOCK, 0.2F, 40), 4, entity.getBbWidth() * 0.4, 0.0);
			}
			return false;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Frozen f : List.copyOf(FROZEN.values())) resume(f, false);
			FROZEN.clear();
			GRIPS.clear();
			GATES.clear();
			DEPARTURES.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Мир сохранили посреди остановки времени: возвращаем существу его ИИ и тяжесть.
			if (entity.getTags().contains(TS) && !FROZEN.containsKey(entity)) {
				if (entity instanceof Mob m) m.setNoAi(entity.getTags().contains(TS_NOAI));
				entity.setNoGravity(entity.getTags().contains(TS_NOGRAV));
				entity.removeTag(TS);
				entity.removeTag(TS_NOAI);
				entity.removeTag(TS_NOGRAV);
			}
		});
	}

	private static boolean timeStop(Cast c) {
		// Копии не останавливают время ещё раз — каждая продлевает остановку на 1,5 с (не больше 10 с).
		if (c.copy > 0) return true;
		SpellKit.release(c);
		FROZEN.keySet().removeIf(Entity::isRemoved);
		int dur = Math.min(200, 120 + 30 * (c.copies - 1));
		Vec3 at = c.caster.position();
		double r = c.spell.range;
		List<Frozen> mine = new ArrayList<>();
		for (Entity e : c.level.getEntities(c.caster, new AABB(at, at).inflate(r), e -> e.position().distanceToSqr(at) <= r * r && stoppable(c, e))) {
			if (mine.size() >= 256) break;
			mine.add(freeze(e, e instanceof LivingEntity l && boss(l) ? 40 : dur));
		}
		// Другие игроки (не союзники по команде) вязнут, но не застывают.
		for (ServerPlayer other : c.level.players()) {
			if (other == c.caster || other.distanceToSqr(at) > r * r || other.isSpectator()) continue;
			if (c.caster.isAlliedTo(other) || !c.caster.canHarmPlayer(other)) continue;
			other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), c.caster);
			other.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 2), c.caster);
		}
		clockFx(c, at, dur);
		MagicFx.soundFar(c.level, at, MagicSounds.TIMESTOP, 128, 1.0F);
		MagicFx.sphere(c.level, at.add(0, 1, 0), 1.0F, (float) r, STILL, 0xE6EEFF, 26);
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), SpellKit.UP, 1.0F, (float) r, 0xE6EEFF, 22);
		MagicFx.flash(c.level, at, (float) r, 0.45F, 0xC8D4F0, 18);
		Zones.add(c, at, r, dur + 1, (z, age) -> {
			for (Frozen f : mine) {
				if (FROZEN.get(f.e) != f) continue;
				if (f.e.isRemoved()) {
					FROZEN.remove(f.e);
				} else if (age >= f.until) {
					resume(f, true);
				} else {
					hold(f);
				}
			}
			// Свои снаряды мага замирают в двух блоках от него и ждут конца остановки.
			if (age % 2 == 0 && age < dur) {
				Vec3 eye = c.eye();
				for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, c.caster.getBoundingBox().inflate(12),
						p -> p.getOwner() == c.caster && !FROZEN.containsKey(p) && p.position().distanceToSqr(eye) >= 4)) {
					mine.add(freeze(p, dur));
				}
			}
			// Стрелки часов у ног мага ползут всё медленнее; оттенок застывшего мира держится.
			if (age % 4 == 0 && age < dur) {
				double slow = 1.0 - 0.7 * age / (double) dur;
				for (int hand = 0; hand < 2; hand++) {
					double a = (hand == 0 ? 0.6 : 0.05) * age * slow - Math.PI / 2;
					double len = hand == 0 ? 3.0 : 1.8;
					Vec3 tip = at.add(Math.cos(a) * len, 0.12, Math.sin(a) * len);
					MagicFx.line(c.level, at.add(0, 0.12, 0), tip, MagicParticles.spark(hand == 0 ? CLOCK : 0xFFFFFF, 0.12F, 5), 0.35);
				}
			}
			if (age % 20 == 10 && age < dur) MagicFx.flash(c.level, at, (float) r, 0.16F, STILL, 26);
			if (age % 10 == 5 && age < dur) {
				int n = 0;
				for (Frozen f : mine) {
					if (n++ >= 40 || FROZEN.get(f.e) != f || !(f.e instanceof LivingEntity)) continue;
					MagicFx.one(c.level, f.e.position().add(0, f.e.getBbHeight() + 0.4, 0), MagicParticles.glyph(CLOCK, 0.18F, 10), 0, 0, 0);
				}
			}
			if (age % 40 == 20 && age < dur) MagicFx.sound(c.level, at, MagicSounds.DOMAIN, 0.6F, 0.5F);
			if (age == dur) {
				// Часы разбиваются, цвет возвращается волной.
				MagicFx.burst(c.level, at.add(0, 1, 0), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), 60, 1.5, 0.3);
				MagicFx.burst(c.level, at.add(0, 0.3, 0), MagicParticles.spark(CLOCK, 0.3F, 20), 30, 1.8, 0.2);
				MagicFx.shockwave(c.level, at.add(0, 0.2, 0), SpellKit.UP, 0.5F, (float) r, 0xFFFFFF, 18);
				MagicFx.flash(c.level, at, (float) r, 0.3F, 0xFFFFFF, 6);
				MagicFx.soundFar(c.level, at, MagicSounds.SHATTER, 64, 0.8F);
				MagicFx.soundFar(c.level, at, MagicSounds.GUST, 64, 0.6F);
			}
		});
		return true;
	}

	/** Что останавливается: существа (кроме игроков и своих призванных), снаряды, предметы, падающие блоки, динамит. */
	private static boolean stoppable(Cast c, Entity e) {
		if (!e.isAlive() || e instanceof Player || FROZEN.containsKey(e) || Minions.isOwnedBy(e, c.caster)) return false;
		if (e instanceof Projectile p) return p.getOwner() != c.caster;
		return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof FallingBlockEntity
				|| e instanceof PrimedTnt;
	}

	private static Frozen freeze(Entity e, int until) {
		Frozen f = new Frozen(e, until);
		FROZEN.put(e, f);
		e.addTag(TS);
		if (f.noAi) e.addTag(TS_NOAI);
		if (f.noGravity) e.addTag(TS_NOGRAV);
		if (e instanceof Mob m) {
			m.setNoAi(true);
			m.getNavigation().stop();
		}
		e.setNoGravity(true);
		e.setDeltaMovement(Vec3.ZERO);
		e.hurtMarked = true;
		return f;
	}

	private static void hold(Frozen f) {
		Entity e = f.e;
		e.setDeltaMovement(Vec3.ZERO);
		if (e.position().distanceToSqr(f.pos) > 1.0E-4) e.setPos(f.pos);
		if (f.fire > 0) e.setRemainingFireTicks(f.fire);
		if (e instanceof PrimedTnt t) t.setFuse(f.fuse);
	}

	/** Время пошло: ИИ и тяжесть возвращаются, снаряды летят дальше, накопленные удары проходят разом. */
	private static void resume(Frozen f, boolean hits) {
		Entity e = f.e;
		FROZEN.remove(e);
		e.removeTag(TS);
		e.removeTag(TS_NOAI);
		e.removeTag(TS_NOGRAV);
		if (e instanceof Mob m) m.setNoAi(f.noAi);
		e.setNoGravity(f.noGravity);
		if (e instanceof PrimedTnt t) t.setFuse(f.fuse);
		e.setDeltaMovement(e instanceof LivingEntity ? Vec3.ZERO : f.vel);
		e.hurtMarked = true;
		if (!hits || f.hits.isEmpty() || !(e instanceof LivingEntity l) || !(e.level() instanceof ServerLevel level)) return;
		resuming = true;
		try {
			for (Hit h : f.hits) {
				if (!l.isAlive()) break;
				l.invulnerableTime = 0;
				l.hurtServer(level, h.source, h.amount);
				Vec3 src = h.source.getSourcePosition();
				if (src != null) Cast.knock(l, src, 0.4, 0.1);
			}
		} finally {
			resuming = false;
		}
		MagicFx.burst(level, Cast.center(l), MagicParticles.spark(0xFFFFFF, 0.35F, 10), 6 + 2 * f.hits.size(), l.getBbWidth() * 0.5, 0.2);
		MagicFx.sound(level, l.position(), MagicSounds.impact(School.SPACE), 1.0F, 0.8F);
	}

	/** Три циферблата у ног: строятся по одному, двенадцать часовых меток появляются одна за другой. */
	private static void clockFx(Cast c, Vec3 at, int dur) {
		float[] radii = {1.5F, 2.5F, 3.5F};
		for (int i = 0; i < radii.length; i++) {
			int k = i;
			Zones.later(c, i * 4, () -> MagicFx.circle(c.level, School.SPACE, 6, k == 1 ? STILL : CLOCK, 0xFFF4D8, at.add(0, 0.05 + 0.02 * k, 0),
					SpellKit.UP, radii[k], 1, 0, 8, dur - k * 4, 14, (k % 2 == 0 ? 1 : -1) * 0.05F / (k + 1)));
		}
		for (int h = 0; h < 12; h++) {
			int hour = h;
			Zones.later(c, 4 + h, () -> {
				double a = Math.PI * 2 * hour / 12 - Math.PI / 2;
				Vec3 p = at.add(Math.cos(a) * 3.5, 0.15, Math.sin(a) * 3.5);
				MagicFx.one(c.level, p, MagicParticles.glyph(CLOCK, 0.3F, Math.max(20, dur - 4 - hour)), 0, 0, 0);
				MagicFx.sound(c.level, p, MagicSounds.CIRCLE_DRAW, 0.3F, 1.8F - hour * 0.05F);
			});
		}
	}

	// =====================================================================================================
	// Рассечение Сущего (reality_slash)
	// =====================================================================================================

	private static final double[] SLASH_TILT = {0, 45, -45, 90};

	private static boolean realitySlash(Cast c) {
		SpellKit.release(c);
		Vec3 dir = c.look();
		Vec3 from = c.hand();
		boolean flat = c.caster.isShiftKeyDown();
		double a = Math.toRadians(SLASH_TILT[Math.min(c.copy, SLASH_TILT.length - 1)] + (flat ? 90 : 0));
		Vec3 span = CircleLayout.up(dir).scale(Math.cos(a)).add(CircleLayout.right(dir).scale(Math.sin(a))).normalize();
		Vec3 normal = dir.cross(span).normalize();
		// Ряд тонких колец до самого конца разреза — по два за тик.
		for (int i = 1; i <= 8; i++) {
			int k = i;
			Zones.later(c, (i - 1) / 2, () -> {
				Vec3 p = from.add(dir.scale(8.0 * k));
				MagicFx.circle(c.level, School.SPACE, 6, 0xFFFFFF, 0xFFFFFF, p, dir, 0.6F + 0.05F * k, 1, 0, 2, 6, 6, 0.4F);
				MagicFx.sound(c.level, p, MagicSounds.CIRCLE_DRAW, 0.4F, 2.0F);
			});
		}
		// Три тика тишины — и линия становится истиной.
		Zones.later(c, 5, () -> sever(c, from, dir, span, normal));
		return true;
	}

	private static void sever(Cast c, Vec3 from, Vec3 dir, Vec3 span, Vec3 normal) {
		double len = c.spell.range;
		Vec3 start = from.add(dir.scale(2.5));
		Vec3 to = from.add(dir.scale(len));
		MagicFx.beam(c.level, from, to, 0.04F, 0xFFFFFF, 0xFFFFFF, 3);
		Zones.later(c, 2, () -> {
			for (int h = -4; h <= 4; h += 2) {
				MagicFx.beam(c.level, start.add(span.scale(h)), to.add(span.scale(h)), 0.14F, ABYSS, 0x2A0A3A, 22);
			}
			for (int h : new int[] {-4, 0, 4}) {
				MagicFx.beam(c.level, start.add(span.scale(h)).add(normal.scale(0.14)), to.add(span.scale(h)).add(normal.scale(0.14)), 0.05F,
						0xFF2244, 0xFFC0C8, 16);
				MagicFx.beam(c.level, start.add(span.scale(h)).add(normal.scale(-0.14)), to.add(span.scale(h)).add(normal.scale(-0.14)), 0.05F,
						0x22F0FF, 0xC0FCFF, 16);
			}
		});
		// Всё, что пересекает плоскость, — рассечено; броня не спасает.
		AABB box = new AABB(start, to).inflate(5);
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (!c.isFoe(e)) continue;
			Vec3 rel = Cast.center(e).subtract(from);
			double along = rel.dot(dir);
			if (along < 0 || along > len) continue;
			if (Math.abs(rel.dot(normal)) > 0.3 + e.getBbWidth() * 0.5) continue;
			if (Math.abs(rel.dot(span)) > 4 + e.getBbHeight() * 0.5) continue;
			float dmg = c.amount(boss(e) ? 40.0F : 64.0F);
			c.hurt(e, throughArmor(c, e, dmg), MagicDamage.VOID);
			Vec3 mid = Cast.center(e);
			MagicFx.beam(c.level, mid.add(span.scale(1.2)), mid.add(span.scale(-1.2)), 0.05F, 0xFFFFFF, 0xFFFFFF, 6);
			MagicFx.burst(c.level, mid, MagicParticles.spark(0xFF2244, 0.3F, 12), 10, 0.3, 0.2);
			MagicFx.burst(c.level, mid, MagicParticles.spark(0x22F0FF, 0.3F, 12), 10, 0.3, 0.2);
		}
		// Блоки в плоскости разреза исчезают (не больше ~512): линиями через каждый блок высоты.
		if (Terrain.griefing(c.level)) {
			int total = 0;
			for (int h = 0; h <= 8 && total < 512; h++) {
				double off = (h % 2 == 0 ? 1 : -1) * ((h + 1) / 2);
				total += Terrain.tunnel(c, start.add(span.scale(off)), to.add(span.scale(off)), 0.8, Terrain.Rim.NONE);
			}
		}
		MagicFx.flash(c.level, from, 48, 0.55F, 0xFFFFFF, 6);
		Magic.shake(c.level, from, 2.6F, 14, 48);
		MagicFx.soundFar(c.level, from, MagicSounds.SHATTER, 96, 1.4F);
		MagicFx.soundFar(c.level, from.add(dir.scale(len * 0.5)), MagicSounds.BEAM_BLAST, 96, 0.6F);
		MagicFx.soundFar(c.level, from, MagicSounds.GLITCH, 48, 0.7F);
	}

	/** Урон, который после брони и прочности брони остаётся примерно {@code dmg} (не больше удвоения). */
	private static float throughArmor(Cast c, LivingEntity e, float dmg) {
		DamageSource src = MagicDamage.VOID.source(c.level, c.caster, c.caster);
		float after = CombatRules.getDamageAfterAbsorb(e, dmg, src, e.getArmorValue(),
				(float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		if (after <= 0.01F) return dmg * 2;
		return Math.min(dmg * 2, dmg * dmg / after);
	}

	// =====================================================================================================
	// Опустошение Планеты (planetary_devastation)
	// =====================================================================================================

	private static final BlockState[] MOON = {Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
			Blocks.DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};

	private static boolean planetaryDevastation(Cast c) {
		SpellKit.release(c);
		Vec3 ground = c.aimGround();
		Vec3 core = ground.add(0, c.spell.skyHeight(), 0);
		int fly = 12;
		int pull = 120;
		int hang = 600;
		// Небо темнеет, земляной круг строится под небесным, чёрное ядро взмывает от руки.
		MagicFx.soundFar(c.level, c.eye(), MagicSounds.RELEASE_ULTIMATE, 192, 0.8F);
		MagicFx.flash(c.level, ground, 128, 0.35F, 0x140820, fly + pull);
		MagicFx.circle(c.level, School.SPACE, 7, 0x2A1048, 0xB89CFF, ground.add(0, 0.1, 0), SpellKit.UP, 12.0F, 3, 0.6F, 30, fly + pull, 30, 0.08F);
		MagicFx.circle(c.level, School.SPACE, 7, 0x3A1060, 0xE2C8FF, core.add(0, 2, 0), SpellKit.DOWN, 8.0F, 2, 0.8F, 20, fly + pull - 10, 30, -0.12F);
		MagicFx.comet(c.level, c.hand(), core, 1.2F, ABYSS, 0xB89CFF, fly);
		Magic.shake(c.level, ground, 1.5F, fly + pull, 96);
		Zones.later(c, fly, () -> {
			MagicFx.soundFar(c.level, core, MagicSounds.DOMAIN, 160, 0.5F);
			MagicFx.tornado(c.level, ground, 10.0F, (float) c.spell.skyHeight(), 0x3A1060, 0xB89CFF, pull);
			BlockState debris = c.level.getBlockState(BlockPos.containing(ground).below());
			if (debris.isAir()) debris = Blocks.STONE.defaultBlockState();
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, debris);
			Zones.add(c, core, 28, pull, (z, age) -> {
				// Поле тяжести тянет всех (не союзников) и всё к ядру; боссов — втрое слабее.
				if (age % 2 == 0) {
					for (Entity e : c.level.getEntities(c.caster, new AABB(core, core).inflate(28), e -> e.position().distanceToSqr(core) <= 28 * 28)) {
						if (e instanceof LivingEntity l ? !c.isFoe(l) : !(e instanceof ItemEntity || e instanceof ExperienceOrb
								|| e instanceof FallingBlockEntity)) {
							continue;
						}
						Vec3 to = core.subtract(Cast.center(e));
						double d = to.length();
						if (d < 0.5) continue;
						double speed = e instanceof LivingEntity l && boss(l) ? 0.06 : 0.18;
						e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(to.scale(speed * 1.6 / d)));
						e.hurtMarked = true;
					}
				}
				if (age % 10 == 0) {
					int step = age / 10;
					for (LivingEntity e : c.foesAround(core, 9)) c.hurt(e, c.amount(5.0F), MagicDamage.VOID);
					// Каменная оболочка растёт слоями до радиуса 7 и висит ещё 30 с после сжатия.
					growMoon(c, core, 1.0 + 0.5 * step, hang + pull - age);
					// Земля под ядром вынимается кольцами до радиуса 10.
					Terrain.crater(c, ground, Math.max(1.0, 10.0 * step / 12.0), 0.6, Terrain.Rim.RUBBLE);
					MagicFx.shockwave(c.level, ground.add(0, 0.3, 0), SpellKit.UP, 20.0F, 2.0F, 0xB89CFF, 12);
				}
				if (age % 4 == 0) {
					for (int i = 0; i < 10; i++) {
						Vec3 p = SpellKit.inDisc(c.level.random, ground, 10).add(0, 0.3, 0);
						MagicFx.one(c.level, p, dust, 0, 0.9 + c.level.random.nextDouble() * 0.6, 0);
					}
					MagicFx.cloud(c.level, core, MagicParticles.spark(0x6A3FA0, 0.4F, 14), 10, 6, 6, 6, 0.2);
				}
				if (age % 20 == 0) {
					MagicFx.core(c.level, core, 1.2F + age / 40.0F, 3.0F + age / 30.0F, ABYSS, 0xB89CFF, 22);
					MagicFx.soundFar(c.level, core, MagicSounds.RUMBLE, 128, 0.5F + age / 400.0F);
				}
				if (age >= pull) compress(c, core);
			});
		});
		// Луна висит полминуты и осыпается (временные блоки возвращаются сами).
		Zones.later(c, fly + pull + hang, () -> {
			MagicFx.burst(c.level, core, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 120, 4, 0.2);
			MagicFx.soundFar(c.level, core, MagicSounds.RUMBLE, 96, 0.7F);
		});
		return true;
	}

	/** Слой каменной луны (полая сфера радиуса {@code r}) из временных блоков. */
	private static void growMoon(Cast c, Vec3 core, double r, int ticks) {
		BlockPos o = BlockPos.containing(core);
		int ri = Mth.ceil(r);
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-ri, -ri, -ri), o.offset(ri, ri, ri))) {
			double d = Math.sqrt(p.distSqr(o));
			if (d > r || d < r - 1.1) continue;
			Terrain.temp(c, p, MOON[c.level.random.nextInt(MOON.length)], ticks + c.level.random.nextInt(20), false);
		}
	}

	/** Сжатие: 60 урона у ядра (боссам 40), белая вспышка, низкий удар. */
	private static void compress(Cast c, Vec3 core) {
		for (LivingEntity e : c.foesAround(core, 10)) c.hurt(e, c.amount(boss(e) ? 40.0F : 60.0F), MagicDamage.VOID);
		growMoon(c, core, 7.0, 600);
		MagicFx.flash(c.level, core, 160, 0.85F, 0xFFFFFF, 16);
		MagicFx.sphere(c.level, core, 14.0F, 6.0F, ABYSS, 0xB89CFF, 14);
		MagicFx.shockwave(c.level, core, SpellKit.UP, 7.0F, 48.0F, 0xE2C8FF, 24);
		MagicFx.shockwave(c.level, core, c.look(), 7.0F, 36.0F, 0xB89CFF, 20);
		MagicFx.soundFar(c.level, core, MagicSounds.EXPLOSION_HUGE, 192, 0.5F);
		MagicFx.soundFar(c.level, core, MagicSounds.SHOCKWAVE, 160, 0.6F);
		Magic.shake(c.level, core, 4.0F, 30, 128);
	}

	// =====================================================================================================
	// Общее
	// =====================================================================================================

	/** Боссы: получают урон, но не изгоняются, не застывают надолго и не швыряются. */
	static boolean boss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	private static Vec3 randomDir(Cast c) {
		double y = c.level.random.nextDouble() * 2 - 1;
		double a = c.level.random.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
	}
}
