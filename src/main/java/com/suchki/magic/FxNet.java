package com.suchki.magic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Пакеты эффектов в мире, которые рисует клиент (не частицы: те видны только в 32 блоках и гаснут, когда центр
 * уходит с экрана). Отправляет {@link MagicFx}, рисует {@code client.magic.WorldFxRenderer}.
 */
public final class FxNet {
	private FxNet() {}

	/**
	 * Магический круг в мире: рисуется по штрихам {@code draw} тиков, держится {@code hold}, гаснет {@code fade}.
	 * {@code design} — порядковый номер {@link School} (узор круга), {@code tier} — ранг (сложность узора),
	 * {@code layers} — ярусы вдоль нормали через {@code layerGap} блоков, {@code spin} — оборотов в секунду.
	 */
	public record CircleFx(Vec3 pos, Vec3 normal, float radius, int color, int light, int design, int tier, int layers,
			float layerGap, int draw, int hold, int fade, float spin, int seed) implements CustomPacketPayload {
		public static final Type<CircleFx> TYPE = new Type<>(Magic.id("circle_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CircleFx> CODEC = StreamCodec.of(
				(buf, p) -> {
					Vec3.STREAM_CODEC.encode(buf, p.pos);
					Vec3.STREAM_CODEC.encode(buf, p.normal);
					buf.writeFloat(p.radius);
					buf.writeInt(p.color);
					buf.writeInt(p.light);
					buf.writeVarInt(p.design);
					buf.writeVarInt(p.tier);
					buf.writeVarInt(p.layers);
					buf.writeFloat(p.layerGap);
					buf.writeVarInt(p.draw);
					buf.writeVarInt(p.hold);
					buf.writeVarInt(p.fade);
					buf.writeFloat(p.spin);
					buf.writeInt(p.seed);
				},
				buf -> new CircleFx(Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readInt(),
						buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(),
						buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Прочие эффекты в мире. Смысл полей зависит от {@code kind} (см. константы); {@code ticks} — время жизни,
	 * {@code seed} — для случайных узоров (молния, трещины).
	 */
	public record WorldFx(int kind, Vec3 a, Vec3 b, float r0, float r1, int color, int light, int ticks, int seed)
			implements CustomPacketPayload {
		/** Луч из {@code a} в {@code b}, толщина {@code r0}; {@code r1} > 0 — луч сужается к концу. */
		public static final int BEAM = 0;
		/** Ветвистая молния из {@code a} в {@code b}, толщина {@code r0}. */
		public static final int LIGHTNING = 1;
		/** Расширяющаяся светящаяся сфера с центром {@code a}: радиус от {@code r0} до {@code r1}, гаснет к концу. */
		public static final int SPHERE = 2;
		/** Купол (полусфера) на земле в {@code a}, радиус {@code r0}, держится и гаснет в конце. */
		public static final int DOME = 3;
		/** Чёрное ядро (чёрная дыра): тёмный шар радиуса {@code r0} с аккреционным кольцом радиуса {@code r1}. */
		public static final int CORE = 4;
		/** Ударное кольцо в {@code a}, нормаль {@code b}, радиус от {@code r0} до {@code r1}. */
		public static final int RING = 5;
		/** Столп света от {@code a} вверх: радиус {@code r0}, высота {@code r1}. */
		public static final int PILLAR = 6;
		/** Вспышка экрана у игроков в {@code r0} блоках от {@code a} (цвет {@code color}, сила {@code r1}). */
		public static final int FLASH = 7;
		/** Смерч в {@code a}: радиус {@code r0}, высота {@code r1}. */
		public static final int TORNADO = 8;
		/** Летящий серп (полумесяц) из {@code a} по направлению {@code b}: ширина {@code r0}, длина полёта {@code r1}. */
		public static final int SLASH = 9;
		/** Спиральный луч из {@code a} в {@code b}, толщина {@code r0}. */
		public static final int HELIX = 10;
		/** Купол-сфера (барьер, «расширение территории») вокруг {@code a}, радиус {@code r0}, полупрозрачная. */
		public static final int BARRIER = 11;
		/** Падающая звезда/метеор-след из {@code a} в {@code b}, толщина {@code r0}. */
		public static final int COMET = 12;

		public static final Type<WorldFx> TYPE = new Type<>(Magic.id("world_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, WorldFx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.kind);
					Vec3.STREAM_CODEC.encode(buf, p.a);
					Vec3.STREAM_CODEC.encode(buf, p.b);
					buf.writeFloat(p.r0);
					buf.writeFloat(p.r1);
					buf.writeInt(p.color);
					buf.writeInt(p.light);
					buf.writeVarInt(p.ticks);
					buf.writeInt(p.seed);
				},
				buf -> new WorldFx(buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readFloat(),
						buf.readFloat(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Мультикаст: сколько копий выпускать (клиент → сервер). */
	public record SetMulticast(int copies) implements CustomPacketPayload {
		public static final Type<SetMulticast> TYPE = new Type<>(Magic.id("set_multicast"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetMulticast> CODEC =
				ByteBufCodecs.VAR_INT.map(SetMulticast::new, SetMulticast::copies).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
