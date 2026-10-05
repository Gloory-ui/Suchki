package com.suchki.client;

import com.suchki.SuchkiConfig;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.SkillTree;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Дерево навыков активной роли. Внизу корень (иконка роли), из него растёт ствол, который разветвляется
 * на три ветви-кроны; навыки висят на ветвях. Изученное светится цветом роли, доступное мягко мерцает,
 * при изучении — вспышка. Раскладка подстраивается под размер экрана. Клавиша K.
 */
public class SkillTreeScreen extends Screen {
	private static final int BARK = 0x6B4A2B;
	private static final int BARK_DARK = 0x2A1A0E;
	private static final int OPEN_MS = 260;
	private static final int BURST_MS = 650;

	private PlayerSkills shown = PlayerSkills.EMPTY;
	private long openedAt;
	private long lastFrame;
	private double xpShown = -1;
	private final Map<String, Long> burstAt = new HashMap<>();
	private final Map<String, Double> hoverGlow = new HashMap<>();

	// Раскладка (пересчитывается в init).
	private boolean compact;
	private int headerBottom, rootCX, rootCY, rootSize, nodeSize, capSize, step, crown;

	private record Placed(SkillTree.Node node, int x, int y, int size) {
		boolean contains(double mx, double my) {
			return mx >= x - size / 2.0 && mx < x + size / 2.0 && my >= y - size / 2.0 && my < y + size / 2.0;
		}
	}

	private final List<Placed> placed = new ArrayList<>();

	public SkillTreeScreen() {
		super(Component.translatable("suchki.skill.title"));
	}

	private PlayerSkills data() {
		return this.minecraft.player == null ? PlayerSkills.EMPTY : Skills.get(this.minecraft.player);
	}

	@Override
	protected void init() {
		if (openedAt == 0) openedAt = Ui.now();
		this.shown = data();
		layout();
		int by = this.height - 26;
		int bw = Math.min(150, this.width / 2 - 12);
		addRenderableWidget(Button.builder(Component.translatable("suchki.skill.switch_button"),
				b -> this.minecraft.setScreen(new RoleSelectScreen(false, this))).bounds(this.width / 2 - bw - 4, by, bw, 20).build());
		Button reset = addRenderableWidget(Button.builder(
				Component.translatable("suchki.skill.reset_button", SuchkiConfig.INSTANCE.skillResetLevels),
				b -> ClientPlayNetworking.send(Skills.Reset.INSTANCE)).bounds(this.width / 2 + 4, by, bw, 20).build());
		PlayerSkills.Progress p = shown.current();
		reset.active = p != null && !p.nodes().isEmpty();
	}

	/** Размеры подбираются так, чтобы дерево поместилось между шапкой и кнопками на любом экране. */
	private void layout() {
		compact = this.height < 320;
		headerBottom = 6 + (compact ? 34 : 52);
		rootSize = compact ? 24 : 30;
		rootCX = this.width / 2;
		rootCY = this.height - 32 - rootSize / 2 - 4;
		int trunk = compact ? 10 : 16;
		int top = headerBottom + 14;
		// Шаг между ступенями должен вмещать вершину (она крупнее) — иначе 4-я и 5-я наползают друг на друга.
		nodeSize = 24;
		for (int i = 0; i < 3; i++) {
			capSize = nodeSize + (compact ? 4 : 6);
			step = (rootCY - rootSize / 2 - trunk - nodeSize / 2 - (top + capSize / 2)) / 4;
			nodeSize = Math.max(16, Math.min(24, step - (capSize - nodeSize) / 2 - 4));
		}
		capSize = nodeSize + (compact ? 4 : 6);
		step = Math.max((nodeSize + capSize) / 2 + 3, Math.min(48, step));
		crown = Math.max(90, Math.min(200, this.width / 2 - capSize / 2 - 14));
	}

	private int nodeY(int tier) {
		int trunk = compact ? 10 : 16;
		return rootCY - rootSize / 2 - trunk - nodeSize / 2 - (tier - 1) * step;
	}

