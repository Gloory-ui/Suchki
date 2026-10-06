package com.suchki.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.suchki.mixin.MobAccessor;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;

/**
 * Призванные существа: хозяин, время жизни, союзный ИИ. Метка хранится в самом существе (вложение
 * suchki:summon), поэтому после перезапуска сервера призванные тоже исчезнут вовремя и останутся союзниками.
 */
public final class Minions {
	public record Summon(UUID owner, long expires, int color) {
		static final Codec<Summon> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Summon::owner),
				Codec.LONG.fieldOf("expires").forGetter(Summon::expires),
				Codec.INT.optionalFieldOf("color", School.CONJURATION.color).forGetter(Summon::color)
		).apply(i, Summon::new));
	}

	public static final AttachmentType<Summon> SUMMON = AttachmentRegistry.<Summon>builder()
			.persistent(Summon.CODEC)
			.buildAndRegister(Magic.id("summon"));

	/** Загруженные сейчас призванные существа. */
	private static final Set<Entity> LOADED = new HashSet<>();
	/** Конь исчезает, если на нём никто не сидит столько тиков. */
	private static final int HORSE_IDLE = 200;
	private static final java.util.Map<Entity, Long> LAST_RIDDEN = new java.util.WeakHashMap<>();

	private Minions() {}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!entity.hasAttached(SUMMON)) return;
			LOADED.add(entity);
			if (entity instanceof Mob mob) configureAi(mob);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(Minions::tick);
	}

	public static boolean isSummon(Entity e) {
		return e.hasAttached(SUMMON);
	}

	public static boolean isOwnedBy(Entity e, Entity owner) {
		Summon s = e.getAttached(SUMMON);
		return s != null && s.owner().equals(owner.getUUID());
	}

	public static UUID ownerOf(Entity e) {
		Summon s = e.getAttached(SUMMON);
		return s == null ? null : s.owner();
	}

	/** Сделать существо призванным: хозяин, время жизни, союзный ИИ; добавить в мир с эффектом. */
	public static <T extends Entity> T summon(Cast c, T entity, int ticks) {
		entity.setAttached(SUMMON, new Summon(c.caster.getUUID(), c.level.getGameTime() + ticks, c.spell.school.color));
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			if (mob instanceof TamableAnimal t) t.tame(c.caster);
			if (mob instanceof Vex vex) vex.setLimitedLife(ticks + 20);
		}
		c.level.addFreshEntity(entity);
		MagicFx.summoned(c.level, entity, c.spell.school.color);
		return entity;
	}

	/** Союзный ИИ: защищает хозяина, бьёт то, что бьёт хозяин, и ближайших монстров; держится рядом. */
	static void configureAi(Mob mob) {
		if (mob instanceof TamableAnimal || mob instanceof AbstractHorse) return;
		MobAccessor acc = (MobAccessor) mob;
		acc.suchki$targetSelector().removeAllGoals(g -> true);
		acc.suchki$targetSelector().addGoal(1, new GuardOwnerGoal(mob));
		acc.suchki$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, Mob.class, 5, false, false,
				(target, level) -> target instanceof Enemy && !isSummon(target)));
		if (!(mob instanceof Vex)) acc.suchki$goalSelector().addGoal(2, new FollowSummonerGoal(mob));
	}

	private static ServerPlayer owner(Mob mob) {
		UUID id = ownerOf(mob);
		return id == null || !(mob.level() instanceof ServerLevel level) ? null : level.getServer().getPlayerList().getPlayer(id);
	}

	private static void tick(MinecraftServer server) {
		if (LOADED.isEmpty() || server.getTickCount() % 10 != 0) return;
		for (Entity e : Set.copyOf(LOADED)) {
			if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) {
				LOADED.remove(e);
				continue;
			}
			Summon s = e.getAttached(SUMMON);
			if (s == null) {
				LOADED.remove(e);
				continue;
			}
			boolean expired = level.getGameTime() >= s.expires();
			if (e instanceof AbstractHorse horse) {
				long now = level.getGameTime();
				if (horse.isVehicle()) LAST_RIDDEN.put(e, now);
				else if (now - LAST_RIDDEN.computeIfAbsent(e, k -> now) > HORSE_IDLE) expired = true;
			}
			if (expired) {
				MagicFx.poof(level, e, s.color());
				e.discard();
				LOADED.remove(e);
				continue;
			}
			// Призрачное мерцание.
			if (level.random.nextInt(3) == 0) {
				MagicFx.one(level, e.position().add((level.random.nextDouble() - 0.5) * e.getBbWidth(),
						level.random.nextDouble() * e.getBbHeight(), (level.random.nextDouble() - 0.5) * e.getBbWidth()),
						MagicParticles.spark(s.color(), 0.12F, 14), 0, 0.02, 0);
			}
		}
	}

	/** Цель — тот, кто ударил хозяина, или тот, кого хозяин ударил. */
	static final class GuardOwnerGoal extends TargetGoal {
		private LivingEntity candidate;

		GuardOwnerGoal(Mob mob) {
			super(mob, false);
			setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = owner(mob);
			if (owner == null) return false;
			LivingEntity attacker = owner.getLastHurtByMob();
			if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && valid(attacker)) {
				candidate = attacker;
				return true;
			}
			LivingEntity victim = owner.getLastHurtMob();
			if (victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && valid(victim)) {
				candidate = victim;
				return true;
			}
			return false;
		}

		private boolean valid(LivingEntity e) {
			return e.isAlive() && e != mob && !Cast.allyOf(owner(mob), e) && canAttack(e, TargetingConditions.DEFAULT);
		}

		@Override
		public void start() {
			mob.setTarget(candidate);
			super.start();
		}
	}

	/** Держаться рядом с хозяином, отставших — перенести к нему. */
	static final class FollowSummonerGoal extends Goal {
		private final Mob mob;
		private int recalc;

		FollowSummonerGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = owner(mob);
			return owner != null && mob.getTarget() == null && owner.level() == mob.level() && mob.distanceToSqr(owner) > 36;
		}

		@Override
		public boolean canContinueToUse() {
			ServerPlayer owner = owner(mob);
			return owner != null && mob.getTarget() == null && owner.level() == mob.level() && mob.distanceToSqr(owner) > 9;
		}

		@Override
		public void tick() {
			ServerPlayer owner = owner(mob);
			if (owner == null || --recalc > 0) return;
			recalc = 10;
			if (mob.distanceToSqr(owner) > 24 * 24) {
				mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
				mob.getNavigation().stop();
			} else {
				mob.getNavigation().moveTo(owner, 1.2);
			}
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}
	}
}
