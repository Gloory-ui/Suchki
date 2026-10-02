/*
 * Склейка шаров опыта основана на Clumps (https://github.com/jaredlll08/Clumps).
 * Copyright (c) 2021 Jaredlll08, лицензия MIT — полный текст в THIRD_PARTY_LICENSES.md.
 * Изменения Suchki: убрана платформенная обвязка, добавлены радиус склейки и притяжение к игроку из конфига.
 */
package com.suchki.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.suchki.SuchkiConfig;
import com.suchki.xp.ClumpedOrb;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Mixin(value = ExperienceOrb.class, priority = 1001)
public abstract class ExperienceOrbMixin extends Entity implements ClumpedOrb {
	@Shadow private int count;
	@Shadow private int age;

	@Shadow protected abstract int repairPlayerItems(ServerPlayer player, int value);

	@Shadow public abstract int getValue();

	@Shadow protected abstract void setValue(int value);

	/** Значение шара → сколько таких шаров склеено. */
	@Unique private Map<Integer, Integer> suchki$clumped;
	@Unique private Optional<EnchantedItemInUse> suchki$repairing = Optional.empty();

	private ExperienceOrbMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	// --- Склейка (Clumps): любые шары сливаются в один, независимо от значения ---

	@Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
	private void suchki$canMerge(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(other.isAlive() && !this.is(other));
	}

	@Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
	private static void suchki$canMergeStatic(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(orb.isAlive());
	}

	@WrapOperation(method = "scanForMerges",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"))
	private AABB suchki$mergeRadius(AABB box, double radius, Operation<AABB> original) {
		return original.call(box, SuchkiConfig.INSTANCE.xpMergeRadius);
	}

	/** Склеенный шар отдаёт весь опыт за одно касание. */
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void suchki$playerTouch(Player rawPlayer, CallbackInfo ci) {
		if (!(rawPlayer instanceof ServerPlayer player)) return;
		player.takeXpDelay = 0;
		player.take(this, 1);
		if (this.getValue() != 0 || this.suchki$resolve()) {
			int toGive = 0;
			for (Map.Entry<Integer, Integer> e : this.suchki$getClumped().entrySet()) {
				for (int i = 0; i < e.getValue(); i++) {
					toGive += this.repairPlayerItems(player, e.getKey());
				}
			}
			if (toGive > 0) player.giveExperiencePoints(toGive);
		}
		this.discard();
		ci.cancel();
	}

	@ModifyVariable(method = "repairPlayerItems", index = 3, at = @At(value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRandomItemWith(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Optional;"))
	private Optional<EnchantedItemInUse> suchki$captureRepairing(Optional<EnchantedItemInUse> entry) {
		this.suchki$repairing = entry;
		return entry;
	}

	@Inject(method = "repairPlayerItems", cancellable = true, at = @At(value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRandomItemWith(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Optional;"))
	private void suchki$repair(ServerPlayer player, int value, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(this.suchki$repairing.map(found -> {
			ItemStack stack = found.itemStack();
			int xpToRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), stack, value);
			int repaired = Math.min(xpToRepair, stack.getDamageValue());
			stack.setDamageValue(stack.getDamageValue() - repaired);
			if (repaired > 0) {
				int left = value - repaired * value / xpToRepair;
				if (left > 0) return this.repairPlayerItems(player, left);
			}
			return 0;
		}).orElse(value));
	}

	@Inject(method = "merge", cancellable = true,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;discard()V", shift = At.Shift.BEFORE))
	private void suchki$merge(ExperienceOrb other, CallbackInfo ci) {
		this.age = Math.min(this.age, ((ExperienceOrbAccessor) other).suchki$getAge());
		this.suchki$setClumped(sum(this.suchki$getClumped(), ((ClumpedOrb) other).suchki$getClumped()));
		this.count = this.suchki$getClumped().values().stream().reduce(Integer::sum).orElse(1);
		other.discard();
		ci.cancel();
	}

	@Inject(method = "tryMergeToExisting", at = @At("HEAD"), cancellable = true)
	private static void suchki$tryMergeToExisting(ServerLevel level, Vec3 pos, int value, CallbackInfoReturnable<Boolean> cir) {
		double size = SuchkiConfig.INSTANCE.xpMergeRadius * 2.0;
		List<ExperienceOrb> list = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class),
				AABB.ofSize(pos, size, size, size), Entity::isAlive);
		if (list.isEmpty()) {
			cir.setReturnValue(false);
			return;
		}
		ExperienceOrb orb = list.getFirst();
		ClumpedOrb clumped = (ClumpedOrb) orb;
		clumped.suchki$setClumped(sum(clumped.suchki$getClumped(), Collections.singletonMap(value, 1)));
		((ExperienceOrbAccessor) orb).suchki$setCount(clumped.suchki$getClumped().values().stream().reduce(Integer::sum).orElse(1));
		((ExperienceOrbAccessor) orb).suchki$setAge(0);
		cir.setReturnValue(true);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void suchki$save(ValueOutput out, CallbackInfo ci) {
		if (this.suchki$clumped == null) return;
		CompoundTag map = new CompoundTag();
		this.suchki$clumped.forEach((value, n) -> map.putInt(String.valueOf(value), n));
		out.store("clumpedMap", CompoundTag.CODEC, map);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void suchki$load(ValueInput in, CallbackInfo ci) {
		Map<Integer, Integer> map = new HashMap<>();
		in.read("clumpedMap", CompoundTag.CODEC).ifPresentOrElse(tag -> {
			for (String key : tag.keySet()) tag.getInt(key).ifPresent(n -> map.put(Integer.parseInt(key), n));
		}, () -> map.put(this.getValue(), this.count));
		this.suchki$setClumped(map);
	}

	// --- Притяжение к игроку: радиус и сила из конфига (в ванили 8 блоков и слабая тяга) ---

	@ModifyConstant(method = "followNearbyPlayer", constant = @Constant(doubleValue = 64.0))
	private double suchki$followRangeSqr(double original) {
		double r = SuchkiConfig.INSTANCE.xpPullRange;
		return r * r;
	}

	@ModifyConstant(method = "followNearbyPlayer", constant = @Constant(doubleValue = 8.0))
	private double suchki$followRange(double original) {
		return SuchkiConfig.INSTANCE.xpPullRange;
	}

	@ModifyConstant(method = "followNearbyPlayer", constant = @Constant(doubleValue = 0.1))
	private double suchki$pullStrength(double original) {
		return original * SuchkiConfig.INSTANCE.xpPullStrength;
	}

	// --- ClumpedOrb ---

	@Override
	public Map<Integer, Integer> suchki$getClumped() {
		if (this.suchki$clumped == null) {
			this.suchki$clumped = new HashMap<>();
			this.suchki$clumped.put(this.getValue(), 1);
		}
		return this.suchki$clumped;
	}

	@Override
	public void suchki$setClumped(Map<Integer, Integer> map) {
		this.suchki$clumped = map;
		this.suchki$resolve();
	}

	@Unique
	private boolean suchki$resolve() {
		this.setValue(this.suchki$getClumped().entrySet().stream()
				.map(e -> e.getKey() * e.getValue()).reduce(Integer::sum).orElse(1));
		return this.getValue() > 0;
	}

	@Unique
	private static Map<Integer, Integer> sum(Map<Integer, Integer> a, Map<Integer, Integer> b) {
		return Stream.of(a, b).flatMap(m -> m.entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
	}
}
