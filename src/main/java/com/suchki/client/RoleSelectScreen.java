package com.suchki.client;

import com.suchki.skill.PlayerSkills;
import com.suchki.skill.SkillTree;
import com.suchki.skill.Skills;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Выбор роли: слева прокручиваемый список карточек (колесо мыши, плавная прокрутка), справа подробности
 * выбранной роли и кнопка «Выбрать». Двойной щелчок по карточке — тоже выбор.
 * При первом входе окно обязательное — закрыть его, не выбрав роль, нельзя.
 */
public class RoleSelectScreen extends Screen {
	private static final int CARD_H = 34;
	private static final int CARD_GAP = 4;
	private static final int PANEL_BG = 0xD0101418;
	private static final int PANEL_BORDER = 0xFF3A4048;
	private static final int OPEN_MS = 260;
	private static final int DETAIL_MS = 180;

	private final boolean mandatory;
	private final @Nullable Screen parent;
	private final List<SkillTree.Role> roles = new ArrayList<>();
	private PlayerSkills skills = PlayerSkills.EMPTY;
	private int selected = -1;
	private double scrollTarget, scroll, highlightY = -1;
	private long openedAt, selectedAt, lastFrame;
	private Button chooseButton;

	private int left, top, bottom, listW, detailX, detailW;

