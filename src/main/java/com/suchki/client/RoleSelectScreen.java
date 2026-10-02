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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Выбор роли. При первом входе — обязательный: окно нельзя закрыть, пока роль не выбрана.
 * Из окна навыков открывается для смены роли (тогда есть кнопка «Отмена»).
 */
public class RoleSelectScreen extends Screen {
	private static final int COLS = 3;
	private static final int W = 128;
	private static final int H = 24;
	private static final int GAP = 6;

	private final boolean mandatory;
	private final @Nullable Screen parent;
	private final List<ItemStack> icons = new ArrayList<>();
	private final List<int[]> iconPos = new ArrayList<>();

	public RoleSelectScreen(boolean mandatory, @Nullable Screen parent) {
		super(Component.translatable(mandatory ? "suchki.skill.choose_title" : "suchki.skill.switch_title"));
		this.mandatory = mandatory;
		this.parent = parent;
	}

	@Override
	protected void init() {
		icons.clear();
		iconPos.clear();
		PlayerSkills skills = this.minecraft.player == null ? PlayerSkills.EMPTY : Skills.get(this.minecraft.player);
		List<SkillTree.Role> roles = new ArrayList<>(SkillTree.roles().values());
		int rows = (roles.size() + COLS - 1) / COLS;
		int totalW = COLS * W + (COLS - 1) * GAP;
		int x0 = (this.width - totalW) / 2;
		int y0 = Math.max(40, (this.height - rows * (H + GAP)) / 2);
		for (int i = 0; i < roles.size(); i++) {
			SkillTree.Role role = roles.get(i);
			int x = x0 + (i % COLS) * (W + GAP);
			int y = y0 + (i / COLS) * (H + GAP);
			boolean current = skills.active().filter(role.id()::equals).isPresent();
			PlayerSkills.Progress p = skills.roles().get(role.id());
			Component label = Component.literal(role.name())
					.append(p != null ? Component.literal("  ур. " + p.level()).withStyle(ChatFormatting.GRAY) : Component.empty());
			Button b = Button.builder(label, btn -> choose(role.id())).bounds(x + 20, y, W - 20, H).build();
			b.setTooltip(Tooltip.create(Component.translatable("suchki.skill.role_tooltip", role.xpFrom(),
					String.join(" · ", role.branches()))));
			b.active = !current;
			addRenderableWidget(b);
			icons.add(new ItemStack(BuiltInRegistries.ITEM.getValue(role.icon())));
			iconPos.add(new int[] {x + 2, y + 4});
		}
		if (!mandatory) {
			addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
					.bounds(this.width / 2 - 60, y0 + rows * (H + GAP) + 10, 120, 20).build());
		}
	}

	private void choose(String role) {
		ClientPlayNetworking.send(new Skills.ChooseRole(role));
		this.minecraft.setScreen(this.mandatory ? null : this.parent);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		g.drawCenteredString(this.font, Component.translatable(mandatory ? "suchki.skill.choose_hint" : "suchki.skill.switch_hint"),
				this.width / 2, 28, 0xFFAAAAAA);
		for (int i = 0; i < icons.size(); i++) g.renderItem(icons.get(i), iconPos.get(i)[0], iconPos.get(i)[1]);
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
