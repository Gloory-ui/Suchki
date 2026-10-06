package com.suchki.client.magic;

import com.suchki.Suchki;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.FocusItem;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicData;
import com.suchki.magic.Spell;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * HUD мага: полоса маны над сытостью (видна с фокусом в руке, при касте или пока мана не полная),
 * значок выбранного заклинания справа от хотбара с перезарядкой и кольцо накопления вокруг прицела.
 */
public final class MagicHud {
	static final Identifier MANA_ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "mana_bar");
	static final Identifier RING_ID = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "charge_ring");
	private static final Identifier BAR_EMPTY = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/mana_bar_empty.png");
	private static final Identifier BAR_FULL = Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/mana_bar_full.png");
	private static final int BAR_W = 81;

	/** Плавная видимость полосы маны 0…1 и плавное значение маны для отрисовки. */
	private static float shown;
	private static float shownMana = -1;

	private MagicHud() {}

	public static Identifier spellIcon(Spell spell) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/spell/" + spell.id + ".png");
	}

	public static Identifier schoolIcon(String school) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "textures/gui/school_" + school + ".png");
	}

	static void register() {
		HudStatusBarHeightRegistry.addRight(MANA_ID, player -> wantsBar(player) || shown > 0.05F ? 10 : 0);
		HudElementRegistry.attachElementAfter(VanillaHudElements.AIR_BAR, MANA_ID, MagicHud::renderMana);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, RING_ID, MagicHud::renderRing);
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
		// Сколько маны заберёт выбранное заклинание — светлая метка на полосе.
		Spell spell = Magic.data(p).selectedSpell();
		if (spell != null && spell.charged() && holdsFocus(p)) {
			float cost = Magic.cost(p, spell, focus(p));
			int mark = x + Math.round(BAR_W * Mth.clamp((mana - cost) / max, 0, 1));
			g.fill(mark, y - 1, mark + 1, y + 6, (Math.round(200 * shown) << 24) | 0xFFFFFF);
		}
		if (holdsFocus(p)) {
			String text = Math.round(mana) + " / " + Math.round(max);
			g.pose().pushMatrix();
			g.pose().translate(x + BAR_W / 2.0F, y - 0.5F);
			g.pose().scale(0.6F, 0.6F);
			g.drawString(mc.font, text, -mc.font.width(text) / 2, 0, (alpha << 24) | 0xD8F0FF, true);
			g.pose().popMatrix();
		}
		renderSelected(g, p, alpha);
	}

	private static boolean holdsFocus(Player p) {
		return focus(p) != null;
	}

	private static FocusItem focus(Player p) {
		if (p.getMainHandItem().getItem() instanceof FocusItem f) return f;
		return p.getOffhandItem().getItem() instanceof FocusItem f ? f : null;
	}

	/** Значок выбранного заклинания справа от хотбара; тёмная «шторка» — перезарядка. */
	private static void renderSelected(GuiGraphics g, Player p, int alpha) {
		if (!holdsFocus(p)) return;
		MagicData data = Magic.data(p);
		Spell spell = data.selectedSpell();
		if (spell == null) return;
		int x = g.guiWidth() / 2 + 91 + 5;
		int y = g.guiHeight() - 21;
		int size = 20;
		g.blit(RenderPipelines.GUI_TEXTURED, spellIcon(spell), x, y, 0, 0, size, size, 32, 32, 32, 32, (alpha << 24) | 0xFFFFFF);
		long now = p.level().getGameTime();
		long ready = data.readyAt(spell.id);
		if (ready > now && spell.cooldownTicks > 0) {
			float left = Mth.clamp((ready - now) / (float) spell.cooldownTicks, 0, 1);
			int h = Math.round(size * left);
			g.fill(x + 2, y + size - h, x + size - 2, y + size - 1, 0x99000000);
		}
		FocusItem focus = focus(p);
		if (focus != null && (Magic.mana(p) < Magic.cost(p, spell, focus) * (spell.charged() ? 1 : 0.5F) || spell.tier > focus.maxTier)) {
			g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x66400010);
		}
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
		int color = pose.spell.school.color;
		int a = Math.round(220 * pose.weight);
		if (pose.style == CastStyle.CHANNEL) {
			arc(g, cx, cy, 9, pose.channel, (a << 24) | MagicRender.lighten(color, 0.4F), (a / 3 << 24) | 0x202020);
			return;
		}
		int inner = pose.form >= 1 ? MagicRender.lighten(color, 0.75F) : MagicRender.lighten(color, 0.45F);
		arc(g, cx, cy, 9, pose.form, (a << 24) | inner, (a / 3 << 24) | 0x202020);
		if (pose.form >= 1) {
			float flicker = pose.over >= 1 ? 0.6F + 0.4F * Mth.sin((p.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.8F) : 1.0F;
			int outerColor = pose.hold > 0.5F && (p.tickCount / 2) % 2 == 0 ? 0xFF6060 : color;
			arc(g, cx, cy, 12, pose.over, (Math.round(a * flicker) << 24) | outerColor, (a / 4 << 24) | 0x202020);
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