	/** Крона: ветви сначала уходят вбок, потом тянутся вверх. Средняя ветвь растёт прямо и чуть покачивается. */
	private int nodeX(int branch, int tier) {
		double t = tier / 5.0;
		int spread = (int) Math.round(crown * (1 - (1 - t) * (1 - t)));
		return switch (branch) {
			case 0 -> rootCX - spread;
			case 2 -> rootCX + spread;
			default -> rootCX + (tier % 2 == 0 ? 3 : -3);
		};
	}

	private int forkY() {
		return rootCY - rootSize / 2 - (compact ? 6 : 10);
	}

	@Override
	public void tick() {
		PlayerSkills now = data();
		if (now != this.shown) {
			// Сервер подтвердил покупку: запускаем вспышку на новых навыках и перестраиваем кнопки.
			PlayerSkills.Progress before = shown.current(), after = now.current();
			if (before != null && after != null) {
				for (String id : after.nodes()) if (!before.has(id)) burstAt.put(id, Ui.now());
			}
			this.rebuildWidgets();
		}
	}

	// --- Состояние навыка ---

	private enum State { OWNED, AVAILABLE, NO_POINTS, LOCKED }

	private State state(SkillTree.Role role, PlayerSkills.Progress p, SkillTree.Node n) {
		if (p.has(n.id())) return State.OWNED;
		SkillTree.Node prev = n.tier() > 1 ? role.node(n.branch(), n.tier() - 1) : null;
		if (prev != null && !p.has(prev.id())) return State.LOCKED;
		return p.freePoints() >= n.cost() ? State.AVAILABLE : State.NO_POINTS;
	}

