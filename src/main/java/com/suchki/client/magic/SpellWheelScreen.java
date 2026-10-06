package com.suchki.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicData;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellTomeItem;
import com.suchki.magic.Spells;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Колесо заклинаний (зажать R): внутри — 7 школ, при наведении на школу снаружи раскрывается дуга её
 * изученных заклинаний. Отпустил R (или щёлкнул) — выбрано заклинание под курсором.
 */
public class SpellWheelScreen extends Screen {
	private static final float R_SCHOOL = 56;
	private static final float R_SPELL = 112;
	private static final float INNER_DEAD = 22;
	private static final float OUTER_EDGE = 86;

	private final KeyMapping key;
	private int ticks;
	private School school;
	private Spell hovered;
	private final float[] schoolGlow = new float[School.values().length];

	public SpellWheelScreen(KeyMapping key) {
		super(Component.translatable("suchki.wheel.title"));
		this.key = key;
	}

	@Override
	protected void init() {
		Spell sel = minecraft.player == null ? null : Magic.data(minecraft.player).selectedSpell();
		school = sel != null ? sel.school : School.DESTRUCTION;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		// Отпустили клавишу — выбираем то, что под курсором.
		int code = KeyBindingHelper.getBoundKeyOf(key).getValue();
		boolean down = code > 0 && InputConstants.isKeyDown(minecraft.getWindow(), code);
		if (!down && ticks > 1) finish();
	}

