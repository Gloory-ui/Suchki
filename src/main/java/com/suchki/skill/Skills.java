package com.suchki.skill;

import com.suchki.Suchki;
import com.suchki.SuchkiConfig;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/** Роли и навыки: хранение, опыт, покупка навыков, смена роли, атрибуты, команды от клиента. */
public final class Skills {
	public static final AttachmentType<PlayerSkills> DATA = AttachmentRegistry.<PlayerSkills>builder()
			.persistent(PlayerSkills.CODEC)
			.copyOnDeath()
			.initializer(() -> PlayerSkills.EMPTY)
			.syncWith(PlayerSkills.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "skills"));

	// --- Команды от клиента ---
	public record ChooseRole(String role) implements CustomPacketPayload {
		public static final Type<ChooseRole> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "choose_role"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChooseRole> CODEC =
				ByteBufCodecs.STRING_UTF8.map(ChooseRole::new, ChooseRole::role).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Unlock(String node) implements CustomPacketPayload {
		public static final Type<Unlock> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "unlock_skill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Unlock> CODEC =
				ByteBufCodecs.STRING_UTF8.map(Unlock::new, Unlock::node).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Reset() implements CustomPacketPayload {
		public static final Reset INSTANCE = new Reset();
		public static final Type<Reset> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "reset_skills"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reset> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private Skills() {}

	public static void init() {
		SkillTree.load();
		PayloadTypeRegistry.playC2S().register(ChooseRole.TYPE, ChooseRole.CODEC);
		PayloadTypeRegistry.playC2S().register(Unlock.TYPE, Unlock.CODEC);
		PayloadTypeRegistry.playC2S().register(Reset.TYPE, Reset.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChooseRole.TYPE, (p, ctx) -> chooseRole(ctx.player(), p.role()));
		ServerPlayNetworking.registerGlobalReceiver(Unlock.TYPE, (p, ctx) -> unlock(ctx.player(), p.node()));
		ServerPlayNetworking.registerGlobalReceiver(Reset.TYPE, (p, ctx) -> reset(ctx.player()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyAttributes(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyAttributes(newPlayer));
		SkillXp.register();
		SkillEffects.register();
	}

	public static PlayerSkills get(Player player) {
		return player.getAttachedOrElse(DATA, PlayerSkills.EMPTY);
	}

	private static void set(ServerPlayer player, PlayerSkills skills) {
		player.setAttached(DATA, skills);
	}

	/** Сумма особого эффекта активной роли игрока. */
	public static double perk(Player player, Perk perk) {
		return get(player).perk(perk);
	}

	/** Только для самопроверки (/suchki_test): если ≥ 0, все шансовые навыки срабатывают с этой вероятностью. */
	public static double testChanceOverride = -1;

	public static boolean roll(Player player, Perk perk) {
		double chance = perk(player, perk);
		if (chance > 0 && testChanceOverride >= 0) chance = testChanceOverride;
		return chance > 0 && player.getRandom().nextDouble() < chance;
	}

	// --- Опыт ---

	/** Начислить опыт активной роли, если игрок сейчас играет за {@code role}. */
	public static void addXp(ServerPlayer player, String role, double amount) {
		PlayerSkills s = get(player);
		if (s.active().isEmpty() || !s.active().get().equals(role) || amount <= 0) return;
		PlayerSkills.Progress p = s.progress(role);
		int max = SkillTree.maxLevel();
		if (p.level() >= max) return;
		int xp = p.xp() + (int) Math.max(1, Math.round(amount * SuchkiConfig.INSTANCE.skillXpRate));
		int level = p.level();
		boolean up = false;
		while (level < max && xp >= SkillTree.xpToNext(level)) {
			xp -= SkillTree.xpToNext(level);
			level++;
			up = true;
		}
		if (level >= max) xp = 0;
		set(player, s.with(role, new PlayerSkills.Progress(level, xp, p.nodes())));
		if (up) {
			SkillTree.Role r = SkillTree.role(role);
			player.sendSystemMessage(Component.translatable("suchki.skill.level_up", r.name(), level)
					.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
		}
	}

	// --- Действия игрока ---

	public static void chooseRole(ServerPlayer player, String role) {
		if (SkillTree.role(role) == null) return;
		PlayerSkills s = get(player);
		if (s.active().filter(role::equals).isPresent()) return;
		long now = player.level().getGameTime();
		long cooldown = SuchkiConfig.INSTANCE.roleSwitchCooldownMinutes * 60L * 20L;
		boolean op = player.level().getServer().getPlayerList().isOp(player.nameAndId());
		if (s.active().isPresent() && now - s.lastSwitch() < cooldown && !op) {
			long left = (cooldown - (now - s.lastSwitch())) / 20 / 60 + 1;
			player.sendSystemMessage(Component.translatable("suchki.skill.switch_cooldown", left).withStyle(ChatFormatting.RED));
			return;
		}
		set(player, s.switchTo(role, now));
		applyAttributes(player);
		player.sendSystemMessage(Component.translatable("suchki.skill.role_chosen", SkillTree.role(role).name())
				.withStyle(ChatFormatting.GREEN));
	}

	public static void unlock(ServerPlayer player, String nodeId) {
		SkillTree.Node node = SkillTree.node(nodeId);
		PlayerSkills s = get(player);
		if (node == null || s.active().filter(node.role()::equals).isEmpty()) return;
		PlayerSkills.Progress p = s.progress(node.role());
		if (p.has(nodeId) || p.freePoints() < node.cost()) return;
		if (node.tier() > 1) {
			SkillTree.Node prev = SkillTree.role(node.role()).node(node.branch(), node.tier() - 1);
			if (prev == null || !p.has(prev.id())) return;
		}
		set(player, s.with(node.role(), p.withNode(nodeId)));
		applyAttributes(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	/** Сброс навыков активной роли за опыт: очки возвращаются, уровень роли остаётся. */
	public static void reset(ServerPlayer player) {
		PlayerSkills s = get(player);
		PlayerSkills.Progress p = s.current();
		if (p == null || p.nodes().isEmpty()) return;
		int cost = SuchkiConfig.INSTANCE.skillResetLevels;
		if (!player.hasInfiniteMaterials() && player.experienceLevel < cost) {
			player.sendSystemMessage(Component.translatable("suchki.skill.reset_cost", cost).withStyle(ChatFormatting.RED));
			return;
		}
		if (!player.hasInfiniteMaterials()) player.giveExperienceLevels(-cost);
		set(player, s.with(s.active().get(), new PlayerSkills.Progress(p.level(), p.xp(), java.util.List.of())));
		applyAttributes(player);
	}

	// --- Атрибуты ---

	private static Identifier modifierId(SkillTree.Node node, int index) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "skill/" + node.id() + "/" + index);
	}

	/** Снять бонусы всех навыков и наложить бонусы взятых навыков активной роли. */
	public static void applyAttributes(ServerPlayer player) {
		Set<Holder<Attribute>> touched = new HashSet<>();
		for (SkillTree.Node node : SkillTree.allNodes()) {
			for (int i = 0; i < node.attrs().size(); i++) {
				AttributeInstance inst = player.getAttribute(node.attrs().get(i).attribute());
				if (inst != null) inst.removeModifier(modifierId(node, i));
				touched.add(node.attrs().get(i).attribute());
			}
		}
		PlayerSkills.Progress p = get(player).current();
		if (p != null) {
			for (String id : p.nodes()) {
				SkillTree.Node node = SkillTree.node(id);
				if (node == null) continue;
				for (int i = 0; i < node.attrs().size(); i++) {
					SkillTree.AttrBonus b = node.attrs().get(i);
					AttributeInstance inst = player.getAttribute(b.attribute());
					if (inst != null) inst.addPermanentModifier(new AttributeModifier(modifierId(node, i), b.amount(), b.operation()));
				}
			}
		}
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	public static Optional<SkillTree.Role> activeRole(Player player) {
		return get(player).active().map(SkillTree::role);
	}
}
