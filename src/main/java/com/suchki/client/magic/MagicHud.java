package com.suchki.client.magic;

import com.suchki.Suchki;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicData;
import com.suchki.magic.Spell;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * HUD мага: полоса маны над сытостью (видна с фокусом в руке, при касте или пока мана не полная),
 * значок выбранного заклинания справа от хотбара (ранг, перезарядка, «×N» мультикаста), кольцо накопления
 * вокруг прицела и всплывашка переключения мультикаста.
 */
public final class MagicHud {
	static final Identifier MANA_ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "mana_bar");
	static final Identifier RING_ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "charge_ring");
	static final Identifier TOAST_ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "magic_toast");
	private static final Identifier BAR_EMPTY = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/mana_bar_empty.png");
	private static final Identifier BAR_FULL = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/mana_bar_full.png");
	private static final int BAR_W = 81;
	/** Цвет рамки ранга 1…7 (серый, зелёный, синий, фиолетовый, золотой, алый, белый). */
	static final int[] TIER_COLOR = {0xA0A0A0, 0xA0A0A0, 0x6CD46C, 0x4AA8FF, 0xB070FF, 0xFFC23A, 0xFF5A4A, 0xFFFFFF};
	static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII"};
	/** Сколько тиков висит всплывашка мультикаста. */
	private static final int TOAST_TICKS = 40;

	/** Плавная видимость полосы маны 0…1 и плавное значение маны для отрисовки. */
	private static float shown;
	private static float shownMana = -1;

	/** Всплывашка (мультикаст): текст, пояснение, цвет, N из max и когда показана. */
	private static Component toastText;
	private static Component toastSub;
	private static int toastColor;
	private static int toastN;
	private static int toastMax;
	private static long toastAt = -1000;
	/** Для скриншотов: возраст всплывашки зафиксирован (−1 — живое время). */
	private static float debugToastAge = -1;

	/** Есть ли текстура (значки школ и заклинаний рисует генератор; пока их нет — рисуем запасной значок). */
	private static final Map<Identifier, Boolean> TEXTURES = new HashMap<>();

	private MagicHud() {}

	public static Identifier spellIcon(Spell spell) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/spell/" + spell.id + ".png");
	}

	public static Identifier schoolIcon(String school) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/school_" + school + ".png");
	}

	/** Есть ли такая текстура в ресурсах (кэшируется; сбрасывается при перезагрузке ресурсов — новым экраном). */
	static boolean hasTexture(Identifier id) {
		return TEXTURES.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
	}

	static void clearTextureCache() {
		TEXTURES.clear();
	}

	static void register() {
		HudStatusBarHeightRegistry.addRight(MANA_ID, player -> wantsBar(player) || shown > 0.05F ? 10 : 0);
		HudElementRegistry.attachElementAfter(VanillaHudElements.AIR_BAR, MANA_ID, MagicHud::renderMana);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, RING_ID, MagicHud::renderRing);
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, TOAST_ID, MagicHud::renderToast);
	}

	static boolean wantsBar(Player p) {
		if (!Magic.isMage(p) || p.isSpectator()) return false;
		boolean focus = p.getMainHandItem().getItem() instanceof FocusItem || p.getOffhandItem().getItem() instanceof FocusItem;
		return focus || Magic.mana(p) < Magic.maxMana(p) - 0.5F;
	}

	static void tick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		shown = Mth.approach(shown, wantsBar(p) ? 1.0F : 0.0F, 0.12F);
	}

	/** Показать всплывашку над хотбаром (мультикаст ×N из max; sub — пояснение или null). */
	static void toast(Component text, Component sub, int color, int n, int max) {
		Minecraft mc = Minecraft.getInstance();
		toastText = text;
		toastSub = sub;
		toastColor = color;
		toastN = n;
		toastMax = max;
		toastAt = mc.level != null ? mc.level.getGameTime() : 0;
	}

	/** Для скриншотов: показать всплывашку мультикаста с зафиксированным возрастом (тиков), age &lt; 0 — убрать. */
	public static void debugToast(int n, int max, int color, float age) {
		if (age < 0) {
			debugToastAge = -1;
			toastText = null;
			return;
		}
		toast(Component.translatable("suchki.multicast.toast", n), null, color, n, max);
		debugToastAge = age;
	}

	/** Какой фокус в руках (основная рука важнее). */
	static FocusItem focus(Player p) {
		if (p.getMainHandItem().getItem() instanceof FocusItem f) return f;
		return p.getOffhandItem().getItem() instanceof FocusItem f ? f : null;
	}

	/** Сколько маны нужно, чтобы начать каст (как на сервере: копии мультикаста умножают цену, поток — половина). */
	static float startCost(Player p, Spell spell, FocusItem focus) {
		return Magic.cost(p, spell, focus) * (spell.charged() ? 1.0F : 0.5F) * Magic.copiesFor(p, spell);
	}

	private static void renderMana(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || shown <= 0.01F || !Magic.isMage(p)) return;
		float max = Magic.maxMana(p);
		float mana = Magic.mana(p);
		shownMana = shownMana < 0 ? mana : Mth.lerp(0.25F, shownMana, mana);
		float frac = Mth.clamp(shownMana / max, 0, 1);
		int screenW = g.guiWidth();
		int x = screenW / 2 + 91 - BAR_W;
		int y = g.guiHeight() - HudStatusBarHeightRegistry.getHeight(MANA_ID) + 2;
		int alpha = Math.round(255 * shown);
		int white = alpha << 24 | 0xFFFFFF;
		// Рамка и подложка.
		g.fill(x - 1, y - 1, x + BAR_W + 1, y + 6, (Math.round(160 * shown) << 24) | 0x0A0A1E);
		g.blit(RenderPipelines.GUI_TEXTURED, BAR_EMPTY, x, y, 0, 0, BAR_W, 5, 182, 5, 182, 5, white);
		int filled = Math.round(BAR_W * frac);
		if (filled > 0) g.blit(RenderPipelines.GUI_TEXTURED, BAR_FULL, x, y, 0, 0, filled, 5, Math.round(182 * frac), 5, 182, 5, white);
		// Сколько маны заберёт выбранное заклинание (со всеми копиями мультикаста) — светлая метка на полосе.
		Spell spell = Magic.data(p).selectedSpell();
		FocusItem focus = focus(p);
		if (spell != null && spell.charged() && focus != null) {
			float cost = Magic.cost(p, spell, focus) * Magic.copiesFor(p, spell);
			int mark = x + Math.round(BAR_W * Mth.clamp((mana - cost) / max, 0, 1));
			g.fill(mark, y - 1, mark + 1, y + 6, (Math.round(200 * shown) << 24) | 0xFFFFFF);
		}
		if (focus != null) {
			String text = Math.round(mana) + " / " + Math.round(max);
			g.pose().pushMatrix();
			g.pose().translate(x + BAR_W / 2.0F, y - 0.5F);
			g.pose().scale(0.6F, 0.6F);
			g.drawString(mc.font, text, -mc.font.width(text) / 2, 0, (alpha << 24) | 0xD8F0FF, true);
			g.pose().popMatrix();
		}
		renderSelected(g, p, alpha);
	}

	/** Значок выбранного заклинания справа от хотбара; тёмная «шторка» — перезарядка, «×N» — мультикаст. */
	private static void renderSelected(GuiGraphics g, Player p, int alpha) {
		FocusItem focus = focus(p);
		if (focus == null) return;
		MagicData data = Magic.data(p);
		Spell spell = data.selectedSpell();
		if (spell == null) return;
		Minecraft mc = Minecraft.getInstance();
		int x = g.guiWidth() / 2 + 91 + 5;
		int y = g.guiHeight() - 21;
		int size = 20;
		// Рамка цвета ранга.
		int frame = (Math.round(alpha * 0.85F) << 24) | TIER_COLOR[Math.min(7, Math.max(1, spell.tier))];
		g.fill(x - 1, y - 1, x + size + 1, y, frame);
		g.fill(x - 1, y + size, x + size + 1, y + size + 1, frame);
		g.fill(x - 1, y, x, y + size, frame);
		g.fill(x + size, y, x + size + 1, y + size, frame);
		spellIcon(g, spell, x, y, size, alpha / 255.0F);
		long now = p.level().getGameTime();
		long ready = data.readyAt(spell.id);
		if (ready > now && spell.cooldownTicks > 0) {
			float left = Mth.clamp((ready - now) / (float) spell.cooldownTicks, 0, 1);
			int h = Math.round(size * left);
			g.fill(x + 2, y + size - h, x + size - 2, y + size - 1, 0x99000000);
		}
		if (Magic.mana(p) < startCost(p, spell, focus) || spell.tier > focus.maxTier) {
			g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x66400010);
		}
		Font font = mc.font;
		// Ранг римской цифрой в левом нижнем углу.
		g.pose().pushMatrix();
		g.pose().translate(x + 1, y + size - 5);
		g.pose().scale(0.5F, 0.5F);
		g.drawString(font, ROMAN[Math.min(7, Math.max(1, spell.tier))], 0, 0, (alpha << 24) | TIER_COLOR[Math.min(7, Math.max(1, spell.tier))], true);
		g.pose().popMatrix();
		int copies = Magic.copiesFor(p, spell);
		if (copies > 1) badge(g, font, x + size - 2, y - 3, copies, spell.lightColor(), alpha);
	}

	/** «×N» мультикаста — маленькая плашка в углу значка. */
	static void badge(GuiGraphics g, Font font, float right, float top, int n, int color, int alpha) {
		String s = "×" + n;
		int w = font.width(s);
		g.pose().pushMatrix();
		g.pose().translate(right, top);
		g.pose().scale(0.75F, 0.75F);
		g.fill(-w / 2 - 2, -1, w / 2 + 3, 9, (Math.round(alpha * 0.8F) << 24) | 0x140A24);
		g.fill(-w / 2 - 2, 9, w / 2 + 3, 10, (alpha << 24) | color);
		g.drawString(font, s, -w / 2 + 1, 0, (alpha << 24) | 0xFFFFFF, true);
		g.pose().popMatrix();
	}

	/** Значок заклинания (или запасной: предмет-значок на цветной подложке, пока нет картинки). */
	static void spellIcon(GuiGraphics g, Spell spell, float x, float y, float size, float alpha) {
		int s = Math.round(size);
		int a = Math.round(255 * alpha);
		Identifier tex = spellIcon(spell);
		if (hasTexture(tex)) {
			g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0, s, s, 32, 32, 32, 32, (a << 24) | 0xFFFFFF);
			return;
		}
		g.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, (Math.round(a * 0.55F) << 24) | (spell.color() & 0x3F3F3F));
		g.pose().pushMatrix();
		g.pose().translate(x + size / 2.0F - 8 * size / 20.0F, y + size / 2.0F - 8 * size / 20.0F);
		g.pose().scale(size / 20.0F, size / 20.0F);
		g.renderItem(new net.minecraft.world.item.ItemStack(spell.iconItem()), 0, 0);
		g.pose().popMatrix();
	}

	/** Всплывашка мультикаста над хотбаром: «Мультикаст ×N» крупно, кружки-копии (N из max), пояснение. */
	private static void renderToast(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (toastText == null || mc.options.hideGui || mc.level == null) return;
		float age = debugToastAge >= 0 ? debugToastAge : mc.level.getGameTime() - toastAt + delta.getGameTimeDeltaPartialTick(false);
		if (age < 0 || age > TOAST_TICKS) return;
		float in = CastAnim.easeOut(Mth.clamp(age / 4.0F, 0, 1));
		float out = 1.0F - Mth.clamp((age - (TOAST_TICKS - 8)) / 8.0F, 0, 1);
		float a = in * out;
		if (a <= 0.02F) return;
		Font font = mc.font;
		float cx = g.guiWidth() / 2.0F;
		float cy = g.guiHeight() - 100;
		int alpha = Math.round(255 * a);
		float textScale = 1.5F;
		int w = font.width(toastText);
		int subW = toastSub != null ? font.width(toastSub) : 0;
		float boxW = Math.max(w * textScale + 40, subW * 0.75F + 24);
		float boxH = toastSub != null ? 44 : 36;
		// Мягкая подложка, тонкие черты сверху и снизу, свечение цвета заклинания.
		IncantationHud.softRect(g, cx, cy + boxH / 2 - 8, boxW, boxH, 20, 6, 0x0C0618, 0.62F * a);
		IncantationHud.glowBlob(g, cx, cy, w * textScale + 30, 30, toastColor, 0.3F * a * (1.5F - 0.5F * in));
		IncantationHud.hairline(g, cx, cy - 10, boxW * (0.5F + 0.5F * in), toastColor, 0.9F * a);
		IncantationHud.hairline(g, cx, cy + boxH - 8, boxW * 0.7F * (0.5F + 0.5F * in), toastColor, 0.6F * a);
		// Поп-масштаб: 1.4 → 1.0 с отскоком; блик пробегает по тексту при появлении.
		float pop = 1.0F + 0.4F * (1.0F - in) + 0.06F * CastAnim.bump(Mth.clamp((age - 3) / 5.0F, 0, 1));
		float sweep = Mth.clamp(age / 10.0F, 0, 1);
		int textColor = mix(toastColor, 0xFFFFFF, 0.35F + 0.65F * CastAnim.bump(sweep));
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(textScale * pop, textScale * pop);
		g.drawString(font, toastText, -w / 2, -4, (alpha << 24) | textColor, true);
		g.pose().popMatrix();
		// Кружки-копии: горящие — сколько копий выйдет, тусклые — до предела по уровню.
		int dots = Math.max(1, toastMax);
		float spacing = 13;
		for (int i = 0; i < dots; i++) {
			float dx = cx + (i - (dots - 1) / 2.0F) * spacing;
			boolean on = i < toastN;
			float lit = on ? Mth.clamp((age - 2 - i * 1.5F) / 3.0F, 0, 1) : 0;
			int c = on ? mix(0x404050, toastColor, lit) : 0x404050;
			ringDots(g, dx, cy + 12, 4.5F, 14, (alpha << 24) | c);
			if (on && lit > 0) {
				IncantationHud.glowBlob(g, dx, cy + 12, 14, 14, toastColor, 0.6F * a * lit);
				g.fill(Math.round(dx) - 1, Math.round(cy + 12) - 1, Math.round(dx) + 1, Math.round(cy + 12) + 1, (alpha << 24) | 0xFFFFFF);
			}
		}
		if (toastSub != null) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy + 22);
			g.pose().scale(0.75F, 0.75F);
			g.drawString(font, toastSub, -subW / 2, 0, (alpha << 24) | 0xC8C8D8, true);
			g.pose().popMatrix();
		}
	}

	/** Маленький круг из точек (значок копии мультикаста). */
	private static void ringDots(GuiGraphics g, float cx, float cy, float r, int n, int argb) {
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2 * i / n;
			int x = Math.round(cx + (float) Math.cos(ang) * r);
			int y = Math.round(cy + (float) Math.sin(ang) * r);
			g.fill(x, y, x + 1, y + 1, argb);
		}
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bb = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | gg << 8 | bb;
	}

	/** Кольцо накопления вокруг прицела: внутреннее — формирование, внешнее — накопление, на пределе мерцает. */
	private static void renderRing(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
		Pose pose = CastAnim.compute(p, delta.getGameTimeDeltaPartialTick(false));
		if (pose == null || !pose.casting() || pose.weight < 0.05F) return;
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int color = pose.spell.color();
		int a = Math.round(220 * pose.weight);
		if (pose.style == CastStyle.CHANNEL) {
			arc(g, cx, cy, 9, pose.channel, (a << 24) | MagicRender.lighten(color, 0.4F), (a / 3 << 24) | 0x202020);
			return;
		}
		// Формирование учитывает копии мультикаста (CastAnim берёт время с их учётом) — кольцо идёт медленнее.
		int inner = pose.form >= 1 ? MagicRender.lighten(color, 0.75F) : MagicRender.lighten(color, 0.45F);
		arc(g, cx, cy, 9, pose.form, (a << 24) | inner, (a / 3 << 24) | 0x202020);
		if (pose.form >= 1) {
			float flicker = pose.over >= 1 ? 0.6F + 0.4F * Mth.sin((p.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.8F) : 1.0F;
			int outerColor = pose.hold > 0.5F && (p.tickCount / 2) % 2 == 0 ? 0xFF6060 : color;
			arc(g, cx, cy, 12, pose.over, (Math.round(a * flicker) << 24) | outerColor, (a / 4 << 24) | 0x202020);
		}
		// Копии мультикаста — точки над кольцом.
		if (pose.copies > 1) {
			for (int i = 0; i < pose.copies; i++) {
				int dx = cx + (i * 2 - (pose.copies - 1)) * 2;
				g.fill(dx, cy - 17, dx + 1, cy - 16, (a << 24) | MagicRender.lighten(color, 0.6F));
			}
		}
	}

	/** Кольцо из точек радиусом {@code r}, заполненное на долю {@code frac} (по часовой от верха). */
	private static void arc(GuiGraphics g, int cx, int cy, int r, float frac, int fill, int empty) {
		int n = 40;
		for (int i = 0; i < n; i++) {
			double ang = -Math.PI / 2 + Math.PI * 2 * (i + 0.5) / n;
			int x = cx + (int) Math.round(Math.cos(ang) * r);
			int y = cy + (int) Math.round(Math.sin(ang) * r);
			g.fill(x, y, x + 1, y + 1, (i + 0.5F) / n <= frac ? fill : empty);
		}
	}
}
