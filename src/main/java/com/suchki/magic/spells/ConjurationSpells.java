package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.CastStyle.SUMMON;
import static com.suchki.magic.School.CONJURATION;

import com.suchki.magic.Cast;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicItems;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minions;
import com.suchki.magic.SpectralItem;
import com.suchki.magic.Spell;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Колдовство: призрачное оружие и инструменты, призванные союзники, возврат домой. */
public final class ConjurationSpells {
	static final int TEAL = 0x3FE0D0;

	private ConjurationSpells() {}

	public static void register() {
		Spell.of("bound_blade", CONJURATION, 1, SELF).mana(15).charge(10).cooldown(40).icon("diamond_sword")
				.action(c -> {
					ItemStack blade = spectral(c, MagicItems.SPECTRAL_BLADE, c.duration(2400));
					if (c.strong()) blade.enchant(enchantment(c, Enchantments.SHARPNESS), 5);
					return give(c, blade);
				})
				.register();

		Spell.of("familiar", CONJURATION, 1, SUMMON).mana(20).charge(14).cooldown(200).icon("bone")
				.action(c -> {
					int count = c.strong() ? 2 : 1;
					int life = Math.round(1800 * c.power);
					int made = 0;
					for (int i = 0; i < count; i++) {
						Wolf wolf = EntityType.WOLF.create(c.level, EntitySpawnReason.MOB_SUMMONED);
						if (wolf == null) continue;
						if (place(c, wolf, 2.0, i - (count - 1) / 2.0)) {
							wolf.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
							Minions.summon(c, wolf, life);
							made++;
						}
					}
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_SUMMON, 0.9F, 1.3F);
					return made > 0;
				})
				.register();

