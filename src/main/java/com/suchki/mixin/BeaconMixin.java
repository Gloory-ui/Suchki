package com.suchki.mixin;

import com.suchki.SuchkiConfig;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Маяк 5-го яруса: пирамида на слой больше (11×11 снизу). Даёт основной эффект на уровень выше
 * и одновременно второй эффект, радиус больше. Ярусы 1–4 работают как в ванили.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconMixin {
	private static final int SUCHKI_MAX_TIER = 5;

	@ModifyConstant(method = "updateBase", constant = @Constant(intValue = 4))
	private static int suchki$moreLayers(int original) {
		return SUCHKI_MAX_TIER;
	}

	@Inject(method = "applyEffects", at = @At("HEAD"), cancellable = true)
	private static void suchki$tierFive(Level level, BlockPos pos, int levels, @Nullable Holder<MobEffect> primary,
			@Nullable Holder<MobEffect> secondary, CallbackInfo ci) {
		if (levels < SUCHKI_MAX_TIER) return;
		ci.cancel();
		if (level.isClientSide() || primary == null) return;
		double radius = SuchkiConfig.INSTANCE.beaconTierFiveRadius;
		int duration = (9 + levels * 2) * 20;
		boolean same = Objects.equals(primary, secondary);
		// Тот же эффект во второй ячейке — ещё +1 к уровню (II → III).
		int amplifier = same ? 2 : 1;
		AABB area = new AABB(pos).inflate(radius).expandTowards(0.0, level.getHeight(), 0.0);
		List<Player> players = level.getEntitiesOfClass(Player.class, area);
		for (Player player : players) {
			player.addEffect(new MobEffectInstance(primary, duration, amplifier, true, true));
			if (!same && secondary != null) player.addEffect(new MobEffectInstance(secondary, duration, 0, true, true));
		}
	}
}
