package com.suchki.client.magic;

import com.suchki.Suchki;
import com.suchki.SuchkiConfig;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.Spell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Заклинательная фраза на экране во время каста и выкрик названия на выпуске.
 *
 * <p>Фраза ({@link Spell#chantKey()}, строки через «|») печатается по буквам в такт построению круга: готовые
 * строки остаются, текущая допечатывается, последняя буква — ровно когда круг замкнулся. Стоит по центру над
 * хотбаром и строкой действий; если строк больше, чем помещается, старые уезжают вверх и гаснут.
 * Каждая буква: перелив цвета стихии (основной ↔ светлый ↔ белый бегущим бликом), лёгкое парение, дрожь,
 * растущая с накоплением и сильная на пределе; глитчи — буквы на миг становятся рунами (шрифт стола зачарований),
 * красно-голубое расслоение, сдвиг горизонтальных полос, рывки строки. Чем выше ранг, тем больше глитчей.
 * Круг замкнулся — вспышка текста; выпуск — название заклинания крупно по центру с ударным появлением и
 * глитчем (у рангов 5+ — крупнее и с хроматической аберрацией), фраза разлетается; срыв — фраза осыпается.
 *
 * <p>Для скриншотов и самопроверки есть {@code debug*}: показать любую фразу с любым прогрессом и замороженным
 * временем.
 */
public final class IncantationHud {
	static final Identifier ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "incantation");
	/** Руны — шрифт стола зачарований (в нём есть только латиница a–z). */
	private static final Style RUNES = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("alt")));
	private static final Component[] RUNE = new Component[26];
	/** Пауза между строками фразы (в «буквах» печати). */
	private static final float LINE_PAUSE = 3.0F;
	private static final float LINE_H = 12.0F;
	/** Сколько тиков фраза разлетается после выпуска (и осыпается после срыва). */
	private static final float END_TICKS = 10.0F;
	private static final float FIZZLE_TICKS = 14.0F;
	private static final float FLASH_TICKS = 10.0F;

	static {
		for (int i = 0; i < 26; i++) {
			RUNE[i] = Component.literal(String.valueOf((char) ('a' + i))).withStyle(RUNES);
		}
	}

	/** Что рисуем: фраза одного каста. */
	static final class Chant {
		String[] lines = new String[0];
		int color = 0xFFFFFF;
		int light = 0xFFFFFF;
		int tier = 1;
		/** Доля напечатанного 0…1 (= формирование круга). */
		float progress;
		float over;
		float hold;
		/** Тиков после «круг замкнулся» (−1 — не было). */
		float flash = -1;
		/** Тиков после выпуска/срыва (−1 — каст идёт). */
		float end = -1;
		boolean fizzle;
		/** Принудительная сила глитча (для скриншотов), 0 — по расписанию. */
		float burst;
		int seed;

		Chant copy() {
			Chant c = new Chant();
			c.lines = lines;
			c.color = color;
			c.light = light;
			c.tier = tier;
			c.progress = progress;
			c.over = over;
			c.hold = hold;
			c.flash = flash;
			c.end = end;
			c.fizzle = fizzle;
			c.burst = burst;
			c.seed = seed;
			return c;
		}
	}

	/** Выкрик названия заклинания на выпуске. */
	static final class Shout {
		String text = "";
		int color;
		int light;
		int tier;
		int seed;
		/** Когда начался (игровое время). */
		long start;
	}

	// --- Живое состояние своего каста ---
	private static Spell liveSpell;
	private static String[] liveLines = new String[0];
	private static long liveReadyAt = -1;
	private static float liveForm;
	private static int liveSeed;
	private static boolean liveShouted;
	/** Снимок фразы в момент выпуска/срыва — дорисовываем её распад. */
	private static Chant ending;
	private static long endingAt;
	private static Shout shout;
	private static Spell lastShoutSpell;
	private static long lastShoutAt = -100;

	// --- Отладка (скриншоты) ---
	private static Chant debugChant;
	private static Shout debugShout;
	private static float debugShoutAge;
	private static float debugTime = -1;

	private static final Map<String, String[]> CHANTS = new HashMap<>();
	private static String chantLang = "";

	private IncantationHud() {}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, ID, IncantationHud::render);
	}

	// --- Текст фразы ---

	/** Строки фразы заклинания в текущем языке (пусто — фразы нет). */
	public static String[] chantLines(Spell spell) {
		String lang = Minecraft.getInstance().getLanguageManager().getSelected();
		if (!lang.equals(chantLang)) {
			CHANTS.clear();
			chantLang = lang;
		}
		return CHANTS.computeIfAbsent(spell.id, id -> {
			String key = spell.chantKey();
			if (!I18n.exists(key)) return new String[0];
			return split(I18n.get(key));
		});
	}

	static String[] split(String text) {
		List<String> out = new ArrayList<>();
		for (String line : text.split("\\|")) {
			String t = line.trim();
			if (!t.isEmpty()) out.add(t);
		}
		return out.toArray(new String[0]);
	}

	/**
	 * Сколько букв каждой строки видно при доле {@code progress}: строки печатаются одна за другой с короткой
	 * паузой, последняя буква последней строки — ровно при 1.
	 */
	public static float[] reveal(String[] lines, float progress) {
		float[] out = new float[lines.length];
		if (lines.length == 0) return out;
		float total = 0;
		for (String l : lines) total += l.codePointCount(0, l.length());
		total += LINE_PAUSE * (lines.length - 1);
		float units = Mth.clamp(progress, 0, 1) * total;
		float start = 0;
		for (int i = 0; i < lines.length; i++) {
			int len = lines[i].codePointCount(0, lines[i].length());
			out[i] = Mth.clamp(units - start, 0, len);
			start += len + LINE_PAUSE;
		}
		if (progress >= 1) {
			for (int i = 0; i < lines.length; i++) out[i] = lines[i].codePointCount(0, lines[i].length());
		}
		return out;
	}

	// --- События каста (из MagicClient) ---

	/** Свой выпуск: фраза разлетается, название выкрикивается. */
	static void onRelease(Spell spell) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long now = mc.level.getGameTime();
		finishLive(now, false);
		if (spell.style != CastStyle.CHANNEL) startShout(spell, now);
	}

	/** Свой срыв: фраза осыпается; если выпуск уже предсказан, а сервер сказал «сорвалось» — выкрик отменяется. */
	static void onFizzle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long now = mc.level.getGameTime();
		finishLive(now, true);
		if (shout != null && now - shout.start <= 6) shout = null;
	}

	private static void finishLive(long now, boolean fizzle) {
		if (liveSpell == null) return;
		if (liveLines.length > 0) {
			Chant c = liveChant(now, 0);
			c.end = 0;
			c.fizzle = fizzle;
			ending = c;
			endingAt = now;
		}
		liveSpell = null;
	}

	private static void startShout(Spell spell, long now) {
		if (spell == lastShoutSpell && now - lastShoutAt <= 6) return;
		lastShoutSpell = spell;
		lastShoutAt = now;
		Shout s = new Shout();
		s.text = spell.displayName().getString();
		s.color = spell.color();
		s.light = spell.lightColor();
		s.tier = spell.tier;
		s.seed = spell.id.hashCode();
		s.start = now;
		shout = s;
	}

	/** Каждый тик клиента: следим за своим кастом (смена заклинания, «готово», глитч со звуком). */
	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			liveSpell = null;
			return;
		}
		long now = mc.level.getGameTime();
		Pose pose = CastAnim.compute(p, 0);
		if (pose != null && pose.casting()) {
			if (pose.spell != liveSpell) {
				liveSpell = pose.spell;
				liveLines = chantLines(pose.spell);
				liveReadyAt = -1;
				liveForm = 0;
				liveShouted = false;
				liveSeed = (int) (now * 31 + pose.spell.id.hashCode());
				ending = null;
			}
			if (pose.form >= 1 && liveForm < 1) {
				liveReadyAt = now;
				// Поток не «выпускается» — название звучит, когда круг замкнулся.
				if (pose.style == CastStyle.CHANNEL && !liveShouted) {
					liveShouted = true;
					startShout(pose.spell, now);
				}
			}
			liveForm = pose.form;
			// Сильный глитч надписи — щелчок и рывок камеры в такт.
			if (liveLines.length > 0 && enabled()) {
				float b = burst(now, pose.spell.tier, pose.over, pose.hold, liveSeed);
				float before = burst(now - 1, pose.spell.tier, pose.over, pose.hold, liveSeed);
				if (b > 0.55F && before <= 0.0F) {
					CastSounds.glitch(b);
					CastCamera.glitch(b * (0.4F + 0.1F * pose.spell.tier));
				}
			}
		} else if (liveSpell != null) {
			finishLive(now, false);
		}
		if (ending != null && now - endingAt > FIZZLE_TICKS + 2) ending = null;
		if (shout != null && now - shout.start > shoutTicks(shout.tier) + 2) shout = null;
	}

	private static Chant liveChant(long now, float pt) {
		Chant c = new Chant();
		c.lines = liveLines;
		c.color = liveSpell.color();
		c.light = liveSpell.lightColor();
		c.tier = liveSpell.tier;
		c.seed = liveSeed;
		Pose pose = CastAnim.compute(Minecraft.getInstance().player, pt);
		if (pose != null && pose.casting()) {
			c.progress = pose.form;
			c.over = pose.over;
			c.hold = pose.hold;
		} else {
			c.progress = liveForm;
		}
		c.flash = liveReadyAt >= 0 ? now - liveReadyAt + pt : -1;
		return c;
	}

	private static boolean enabled() {
		return SuchkiConfig.INSTANCE.showIncantations;
	}

	// --- Отладка для скриншотов ---

	/** Показать фразу (строки, цвета, ранг, доля напечатанного 0…1, накопление 0…1) вместо живой. */
	public static void debugShow(String[] lines, int color, int light, int tier, float progress, float over) {
		debugShow(lines, color, light, tier, progress, over, 0, -1, 0);
	}

	/**
	 * Полная версия: {@code hold} — удержание на пределе 0…1, {@code flashAge} — тиков после «готово» (−1 — нет),
	 * {@code burst} — принудительный глитч 0…1 (0 — по расписанию).
	 */
	public static void debugShow(String[] lines, int color, int light, int tier, float progress, float over, float hold, float flashAge,
			float burst) {
		Chant c = new Chant();
		c.lines = lines;
		c.color = color;
		c.light = light;
		c.tier = tier;
		c.progress = progress;
		c.over = over;
		c.hold = hold;
		c.flash = flashAge;
		c.burst = burst;
		c.seed = 12345;
		debugChant = c;
	}

	/** Показать распад фразы: {@code age} тиков после выпуска (или срыва — {@code fizzle}). */
	public static void debugEnd(String[] lines, int color, int light, int tier, float age, boolean fizzle) {
		debugShow(lines, color, light, tier, 1, 0.5F, 0, -1, 0);
		debugChant.end = age;
		debugChant.fizzle = fizzle;
	}

	/** Показать выкрик названия на возрасте {@code age} тиков. */
	public static void debugShout(String name, int color, int light, int tier, float age) {
		Shout s = new Shout();
		s.text = name;
		s.color = color;
		s.light = light;
		s.tier = tier;
		s.seed = name.hashCode();
		debugShout = s;
		debugShoutAge = age;
	}

	/** Заморозить время анимаций (переливы, дрожь, глитчи) — для повторяемых скриншотов; −1 — живое время. */
	public static void debugTime(float time) {
		debugTime = time;
	}

	public static void debugClear() {
		debugChant = null;
		debugShout = null;
		debugTime = -1;
	}

	/** Сила глитч-всплеска отладочной фразы в момент {@code time} (чтобы выбрать кадр с глитчем или без). */
	public static float debugBurstAt(float time, int tier, float over, float hold) {
		return burst(time, tier, over, hold, 12345);
	}

	/** Для самопроверки: показывается ли сейчас своя фраза/выкрик (живые). */
	public static boolean liveChantShown() {
		return liveSpell != null && liveLines.length > 0;
	}

	public static String liveShoutText() {
		return shout != null ? shout.text : null;
	}

	// --- Отрисовка ---

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.level == null) return;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float time = debugTime >= 0 ? debugTime : now + pt;
		Font font = mc.font;
		if (debugChant != null || debugShout != null) {
			if (debugChant != null) drawChant(g, font, debugChant, time);
			if (debugShout != null) drawShout(g, font, debugShout, debugShoutAge, time);
			return;
		}
		if (!enabled()) return;
		if (liveSpell != null && liveLines.length > 0) {
			drawChant(g, font, liveChant(now, pt), time);
		} else if (ending != null) {
			Chant c = ending.copy();
			c.end = now - endingAt + pt;
			if (c.end <= (c.fizzle ? FIZZLE_TICKS : END_TICKS)) drawChant(g, font, c, time);
		}
		if (shout != null) drawShout(g, font, shout, now - shout.start + pt, time);
	}

	/** Сила глитч-всплеска в момент {@code time}: 0 — нет; расписание детерминированное (по слотам в 6 тиков). */
	static float burst(float time, int tier, float over, float hold, int seed) {
		int slot = Mth.floor(time / 6.0F);
		float chance = 0.02F + 0.02F * tier + 0.1F * over + 0.22F * hold;
		if (rnd(slot, seed, 7) >= chance) return 0;
		float start = slot * 6.0F + rnd(slot, seed, 8) * 3.0F;
		float len = 1.5F + rnd(slot, seed, 9) * (1.0F + 0.2F * tier);
		float local = time - start;
		if (local < 0 || local > len) return 0;
		return Mth.clamp(0.45F + 0.4F * rnd(slot, seed, 10) + 0.06F * tier, 0, 1);
	}

	private static void drawChant(GuiGraphics g, Font font, Chant c, float time) {
		if (c.lines.length == 0) return;
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float[] shown = reveal(c.lines, c.progress);
		boolean ended = c.end >= 0;
		float endLen = c.fizzle ? FIZZLE_TICKS : END_TICKS;
		float endK = ended ? Mth.clamp(c.end / endLen, 0, 1) : 0;
		if (endK >= 1) return;
		// Текущая строка — последняя, где уже что-то напечатано.
		int cur = 0;
		for (int i = 0; i < c.lines.length; i++) if (shown[i] > 0) cur = i;
		if (shown[0] <= 0 && !ended) return;
		// Сколько строк помещается между прицелом и строкой действий.
		float bottom = gh - 86;
		float top = gh / 2.0F + 20;
		int maxVis = Mth.clamp(Mth.floor((bottom - top) / LINE_H), 2, 7);
		int firstVis = Math.max(0, cur - maxVis + 1);
		// Новая строка въезжает снизу за первые буквы, верхняя в это время уходит и гаснет.
		float enter = cur > 0 ? CastAnim.easeOut(Mth.clamp(shown[cur] / 2.5F, 0, 1)) : 1;
		float shift = (1 - enter) * LINE_H;
		boolean leaving = cur - maxVis >= 0 && enter < 1;
		int from = leaving ? cur - maxVis : firstVis;

		float rank = 1.0F + 0.05F * (c.tier - 1);
		float burst = c.burst > 0 ? c.burst : burst(time, c.tier, c.over, c.hold, c.seed);
		if (ended) burst = Math.max(burst, 0.6F * (1 - endK) + (c.fizzle ? 0.4F : 0));
		float flash = c.flash >= 0 && c.flash < FLASH_TICKS ? (1 - c.flash / FLASH_TICKS) * (1 - c.flash / FLASH_TICKS) : 0;
		float alphaAll = ended ? 1 - endK * endK : 1;

		// Подложка: мягкое тёмное пятно и цветное свечение стихии (растёт с накоплением, вспыхивает на «готово»).
		int widest = 0;
		for (int i = from; i <= cur; i++) widest = Math.max(widest, font.width(c.lines[i]));
		widest = Math.min(widest, gw - 24);
		int rows = cur - firstVis + 1;
		float blockH = rows * LINE_H;
		float cx = gw / 2.0F;
		float cy = bottom - blockH / 2.0F;
		softRect(g, cx, cy, widest + 70, blockH + 4, 34, 11, 0x06030E, 0.5F * alphaAll);
		glowBlob(g, cx, cy, widest + 40, blockH + 26, c.color, (0.14F + 0.14F * c.over + 0.14F * c.hold + 0.7F * flash) * alphaAll);
		// Рамка: тонкие черты цвета стихии сверху и снизу, гаснут к краям; по верхней бежит строка рун.
		float panelTop = cy - blockH / 2.0F - 6;
		float panelBottom = bottom + 2;
		float lineW = widest + 50 + 30 * c.over;
		hairline(g, cx, panelTop, lineW, c.light, (0.45F + 0.35F * c.over) * alphaAll);
		hairline(g, cx, panelBottom, lineW * 0.8F, c.color, (0.5F + 0.3F * c.over) * alphaAll);
		// На маленьком экране руны над рамкой залезли бы на прицел.
		if (panelTop - 9 > gh / 2.0F + 8) runeTicker(g, font, cx, panelTop - 5, lineW, c, time, alphaAll);
		if (flash > 0) {
			// Вспышка «круг замкнулся»: светлые черты разлетаются в стороны сверху и снизу.
			float w = (widest + 40) * (0.6F + 0.9F * (1 - flash));
			hairline(g, cx, panelBottom + 2, w, 0xFFFFFF, 0.95F * flash);
			hairline(g, cx, panelTop - 2, w * 0.8F, 0xFFFFFF, 0.8F * flash);
			hairline(g, cx, panelBottom + 4, w * 0.6F, c.light, 0.6F * flash);
		}

		int gi = 0;
		for (int i = 0; i < c.lines.length; i++) {
			int len = c.lines[i].codePointCount(0, c.lines[i].length());
			if (i >= from && i <= cur) {
				int row = cur - i;
				float y = bottom - LINE_H * (row + 1) + 2 + shift;
				float lineA = alphaAll;
				if (i < firstVis) lineA *= 1 - enter;
				else if (row > 0) lineA *= 0.92F - 0.06F * Math.min(3, row - 1);
				if (i == cur && cur > 0) lineA *= Mth.clamp(enter * 1.5F, 0, 1);
				drawLine(g, font, c, i, gi, shown[i], y, lineA, time, rank, burst, flash, endK, cur == i);
			}
			gi += len;
		}
		// Сканлинии во время всплеска.
		if (burst > 0.3F) {
			int a = Math.round(40 * burst * alphaAll);
			int x0 = Math.round(cx - widest / 2.0F - 10);
			int x1 = Math.round(cx + widest / 2.0F + 10);
			for (float y = bottom - blockH; y < bottom; y += 3) {
				g.fill(x0, Math.round(y), x1, Math.round(y) + 1, (a << 24) | c.light);
			}
		}
	}

	/** Одна строка фразы: буква за буквой, с полосами-сдвигами при глитче. */
	private static void drawLine(GuiGraphics g, Font font, Chant c, int line, int gi0, float shown, float y, float alpha, float time,
			float rank, float burst, float flash, float endK, boolean current) {
		if (alpha <= 0.02F) return;
		String text = c.lines[line];
		int gw = g.guiWidth();
		int[] cps = text.codePoints().toArray();
		float[] adv = new float[cps.length];
		float width = 0;
		for (int j = 0; j < cps.length; j++) {
			adv[j] = font.width(new String(Character.toChars(cps[j])));
			width += adv[j];
		}
		float scale = Math.min(1.0F, (gw - 24) / Math.max(1.0F, width));
		float x0 = gw / 2.0F - width * scale / 2.0F;
		Glyphs gl = layout(c, cps, adv, gi0, shown, x0, y, scale, alpha, time, rank, burst, flash, endK);
		// Курсор печати — тонкая мигающая черта на месте следующей буквы.
		if (current && c.end < 0 && c.progress < 1 && gl.cursorX >= 0 && (Mth.floor(time / 4) & 1) == 0) {
			int a = Math.round(220 * alpha);
			g.fill(Math.round(gl.cursorX), Math.round(y - 1), Math.round(gl.cursorX) + 1, Math.round(y + 8), (a << 24) | c.light);
		}
		// Рывок всей строки при всплеске (не у каждой строки).
		int slot = Mth.floor(time / 6.0F);
		boolean hit = burst > 0 && rnd(line, slot, c.seed + 11) < 0.45F + 0.3F * burst;
		float lineJx = hit ? (rnd(line, Mth.floor(time * 3), c.seed + 12) * 2 - 1) * 3.5F * burst * rank : 0;
		int yi = Math.round(y);
		if (c.end >= 0) {
			// Распад: буквы разлетаются далеко — без полос.
			gl.draw(g, font, lineJx, Integer.MIN_VALUE);
		} else if (hit && burst > 0.4F) {
			// Строка режется на полосы, каждая сдвинута по-своему.
			int[] cuts = {yi - 10, yi + 2, yi + 5, yi + 8, yi + 20};
			for (int b = 0; b < cuts.length - 1; b++) {
				float dx = (rnd(line * 7 + b, Mth.floor(time * 4), c.seed + 13) * 2 - 1) * 6.0F * burst;
				g.enableScissor(0, cuts[b], gw, cuts[b + 1]);
				gl.draw(g, font, lineJx + dx, yi + 4);
				g.disableScissor();
			}
		} else {
			gl.draw(g, font, lineJx, yi + 4);
		}
	}

	/** Разметка букв строки (позиции, цвета, руны) — рисуется несколькими проходами одинаково. */
	private static final class Glyphs {
		int n;
		float[] x;
		float[] y;
		float[] scale;
		int[] main;
		int[] top;
		int[] alpha;
		String[] str;
		int[] rune;
		float[] split;
		float cursorX = -1;
		/** Сжатие по вертикали относительно горизонтали (выкрик растягивается к концу). */
		float sy = 1;
		/** Цвет обводки букв выкрика. */
		int outline;

		Glyphs(int cap) {
			x = new float[cap];
			y = new float[cap];
			scale = new float[cap];
			main = new int[cap];
			top = new int[cap];
			alpha = new int[cap];
			str = new String[cap];
			rune = new int[cap];
			split = new float[cap];
		}

		/**
		 * Проходы: красно-голубое расслоение → буква с тенью → верхняя половина светлее (двухцветная «аниме»-заливка,
		 * обрезка по {@code midY}; {@code Integer.MIN_VALUE} — без неё).
		 */
		void draw(GuiGraphics g, Font font, float dx, int midY) {
			for (int i = 0; i < n; i++) {
				if (split[i] <= 0) continue;
				int sa = Math.round(alpha[i] * Math.min(0.7F, 0.2F + 0.5F * split[i]));
				float off = 0.5F + 1.1F * split[i];
				glyph(g, font, i, x[i] + dx - off, (sa << 24) | 0xFF2850, false);
				glyph(g, font, i, x[i] + dx + off, (sa << 24) | 0x28E8FF, false);
			}
			for (int i = 0; i < n; i++) glyph(g, font, i, x[i] + dx, (alpha[i] << 24) | main[i], true);
			if (midY == Integer.MIN_VALUE) return;
			g.enableScissor(0, midY - 40, g.guiWidth(), midY);
			for (int i = 0; i < n; i++) glyph(g, font, i, x[i] + dx, (alpha[i] << 24) | top[i], false);
			g.disableScissor();
		}

		/** Выкрик: аберрация → тёмная обводка в четыре стороны → буква → светлая верхняя половина. */
		void drawShout(GuiGraphics g, Font font, float dx, int midY) {
			for (int i = 0; i < n; i++) {
				if (split[i] <= 0.05F) continue;
				int sa = Math.round(alpha[i] * Math.min(0.75F, 0.3F + 0.35F * split[i]));
				float off = (0.4F + 0.9F * split[i]) * scale[i];
				glyph(g, font, i, x[i] + dx - off, (sa << 24) | 0xFF2048, false);
				glyph(g, font, i, x[i] + dx + off, (sa << 24) | 0x20E0FF, false);
			}
			for (int i = 0; i < n; i++) {
				int dark = (Math.round(alpha[i] * 0.9F) << 24) | outline;
				float o = scale[i] * 0.55F;
				glyphAt(g, font, i, x[i] + dx - o, y[i], dark);
				glyphAt(g, font, i, x[i] + dx + o, y[i], dark);
				glyphAt(g, font, i, x[i] + dx, y[i] - o * sy, dark);
				glyphAt(g, font, i, x[i] + dx, y[i] + o * sy, dark);
			}
			for (int i = 0; i < n; i++) glyph(g, font, i, x[i] + dx, (alpha[i] << 24) | main[i], false);
			g.enableScissor(0, midY - 200, g.guiWidth(), midY);
			for (int i = 0; i < n; i++) glyph(g, font, i, x[i] + dx, (alpha[i] << 24) | top[i], false);
			g.disableScissor();
		}

		private void glyphAt(GuiGraphics g, Font font, int i, float gx, float gy, int argb) {
			float keep = y[i];
			y[i] = gy;
			glyph(g, font, i, gx, argb, false);
			y[i] = keep;
		}

		private void glyph(GuiGraphics g, Font font, int i, float gx, int argb, boolean shadow) {
			g.pose().pushMatrix();
			g.pose().translate(gx, y[i]);
			g.pose().scale(scale[i], scale[i] * sy);
			if (rune[i] >= 0) {
				g.drawString(font, RUNE[rune[i]], -font.width(RUNE[rune[i]]) / 2, -4, argb, shadow);
			} else {
				g.drawString(font, str[i], -font.width(str[i]) / 2, -4, argb, shadow);
			}
			g.pose().popMatrix();
		}
	}

	private static Glyphs layout(Chant c, int[] cps, float[] adv, int gi0, float shown, float x0, float y, float scale, float alpha, float time,
			float rank, float burst, float flash, float endK) {
		Glyphs gl = new Glyphs(cps.length);
		boolean ended = c.end >= 0;
		int tick2 = Mth.floor(time * 2);
		// Дрожь растёт с накоплением, на пределе — сильная; глитчей тем больше, чем выше ранг.
		float vib = (0.05F + 0.15F * c.over + 0.35F * c.hold) * rank + 0.35F * burst;
		float shake = (0.08F + 0.45F * c.over + 1.3F * c.hold) * rank;
		float shakeX = (rnd(gi0, tick2, c.seed + 41) * 2 - 1) * shake;
		float shakeY = (rnd(gi0, tick2, c.seed + 42) * 2 - 1) * shake * 0.7F;
		float replaceChance = 0.002F + 0.0025F * c.tier + 0.015F * c.over + 0.04F * c.hold + 0.12F * burst + (ended ? endK * 0.7F : 0);
		float splitBase = 0.5F * burst + 0.3F * c.hold + 0.6F * flash + (c.tier >= 6 ? 0.1F * c.over : 0);
		float sweepPeriod = 46.0F - 2.0F * c.tier;
		float sweepX = ((time % sweepPeriod) / sweepPeriod) * 1.6F - 0.3F;
		int deep = MagicHud.mix(c.color, 0x000000, 0.15F);
		float x = x0;
		for (int j = 0; j < cps.length; j++) {
			float advJ = adv[j] * scale;
			float appear = Mth.clamp(shown - j, 0, 1);
			if (appear <= 0) {
				gl.cursorX = x;
				break;
			}
			int gi = gi0 + j;
			int cp = cps[j];
			if (cp == ' ') {
				x += advJ;
				continue;
			}
			// Свежая буква: вспыхивает белым и «падает» в строку с увеличения.
			float fresh = Mth.clamp(1 - (shown - j) / 4.0F, 0, 1);
			float pop = 1.0F + 0.7F * fresh * fresh + 0.12F * flash;
			// Перелив: волна основной ↔ светлый по строке и бегущий белый блик.
			float u = (j + 0.5F) / Math.max(1, cps.length);
			float wave = 0.5F + 0.5F * Mth.sin(u * 6.0F - time * 0.22F + gi * 0.15F);
			int col = MagicHud.mix(c.light, deep, 0.08F + 0.4F * wave);
			float band = (u - sweepX) / 0.07F;
			col = MagicHud.mix(col, 0xFFFFFF, 0.85F * (float) Math.exp(-band * band));
			col = MagicHud.mix(col, 0xFFFFFF, Math.max(fresh * 0.9F, flash));
			if (c.fizzle && ended) col = MagicHud.mix(col, 0xFF3A3A, Math.min(1, endK * 2));
			// Парение, дрожь, распад.
			float fl = Mth.sin(time * 0.18F + gi * 0.55F) * 0.7F;
			float jx = (rnd(gi, tick2, c.seed + 1) * 2 - 1) * vib + shakeX;
			float jy = (rnd(gi, tick2, c.seed + 2) * 2 - 1) * vib * 0.8F + shakeY;
			float dx = 0, dy = fl - 2.5F * fresh;
			if (ended) {
				float r = rnd(gi, 3, c.seed + 5);
				if (c.fizzle) {
					// Срыв: буквы осыпаются вниз, краснеют, гаснут.
					dy += c.end * c.end * (0.12F + 0.12F * r);
					dx += (r - 0.5F) * c.end * 0.8F;
				} else {
					// Выпуск: буквы взлетают вверх и разлетаются.
					dy -= c.end * c.end * (0.2F + 0.25F * r);
					dx += (r - 0.5F) * c.end * 3.0F;
				}
			}
			int alphaI = Math.round(255 * Mth.clamp(alpha * appear, 0, 1));
			if (alphaI < 5) {
				x += advJ;
				continue;
			}
			boolean rune = rnd(gi, Mth.floor(time / 2), c.seed + 3) < replaceChance;
			int i = gl.n++;
			gl.x[i] = x + advJ / 2 + jx + dx;
			gl.y[i] = y + 4 + jy + dy;
			gl.scale[i] = scale * pop;
			gl.alpha[i] = alphaI;
			gl.str[i] = new String(Character.toChars(cp));
			gl.rune[i] = rune ? (int) (rnd(gi, Mth.floor(time / 2), c.seed + 4) * 26) % 26 : -1;
			gl.main[i] = rune ? MagicHud.mix(c.light, 0xFFFFFF, 0.4F) : col;
			gl.top[i] = MagicHud.mix(gl.main[i], 0xFFFFFF, 0.55F);
			gl.split[i] = rune ? Math.max(0.5F, splitBase * 1.6F) : splitBase > 0.12F ? splitBase : 0;
			x += advJ;
		}
		return gl;
	}

	/** Сколько тиков длится выкрик (у высоких рангов — дольше). */
	static float shoutTicks(int tier) {
		return tier >= 5 ? 36 : 30;
	}

	/** Название заклинания крупно по центру снизу: удар-появление, глитч, перелив, растяжка и угасание. */
	private static void drawShout(GuiGraphics g, Font font, Shout s, float age, float time) {
		float dur = shoutTicks(s.tier);
		if (age < 0 || age > dur || s.text.isEmpty()) return;
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		int[] cps = s.text.codePoints().toArray();
		float[] adv = new float[cps.length];
		float width = 0;
		for (int j = 0; j < cps.length; j++) {
			adv[j] = font.width(new String(Character.toChars(cps[j])));
			width += adv[j];
		}
		float tracking = 0.5F + 1.2F * Mth.clamp(age / dur, 0, 1);
		float fullW = width + tracking * (cps.length - 1);
		boolean big = s.tier >= 5;
		float base = s.tier >= 6 ? 3.6F : big ? 3.2F : s.tier >= 3 ? 2.6F : 2.0F;
		float scale = Math.min(base, (gw - 30) / Math.max(1.0F, fullW));
		float cx = gw / 2.0F;
		float cy = gh / 2.0F + Math.max(30, gh * 0.14F);

		float in = CastAnim.easeOut(Mth.clamp(age / 3.0F, 0, 1));
		float out = Mth.clamp((age - (dur - 8)) / 8.0F, 0, 1);
		float alpha = Mth.clamp(age / 1.5F, 0, 1) * (1 - out * out);
		if (alpha <= 0.02F) return;
		float glitch = Mth.clamp(1 - age / 7.0F, 0, 1) + 0.8F * out;
		// Удар: влетает с увеличения, отскок, к концу растягивается по горизонтали и сплющивается.
		float slam = 1.0F + 0.9F * (1 - in) + 0.07F * CastAnim.bump(Mth.clamp((age - 3) / 5.0F, 0, 1));
		float sx = scale * slam * (1 + 0.7F * out);
		float sy = scale * slam * (1 - 0.75F * out);
		float halfW = fullW * sx / 2.0F;
		float halfH = 9 * sy / 2.0F;

		// Кадр удара (ранг 5+): весь экран на миг окрашивается цветом стихии.
		if (big && age < 3) {
			int fa = Math.round(70 * (1 - age / 3.0F));
			g.fill(0, 0, gw, gh, (fa << 24) | s.light);
		}
		// Лента за названием (у больших — тёмная полоса во всю ширину), свечение стихии.
		if (big) {
			int core = Math.round(halfH + 5);
			int ba = Math.round(175 * alpha);
			g.fillGradient(0, Math.round(cy) - core - 12, gw, Math.round(cy) - core, 0x0005020C, (ba << 24) | 0x05020C);
			g.fill(0, Math.round(cy) - core, gw, Math.round(cy) + core, (ba << 24) | 0x05020C);
			g.fillGradient(0, Math.round(cy) + core, gw, Math.round(cy) + core + 12, (ba << 24) | 0x05020C, 0x0005020C);
		} else {
			glowBlob(g, cx, cy, fullW * sx + 70, 9 * sy + 30, 0x000000, 0.6F * alpha);
		}
		glowBlob(g, cx, cy, fullW * sx + 50, 9 * sy + 26, s.color, (0.3F + 0.4F * (1 - in)) * alpha);
		// Черты-«клинки» сверху и снизу и разлетающиеся штрихи скорости.
		float line = CastAnim.easeOut(Mth.clamp(age / 5.0F, 0, 1));
		float lw = (halfW + 34) * line;
		int la = Math.round(235 * alpha * (1 - 0.5F * out));
		int ly = Math.round(cy + halfH + 4);
		g.fill(Math.round(cx - lw), ly, Math.round(cx + lw), ly + 1, (la << 24) | s.light);
		g.fill(Math.round(cx - lw * 0.6F), ly + 2, Math.round(cx + lw * 0.6F), ly + 3, (la / 2 << 24) | s.color);
		int uy = Math.round(cy - halfH - 5);
		g.fill(Math.round(cx - lw * 0.8F), uy, Math.round(cx + lw * 0.8F), uy + 1, (la / 2 << 24) | s.light);
		if (s.tier >= 3 && age < 16) {
			float k = 1 - age / 16.0F;
			int count = 6 + 2 * s.tier;
			for (int i = 0; i < count; i++) {
				float yy = cy + (rnd(i, 1, s.seed + 30) * 2 - 1) * (halfH + 10);
				float start = halfW * 0.7F + age * (5 + 9 * rnd(i, 2, s.seed + 30));
				float len = (14 + 36 * rnd(i, 3, s.seed + 30)) * k;
				int a = Math.round(170 * k * alpha);
				int side = (i & 1) == 0 ? 1 : -1;
				float x0 = cx + side * start;
				float x1 = x0 + side * len;
				g.fill(Math.round(Math.min(x0, x1)), Math.round(yy), Math.round(Math.max(x0, x1)), Math.round(yy) + 1,
						(a << 24) | ((i % 3) == 0 ? 0xFFFFFF : s.light));
			}
		}

		Glyphs gl = shoutLayout(s, cps, adv, tracking, fullW, cx, cy, sx, sy, alpha, age, time, glitch, in, big);
		int mid = Math.round(cy);
		// Глитч в начале и в конце: полосы, сдвинутые в стороны.
		if (glitch > 0.15F) {
			int slices = 5;
			float top = cy - halfH - 4;
			float sliceH = (halfH * 2 + 8) / slices;
			for (int b = 0; b < slices; b++) {
				float dx = (rnd(b, Mth.floor(time * 3), s.seed + 21) * 2 - 1) * 16.0F * glitch;
				g.enableScissor(0, Mth.floor(top + b * sliceH), gw, Mth.ceil(top + (b + 1) * sliceH));
				gl.drawShout(g, font, dx, mid);
				g.disableScissor();
			}
		} else {
			gl.drawShout(g, font, 0, mid);
		}
	}

	private static Glyphs shoutLayout(Shout s, int[] cps, float[] adv, float tracking, float fullW, float cx, float cy, float sx, float sy,
			float alpha, float age, float time, float glitch, float in, boolean big) {
		Glyphs gl = new Glyphs(cps.length);
		gl.sy = sy / sx;
		int tick2 = Mth.floor(time * 2);
		int A = Math.round(255 * alpha);
		int deep = MagicHud.mix(s.color, 0x000000, 0.2F);
		float x = -fullW / 2.0F;
		for (int j = 0; j < cps.length; j++) {
			int cp = cps[j];
			if (cp == ' ') {
				x += adv[j] + tracking;
				continue;
			}
			float u = (j + 0.5F) / cps.length;
			// Первые тики — белый удар, дальше перелив стихии с бегущим бликом.
			float wave = 0.5F + 0.5F * Mth.sin(u * 5.0F - time * 0.3F);
			int col = MagicHud.mix(s.light, deep, 0.2F + 0.55F * wave);
			float sweep = (u - ((age / 14.0F) % 1.4F - 0.2F)) / 0.08F;
			col = MagicHud.mix(col, 0xFFFFFF, Math.max(1 - in, 0.9F * (float) Math.exp(-sweep * sweep)));
			float vib = 0.12F + 0.5F * glitch + (big ? 0.12F : 0);
			float jx = (rnd(j, tick2, s.seed + 1) * 2 - 1) * vib;
			float jy = (rnd(j, tick2, s.seed + 2) * 2 - 1) * vib;
			float fl = Mth.sin(time * 0.2F + j * 0.6F) * 0.3F;
			boolean rune = rnd(j, Mth.floor(time / 2), s.seed + 3) < 0.4F * glitch;
			int i = gl.n++;
			gl.x[i] = cx + (x + adv[j] / 2.0F + jx) * sx;
			gl.y[i] = cy + (jy + fl) * sy;
			gl.scale[i] = sx;
			gl.alpha[i] = A;
			gl.str[i] = new String(Character.toChars(cp));
			gl.rune[i] = rune ? (int) (rnd(j, Mth.floor(time / 2), s.seed + 4) * 26) % 26 : -1;
			gl.main[i] = col;
			gl.top[i] = MagicHud.mix(col, 0xFFFFFF, 0.6F);
			// Хроматическая аберрация (ранг 5+) и красно-голубое расслоение на глитче.
			gl.split[i] = (big ? 0.6F : 0) + 1.2F * glitch;
			gl.outline = MagicHud.mix(s.color, 0x000000, 0.8F);
			x += adv[j] + tracking;
		}
		return gl;
	}

	/**
	 * Мягкая подложка под фразу: ровная середина, края по горизонтали ({@code fadeX}) и по вертикали ({@code fadeY})
	 * плавно гаснут (края — из узких полос с убывающей прозрачностью).
	 */
	static void softRect(GuiGraphics g, float cx, float cy, float w, float h, float fadeX, float fadeY, int rgb, float alpha) {
		if (alpha <= 0.01F) return;
		int a = Math.round(255 * Mth.clamp(alpha, 0, 1));
		int top = Math.round(cy - h / 2);
		int bottom = Math.round(cy + h / 2);
		int fy = Math.round(fadeY);
		int inner0 = Math.round(cx - w / 2 + fadeX);
		int inner1 = Math.round(cx + w / 2 - fadeX);
		column(g, inner0, inner1, top, bottom, fy, rgb, a);
		int strips = Math.max(6, Math.round(fadeX));
		for (int i = 0; i < strips; i++) {
			float k = (i + 0.5F) / strips;
			int sa = Math.round(a * k * k * (3 - 2 * k));
			int x0 = Math.round(cx - w / 2 + fadeX * i / strips);
			int x1 = Math.round(cx - w / 2 + fadeX * (i + 1) / strips);
			column(g, x0, x1, top, bottom, fy, rgb, sa);
			int r0 = Math.round(cx + w / 2 - fadeX * (i + 1) / strips);
			int r1 = Math.round(cx + w / 2 - fadeX * i / strips);
			column(g, r0, r1, top, bottom, fy, rgb, sa);
		}
	}

	/** Столбец подложки: сплошной между top и bottom, сверху и снизу — растушёвка на {@code fy}. */
	private static void column(GuiGraphics g, int x0, int x1, int top, int bottom, int fy, int rgb, int a) {
		if (x1 <= x0 || a <= 1) return;
		int argb = (a << 24) | rgb;
		g.fillGradient(x0, top - fy, x1, top, rgb, argb);
		g.fill(x0, top, x1, bottom, argb);
		g.fillGradient(x0, bottom, x1, bottom + fy, argb, rgb);
	}

	/** Тонкая горизонтальная черта шириной {@code w} по центру {@code cx}, гаснущая к концам. */
	static void hairline(GuiGraphics g, float cx, float y, float w, int rgb, float alpha) {
		if (alpha <= 0.01F || w <= 2) return;
		int a = Math.round(255 * Mth.clamp(alpha, 0, 1));
		int yi = Math.round(y);
		int seg = 10;
		for (int i = 0; i < seg; i++) {
			float k = 1 - Math.abs((i + 0.5F) / seg * 2 - 1);
			int sa = Math.round(a * Math.min(1, k * 1.8F));
			if (sa <= 1) continue;
			int x0 = Math.round(cx - w / 2 + w * i / seg);
			int x1 = Math.round(cx - w / 2 + w * (i + 1) / seg);
			g.fill(x0, yi, x1, yi + 1, (sa << 24) | rgb);
		}
	}

	/** Строка мелких рун, бегущая вдоль рамки фразы (как надпись по кругу), гаснет к краям. */
	private static void runeTicker(GuiGraphics g, Font font, float cx, float y, float w, Chant c, float time, float alpha) {
		float step = 7.0F;
		float speed = 0.35F + 0.25F * c.over + 0.6F * c.hold;
		float offset = (time * speed) % step;
		int n = (int) (w / step);
		g.pose().pushMatrix();
		g.pose().translate(0, y);
		for (int i = -1; i <= n; i++) {
			float x = cx - w / 2 + i * step + offset;
			float u = (x - (cx - w / 2)) / w;
			if (u < 0 || u > 1) continue;
			float k = 1 - Math.abs(u * 2 - 1);
			int a = Math.round(255 * alpha * Math.min(1, k * 2.0F) * (0.6F + 0.25F * c.over + 0.15F * c.hold));
			if (a < 5) continue;
			int idx = (int) (rnd(i - Mth.floor(time * speed / step), 77, c.seed) * 26) % 26;
			g.pose().pushMatrix();
			g.pose().translate(x, 0);
			g.pose().scale(0.6F, 0.6F);
			g.drawString(font, RUNE[idx], -font.width(RUNE[idx]) / 2, -4, (a << 24) | c.light, false);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	/** Мягкое пятно (текстура искры) — подложка и свечение. */
	static void glowBlob(GuiGraphics g, float cx, float cy, float w, float h, int rgb, float alpha) {
		if (alpha <= 0.01F) return;
		int a = Math.round(255 * Mth.clamp(alpha, 0, 1));
		int iw = Math.round(w);
		int ih = Math.round(h);
		g.blit(RenderPipelines.GUI_TEXTURED, MagicRender.SPARK, Math.round(cx - w / 2), Math.round(cy - h / 2), 0, 0, iw, ih, 32, 32, 32, 32,
				(a << 24) | rgb);
	}

	/** Детерминированный «случай» 0…1 по трём числам. */
	static float rnd(int a, int b, int c) {
		int h = a * 0x27D4EB2D ^ b * 0x165667B1 ^ c * 0x9E3779B9;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return (h >>> 8) / (float) (1 << 24);
	}
}
