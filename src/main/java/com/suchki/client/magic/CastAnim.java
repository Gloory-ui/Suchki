package com.suchki.client.magic;

import com.suchki.magic.CastStyle;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Состояние каста любого игрока на клиенте: какое заклинание, фаза (формирование, накопление, предел,
 * поток), выпуск. Из него считаются позы рук (от третьего и первого лица), эффекты у руки, звук и камера.
 * Зарядка берётся из ванильного «сколько тиков держит предмет» — она есть и у чужих игроков.
 */
public final class CastAnim {
	/** Сколько тиков длится анимация выпуска. */
	static final int RELEASE_TICKS = 11;
	static final int RITUAL_RELEASE_TICKS = 14;
	static final int FIZZLE_TICKS = 8;

	record Release(Spell spell, float power, long tick, boolean staff, HumanoidArm castArm, boolean fizzle) {}

	/** Последние выпуски по id игрока (для анимации после окончания каста). */
	static final Map<Integer, Release> RELEASES = new HashMap<>();

	/** Каст в одном кадре. */
	public static final class Pose {
		public Spell spell;
		public CastStyle style;
		public boolean staff;
		/** Рука, которая колдует (с посохом — рука с посохом, с гримуаром — свободная). */
		public HumanoidArm castArm;
		/** Насколько поза мага перекрывает обычную (плавно 0→1 в начале и 1→0 в конце). */
		public float weight;
		/** Формирование 0…1 (1 — заклинание готово). */
		public float form;
		/** Накопление 0…1 (1 — предел силы). */
		public float over;
		/** Удержание на пределе 0…1 (1 — сейчас сорвётся). */
		public float hold;
		/** Разгон потока 0…1. */
		public float channel;
		/** Выпуск 0…1 или −1, если ещё колдует. */
		public float release = -1;
		public float releasePower;
		public boolean fizzle;
		/** Время каста в тиках (с долей кадра) — для дрожи и вращения. */
		public float time;

		public boolean casting() {
			return release < 0;
		}

		/** Насколько сильно сияет рука/фокус (0…1+). */
		public float glow() {
			if (!casting()) return fizzle ? 0 : (1.0F - release) * (0.6F + 0.4F * Math.min(1.0F, releasePower / 2.0F));
			if (style == CastStyle.CHANNEL) return 0.5F + 0.5F * channel;
			return 0.25F * form + 0.55F * (form >= 1 ? 1 : 0) * 0.5F + 0.6F * over + 0.2F * hold;
		}

		/** Сила тряски от нестабильности (предел) 0…1. */
		public float instability() {
			return casting() ? 0.15F * over + 0.85F * hold : 0;
		}
	}

	private CastAnim() {}

	/** Чем колдовал игрок в последний раз (рука и вид фокуса) — пакет о выпуске приходит, когда каст уже кончился. */
	private record Grip(boolean staff, HumanoidArm castArm) {}

	private static final Map<Integer, Grip> GRIPS = new HashMap<>();

	static void onRelease(Player p, Spell spell, float power, boolean fizzle) {
		Grip grip = GRIPS.getOrDefault(p.getId(), new Grip(true, p.getMainArm()));
		long now = p.level().getGameTime();
		// Свой выпуск клиент предсказывает сразу; ответ сервера только уточняет силу, не перезапуская анимацию.
		Release prev = RELEASES.get(p.getId());
		long tick = prev != null && prev.spell == spell && now - prev.tick <= 6 ? prev.tick : now;
		RELEASES.put(p.getId(), new Release(spell, power, tick, grip.staff, grip.castArm, fizzle));
	}

	/** Был ли недавно выпуск этого заклинания у игрока (чтобы не дёргать камеру дважды). */
	static boolean releasedRecently(Player p, Spell spell) {
		Release prev = RELEASES.get(p.getId());
		return prev != null && prev.spell == spell && p.level().getGameTime() - prev.tick <= 6;
	}

