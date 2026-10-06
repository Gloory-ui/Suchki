package com.suchki.magic;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Разум мобов под иллюзиями: Страх (бегут прочь и не нападают), Умиротворение (не нападают на игроков),
 * Бешенство (бросаются на других мобов), Двойник (все целятся в двойника). Работает через {@link Mob#setTarget}:
 * миксин спрашивает {@link #blockTarget}, можно ли мобу выбрать эту цель.
 */
public final class Minds {
	public enum Mode { FEAR, CALM, FRENZY, DECOY }

	record State(Mode mode, long until, Vec3 from, Entity decoy) {}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	/** Пока true — цель ставим мы сами, не блокируем. */
	private static boolean forcing;

	private Minds() {}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Minds::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// Ударили умиротворённого — он приходит в себя.
			if (entity instanceof Mob mob && source.getEntity() instanceof Player) {
				State s = STATES.get(mob);
				if (s != null && s.mode == Mode.CALM) STATES.remove(mob);
			}
		});
	}

	public static void apply(Mob mob, Mode mode, int ticks, Vec3 from, Entity decoy) {
		STATES.put(mob, new State(mode, mob.level().getGameTime() + ticks, from, decoy));
		if (mode != Mode.FRENZY) force(mob, mode == Mode.DECOY && decoy instanceof LivingEntity l ? l : null);
	}

	public static Mode modeOf(Mob mob) {
		State s = STATES.get(mob);
		return s == null ? null : s.mode;
	}

	public static void clear(Mob mob) {
		STATES.remove(mob);
	}

	/** Можно ли мобу выбрать цель {@code target}? (зовётся из миксина Mob.setTarget) */
	public static boolean blockTarget(Mob mob, LivingEntity target) {
		if (forcing || target == null) return false;
		// Призванные не нападают на своих.
		if (Minions.isSummon(mob) && Cast.friendly(target)) return true;
		State s = STATES.get(mob);
		if (s == null) return false;
		return switch (s.mode) {
			case FEAR -> true;
			case CALM, FRENZY -> Cast.friendly(target);
			case DECOY -> target != s.decoy && Cast.friendly(target);
		};
	}

	private static void force(Mob mob, LivingEntity target) {
		forcing = true;
		try {
			mob.setTarget(target);
			if (target == null) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		} finally {
			forcing = false;
		}
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) return;
		int t = server.getTickCount();
		STATES.entrySet().removeIf(e -> {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level) || level.getGameTime() >= s.until) return true;
			switch (s.mode) {
				case FEAR -> {
					if (mob.getTarget() != null) force(mob, null);
					if (mob instanceof PathfinderMob pm && (t % 10 == 0 || pm.getNavigation().isDone())) {
						Vec3 away = DefaultRandomPos.getPosAway(pm, 16, 7, s.from);
						if (away != null) pm.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
					}
					if (t % 8 == 0) MagicFx.burst(level, mob.getEyePosition().add(0, 0.4, 0), ParticleTypes.SMOKE, 2, 0.15, 0.0);
				}
				case CALM -> {
					if (mob.getTarget() instanceof Player) force(mob, null);
					if (t % 30 == 0) MagicFx.burst(level, mob.getEyePosition().add(0, 0.5, 0),
							MagicParticles.mote(School.ILLUSION.light, 0.12F, 20), 1, 0.2, 0.0);
				}
				case FRENZY -> {
					LivingEntity target = mob.getTarget();
					if (target == null || !target.isAlive() || target instanceof Player) {
						Mob other = nearestOther(level, mob);
						force(mob, other);
					}
					if (t % 10 == 0) MagicFx.burst(level, mob.getEyePosition().add(0, 0.5, 0), ParticleTypes.ANGRY_VILLAGER, 1, 0.2, 0.0);
				}
				case DECOY -> {
					if (!(s.decoy instanceof LivingEntity d) || !d.isAlive()) return true;
					if (mob.getTarget() != d) force(mob, d);
				}
			}
			return false;
		});
	}

	private static Mob nearestOther(ServerLevel level, Mob mob) {
		Mob best = null;
		double bestDist = Double.MAX_VALUE;
		for (Mob other : level.getEntitiesOfClass(Mob.class, new AABB(mob.blockPosition()).inflate(12), m -> m != mob && m.isAlive()
				&& !Cast.friendly(m))) {
			double d = other.distanceToSqr(mob);
			if (d < bestDist) {
				bestDist = d;
				best = other;
			}
		}
		return best;
	}

	/** Для самопроверки. */
	public static int count() {
		return STATES.size();
	}
}