	// --- Отрисовка (в фоне, чтобы кнопки были поверх) ---

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		long frame = Ui.now();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (frame - lastFrame) / 1000.0);
		lastFrame = frame;
		placed.clear();

		PlayerSkills.Progress p = shown.current();
		SkillTree.Role role = shown.active().map(SkillTree::role).orElse(null);
		if (role == null || p == null) {
			g.drawCenteredString(this.font, Component.translatable("suchki.skill.no_role"), this.width / 2, 30, 0xFFFFFFFF);
			return;
		}
		double open = Ui.easeOut(Ui.progress(openedAt, OPEN_MS));
		int slide = (int) Math.round((1 - open) * 10);

		g.pose().pushMatrix();
		g.pose().translate(0, slide);
		renderHeader(g, role, p, open, dt);
		renderTree(g, role, p, open, mouseX, mouseY - slide, dt);
		g.pose().popMatrix();
	}

	private void renderTree(GuiGraphics g, SkillTree.Role role, PlayerSkills.Progress p, double open, int mx, int my, double dt) {
		int color = role.color();
		int glow = Ui.mix(color, 0xFFFFFF, 0.3);
		double breathe = Ui.pulse(2600);

		// Подложка кроны и «земля» под корнем.
		int areaTop = headerBottom + 4;
		int areaW = Math.min(this.width - 12, crown * 2 + capSize + 28);
		int ax = rootCX - areaW / 2;
		Ui.panel(g, ax, areaTop, areaW, rootCY + rootSize / 2 + 6 - areaTop,
				Ui.fade(0x900A0C0E, open), Ui.fade(Ui.argb(0xFF, Ui.shade(color, 0.4)), open));
		g.fillGradient(ax + 1, rootCY - 2, ax + areaW - 1, rootCY + rootSize / 2 + 5,
				0x00000000, Ui.fade(Ui.argb(0x70, Ui.shade(color, 0.5)), open));

		// Ствол: от корня до развилки.
		int fork = forkY();
		bark(g, rootCX, rootCY - rootSize / 2 + 2, rootCX, fork, compact ? 6 : 8, false, open);

		// Ветви: развилка → ступень 1 (изгиб), дальше между ступенями. Толщина убывает к вершине.
		for (int b = 0; b < 3; b++) {
			double px = rootCX, py = fork;
			boolean prevOwned = true;
			for (int t = 1; t <= 5; t++) {
				SkillTree.Node n = role.node(b, t);
				if (n == null) break;
				int nx = nodeX(b, t), ny = nodeY(t);
				State s = state(role, p, n);
				int thick = Math.max(2, (compact ? 5 : 6) - t);
				boolean lit = s == State.OWNED && prevOwned;
				boolean next = prevOwned && s != State.OWNED;
				if (t == 1) bark(g, px, py, nx, ny, thick, true, open);
				else bark(g, px, py, nx, ny, thick, false, open);
				// Сок по ветви: изученное — цвет роли, следующая ступень — тёплое мерцание.
				int sap = lit ? Ui.argb(0xFF, glow) : next ? Ui.argb((int) (90 + 90 * breathe), 0xE8C060) : 0;
				if (sap != 0) {
					if (t == 1) Ui.curve(g, px, py, px, ny, nx, ny, Math.max(1, thick - 3), Ui.fade(sap, open));
					else Ui.line(g, px, py, nx, ny, Math.max(1, thick - 3), Ui.fade(sap, open));
				}
				px = nx;
				py = ny;
				prevOwned = s == State.OWNED;
			}
		}

		// Названия ветвей над вершинами.
		for (int b = 0; b < 3; b++) {
			int bx = nodeX(b, 5);
			int ly = nodeY(5) - capSize / 2 - 11;
			Component name = Component.literal(role.branches().get(b)).withStyle(ChatFormatting.BOLD);
			int half = this.font.width(name) / 2;
			bx = Math.max(ax + half + 4, Math.min(ax + areaW - half - 4, bx));
			g.drawCenteredString(this.font, name, bx, ly, Ui.fade(Ui.argb(0xFF, glow), open));
		}

		// Навыки.
		for (int b = 0; b < 3; b++) {
			ItemStack icon = b < role.branchIcons().size()
					? new ItemStack(BuiltInRegistries.ITEM.getValue(role.branchIcons().get(b))) : ItemStack.EMPTY;
			for (int t = 1; t <= 5; t++) {
				SkillTree.Node n = role.node(b, t);
				if (n == null) continue;
				int size = t == 5 ? capSize : nodeSize;
				Placed pl = new Placed(n, nodeX(b, t), nodeY(t), size);
				placed.add(pl);
				double target = pl.contains(mx, my) ? 1 : 0;
				double hover = Ui.approach(hoverGlow.getOrDefault(n.id(), 0.0), target, 18, dt);
				hoverGlow.put(n.id(), hover);
				drawNode(g, pl, state(role, p, n), color, breathe, hover, icon, open);
			}
		}

		// Корень.
		int rx = rootCX - rootSize / 2, ry = rootCY - rootSize / 2;
		g.fill(rx + 2, ry + 2, rx + rootSize + 2, ry + rootSize + 2, Ui.fade(0x80000000, open));
		Ui.panel(g, rx - 1, ry - 1, rootSize + 2, rootSize + 2, Ui.fade(0xFF000000, open), Ui.fade(0xFF000000, open));
		Ui.panel(g, rx, ry, rootSize, rootSize, Ui.fade(Ui.argb(0xFF, Ui.shade(color, 0.45)), open),
				Ui.fade(Ui.argb(0xFF, glow), open));
		float rs = rootSize / 16F * 0.8F;
		Ui.item(g, new ItemStack(BuiltInRegistries.ITEM.getValue(role.icon())), rootCX - 8 * rs, rootCY - 8 * rs, rs);
	}

	/** Кора: тёмный контур и коричневая середина; t1-ветви идут дугой от развилки. */
	private void bark(GuiGraphics g, double x1, double y1, double x2, double y2, int thick, boolean curved, double open) {
		int outline = Ui.fade(Ui.argb(0xFF, BARK_DARK), open);
		int wood = Ui.fade(Ui.argb(0xFF, BARK), open);
		if (curved) {
			Ui.curve(g, x1, y1, x1, y2, x2, y2, thick + 2, outline);
			Ui.curve(g, x1, y1, x1, y2, x2, y2, thick, wood);
		} else {
			Ui.line(g, x1, y1, x2, y2, thick + 2, outline);
			Ui.line(g, x1, y1, x2, y2, thick, wood);
		}
	}

	private void drawNode(GuiGraphics g, Placed pl, State s, int color, double breathe, double hover, ItemStack icon, double open) {
		int size = pl.size(), cx = pl.x(), cy = pl.y();
		boolean cap = pl.node().tier() == 5;
		// Вспышка при изучении: короткое «вздутие» рамки и расходящееся кольцо.
		Long burst = burstAt.get(pl.node().id());
		double bt = burst == null ? 1 : Ui.progress(burst, BURST_MS);
		if (bt >= 1 && burst != null) burstAt.remove(pl.node().id());
		int grow = bt < 1 ? (int) Math.round(Math.sin(bt * Math.PI) * 3) : 0;
		int x = cx - size / 2 - grow, y = cy - size / 2 - grow, sz = size + grow * 2;

		int border, inner;
		switch (s) {
			case OWNED -> {
				border = Ui.mix(color, 0xFFFFFF, 0.35 + 0.25 * hover);
				inner = Ui.shade(color, 0.5);
			}
			case AVAILABLE -> {
				// Мягкое мерцание: от серого к тёплому золоту, без резких вспышек.
				border = Ui.mix(Ui.mix(0x8A9098, 0xE8C060, 0.35 + 0.45 * breathe), 0xFFFFFF, hover * 0.6);
				inner = 0x262A30;
			}
			case NO_POINTS -> {
				border = Ui.mix(0x6A7078, 0xB0B8C0, hover);
				inner = 0x1E2126;
			}
			default -> {
				border = Ui.mix(0x34383E, 0x6A7078, hover);
				inner = 0x15171A;
			}
		}
		if (s == State.OWNED) {
			// Ореол вокруг изученного навыка.
			g.fill(x - 2, y - 2, x + sz + 2, y + sz + 2, Ui.fade(Ui.argb(0x40, color), open));
		}
		g.fill(x + 2, y + 2, x + sz + 2, y + sz + 2, Ui.fade(0x80000000, open));
		Ui.panel(g, x - 1, y - 1, sz + 2, sz + 2, Ui.fade(0xFF000000, open), Ui.fade(0xFF000000, open));
		Ui.panel(g, x, y, sz, sz, Ui.fade(Ui.argb(0xFF, inner), open), Ui.fade(Ui.argb(0xFF, border), open));
		if (cap) Ui.outline(g, x + 2, y + 2, sz - 4, sz - 4, Ui.fade(Ui.argb(0x90, border), open));
		if (s == State.OWNED) g.fillGradient(x + 1, y + 1, x + sz - 1, y + sz / 2, Ui.fade(0x38FFFFFF, open), 0x00FFFFFF);

		float scale = Math.min(1.25F, (sz - 6) / 16F);
		Ui.item(g, icon, cx - 8 * scale, cy - 8 * scale, scale);
		if (s == State.LOCKED || s == State.NO_POINTS) {
			g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, Ui.fade(s == State.LOCKED ? 0xB0000000 : 0x70000000, open));
		}
		if (bt < 1) {
			int ring = (int) Math.round(bt * 10);
			Ui.outline(g, x - ring, y - ring, sz + ring * 2, sz + ring * 2,
					Ui.argb((int) (220 * (1 - bt)), Ui.mix(color, 0xFFFFFF, 0.5)));
		}
		if (!compact || cap) {
			g.drawString(this.font, String.valueOf(pl.node().tier()), x + sz - 6, y + sz - 9,
					Ui.fade(s == State.OWNED ? 0xFFFFFFFF : 0xFF8A949E, open));
		}
	}

	private void renderHeader(GuiGraphics g, SkillTree.Role role, PlayerSkills.Progress p, double open, double dt) {
		int w = Math.min(300, this.width - 12), x = this.width / 2 - w / 2, y = 6, h = headerBottom - y;
		Ui.panel(g, x, y, w, h, Ui.fade(0xC0101418, open), Ui.fade(Ui.argb(0xFF, Ui.shade(role.color(), 0.8)), open));
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h / 2, Ui.fade(Ui.argb(0x80, Ui.shade(role.color(), 0.6)), open), 0x00000000);
		int textX = compact ? x + 30 : x + 46;
		Ui.item(g, new ItemStack(BuiltInRegistries.ITEM.getValue(role.icon())), x + 7, y + (compact ? 5 : 8), compact ? 1.25F : 2.0F);
		Component title = Component.literal(role.name()).withStyle(ChatFormatting.BOLD);
		g.drawString(this.font, title, textX, y + 7, Ui.fade(Ui.argb(0xFF, Ui.mix(role.color(), 0xFFFFFF, 0.4)), open));
		Component level = Component.translatable("suchki.skill.level", p.level());
		if (compact) g.drawString(this.font, level, textX + this.font.width(title) + 6, y + 7, Ui.fade(0xFFE0E4E8, open));
		else g.drawString(this.font, level, textX, y + 19, Ui.fade(0xFFE0E4E8, open));

		Component points = Component.translatable("suchki.skill.points", p.freePoints());
		int pw = this.font.width(points) + 12;
		int badge = p.freePoints() > 0 ? Ui.argb(0xFF, Ui.mix(0xB08A20, 0xFFE680, Ui.pulse(2600))) : 0xFF5A6068;
		Ui.panel(g, x + w - pw - 8, y + 5, pw, 14, Ui.fade(0xC0000000, open), Ui.fade(badge, open));
		g.drawString(this.font, points, x + w - pw - 2, y + 8, Ui.fade(p.freePoints() > 0 ? 0xFFFFE680 : 0xFF9AA4B0, open));

		// Полоса опыта плавно догоняет настоящее значение.
		int barX = textX, barY = compact ? y + 19 : y + 34, barW = x + w - 8 - textX;
		int need = SkillTree.xpToNext(p.level());
		boolean max = p.level() >= SkillTree.maxLevel();
		double target = max ? 1 : Math.min(1.0, p.xp() / (double) need);
		xpShown = xpShown < 0 || target < xpShown - 0.5 ? target : Ui.approach(xpShown, target, 6, dt);
		int fill = (int) Math.round(barW * xpShown);
		g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, Ui.fade(0xFF000000, open));
		g.fill(barX, barY, barX + barW, barY + 4, Ui.fade(0xFF1E2228, open));
		g.fillGradient(barX, barY, barX + fill, barY + 4, Ui.fade(0xFFB8FF60, open), Ui.fade(0xFF4CA020, open));
		Component xp = max ? Component.translatable("suchki.skill.max_level") : Component.translatable("suchki.skill.xp", p.xp(), need);
		if (compact) g.drawString(this.font, xp, barX, barY + 6, Ui.fade(0xFF9AA4B0, open));
		else g.drawCenteredString(this.font, xp, barX + barW / 2, barY + 8, Ui.fade(0xFFB0B8C0, open));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Placed hovered = hovered(mouseX, mouseY);
		PlayerSkills.Progress p = shown.current();
		SkillTree.Role role = shown.active().map(SkillTree::role).orElse(null);
		if (hovered == null || p == null || role == null) return;
		SkillTree.Node n = hovered.node();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(n.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		lines.add(Component.literal(role.branches().get(n.branch()) + " · ").append(Component.translatable("suchki.skill.tier", n.tier()))
				.withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal(n.desc()).withStyle(ChatFormatting.WHITE));
		lines.add(Component.empty());
		switch (state(role, p, n)) {
			case OWNED -> lines.add(Component.translatable("suchki.skill.owned").withStyle(ChatFormatting.GREEN));
			case AVAILABLE -> lines.add(Component.translatable("suchki.skill.click_to_learn", n.cost()).withStyle(ChatFormatting.YELLOW));
			case NO_POINTS -> lines.add(Component.translatable("suchki.skill.not_enough", n.cost(), p.freePoints()).withStyle(ChatFormatting.RED));
			case LOCKED -> lines.add(Component.translatable("suchki.skill.needs_prev").withStyle(ChatFormatting.RED));
		}
		g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	/** Центр навыка на экране (для автотестов интерфейса), или null, если навык сейчас не нарисован. */
	public int @Nullable [] nodeCenter(String nodeId) {
		for (Placed pl : placed) if (pl.node().id().equals(nodeId)) return new int[] {pl.x(), pl.y()};
		return null;
	}

	private @Nullable Placed hovered(double mx, double my) {
		int slide = (int) Math.round((1 - Ui.easeOut(Ui.progress(openedAt, OPEN_MS))) * 10);
		for (Placed pl : placed) if (pl.contains(mx, my - slide)) return pl;
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Placed hit = hovered(event.x(), event.y());
		PlayerSkills.Progress p = shown.current();
		SkillTree.Role role = shown.active().map(SkillTree::role).orElse(null);
		if (hit != null && p != null && role != null) {
			if (state(role, p, hit.node()) == State.AVAILABLE) {
				ClientPlayNetworking.send(new Skills.Unlock(hit.node().id()));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
