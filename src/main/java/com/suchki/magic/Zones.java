package com.suchki.magic;

import com.suchki.Suchki;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Зоны (круг исцеления, святая земля, чёрная дыра, буря…) и отложенные действия (метеоры, волны, этапы долгих
 * заклинаний). Живут только в памяти сервера — после перезапуска их нет, как и должно быть у магии.
 * Ошибка в одной зоне не роняет сервер: зона просто снимается и пишется в лог.
 */
public final class Zones {
	/** Что делает зона каждый тик; {@code age} — сколько тиков ей уже. */
	public interface ZoneTick {
		void tick(Zone zone, int age);
	}

	public static final class Zone {
		public final Cast cast;
		/** Центр зоны (можно двигать — например, бродячий смерч). */
		public Vec3 center;
		public final double radius;
		public final int life;
		final ZoneTick tick;
		int age;
		boolean stopped;

		Zone(Cast cast, Vec3 center, double radius, int life, ZoneTick tick) {
			this.cast = cast;
			this.center = center;
			this.radius = radius;
			this.life = life;
			this.tick = tick;
		}

		/** Снять зону раньше срока. */
		public void stop() {
			stopped = true;
		}

		public int age() {
			return age;
		}

		/** Зона ещё действует: не снята и не истекла. */
		public boolean alive() {
			return !stopped && age < life;
		}
	}

	private record Delayed(Cast cast, long due, Runnable action) {}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final List<Delayed> DELAYED = new ArrayList<>();
	/** Сдвиг «текущего времени» для самопроверки ({@link #runNow}). */
	private static long skew;

	private Zones() {}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	public static Zone add(Cast cast, Vec3 center, double radius, int life, ZoneTick tick) {
		Zone z = new Zone(cast, center, radius, life, tick);
		ZONES.add(z);
		return z;
	}

	/** Выполнить через {@code ticks} тиков (если мир ещё загружен). */
	public static void later(Cast cast, int ticks, Runnable action) {
		if (ticks <= 0) {
			safely(action);
			return;
		}
		DELAYED.add(new Delayed(cast, cast.level.getGameTime() + skew + ticks, action));
	}

	private static void safely(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			Suchki.LOGGER.error("Suchki: ошибка в действии заклинания", e);
		}
	}

	private static void tick() {
		if (!ZONES.isEmpty()) {
			for (Zone z : List.copyOf(ZONES)) {
				if (z.stopped) continue;
				z.age++;
				try {
					z.tick.tick(z, z.age);
				} catch (RuntimeException e) {
					Suchki.LOGGER.error("Suchki: ошибка в зоне заклинания {}", z.cast.spell.id, e);
					z.stopped = true;
				}
			}
			ZONES.removeIf(z -> z.stopped || z.age >= z.life);
		}
		if (!DELAYED.isEmpty()) {
			List<Delayed> due = new ArrayList<>();
			Iterator<Delayed> it = DELAYED.iterator();
			while (it.hasNext()) {
				Delayed d = it.next();
				if (d.cast.level.getGameTime() + skew >= d.due) {
					due.add(d);
					it.remove();
				}
			}
			for (Delayed d : due) safely(d.action);
		}
	}

	/** Для самопроверки: сколько зон и отложенных действий ждёт. */
	public static int active() {
		return ZONES.size() + DELAYED.size();
	}

	/** Для самопроверки: прокрутить зоны и отложенные действия на {@code ticks} тиков вперёд прямо сейчас. */
	public static void runNow(int ticks) {
		for (int i = 0; i < ticks; i++) {
			skew++;
			tick();
		}
	}

	/** Для самопроверки: всё снять. */
	public static void clear() {
		for (Zone z : ZONES) z.stopped = true;
		ZONES.clear();
		DELAYED.clear();
		skew = 0;
	}
}