	private void finish() {
		if (hovered != null && minecraft.player != null && Magic.data(minecraft.player).knows(hovered.id)) {
			ClientPlayNetworking.send(new Magic.SelectSpell(hovered.id));
			minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
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
		int a = Math.round(110 * open);
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

	private static double angleOf(int i, int n) {
		return -Math.PI / 2 + Math.PI * 2 * i / n;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		super.render(g, mouseX, mouseY, pt);
		if (minecraft.player == null) return;
		float open = open(pt);
		float cx = width / 2.0F;
		float cy = height / 2.0F;
		double dx = mouseX - cx, dy = mouseY - cy;
		double dist = Math.sqrt(dx * dx + dy * dy);
		double ang = Math.atan2(dy, dx);
		School[] schools = School.values();

		// Наведение на школу (во внутреннем кольце); снаружи школа остаётся последней наведённой.
		if (dist > INNER_DEAD && dist < OUTER_EDGE) {
			int best = 0;
			double bestDiff = 9;
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
		int si = school.ordinal();
		double base = angleOf(si, schools.length);
		double step = Math.toRadians(spells.size() * 24 > 200 ? 200.0 / spells.size() : 24);
		hovered = null;
		if (dist >= OUTER_EDGE - 6) {
			for (int i = 0; i < spells.size(); i++) {
				double a = base + (i - (spells.size() - 1) / 2.0) * step;
				if (Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - a))) <= Math.toDegrees(step) / 2 + 2) hovered = spells.get(i);
			}
		}

		// Тонкое кольцо-орбита и дуга заклинаний.
		ring(g, cx, cy, R_SCHOOL * open, 0x30FFFFFF);
		if (!spells.isEmpty()) {
			double from = base - (spells.size() - 1) / 2.0 * step;
			double to = base + (spells.size() - 1) / 2.0 * step;
			arcDots(g, cx, cy, R_SPELL * open, from - step * 0.4, to + step * 0.4, (0x70 << 24) | school.color);
		}

		// Школы.
		for (int i = 0; i < schools.length; i++) {
			School s = schools[i];
			schoolGlow[i] = Mth.lerp(0.3F, schoolGlow[i], s == school ? 1.0F : 0.0F);
			double a = angleOf(i, schools.length);
			float x = cx + (float) Math.cos(a) * R_SCHOOL * open;
			float y = cy + (float) Math.sin(a) * R_SCHOOL * open;
			float size = 26 + 8 * schoolGlow[i];
			if (schoolGlow[i] > 0.05F) glow(g, x, y, size * 0.9F, s.color, 0.55F * schoolGlow[i]);
			icon(g, MagicHud.schoolIcon(s.id), x, y, size, known(s).isEmpty() ? 0.45F : 1.0F);
		}

		// Заклинания выбранной школы.
		Spell selected = Magic.data(minecraft.player).selectedSpell();
		long now = minecraft.player.level().getGameTime();
		for (int i = 0; i < spells.size(); i++) {
			Spell sp = spells.get(i);
			double a = base + (i - (spells.size() - 1) / 2.0) * step;
			float x = cx + (float) Math.cos(a) * R_SPELL * open;
			float y = cy + (float) Math.sin(a) * R_SPELL * open;
			boolean hov = sp == hovered;
			float size = hov ? 32 : 26;
			if (hov) glow(g, x, y, 30, sp.school.color, 0.8F);
			if (sp == selected) glow(g, x, y, 22, 0xFFD24A, 0.6F);
			icon(g, MagicHud.spellIcon(sp), x, y, size, 1.0F);
			long ready = Magic.data(minecraft.player).readyAt(sp.id);
			if (ready > now && sp.cooldownTicks > 0) {
				float left = Mth.clamp((ready - now) / (float) sp.cooldownTicks, 0, 1);
				int h = Math.round((size - 4) * left);
				g.fill(Math.round(x - size / 2 + 2), Math.round(y + size / 2 - 2 - h), Math.round(x + size / 2 - 2), Math.round(y + size / 2 - 2),
						0x99000000);
			}
		}

		// Середина: название школы или наведённого заклинания.
		int textA = Math.round(255 * open);
		if (hovered != null) {
			g.drawCenteredString(font, hovered.displayName(), (int) cx, (int) cy - 10, (textA << 24) | hovered.school.light);
			g.drawCenteredString(font, Component.translatable("suchki.tier." + hovered.tier), (int) cx, (int) cy + 2, (textA << 24) | 0xB8B8C8);
		} else {
			g.drawCenteredString(font, school.displayName(), (int) cx, (int) cy - 10, (textA << 24) | school.light);
			String count = known(school).size() + " / " + Spells.of(school).size();
			g.drawCenteredString(font, count, (int) cx, (int) cy + 2, (textA << 24) | 0x9090A0);
		}

		// Описание внизу.
		int boxW = Math.min(300, width - 20);
		int boxY = (int) Math.min(height - 70, cy + R_SPELL + 26);
		List<Component> lines = new ArrayList<>();
		if (hovered != null) {
			SpellTomeItem.describe(hovered, lines::add);
		} else if (spells.isEmpty()) {
			lines.add(Component.translatable(known(School.DESTRUCTION).isEmpty() && allEmpty() ? "suchki.wheel.none" : "suchki.wheel.empty"));
		} else {
			lines.add(Component.translatable("suchki.wheel.hint"));
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) wrapped.addAll(font.split(line, boxW - 12));
		int boxH = wrapped.size() * 10 + 10;
		int boxX = width / 2 - boxW / 2;
		g.fill(boxX, boxY, boxX + boxW, boxY + boxH, (Math.round(170 * open) << 24) | 0x0C0818);
		g.fill(boxX, boxY, boxX + boxW, boxY + 1, (Math.round(200 * open) << 24) | (hovered != null ? hovered.school.color : school.color));
		int ty = boxY + 6;
		for (FormattedCharSequence line : wrapped) {
			g.drawString(font, line, boxX + 6, ty, (textA << 24) | 0xFFFFFF, true);
			ty += 10;
		}
	}

	private boolean allEmpty() {
		for (School s : School.values()) if (!known(s).isEmpty()) return false;
		return true;
	}

	private static void icon(GuiGraphics g, net.minecraft.resources.Identifier tex, float x, float y, float size, float alpha) {
		int s = Math.round(size);
		int a = Math.round(255 * alpha);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x - s / 2.0F), Math.round(y - s / 2.0F), 0, 0, s, s, 32, 32, 32, 32,
				(a << 24) | 0xFFFFFF);
	}

	/** Мягкое свечение под значком (текстура искры частиц). */
	private static void glow(GuiGraphics g, float x, float y, float size, int rgb, float alpha) {
		int s = Math.round(size * 2);
		g.blit(RenderPipelines.GUI_TEXTURED, MagicRender.SPARK, Math.round(x - s / 2.0F), Math.round(y - s / 2.0F), 0, 0, s, s, 32, 32, 32, 32,
				(Math.round(255 * alpha) << 24) | rgb);
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