	public RoleSelectScreen(boolean mandatory, @Nullable Screen parent) {
		super(Component.translatable(mandatory ? "suchki.skill.choose_title" : "suchki.skill.switch_title"));
		this.mandatory = mandatory;
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (openedAt == 0) openedAt = Ui.now();
		roles.clear();
		roles.addAll(SkillTree.roles().values());
		skills = this.minecraft.player == null ? PlayerSkills.EMPTY : Skills.get(this.minecraft.player);
		if (selected < 0) {
			selected = 0;
			for (int i = 0; i < roles.size(); i++) {
				if (skills.active().filter(roles.get(i).id()::equals).isPresent()) selected = i;
			}
			selectedAt = Ui.now();
			scrollTarget = scroll = Math.max(0, selected * (CARD_H + CARD_GAP) - (CARD_H + CARD_GAP));
		}
		int total = Math.min(this.width - 16, 480);
		this.listW = Math.min(180, total * 2 / 5);
		this.left = (this.width - total) / 2;
		this.top = this.height < 260 ? 38 : 46;
		this.bottom = this.height - 10;
		this.detailX = left + listW + 6;
		this.detailW = total - listW - 6;

		int buttonY = bottom - 26;
		int bw = mandatory ? detailW - 16 : (detailW - 22) / 2;
		chooseButton = addRenderableWidget(Button.builder(Component.empty(), b -> choose(roles.get(selected).id()))
				.bounds(detailX + 8, buttonY, bw, 20).build());
		if (!mandatory) {
			addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
					.bounds(detailX + 14 + bw, buttonY, bw, 20).build());
		}
		updateButton();
		clampScroll();
	}

	private void updateButton() {
		SkillTree.Role role = roles.get(selected);
		boolean current = skills.active().filter(role.id()::equals).isPresent();
		chooseButton.setMessage(current ? Component.translatable("suchki.skill.current_role")
				: Component.translatable("suchki.skill.choose_button", role.name()));
		chooseButton.active = !current;
	}

	private void select(int index) {
		if (index == selected) return;
		selected = index;
		selectedAt = Ui.now();
		updateButton();
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void choose(String role) {
		ClientPlayNetworking.send(new Skills.ChooseRole(role));
		this.minecraft.setScreen(this.mandatory ? null : this.parent);
	}

	// --- Прокрутка ---

	private int listTop() {
		return top + 5;
	}

	private int listBottom() {
		return bottom - 5;
	}

	private int contentHeight() {
		return roles.size() * (CARD_H + CARD_GAP) - CARD_GAP;
	}

	private double maxScroll() {
		return Math.max(0, contentHeight() - (listBottom() - listTop()));
	}

	private void clampScroll() {
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= left && mouseX < left + listW) {
			scrollTarget = Mth.clamp(scrollTarget - scrollY * (CARD_H + CARD_GAP), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		// Стрелки вверх/вниз листают роли, Enter — выбрать.
		int key = event.key();
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
			int next = Mth.clamp(selected + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, roles.size() - 1);
			select(next);
			ensureVisible(next);
			return true;
		}
		if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && chooseButton.active) {
			choose(roles.get(selected).id());
			return true;
		}
		return super.keyPressed(event);
	}

	private void ensureVisible(int index) {
		int y = index * (CARD_H + CARD_GAP);
		int view = listBottom() - listTop();
		if (y < scrollTarget) scrollTarget = y;
		else if (y + CARD_H > scrollTarget + view) scrollTarget = y + CARD_H - view;
		clampScroll();
	}

	private int cardAt(double mx, double my) {
		if (mx < left + 4 || mx >= left + listW - 8 || my < listTop() || my >= listBottom()) return -1;
		int y = (int) (my - listTop() + scroll);
		int index = y / (CARD_H + CARD_GAP);
		if (index < 0 || index >= roles.size() || y % (CARD_H + CARD_GAP) >= CARD_H) return -1;
		return index;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = cardAt(event.x(), event.y());
		if (index >= 0) {
			if (index != selected) select(index);
			else if (doubleClick && chooseButton.active) choose(roles.get(index).id());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// --- Отрисовка (в фоне, кнопки поверх) ---

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		long frame = Ui.now();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (frame - lastFrame) / 1000.0);
		lastFrame = frame;
		scroll = Ui.approach(scroll, scrollTarget, 14, dt);
		if (Math.abs(scroll - scrollTarget) < 0.5) scroll = scrollTarget;

		double open = Ui.easeOut(Ui.progress(openedAt, OPEN_MS));
		int slide = (int) Math.round((1 - open) * 10);
		g.pose().pushMatrix();
		g.pose().translate(0, slide);
		g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, top - 32,
				Ui.fade(0xFFFFFFFF, open));
		g.drawCenteredString(this.font, Component.translatable(mandatory ? "suchki.skill.choose_hint" : "suchki.skill.switch_hint"),
				this.width / 2, top - 19, Ui.fade(0xFF9AA4B0, open));
		renderList(g, mouseX, mouseY - slide, open, dt);
		renderDetails(g, open);
		g.pose().popMatrix();
	}

	private void renderList(GuiGraphics g, int mouseX, int mouseY, double open, double dt) {
		Ui.panel(g, left, top, listW, bottom - top, Ui.fade(PANEL_BG, open), Ui.fade(PANEL_BORDER, open));
		int hovered = cardAt(mouseX, mouseY);
		double targetY = selected * (CARD_H + CARD_GAP);
		highlightY = highlightY < 0 ? targetY : Ui.approach(highlightY, targetY, 16, dt);

		g.enableScissor(left + 1, listTop(), left + listW - 1, listBottom());
		int x = left + 4, w = listW - 14;
		// Подсветка выбранной карточки плавно переезжает к новой.
		SkillTree.Role sel = roles.get(selected);
		int hy = listTop() + (int) Math.round(highlightY - scroll);
		Ui.panel(g, x - 1, hy - 1, w + 2, CARD_H + 2, Ui.fade(Ui.argb(0xC0, Ui.shade(sel.color(), 0.35)), open),
				Ui.fade(Ui.argb(0xFF, sel.color()), open));
		for (int i = 0; i < roles.size(); i++) {
			int y = listTop() + i * (CARD_H + CARD_GAP) - (int) Math.round(scroll);
			if (y + CARD_H < listTop() || y > listBottom()) continue;
			SkillTree.Role role = roles.get(i);
			boolean isActive = skills.active().filter(role.id()::equals).isPresent();
			if (i != selected) {
				Ui.panel(g, x, y, w, CARD_H, Ui.fade(i == hovered ? 0xB0262C34 : 0xB01A1E24, open),
						Ui.fade(i == hovered ? 0xFF5A6470 : 0xFF2C323A, open));
			}
			g.fill(x + 1, y + 1, x + 4, y + CARD_H - 1, Ui.fade(Ui.argb(0xFF, role.color()), open));
			Ui.item(g, icon(role), x + 8, y + 5, 1.5F);
			Component name = Component.literal(role.name()).withStyle(ChatFormatting.BOLD);
			g.drawString(this.font, name, x + 36, y + 7, Ui.fade(Ui.argb(0xFF, Ui.mix(role.color(), 0xFFFFFF, 0.35)), open));
			PlayerSkills.Progress p = skills.roles().get(role.id());
			Component sub = isActive ? Component.translatable("suchki.skill.badge_active", p == null ? 1 : p.level())
					: p != null ? Component.translatable("suchki.skill.badge_level", p.level())
					: Component.translatable("suchki.skill.badge_new");
			g.drawString(this.font, sub, x + 36, y + 19, Ui.fade(isActive ? 0xFF80FF80 : 0xFF8A949E, open));
		}
		g.disableScissor();

		int viewH = listBottom() - listTop();
		if (contentHeight() > viewH) {
			int trackX = left + listW - 6;
			g.fill(trackX, listTop(), trackX + 3, listBottom(), Ui.fade(0x60000000, open));
			int thumbH = Math.max(16, viewH * viewH / contentHeight());
			int thumbY = listTop() + (int) ((viewH - thumbH) * (scroll / maxScroll()));
			g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Ui.fade(0xFFB0B8C0, open));
		}
	}

	private void renderDetails(GuiGraphics g, double open) {
		SkillTree.Role role = roles.get(selected);
		// Подробности новой роли проявляются плавно.
		double a = open * Ui.easeOut(Ui.progress(selectedAt, DETAIL_MS));
		int x = detailX, y = top, w = detailW, h = bottom - top;
		Ui.panel(g, x, y, w, h, Ui.fade(PANEL_BG, open), Ui.fade(Ui.argb(0xFF, Ui.shade(role.color(), 0.8)), open));
		g.fillGradient(x + 1, y + 1, x + w - 1, y + 40, Ui.fade(Ui.argb(0x90, Ui.shade(role.color(), 0.6)), a), 0x00000000);

		Ui.item(g, icon(role), x + 8, y + 6, 2.0F);
		g.pose().pushMatrix();
		g.pose().translate(x + 46, y + 9);
		g.pose().scale(1.4F, 1.4F);
		g.drawString(this.font, Component.literal(role.name()).withStyle(ChatFormatting.BOLD), 0, 0,
				Ui.fade(Ui.argb(0xFF, Ui.mix(role.color(), 0xFFFFFF, 0.4)), a));
		g.pose().popMatrix();
		int ty = y + 24;
		for (FormattedCharSequence line : this.font.split(Component.translatable("suchki.skill.xp_from", role.xpFrom()), w - 54)) {
			g.drawString(this.font, line, x + 46, ty, Ui.fade(0xFFB8C0C8, a));
			ty += 9;
		}

		// Сколько места осталось до кнопки — от этого зависит, насколько подробно показываем ветки.
		int buttonTop = bottom - 30;
		ty = Math.max(ty, y + 42) + 2;
		List<FormattedCharSequence> desc = this.font.split(Component.literal(role.desc()), w - 16);
		int rowsH = 3 * 20;
		int maxDescLines = Math.max(1, (buttonTop - ty - rowsH - 6) / 10);
		for (int i = 0; i < Math.min(desc.size(), maxDescLines); i++) {
			g.drawString(this.font, desc.get(i), x + 8, ty, Ui.fade(0xFFE0E4E8, a));
			ty += 10;
		}
		ty += 4;
		// Если места хватает — у ветки две строки: «ветка → вершина» и описание вершины.
		boolean roomy = buttonTop - ty >= 3 * 32 + 26;
		int rowH = roomy ? 30 : 18;
		for (int b = 0; b < 3 && ty + rowH <= buttonTop; b++) {
			Ui.panel(g, x + 6, ty, w - 12, rowH, Ui.fade(0x80000000, a), Ui.fade(0xFF2C323A, a));
			if (b < role.branchIcons().size()) {
				g.renderItem(new ItemStack(BuiltInRegistries.ITEM.getValue(role.branchIcons().get(b))), x + 8, ty + (rowH - 16) / 2);
			}
			SkillTree.Node cap = role.node(b, 5);
			Component line = Component.literal(role.branches().get(b)).withStyle(ChatFormatting.WHITE);
			if (cap != null) line = line.copy().append(Component.literal(" → " + cap.name()).withStyle(ChatFormatting.GOLD));
			List<FormattedCharSequence> fit = this.font.split(line, w - 34);
			if (!fit.isEmpty()) g.drawString(this.font, fit.getFirst(), x + 27, ty + (roomy ? 5 : 5), Ui.fade(0xFFFFFFFF, a));
			if (roomy && cap != null) {
				List<FormattedCharSequence> d = this.font.split(Component.literal(cap.desc()), w - 34);
				if (!d.isEmpty()) g.drawString(this.font, d.getFirst(), x + 27, ty + 17, Ui.fade(0xFF8A949E, a));
			}
			ty += rowH + 2;
		}
		if (roomy && ty + 12 <= buttonTop - 12) {
			g.drawCenteredString(this.font, Component.translatable("suchki.skill.select_hint"), x + w / 2, buttonTop - 12,
					Ui.fade(0xFF6A747E, a));
		}
		PlayerSkills.Progress p = skills.roles().get(role.id());
		if (p != null && ty + 10 <= buttonTop) {
			g.drawString(this.font, Component.translatable("suchki.skill.your_progress", p.level(), p.nodes().size()),
					x + 8, ty + 1, Ui.fade(0xFF80FF80, a));
		}
	}

	private static ItemStack icon(SkillTree.Role role) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(role.icon()));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !mandatory;
	}

	@Override
	public void onClose() {
		if (!mandatory) this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
