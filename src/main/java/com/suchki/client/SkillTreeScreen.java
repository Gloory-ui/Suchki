package com.suchki.client;

import com.suchki.SuchkiConfig;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.SkillTree;
import com.suchki.skill.Skills;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Дерево навыков активной роли: 3 ветки по 5 ступеней, опыт роли, свободные очки. Клавиша K. */
public class SkillTreeScreen extends Screen {
	private static final int COL_W = 132;
	private static final int NODE_H = 22;
	private static final int TOP = 62;

	private PlayerSkills shown;

	public SkillTreeScreen() {
		super(Component.translatable("suchki.skill.title"));
	}

	private PlayerSkills data() {
		return this.minecraft.player == null ? PlayerSkills.EMPTY : Skills.get(this.minecraft.player);
	}

	@Override
	protected void init() {
		this.shown = data();
		PlayerSkills.Progress p = shown.current();
		SkillTree.Role role = shown.active().map(SkillTree::role).orElse(null);
		int x0 = (this.width - 3 * COL_W) / 2;
		if (role != null && p != null) {
			for (int branch = 0; branch < 3; branch++) {
				for (int tier = 1; tier <= 5; tier++) {
					SkillTree.Node node = role.node(branch, tier);
					if (node == null) continue;
					boolean owned = p.has(node.id());
					SkillTree.Node prev = tier > 1 ? role.node(branch, tier - 1) : null;
					boolean open = prev == null || p.has(prev.id());
					boolean affordable = p.freePoints() >= node.cost();
					MutableComponent label = Component.literal(owned ? "✔ " : open && affordable ? "+ " : "· ")
							.append(node.name());
					label.withStyle(owned ? ChatFormatting.GREEN : open && affordable ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY);
					Button b = Button.builder(label, btn -> ClientPlayNetworking.send(new Skills.Unlock(node.id())))
							.bounds(x0 + branch * COL_W + 4, TOP + (tier - 1) * (NODE_H + 6), COL_W - 8, NODE_H).build();
					b.active = !owned && open && affordable;
					MutableComponent tip = Component.literal(node.name()).withStyle(ChatFormatting.GOLD)
							.append("\n").append(Component.literal(node.desc()).withStyle(ChatFormatting.WHITE))
							.append("\n").append(Component.translatable("suchki.skill.cost", node.cost()).withStyle(ChatFormatting.GRAY));
					if (!open) tip.append("\n").append(Component.translatable("suchki.skill.needs_prev").withStyle(ChatFormatting.RED));
					b.setTooltip(Tooltip.create(tip));
					addRenderableWidget(b);
				}
			}
		}
		int by = TOP + 5 * (NODE_H + 6) + 8;
		addRenderableWidget(Button.builder(Component.translatable("suchki.skill.switch_button"),
				btn -> this.minecraft.setScreen(new RoleSelectScreen(false, this))).bounds(this.width / 2 - 154, by, 150, 20).build());
		Button reset = Button.builder(Component.translatable("suchki.skill.reset_button", SuchkiConfig.INSTANCE.skillResetLevels),
				btn -> ClientPlayNetworking.send(Skills.Reset.INSTANCE)).bounds(this.width / 2 + 4, by, 150, 20).build();
		reset.active = p != null && !p.nodes().isEmpty();
		addRenderableWidget(reset);
	}

	@Override
	public void tick() {
		// Сервер подтвердил покупку/сброс — перестраиваем кнопки.
		if (data() != this.shown) this.rebuildWidgets();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		PlayerSkills.Progress p = shown.current();
		SkillTree.Role role = shown.active().map(SkillTree::role).orElse(null);
		if (role == null || p == null) {
			g.drawCenteredString(this.font, Component.translatable("suchki.skill.no_role"), this.width / 2, 30, 0xFFFFFFFF);
			return;
		}
		int cx = this.width / 2;
		g.drawCenteredString(this.font, Component.literal(role.name() + " — ").append(Component.translatable("suchki.skill.level", p.level())),
				cx, 10, 0xFF000000 | role.color());
		// Полоска опыта роли.
		int barW = 200;
		int need = SkillTree.xpToNext(p.level());
		boolean max = p.level() >= SkillTree.maxLevel();
		int fill = max ? barW : (int) (barW * (p.xp() / (double) need));
		g.fill(cx - barW / 2 - 1, 23, cx + barW / 2 + 1, 29, 0xFF000000);
		g.fill(cx - barW / 2, 24, cx - barW / 2 + fill, 28, 0xFF80FF20);
		g.drawCenteredString(this.font, max ? Component.translatable("suchki.skill.max_level")
						: Component.translatable("suchki.skill.xp", p.xp(), need), cx, 32, 0xFFAAAAAA);
		g.drawCenteredString(this.font, Component.translatable("suchki.skill.points", p.freePoints()), cx, 43, 0xFFFFFF55);
		int x0 = (this.width - 3 * COL_W) / 2;
		for (int b = 0; b < 3; b++) {
			g.drawCenteredString(this.font, Component.literal(role.branches().get(b)).withStyle(ChatFormatting.BOLD),
					x0 + b * COL_W + COL_W / 2, TOP - 10, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
