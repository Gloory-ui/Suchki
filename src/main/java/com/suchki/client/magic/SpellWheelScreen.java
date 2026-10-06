package com.suchki.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicData;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellTomeItem;
import com.suchki.magic.Spells;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Колесо заклинаний (зажать R): внутри — 12 стихий, при наведении на стихию снаружи раскрываются её изученные
 * заклинания — одной дугой, а если их много, двумя (внутренняя — младшие ранги, внешняя — старшие).
 * У значков: рамка цвета ранга, «+» у слияний, шторка перезарядки, замок, если фокус не выдерживает ранг.
 * Отпустил R (или щёлкнул) — выбрано заклинание под курсором. Колесо сжимается на маленьких экранах.
 */
public class SpellWheelScreen extends Screen {
	public static final float R_SCHOOL = 64;
	public static final float R_SPELL = 118;
	public static final float R_SPELL2 = 150;
	public static final float INNER_DEAD = 24;
	public static final float OUTER_EDGE = 91;
	public static final float ICON = 24;
	/** Больше стольких заклинаний — две дуги. */
	public static final int ONE_ROW_MAX = 9;

	private final KeyMapping key;
	private int ticks;
	private School school;
	private Spell hovered;
	private final float[] schoolGlow = new float[School.values().length];

	public SpellWheelScreen(KeyMapping key) {
		super(Component.translatable("suchki.wheel.title"));
		this.key = key;
	}

	// --- Геометрия (общая для отрисовки, наведения и тестов) ---

	/** Масштаб колеса под размер экрана (1 — полный размер). */
	public static float scale(int guiW, int guiH) {
		float avail = Math.min(guiW, guiH) / 2.0F - 6;
		return Mth.clamp(avail / (R_SPELL2 + ICON / 2 + 4), 0.5F, 1.0F);
	}

	private static double angleOf(int i, int n) {
		return -Math.PI / 2 + Math.PI * 2 * i / n;
	}

	/** Центр значка стихии номер {@code index} (порядок {@link School#values()}). */
	public static float[] schoolCenter(int index, int guiW, int guiH) {
		float k = scale(guiW, guiH);
		double a = angleOf(index, School.values().length);
		return new float[] {guiW / 2.0F + (float) Math.cos(a) * R_SCHOOL * k, guiH / 2.0F + (float) Math.sin(a) * R_SCHOOL * k};
	}

	/** Сколько заклинаний во внутренней дуге (остальные — во внешней). */
	public static int innerCount(int count) {
		return count <= ONE_ROW_MAX ? count : (count + 1) / 2;
	}

	/** Угловой шаг между значками на дуге радиуса {@code r} для {@code n} значков. */
	private static double stepFor(int n, float r) {
		double min = (ICON + 5) / r;
		double nominal = Math.toRadians(20);
		double cap = Math.toRadians(200) / Math.max(1, n);
		return Math.max(min, Math.min(nominal, cap));
	}

	/** Угол заклинания номер {@code index} из {@code count} у стихии {@code school} и его радиус (без масштаба). */
	private static double[] spellPolar(School school, int index, int count) {
		int inner = innerCount(count);
		boolean outer = index >= inner;
		int i = outer ? index - inner : index;
		int n = outer ? count - inner : inner;
		float r = outer ? R_SPELL2 : R_SPELL;
		double step = stepFor(n, r);
		double a = angleOf(school.ordinal(), School.values().length) + (i - (n - 1) / 2.0) * step;
		return new double[] {a, r, step};
	}

	/** Центр значка заклинания номер {@code index} из {@code count} изученных у стихии {@code school}. */
	public static float[] spellCenter(School school, int index, int count, int guiW, int guiH) {
		float k = scale(guiW, guiH);
		double[] pol = spellPolar(school, index, count);
		return new float[] {guiW / 2.0F + (float) (Math.cos(pol[0]) * pol[1] * k), guiH / 2.0F + (float) (Math.sin(pol[0]) * pol[1] * k)};
	}

	// --- Экран ---

