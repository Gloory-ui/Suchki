package com.suchki.skill;

import com.suchki.Suchki;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Постоянные эффекты навыков: ночное зрение в пещерах, подсветка мобов, рост посевов, двойная добыча с мобов. */
public final class SkillEffects {
	/** Ночное зрение Шахтёра держим чуть дольше 10 с — тогда ванильное мерцание (последние 10 с) не появляется. */
	private static final int NIGHT_VISION_TICKS = 15 * 20;

	/** Сервер → охотнику: какие мобы подсвечены. Эффект свечения не используем — его видели бы все игроки. */
	public record HunterSense(List<Integer> ids) implements CustomPacketPayload {
		public static final Type<HunterSense> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Suchki.MOD_ID, "hunter_sense"));
		public static final StreamCodec<ByteBuf, HunterSense> CODEC =
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(HunterSense::new, HunterSense::ids);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private SkillEffects() {}

	static void register() {
		PayloadTypeRegistry.playS2C().register(HunterSense.TYPE, HunterSense.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) tick(p);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer sp && entity instanceof Mob mob
					&& entity.level() instanceof ServerLevel level && Skills.roll(sp, Perk.MOB_LOOT_EXTRA)) {
				((com.suchki.mixin.LivingEntityInvoker) mob).suchki$dropFromLootTable(level, source, true);
				com.suchki.fx.Fx.skillProc(level, sp, mob.position().add(0, 0.5, 0));
			}
		});
	}

	/** Охотник: id враждебных мобов в радиусе навыка (пусто без навыка). */
	public static List<Integer> hunterTargets(ServerPlayer player) {
		double radius = Skills.get(player).perk(Perk.HUNTER_SENSE);
		List<Integer> ids = new ArrayList<>();
		if (radius <= 0) return ids;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(radius),
				m -> m instanceof Enemy && m.isAlive())) {
			ids.add(mob.getId());
		}
		return ids;
	}

	/** Нужно ли ночное зрение Шахтёру: ниже Y=40, включается при свете < 8, держится до света 11. */
	public static boolean wantsNightVision(int y, int light, boolean alreadyOn) {
		return y < 40 && (light < 8 || alreadyOn && light < 11);
	}

	private static void tick(ServerPlayer player) {
		PlayerSkills s = Skills.get(player);
		if (s.current() == null) return;
		ServerLevel level = player.level();

		// Шахтёр: ночное зрение в тёмных пещерах ниже Y=40. Включается в темноте (свет < 8), выключается
		// на свету (≥ 11) — без дёрганья на границе. При выходе снимается сразу, без ванильного мерцания.
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		boolean ours = nv != null && nv.isAmbient() && nv.getDuration() <= NIGHT_VISION_TICKS;
		int light = level.getMaxLocalRawBrightness(player.blockPosition());
		if (s.perk(Perk.NIGHT_VISION_UNDERGROUND) > 0 && wantsNightVision(player.getBlockY(), light, ours)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, true));
		} else if (ours) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}

		// Охотник: враждебные мобы рядом подсвечиваются — только у него на экране (клиент держит список 3 с).
		if (s.perk(Perk.HUNTER_SENSE) > 0 && ServerPlayNetworking.canSend(player, HunterSense.TYPE)) {
			ServerPlayNetworking.send(player, new HunterSense(hunterTargets(player)));
		}

		// Фермер: несколько посевов в радиусе 4 блоков подрастают на одну стадию.
		// Делаем до wanted×3 попыток найти посев, пока не подрастёт wanted штук.
		int wanted = (int) s.perk(Perk.CROP_AURA);
		int grown = 0;
		for (int i = 0; i < wanted * 3 && grown < wanted; i++) {
			BlockPos pos = player.blockPosition().offset(level.random.nextInt(9) - 4, level.random.nextInt(3) - 1,
					level.random.nextInt(9) - 4);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
				level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
				com.suchki.fx.Fx.cropGrew(level, pos);
				grown++;
			}
		}
	}
}