	/** Поза каста игрока в этом кадре или null — не колдует и не выпускал недавно. */
	public static Pose compute(Player p, float pt) {
		String casting = p.getAttached(Magic.CASTING);
		ItemStack use = p.getUseItem();
		Release last = RELEASES.get(p.getId());
		long start = p.level().getGameTime() - p.getTicksUsingItem();
		// Выпуск, случившийся уже после начала этого каста, значит: каст кончился (флаги ещё не дошли).
		boolean ended = last != null && last.tick >= start;
		if (!ended && casting != null && !casting.isEmpty() && p.isUsingItem() && use.getItem() instanceof FocusItem focus) {
			Spell spell = Spells.get(casting);
			if (spell == null) return null;
			Pose pose = new Pose();
			pose.spell = spell;
			pose.style = spell.style;
			pose.staff = focus.staff;
			HumanoidArm held = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
			pose.castArm = focus.staff ? held : held.getOpposite();
			GRIPS.put(p.getId(), new Grip(pose.staff, pose.castArm));
			// Свежий каст отменяет хвост анимации прошлого выпуска.
			if (last != null) RELEASES.remove(p.getId());
			float t = p.getTicksUsingItem() + pt;
			pose.time = t;
			int form = Magic.formTicks(spell, Magic.castSpeed(p, focus));
			int over = Magic.overTicks(spell, form);
			pose.form = Mth.clamp(t / form, 0, 1);
			pose.over = Mth.clamp((t - form) / over, 0, 1);
			pose.hold = Mth.clamp((t - form - over) / Magic.HOLD_TICKS, 0, 1);
			pose.channel = Mth.clamp(t / Magic.CHANNEL_RAMP, 0, 1);
			pose.weight = smooth(Mth.clamp(t / 5.0F, 0, 1));
			return pose;
		}
		Release r = RELEASES.get(p.getId());
		if (r == null) return null;
		int len = r.fizzle ? FIZZLE_TICKS : r.spell.style == CastStyle.RITUAL ? RITUAL_RELEASE_TICKS : RELEASE_TICKS;
		float age = p.level().getGameTime() - r.tick + pt;
		if (age > len || age < 0) {
			if (age > len + 20) RELEASES.remove(p.getId());
			return null;
		}
		Pose pose = new Pose();
		pose.spell = r.spell;
		pose.style = r.spell.style;
		pose.staff = r.staff;
		pose.castArm = r.castArm;
		pose.form = 1;
		pose.release = age / len;
		pose.releasePower = r.power;
		pose.fizzle = r.fizzle;
		pose.time = age;
		// Конец выпуска — плавный возврат к обычной позе.
		pose.weight = 1.0F - smooth(Mth.clamp((pose.release - 0.55F) / 0.45F, 0, 1));
		return pose;
	}

	static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}

	static float easeOut(float x) {
		float k = 1 - x;
		return 1 - k * k * k;
	}

	/** Колокол 0→1→0 на участке [0, 1]. */
	static float bump(float x) {
		return x <= 0 || x >= 1 ? 0 : Mth.sin(x * Mth.PI);
	}

	/** Плавная «дрожь» — сумма синусов (без рывков случайного шума). */
	static float tremble(float t, float seed) {
		return 0.6F * Mth.sin(t * 2.7F + seed) + 0.4F * Mth.sin(t * 4.3F + seed * 1.7F + 1.0F);
	}

	/**
	 * Где в мире рука (или навершие посоха), которой колдуют. От первого лица — у камеры,
	 * от третьего — по повороту тела и типу каста.
	 */
	public static Vec3 handPos(Player p, Pose pose, float pt, boolean firstPerson) {
		Vec3 eye = p.getEyePosition(pt);
		Vec3 look = p.getViewVector(pt);
		float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float side = pose.castArm == HumanoidArm.RIGHT ? 1 : -1;
		if (firstPerson) {
			float lookYaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(lookYaw), 0, -Mth.sin(lookYaw));
			Vec3 up = right.cross(look).normalize().scale(-1);
			double fwd = pose.style == CastStyle.RITUAL || pose.style == CastStyle.SUMMON ? 0.7 : 0.95;
			double lift = pose.style == CastStyle.RITUAL || pose.style == CastStyle.SUMMON ? 0.15 + 0.35 * pose.form : pose.staff ? 0.05 : -0.08;
			double sideOff = pose.style == CastStyle.CHANNEL || pose.style == CastStyle.SELF ? 0.12 : 0.3;
			return eye.add(look.scale(fwd)).add(right.scale(sideOff * side)).add(up.scale(lift - 0.1));
		}
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 shoulder = p.getPosition(pt).add(0, p.isCrouching() ? 1.05 : 1.38, 0).add(right.scale(0.37 * side));
		double reach = pose.staff ? 1.15 : 0.75;
		return switch (pose.style) {
			case BOLT, CHANNEL -> shoulder.add(look.scale(reach)).add(right.scale(-0.12 * side));
			case SELF -> p.getPosition(pt).add(0, 1.15, 0).add(forward.scale(0.45));
			case RITUAL -> p.getPosition(pt).add(0, 1.38 + 0.9 * pose.form, 0).add(forward.scale(0.2));
			case SUMMON -> shoulder.add(0, 0.8 * pose.form + (pose.staff ? 0.5 : 0), 0);
		};
	}
}