	@Override
	protected void init() {
		Spell sel = minecraft.player == null ? null : Magic.data(minecraft.player).selectedSpell();
		school = sel != null ? sel.school : School.FIRE;
		MagicHud.clearTextureCache();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		// Отпустили клавишу — выбираем то, что под курсором (клавиша может быть и кнопкой мыши).
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		long window = minecraft.getWindow().handle();
		boolean down = bound.getType() == InputConstants.Type.MOUSE
				? org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, bound.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS
				: bound.getValue() > 0 && InputConstants.isKeyDown(minecraft.getWindow(), bound.getValue());
		if (!down && ticks > 1) finish();
	}

	private void finish() {
		if (hovered != null && minecraft.player != null && Magic.data(minecraft.player).knows(hovered.id)) {
			ClientPlayNetworking.send(new Magic.SelectSpell(hovered.id));
			minecraft.player.playSound(MagicSounds.SELECT, 0.7F, 0.9F + 0.04F * hovered.tier);
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			finish();
			return true;
		}
		onClose();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
		float open = open(pt);
		int a = Math.round(120 * open);
		g.fillGradient(0, 0, width, height, (a << 24) | 0x05030C, (Math.round(a * 1.3F) << 24) | 0x05030C);
	}

	private float open(float pt) {
		return CastAnim.easeOut(Mth.clamp((ticks + pt) / 4.0F, 0, 1));
	}

	private List<Spell> known(School s) {
		MagicData d = Magic.data(minecraft.player);
		List<Spell> out = new ArrayList<>();
		for (Spell sp : Spells.of(s)) if (d.knows(sp.id)) out.add(sp);
		return out;
	}

	/** Наведённое заклинание (для тестов). */
	public Spell hovered() {
		return hovered;
	}

