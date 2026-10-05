package com.suchki.enchant;

import com.suchki.Suchki;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Пружина (ботинки I–III): столько дополнительных прыжков в воздухе, каков уровень.
 * Прыжок делает клиент (движение игрока считает он), сервер проверяет право и сбрасывает высоту падения.
 * Высота, набранная прыжками в воздухе, не ранит: урон от падения считается только ниже точки, где игрок
 * оторвался от земли (тот же механизм, что у заряда ветра). Падение с обрыва ниже этой точки ранит как в ванили.
 */
public final class SpringJump {
	public record JumpPayload() implements CustomPacketPayload {
		public static final JumpPayload INSTANCE = new JumpPayload();
		public static final Type<JumpPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "spring_jump"));
		public static final StreamCodec<RegistryFriendlyByteBuf, JumpPayload> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Сколько прыжков в воздухе игрок уже сделал с момента отрыва от земли. */
	private static final Map<UUID, Integer> USED = new ConcurrentHashMap<>();
	/** Высота опоры, с которой игрок последний раз оторвался от земли (воды, лестницы). */
	private static final Map<UUID, Double> TAKEOFF_Y = new ConcurrentHashMap<>();

	private SpringJump() {}

	public static int level(ServerPlayer player) {
		return Ench.level(player, SuchkiEnchantments.SPRING, player.getItemBySlot(EquipmentSlot.FEET));
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(JumpPayload.TYPE, JumpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JumpPayload.TYPE, (payload, context) -> onJump(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) track(p);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			USED.remove(handler.player.getUUID());
			TAKEOFF_Y.remove(handler.player.getUUID());
		});
	}

	/** Каждый тик: на опоре прыжки восстанавливаются и запоминается её высота. */
	public static void track(ServerPlayer p) {
		if (p.onGround() || p.isInWater() || p.onClimbable()) {
			USED.remove(p.getUUID());
			TAKEOFF_Y.put(p.getUUID(), p.getY());
		}
	}

	/** Прыжок в воздухе: {@code false}, если прыжков не осталось или игрок на земле/летает. */
	public static boolean onJump(ServerPlayer player) {
		int used = USED.getOrDefault(player.getUUID(), 0);
		if (used >= level(player) || player.onGround() || player.getAbilities().flying) return false;
		USED.put(player.getUUID(), used + 1);
		player.resetFallDistance();
		// Урон от падения — только ниже точки отрыва от земли (если заряд ветра уже задал точку ниже — берём её).
		double base = TAKEOFF_Y.getOrDefault(player.getUUID(), player.getY());
		if (player.currentImpulseImpactPos != null && player.isIgnoringFallDamageFromCurrentImpulse()) {
			base = Math.min(base, player.currentImpulseImpactPos.y);
		}
		player.currentImpulseImpactPos = new Vec3(player.getX(), base, player.getZ());
		player.setIgnoreFallDamageFromCurrentImpulse(true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(),
				SoundSource.PLAYERS, 0.4F, 1.4F);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		return true;
	}
}