		Spell.of("bound_tool", CONJURATION, 2, SELF).mana(20).charge(10).cooldown(40).icon("diamond_pickaxe")
				.action(c -> {
					Item tool = MagicItems.SPECTRAL_PICKAXE;
					HitResult hit = c.level.clip(new net.minecraft.world.level.ClipContext(c.eye(), c.eye().add(c.look().scale(6)),
							net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, c.caster));
					if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
						BlockState state = c.level.getBlockState(bh.getBlockPos());
						if (state.is(BlockTags.MINEABLE_WITH_AXE)) tool = MagicItems.SPECTRAL_AXE;
						else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) tool = MagicItems.SPECTRAL_SHOVEL;
					}
					ItemStack stack = spectral(c, tool, c.duration(3600));
					stack.enchant(enchantment(c, Enchantments.EFFICIENCY), c.strong() ? 5 : 3);
					return give(c, stack);
				})
				.register();

		Spell.of("spirit_blades", CONJURATION, 3, SUMMON).mana(40).charge(20).cooldown(400).icon("iron_sword")
				.action(c -> {
					int count = c.strong() ? 4 : 3;
					int life = Math.round(600 * c.power);
					int made = 0;
					for (int i = 0; i < count; i++) {
						Vex vex = EntityType.VEX.create(c.level, EntitySpawnReason.MOB_SUMMONED);
						if (vex == null) continue;
						double a = Math.PI * 2 * i / count;
						Vec3 at = c.caster.getEyePosition().add(Math.cos(a) * 1.2, 0.5, Math.sin(a) * 1.2);
						vex.snapTo(at.x, at.y, at.z, c.caster.getYRot(), 0);
						vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
						vex.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
						Minions.summon(c, vex, life);
						made++;
					}
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_SUMMON, 1.0F, 1.1F);
					return made > 0;
				})
				.register();

		Spell.of("spectral_steed", CONJURATION, 3, SUMMON).mana(35).charge(20).cooldown(600).icon("saddle")
				.action(c -> {
					Horse horse = EntityType.HORSE.create(c.level, EntitySpawnReason.MOB_SUMMONED);
					if (horse == null || !place(c, horse, 1.5, 0)) return false;
					horse.setTamed(true);
					horse.setOwner(c.caster);
					horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
					horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32);
					horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(1.0);
					horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0);
					horse.setHealth(30.0F);
					Minions.summon(c, horse, Math.round(6000 * c.power));
					c.caster.startRiding(horse);
					MagicFx.sound(c.level, horse.position(), MagicSounds.CAST_SUMMON, 1.0F, 1.4F);
					return true;
				})
				.register();

		Spell.of("recall", CONJURATION, 4, RITUAL).mana(60).charge(60).cooldown(1200).icon("recovery_compass")
				.action(c -> {
					TeleportTransition home = c.caster.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
					MagicFx.burst(c.level, Cast.center(c.caster), MagicParticles.spark(TEAL, 0.4F, 20), 40, 0.5, 0.1);
					MagicFx.burst(c.level, Cast.center(c.caster), ParticleTypes.REVERSE_PORTAL, 60, 0.4, 0.3);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_TELEPORT, 1.0F, 0.8F);
					ServerPlayer moved = c.caster.teleport(home);
					if (moved == null) return false;
					moved.resetFallDistance();
					MagicFx.burst(moved.level(), Cast.center(moved), MagicParticles.spark(TEAL, 0.4F, 20), 40, 0.5, 0.1);
					MagicFx.burst(moved.level(), Cast.center(moved), ParticleTypes.PORTAL, 60, 0.4, 0.4);
					MagicFx.sound(moved.level(), moved.position(), MagicSounds.CAST_TELEPORT, 1.0F, 1.2F);
					return true;
				})
				.register();

		Spell.of("iron_guardian", CONJURATION, 5, SUMMON).mana(100).charge(40).cooldown(1200).icon("carved_pumpkin")
				.action(c -> {
					int count = c.strong() ? 2 : 1;
					int life = Math.round(1200 * c.power);
					int made = 0;
					for (int i = 0; i < count; i++) {
						IronGolem golem = EntityType.IRON_GOLEM.create(c.level, EntitySpawnReason.MOB_SUMMONED);
						if (golem == null) continue;
						golem.setPlayerCreated(true);
						if (place(c, golem, 3.0, (i - (count - 1) / 2.0) * 2.0)) {
							Minions.summon(c, golem, life);
							made++;
						}
					}
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_SUMMON, 1.2F, 0.7F);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_EARTH, 1.0F, 0.8F);
					return made > 0;
				})
				.register();
	}

	/** Поставить существо перед магом (вперёд на {@code ahead}, вбок на {@code side}). */
	private static boolean place(Cast c, Mob mob, double ahead, double side) {
		Vec3 forward = c.look().multiply(1, 0, 1).normalize();
		Vec3 want = c.caster.position().add(forward.scale(ahead)).add(c.right().scale(side * 1.5));
		for (int dy = 0; dy <= 2; dy++) {
			Vec3 p = want.add(0, dy, 0);
			if (c.level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(p))) {
				mob.snapTo(p.x, p.y, p.z, c.caster.getYRot(), 0);
				return true;
			}
		}
		mob.snapTo(c.caster.getX(), c.caster.getY(), c.caster.getZ(), c.caster.getYRot(), 0);
		return true;
	}

	private static ItemStack spectral(Cast c, Item item, int ticks) {
		ItemStack stack = new ItemStack(item);
		stack.set(MagicItems.EXPIRES, c.level.getGameTime() + ticks);
		return stack;
	}

	private static Holder<Enchantment> enchantment(Cast c, ResourceKey<Enchantment> key) {
		return c.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** Убрать старый призрачный предмет того же вида и выдать новый (в свободную руку или в инвентарь). */
	private static boolean give(Cast c, ItemStack stack) {
		var inv = c.caster.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == stack.getItem()) inv.setItem(i, ItemStack.EMPTY);
		}
		boolean ok = c.caster.getOffhandItem().isEmpty() && SpectralItem.is(stack)
				? setOffhand(c, stack) : inv.add(stack);
		if (!ok) return false;
		MagicFx.burst(c.level, Cast.center(c.caster), MagicParticles.spark(TEAL, 0.25F, 16), 16, 0.4, 0.04);
		MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_SUMMON, 0.8F, 1.6F);
		return true;
	}

	private static boolean setOffhand(Cast c, ItemStack stack) {
		c.caster.setItemSlot(EquipmentSlot.OFFHAND, stack);
		return true;
	}
}