	/** Наведённая стихия (для тестов). */
	public School school() {
		return school;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		super.render(g, mouseX, mouseY, pt);
		if (minecraft.player == null) return;
		float open = open(pt);
		float k = scale(width, height);
		float cx = width / 2.0F;
		float cy = height / 2.0F;
		double dx = mouseX - cx, dy = mouseY - cy;
		double dist = Math.sqrt(dx * dx + dy * dy);
		double ang = Math.atan2(dy, dx);
		School[] schools = School.values();
		float time = ticks + pt;

		// Наведение на стихию (во внутреннем кольце); снаружи стихия остаётся последней наведённой.
		if (dist > INNER_DEAD * k && dist < OUTER_EDGE * k) {
			int best = 0;
			double bestDiff = 999;
			for (int i = 0; i < schools.length; i++) {
				double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - angleOf(i, schools.length))));
				if (diff < bestDiff) {
					bestDiff = diff;
					best = i;
				}
			}
			school = schools[best];
		}
		List<Spell> spells = known(school);
		int n = spells.size();
		hovered = null;
		if (dist >= (OUTER_EDGE - 6) * k && n > 0) {
			// Ближайший значок в пределах досягаемости; далеко за кругом — ближайший по углу на внешней дуге.
			double best = 21 * k;
			for (int i = 0; i < n; i++) {
				float[] c = spellCenter(school, i, n, width, height);
				double d = Math.hypot(mouseX - c[0], mouseY - c[1]);
				if (d < best) {
					best = d;
					hovered = spells.get(i);
				}
			}
			if (hovered == null && dist > (R_SPELL2 + 14) * k) {
				double bestDiff = 999;
				for (int i = innerCount(n) < n ? innerCount(n) : 0; i < n; i++) {
					double[] pol = spellPolar(school, i, n);
					double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - pol[0])));
					if (diff <= Math.toDegrees(pol[2]) / 2 + 2 && diff < bestDiff) {
						bestDiff = diff;
						hovered = spells.get(i);
					}
				}
			}
		}

		// Орбита стихий и дуги заклинаний.
		ring(g, cx, cy, R_SCHOOL * k * open, 0x30FFFFFF);
		ring(g, cx, cy, INNER_DEAD * k * open, 0x18FFFFFF);
		if (n > 0) {
			int inner = innerCount(n);
			double[] f = spellPolar(school, 0, n);
			double[] l = spellPolar(school, inner - 1, n);
			arcDots(g, cx, cy, R_SPELL * k * open, f[0] - f[2] * 0.45, l[0] + l[2] * 0.45, (0x70 << 24) | school.color);
			if (inner < n) {
				double[] f2 = spellPolar(school, inner, n);
				double[] l2 = spellPolar(school, n - 1, n);
				arcDots(g, cx, cy, R_SPELL2 * k * open, f2[0] - f2[2] * 0.45, l2[0] + l2[2] * 0.45, (0x50 << 24) | school.light);
			}
		}

		// Стихии.
		for (int i = 0; i < schools.length; i++) {
			School s = schools[i];
			schoolGlow[i] = Mth.lerp(0.3F, schoolGlow[i], s == school ? 1.0F : 0.0F);
			double a = angleOf(i, schools.length);
			float x = cx + (float) Math.cos(a) * R_SCHOOL * k * open;
			float y = cy + (float) Math.sin(a) * R_SCHOOL * k * open;
			float size = (22 + 8 * schoolGlow[i]) * k;
			if (schoolGlow[i] > 0.05F) glow(g, x, y, size * 0.95F, s.color, 0.6F * schoolGlow[i]);
			schoolIcon(g, s, x, y, size, known(s).isEmpty() ? 0.4F : 1.0F);
		}

		// Заклинания выбранной стихии.
		MagicData data = Magic.data(minecraft.player);
		Spell selected = data.selectedSpell();
		FocusItem focus = MagicHud.focus(minecraft.player);
		long now = minecraft.player.level().getGameTime();
		for (int i = 0; i < n; i++) {
			Spell sp = spells.get(i);
			double[] pol = spellPolar(school, i, n);
			float x = cx + (float) (Math.cos(pol[0]) * pol[1] * k * open);
			float y = cy + (float) (Math.sin(pol[0]) * pol[1] * k * open);
			boolean hov = sp == hovered;
			boolean locked = focus != null && sp.tier > focus.maxTier;
			float size = (hov ? ICON + 6 : ICON) * k;
			if (hov) glow(g, x, y, size * 1.05F, sp.color(), 0.85F);
			if (sp == selected) glow(g, x, y, size * 0.8F, 0xFFD24A, 0.65F);
			// Рамка цвета ранга; у Божественного — переливается.
			int tierColor = sp.tier >= 7 ? MagicHud.mix(0xFFFFFF, sp.lightColor(), 0.5F + 0.5F * Mth.sin(time * 0.3F)) : MagicHud.TIER_COLOR[sp.tier];
			frame(g, x, y, size + 2, (Math.round(220 * open) << 24) | tierColor);
			MagicHud.spellIcon(g, sp, x - size / 2, y - size / 2, size, locked ? 0.35F : 1.0F);
			long ready = data.readyAt(sp.id);
			if (ready > now && sp.cooldownTicks > 0) {
				float left = Mth.clamp((ready - now) / (float) sp.cooldownTicks, 0, 1);
				int h = Math.round((size - 2) * left);
				g.fill(Math.round(x - size / 2 + 1), Math.round(y + size / 2 - 1 - h), Math.round(x + size / 2 - 1), Math.round(y + size / 2 - 1),
						0xA0000000);
			}
			if (locked) lock(g, x, y + size / 2 - 5 * k, k);
			if (sp.fusion()) fusionMark(g, sp, x + size / 2 - 1, y - size / 2 + 1, k);
			// Мультикаст: точки в правом нижнем углу — сколько копий можно выпустить.
			if (sp.maxCopies > 1) {
				for (int d = 0; d < sp.maxCopies; d++) {
					int px = Math.round(x + size / 2 - 3 - d * 2.5F * k);
					int py = Math.round(y + size / 2 - 3);
					g.fill(px, py, px + 1, py + 1, 0xE0000000 | sp.lightColor());
				}
			}
		}

		// Середина: стихия или наведённое заклинание.
		int textA = Math.round(255 * open);
		int midY = (int) cy;
		if (hovered != null) {
			centered(g, hovered.displayName(), cx, midY - 13, (textA << 24) | hovered.lightColor(), k);
			centered(g, Component.translatable("suchki.tier." + hovered.tier), cx, midY - 2, (textA << 24) | MagicHud.TIER_COLOR[hovered.tier], k * 0.85F);
			if (hovered.fusion()) {
				centered(g, Component.literal("+ ").append(comboNames(hovered)), cx, midY + 8, (textA << 24) | 0xE8C8FF, k * 0.75F);
			}
		} else {
			centered(g, school.displayName(), cx, midY - 9, (textA << 24) | school.light, k);
			String count = known(school).size() + " / " + Spells.of(school).size();
			centered(g, Component.literal(count), cx, midY + 3, (textA << 24) | 0x9090A0, k * 0.85F);
		}
		int copies = MagicKeys.multicastKey != null ? MagicKeys.currentMulticast(minecraft.player) : 1;
		if (copies > 1) MagicHud.badge(g, font, cx, cy + 18 * k, copies, 0xFFD0F0, textA);

		drawInfo(g, spells, focus, open, k, cx, cy);
	}

	/** Описание наведённого заклинания (справа от колеса, а если места нет — снизу). */
	private void drawInfo(GuiGraphics g, List<Spell> spells, FocusItem focus, float open, float k, float cx, float cy) {
		List<Component> lines = new ArrayList<>();
		if (hovered != null) {
			lines.add(hovered.displayName().copy().withStyle(s -> s.withColor(hovered.lightColor())));
			SpellTomeItem.describe(hovered, lines::add);
			if (hovered.maxCopies <= 1) lines.add(Component.translatable("suchki.wheel.no_multicast").withStyle(ChatFormatting.DARK_GRAY));
			if (focus != null && hovered.tier > focus.maxTier) {
				lines.add(Component.translatable("suchki.wheel.locked", Component.translatable("suchki.tier." + hovered.tier))
						.withStyle(ChatFormatting.RED));
			}
		} else if (spells.isEmpty()) {
			lines.add(Component.translatable(allEmpty() ? "suchki.wheel.none" : "suchki.wheel.empty"));
		} else {
			lines.add(Component.translatable("suchki.wheel.hint"));
		}
		float wheelR = (R_SPELL2 + ICON / 2 + 6) * k;
		int sideSpace = Math.round(width - (cx + wheelR)) - 8;
		int boxW;
		int boxX;
		int boxY;
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		if (sideSpace >= 130) {
			boxW = Math.min(230, sideSpace);
			boxX = Math.round(cx + wheelR);
			for (Component line : lines) wrapped.addAll(font.split(line, boxW - 12));
			boxY = Math.round(cy - (wrapped.size() * 10 + 10) / 2.0F);
		} else {
			boxW = Math.min(300, width - 20);
			boxX = width / 2 - boxW / 2;
			for (Component line : lines) wrapped.addAll(font.split(line, boxW - 12));
			boxY = Math.max(4, height - (wrapped.size() * 10 + 10) - 4);
		}
		int boxH = wrapped.size() * 10 + 10;
		int textA = Math.round(255 * open);
		g.fill(boxX, boxY, boxX + boxW, boxY + boxH, (Math.round(185 * open) << 24) | 0x0C0818);
		g.fill(boxX, boxY, boxX + boxW, boxY + 1, (Math.round(220 * open) << 24) | (hovered != null ? hovered.color() : school.color));
		int ty = boxY + 6;
		for (FormattedCharSequence line : wrapped) {
			g.drawString(font, line, boxX + 6, ty, (textA << 24) | 0xFFFFFF, true);
			ty += 10;
		}
	}

	private static Component comboNames(Spell spell) {
		net.minecraft.network.chat.MutableComponent parts = Component.empty();
		for (School part : spell.combo) {
			if (!parts.getSiblings().isEmpty()) parts.append(" + ");
			parts.append(part.displayName().copy().withStyle(st -> st.withColor(part.light)));
		}
		return parts;
	}

	private boolean allEmpty() {
		for (School s : School.values()) if (!known(s).isEmpty()) return false;
		return true;
	}

	private void centered(GuiGraphics g, Component text, float x, float y, int argb, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(font, text, -font.width(text) / 2, 0, argb, true);
		g.pose().popMatrix();
	}

	/** Значок стихии (или запасной — цветной кружок с буквой, пока картинки нет). */
	private void schoolIcon(GuiGraphics g, School s, float x, float y, float size, float alpha) {
		Identifier tex = MagicHud.schoolIcon(s.id);
		int a = Math.round(255 * alpha);
		if (MagicHud.hasTexture(tex)) {
			int is = Math.round(size);
			g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x - is / 2.0F), Math.round(y - is / 2.0F), 0, 0, is, is, 32, 32, 32, 32,
					(a << 24) | 0xFFFFFF);
			return;
		}
		glow(g, x, y, size * 0.7F, s.color, 0.9F * alpha);
		disc(g, x, y, size * 0.36F, (Math.round(a * 0.85F) << 24) | (s.color & 0x7F7F7F));
		String letter = s.displayName().getString();
		letter = letter.isEmpty() ? "?" : letter.substring(0, 1);
		g.drawString(font, letter, Math.round(x - font.width(letter) / 2.0F), Math.round(y - 4), (a << 24) | s.light, true);
	}

	/** Мягкое свечение под значком (текстура искры частиц). */
	private static void glow(GuiGraphics g, float x, float y, float size, int rgb, float alpha) {
		int s = Math.round(size * 2);
		g.blit(RenderPipelines.GUI_TEXTURED, MagicRender.SPARK, Math.round(x - s / 2.0F), Math.round(y - s / 2.0F), 0, 0, s, s, 32, 32, 32, 32,
				(Math.round(255 * alpha) << 24) | rgb);
	}

	/** Рамка вокруг значка. */
	private static void frame(GuiGraphics g, float x, float y, float size, int argb) {
		int x0 = Math.round(x - size / 2), y0 = Math.round(y - size / 2);
		int x1 = Math.round(x + size / 2), y1 = Math.round(y + size / 2);
		g.fill(x0, y0, x1, y0 + 1, argb);
		g.fill(x0, y1 - 1, x1, y1, argb);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
	}

	/** Закрашенный круг из горизонтальных полосок. */
	private static void disc(GuiGraphics g, float x, float y, float r, int argb) {
		int ri = Math.max(1, Math.round(r));
		for (int dy = -ri; dy <= ri; dy++) {
			int w = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(Math.round(x) - w, Math.round(y) + dy, Math.round(x) + w + 1, Math.round(y) + dy + 1, argb);
		}
	}

	/** Маленький замок: фокус не выдерживает ранг. */
	private static void lock(GuiGraphics g, float x, float y, float k) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(k, k);
		int dark = 0xE0200810;
		int red = 0xFFFF5050;
		g.fill(-4, -1, 5, 6, dark);
		g.fill(-3, 0, 4, 5, red);
		g.fill(-3, -5, -1, 0, red);
		g.fill(2, -5, 4, 0, red);
		g.fill(-1, -6, 2, -4, red);
		g.fill(0, 2, 1, 4, dark);
		g.pose().popMatrix();
	}

	/** Отметка слияния: «+» на подложке цветов стихий-составляющих. */
	private void fusionMark(GuiGraphics g, Spell sp, float x, float y, float k) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(k, k);
		int n = sp.combo.size();
		for (int i = 0; i < n; i++) {
			int c = sp.combo.get(i).color;
			g.fill(-4 + i * 8 / n, -4, -4 + (i + 1) * 8 / n, 4, 0xFF000000 | c);
		}
		g.fill(-1, -3, 1, 3, 0xFFFFFFFF);
		g.fill(-3, -1, 3, 1, 0xFFFFFFFF);
		g.pose().popMatrix();
	}

	private static void ring(GuiGraphics g, float cx, float cy, float r, int argb) {
		int n = Math.max(24, (int) (r * 0.9F));
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			int x = Math.round(cx + (float) Math.cos(a) * r);
			int y = Math.round(cy + (float) Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, argb);
		}
	}

	private static void arcDots(GuiGraphics g, float cx, float cy, float r, double from, double to, int argb) {
		int n = Math.max(8, (int) ((to - from) * r / 4));
		for (int i = 0; i <= n; i++) {
			double a = from + (to - from) * i / n;
			int x = Math.round(cx + (float) Math.cos(a) * r);
			int y = Math.round(cy + (float) Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, argb);
		}
	}
}
