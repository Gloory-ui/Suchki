package com.suchki.enchant;

import com.suchki.SuchkiConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Перки высших уровней, которые делаются событиями Fabric API (остальные — в данных и миксинах). */
public final class Perks {
	public static final String SPAWNER_ENTITY_KEY = "suchki_spawner_entity";

	private Perks() {}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(Perks::afterBlockBreak);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			fireAspectCook(entity, source.getEntity());
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> lootingHeads(entity, source.getEntity()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> baneWeakness(entity, source));
		// Прочность X: изношенный инструмент не бьёт и не копает, пока его не починят.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
				wornOut(player, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
				wornOut(player, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Предмет с Прочностью на максимуме, у которого осталась последняя единица прочности. */
	public static boolean wornOut(Entity holder, ItemStack stack) {
		if (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1) return false;
		return Ench.level(holder, Enchantments.UNBREAKING, stack) >= SuchkiConfig.INSTANCE.unbreakingSaveLevel;
	}

	private static void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (!(level instanceof ServerLevel server) || player.isCreative()) return;
		ItemStack tool = player.getMainHandItem();
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;

		// Удача V: шанс дополнительного самоцвета из руды.
		if (state.is(ConventionalBlockTags.ORES)
				&& Ench.level(player, Enchantments.FORTUNE, tool) >= cfg.fortuneGemLevel
				&& server.random.nextDouble() < cfg.fortuneGemChance) {
			ItemStack gem = new ItemStack(server.random.nextInt(3) == 0 ? Items.DIAMOND : Items.EMERALD);
			net.minecraft.world.level.block.Block.popResource(server, pos, gem);
		}

		// Шёлковое касание: спавнер выпадает предметом и помнит своего моба.
		if (cfg.silkTouchSpawners && state.is(Blocks.SPAWNER) && be instanceof SpawnerBlockEntity spawner
				&& Ench.level(player, Enchantments.SILK_TOUCH, tool) > 0) {
			Entity shown = spawner.getSpawner().getOrCreateDisplayEntity(server, pos);
			ItemStack item = new ItemStack(Items.SPAWNER);
			if (shown != null) {
				CompoundTag tag = new CompoundTag();
				tag.putString(SPAWNER_ENTITY_KEY, EntityType.getKey(shown.getType()).toString());
				item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				item.set(DataComponents.ITEM_NAME,
						Component.translatable("suchki.spawner.named", shown.getType().getDescription()));
			}
			net.minecraft.world.level.block.Block.popResource(server, pos, item);
		}
	}

	/** Заговор огня V: моб, убитый таким оружием, всегда даёт жареный дроп (поджигаем до расчёта лута). */
	private static void fireAspectCook(LivingEntity victim, Entity killer) {
		if (!(killer instanceof Player player)) return;
		ItemStack weapon = player.getMainHandItem();
		if (Ench.level(player, Enchantments.FIRE_ASPECT, weapon) >= SuchkiConfig.INSTANCE.maxLevels
				.getOrDefault("minecraft:fire_aspect", 5)) {
			victim.igniteForSeconds(1);
		}
	}

	/**
	 * Бич членистоногих X: прямой удар по членистоногому накладывает Слабость II на 4–6 секунд
	 * (вместо яда из первого варианта — пауки к яду невосприимчивы).
	 */
	private static void baneWeakness(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) return;
		if (!victim.getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) return;
		if (Ench.level(player, Enchantments.BANE_OF_ARTHROPODS, player.getMainHandItem()) < 10) return;
		int ticks = 80 + victim.getRandom().nextInt(41);
		victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, ticks, 1), player);
	}

	/** Добыча V: шанс выпадения головы моба. */
	private static void lootingHeads(LivingEntity victim, Entity killer) {
		if (!(killer instanceof Player player) || !(victim.level() instanceof ServerLevel server)) return;
		SuchkiConfig cfg = SuchkiConfig.INSTANCE;
		if (Ench.level(player, Enchantments.LOOTING, player.getMainHandItem()) < cfg.lootingHeadLevel) return;
		if (server.random.nextDouble() >= cfg.lootingHeadChance) return;
		EntityType<?> type = victim.getType();
		ItemStack head;
		if (type == EntityType.ZOMBIE) head = new ItemStack(Items.ZOMBIE_HEAD);
		else if (type == EntityType.SKELETON) head = new ItemStack(Items.SKELETON_SKULL);
		else if (type == EntityType.CREEPER) head = new ItemStack(Items.CREEPER_HEAD);
		else if (type == EntityType.WITHER_SKELETON) head = new ItemStack(Items.WITHER_SKELETON_SKULL);
		else if (type == EntityType.PIGLIN) head = new ItemStack(Items.PIGLIN_HEAD);
		else return;
		victim.spawnAtLocation(server, head);
	}
}
