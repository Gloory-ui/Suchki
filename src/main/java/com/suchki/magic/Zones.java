package com.suchki.magic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Зоны (круг исцеления, святая земля, покров тьмы…) и отложенные удары (метеоры, волны землетрясения,
 * небесный суд). Живут только в памяти сервера — после перезапуска их нет, как и должно быть у магии.
 */
public final class Zones {
	/** Что делает зона каждый тик; {@code age} — сколько тиков ей уже. */
	public interface ZoneTick {
		void tick(Zone zone, int age);
	}

	public static final class Zone {
		public final Cast cast;
		public final Vec3 center;
		public final double radius;
		public final int life;
		final ZoneTick tick;
		int age;

		Zone(Cast cast, Vec3 center, double radius, int life, ZoneTick tick) {
			this.cast = cast;
			this.center = center;
			this.radius = radius;
			this.life = life;
			this.tick = tick;
		}
	}

	private record Delayed(Cast cast, long due, Runnable action) {}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final List<Delayed> DELAYED = new ArrayList<>();

	private Zones() {}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ZONES.clear();
			DELAYED.clear();
		});
	}

	public static Zone add(Cast cast, Vec3 center, double radius, int life, ZoneTick tick) {
		Zone z = new Zone(cast, center, radius, life, tick);
		ZONES.add(z);
		return z;
	}

	/** Выполнить через {@code ticks} тиков (если мир ещё загружен). */
	public static void later(Cast cast, int ticks, Runnable action) {
		DELAYED.add(new Delayed(cast, cast.level.getGameTime() + ticks, action));
	}

	private static void tick() {
		if (!ZONES.isEmpty()) {
			for (Zone z : List.copyOf(ZONES)) {
				z.age++;
				z.tick.tick(z, z.age);
			}
			ZONES.removeIf(z -> z.age >= z.life);
		}
		if (!DELAYED.isEmpty()) {
			List<Delayed> due = new ArrayList<>();
			Iterator<Delayed> it = DELAYED.iterator();
			while (it.hasNext()) {
				Delayed d = it.next();
				if (d.cast.level.getGameTime() >= d.due) {
					due.add(d);
					it.remove();
				}
			}
			for (Delayed d : due) d.action.run();
		}
	}

	/** Для самопроверки. */
	public static int active() {
		return ZONES.size() + DELAYED.size();
	}
}
