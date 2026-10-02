package com.suchki.enchant;

import com.suchki.Suchki;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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

/**
 * Пружина (ботинки I–III): столько дополнительных прыжков в воздухе, каков уровень.
 * Прыжок делает клиент (движение игрока считает он), сервер проверяет право и сбрасывает высоту падения.
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

	private SpringJump() {}

	public static int level(ServerPlayer player) {
		return Ench.level(player, SuchkiEnchantments.SPRING, player.getItemBySlot(EquipmentSlot.FEET));
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(JumpPayload.TYPE, JumpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JumpPayload.TYPE, (payload, context) -> onJump(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.onGround() || p.isInWater() || p.onClimbable()) USED.remove(p.getUUID());
			}
		});
	}

	private static void onJump(ServerPlayer player) {
		int used = USED.getOrDefault(player.getUUID(), 0);
		if (used >= level(player) || player.onGround() || player.getAbilities().flying) return;
		USED.put(player.getUUID(), used + 1);
		player.resetFallDistance();
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(),
				SoundSource.PLAYERS, 0.4F, 1.4F);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
	}
}
