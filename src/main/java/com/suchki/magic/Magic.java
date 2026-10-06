package com.suchki.magic;

import com.mojang.serialization.Codec;
import com.suchki.Suchki;
import com.suchki.skill.Perk;
import com.suchki.skill.PlayerSkills;
import com.suchki.skill.Skills;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Магия на сервере: мана, изучение, выбор заклинания, зарядка с накоплением силы, выпуск, перезарядки.
 * Клиент читает отсюда те же формулы (время зарядки, предел), чтобы анимации совпадали с сервером.
 */
public final class Magic {
	public static final String ROLE = "mage";
	/** Сколько тиков можно держать заклинание на пике, прежде чем оно сорвётся само. */
	public static final int HOLD_TICKS = 60;
	/** Сколько тиков после траты маны она не восстанавливается. */
	public static final int REGEN_DELAY = 30;
	/** Сколько тиков разгоняется поток до предела. */
	public static final int CHANNEL_RAMP = 60;
	public static final List<String> STARTER = List.of("firebolt", "healing", "candlelight");

	public static final AttachmentType<MagicData> DATA = AttachmentRegistry.<MagicData>builder()
			.persistent(MagicData.CODEC)
			.copyOnDeath()
			.initializer(() -> MagicData.EMPTY)
			.syncWith(MagicData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(id("magic"));

	public static final AttachmentType<Float> MANA = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.copyOnDeath()
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(id("mana"));

	/** Что игрок колдует прямо сейчас (id заклинания) — видят все, чтобы рисовать анимацию. */
	public static final AttachmentType<String> CASTING = AttachmentRegistry.<String>builder()
			.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
			.buildAndRegister(id("casting"));

	// --- Пакеты ---

	public record SelectSpell(String spell) implements CustomPacketPayload {
		public static final Type<SelectSpell> TYPE = new Type<>(id("select_spell"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpell> CODEC =
				ByteBufCodecs.STRING_UTF8.map(SelectSpell::new, SelectSpell::spell).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Событие каста для анимаций и звуков: готово, ступень накопления, пик, выпуск, срыв. */
	public record CastFx(int entity, String spell, byte kind, float power) implements CustomPacketPayload {
		public static final byte READY = 0, STAGE = 1, PEAK = 2, RELEASE = 3, FIZZLE = 4;
		public static final Type<CastFx> TYPE = new Type<>(id("cast_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CastFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CastFx::entity,
				ByteBufCodecs.STRING_UTF8, CastFx::spell,
				ByteBufCodecs.BYTE, CastFx::kind,
				ByteBufCodecs.FLOAT, CastFx::power,
				CastFx::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Тряска камеры у тех, кто рядом с точкой (взрывы, метеоры, землетрясение). */
	public record Shake(Vec3 at, float strength, int duration) implements CustomPacketPayload {
		public static final Type<Shake> TYPE = new Type<>(id("shake"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC = StreamCodec.composite(
				Vec3.STREAM_CODEC, Shake::at,
				ByteBufCodecs.FLOAT, Shake::strength,
				ByteBufCodecs.VAR_INT, Shake::duration,
				Shake::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Зарядка, которая идёт у игрока прямо сейчас. */
	static final class Charge {
		final Spell spell;
		final FocusItem focus;
		/** Цена (для потока — в секунду) с учётом фокуса и навыков. */
		final float cost;
		final float cap;
		final int form;
		final int over;
		float invested;
		int peakTicks;
		int stage;
		boolean ready;
		boolean starved;
		int channelTicks;

		Charge(Spell spell, FocusItem focus, float cost, float cap, int form, int over) {
			this.spell = spell;
			this.focus = focus;
			this.cost = cost;
			this.cap = cap;
			this.form = form;
			this.over = over;
		}

		float power() {
			return Math.min(cap, invested / cost);
		}
	}

	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	private static final Map<UUID, Long> LAST_SPEND = new HashMap<>();
	private static final Map<UUID, Float> XP_LEFT = new HashMap<>();

	private Magic() {}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Suchki.MOD_ID, path);
	}

	public static void init() {
		Spells.init();
		MagicSounds.init();
		MagicParticles.init();
		MagicEffects.init();
		MagicBlocks.init();
		MagicItems.init();
		MagicEntities.init();
		Minions.init();
		Minds.init();
		Zones.init();
		MagicLoot.init();
		PayloadTypeRegistry.playC2S().register(SelectSpell.TYPE, SelectSpell.CODEC);
		PayloadTypeRegistry.playS2C().register(CastFx.TYPE, CastFx.CODEC);
		PayloadTypeRegistry.playS2C().register(Shake.TYPE, Shake.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectSpell.TYPE, (p, ctx) -> select(ctx.player(), p.spell()));
		ServerTickEvents.END_SERVER_TICK.register(Magic::serverTick);
		// Выброшенный призрачный предмет сразу тает.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && SpectralItem.is(item.getItem())) {
				MagicFx.spectralVanish(level, item.position());
				item.discard();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			CHARGES.remove(id);
			LAST_SPEND.remove(id);
			XP_LEFT.remove(id);
		});
	}

	// --- Кто и сколько ---

	public static MagicData data(Player player) {
		return player.getAttachedOrElse(DATA, MagicData.EMPTY);
	}

	private static void setData(ServerPlayer player, MagicData data) {
		player.setAttached(DATA, data);
	}

	public static boolean isMage(Player player) {
		return Skills.get(player).active().filter(ROLE::equals).isPresent();
	}

	public static int mageLevel(Player player) {
		return Skills.get(player).progress(ROLE).level();
	}

	private static double perk(Player player, Perk perk) {
		PlayerSkills s = Skills.get(player);
		return s.active().filter(ROLE::equals).isPresent() ? s.perk(perk) : 0.0;
	}

	public static float maxMana(Player player) {
		return (float) (100 + mageLevel(player) + perk(player, Perk.MANA_MAX));
	}

	/** Восстановление маны в секунду. */
	public static float regenPerSecond(Player player) {
		return (float) ((2.0 + 0.04 * mageLevel(player)) * (1.0 + perk(player, Perk.MANA_REGEN)));
	}

	public static float mana(Player player) {
		Float m = player.getAttached(MANA);
		return Math.min(m == null ? maxMana(player) : m, maxMana(player));
	}

	static void setMana(ServerPlayer player, float value) {
		player.setAttached(MANA, Math.max(0.0F, Math.min(value, maxMana(player))));
	}

	public static float spellBonus(Player player) {
		return (float) (1.0 + perk(player, Perk.SPELL_POWER));
	}

	public static float costMultiplier(Player player, FocusItem focus) {
		return (float) (focus.costMult * (1.0 - Math.min(0.6, perk(player, Perk.MANA_COST))));
	}

	public static float castSpeed(Player player, FocusItem focus) {
		return (float) (focus.castSpeed * (1.0 + perk(player, Perk.CAST_SPEED)));
	}

	/** Сколько тиков формируется заклинание (до «готово»). */
	public static int formTicks(Spell spell, float speed) {
		return Math.max(2, Math.round(spell.chargeTicks / speed));
	}

	/** Сколько тиков идёт накопление от «готово» до предела. */
	public static int overTicks(Spell spell, int form) {
		return spell.style == CastStyle.RITUAL ? Math.max(20, form) : Math.max(20, Math.min(60, Math.round(form * 1.5F)));
	}

	public static float cost(Player player, Spell spell, FocusItem focus) {
		return spell.mana * costMultiplier(player, focus);
	}

	private static boolean free(Player player) {
		return player.hasInfiniteMaterials();
	}

	// --- Изучение и выбор ---

	public enum LearnResult { LEARNED, NOT_MAGE, LOW_LEVEL, KNOWN }

	public static LearnResult learn(ServerPlayer player, Spell spell) {
		if (!isMage(player)) return LearnResult.NOT_MAGE;
		if (mageLevel(player) < spell.requiredLevel() && !free(player)) return LearnResult.LOW_LEVEL;
		MagicData d = data(player);
		if (d.knows(spell.id)) return LearnResult.KNOWN;
		setData(player, d.learn(spell.id));
		return LearnResult.LEARNED;
	}

	/** Для команд и самопроверки: выучить без проверок. */
	public static void teach(ServerPlayer player, Spell spell) {
		setData(player, data(player).learn(spell.id));
	}

	public static void forgetAll(ServerPlayer player) {
		setData(player, data(player).forgetAll());
	}

	public static void select(ServerPlayer player, String spell) {
		MagicData d = data(player);
		if (!d.knows(spell) || CHARGES.containsKey(player.getUUID())) return;
		setData(player, d.select(spell));
	}

	/** Первый выбор роли Мага: посох ученика и три заклинания. */
	public static void onRoleChosen(ServerPlayer player, String role) {
		if (!ROLE.equals(role) || data(player).starter()) return;
		MagicData d = data(player);
		for (String s : STARTER) d = d.learn(s);
		setData(player, d.select(STARTER.get(0)).withStarter());
		player.setAttached(MANA, maxMana(player));
		ItemStack staff = new ItemStack(MagicItems.APPRENTICE_STAFF);
		if (!player.getInventory().add(staff)) player.drop(staff, false);
		player.sendSystemMessage(Component.translatable("suchki.magic.starter").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// --- Каст ---

	/** Почему нельзя начать каст (или null — можно). Одинаково считается на клиенте и сервере. */
	public static Component startProblem(Player player, FocusItem focus) {
		if (!isMage(player)) return Component.translatable("suchki.magic.not_mage");
		MagicData d = data(player);
		Spell spell = d.selectedSpell();
		if (spell == null) return Component.translatable("suchki.magic.no_spell");
		if (spell.tier > focus.maxTier) {
			return Component.translatable("suchki.magic.focus_too_weak", Component.translatable("suchki.tier." + spell.tier));
		}
		long now = player.level().getGameTime();
		if (d.readyAt(spell.id) > now && !free(player)) {
			return Component.translatable("suchki.magic.cooldown", (d.readyAt(spell.id) - now + 19) / 20);
		}
		float need = spell.charged() ? cost(player, spell, focus) : cost(player, spell, focus) * 0.5F;
		if (mana(player) + 0.01F < need && !free(player)) return Component.translatable("suchki.magic.no_mana");
		return null;
	}

	static void start(ServerPlayer player, FocusItem focus) {
		Spell spell = data(player).selectedSpell();
		if (spell == null) return;
		float speed = castSpeed(player, focus);
		int form = formTicks(spell, speed);
		Charge c = new Charge(spell, focus, cost(player, spell, focus), focus.cap, form, overTicks(spell, form));
		CHARGES.put(player.getUUID(), c);
		player.setAttached(CASTING, spell.id);
		MagicFx.castStart(player.level(), player, spell);
	}

	/** Каждый тик каста (из {@link FocusItem#onUseTick}). */
	static void tick(ServerPlayer player, FocusItem focus) {
		Charge c = CHARGES.get(player.getUUID());
		if (c == null || c.focus != focus) return;
		ServerLevel level = player.level();
		float mana = mana(player);
		boolean free = free(player);
		if (c.spell.charged()) {
			float base = c.cost;
			if (c.invested < base) {
				float take = Math.min(base / c.form, base - c.invested);
				if (!free) take = Math.min(take, mana);
				if (take <= 0.0001F) {
					starve(player, c);
				} else {
					spend(player, take, free);
					c.invested += take;
					if (c.invested >= base - 0.001F) {
						c.invested = base;
						c.ready = true;
						fx(player, c.spell, CastFx.READY, 1.0F);
					}
				}
			} else if (c.invested < base * c.cap) {
				float take = Math.min(base * (c.cap - 1.0F) / c.over, base * c.cap - c.invested);
				if (!free) take = Math.min(take, mana);
				if (take <= 0.0001F) {
					starve(player, c);
					c.peakTicks++;
				} else {
					spend(player, take, free);
					c.invested += take;
					int stage = (int) Math.floor((c.power() - 1.0F) / (c.cap - 1.0F) * 3.0F + 0.0001F);
					if (c.invested >= base * c.cap - 0.001F) {
						c.invested = base * c.cap;
						c.stage = 3;
						fx(player, c.spell, CastFx.PEAK, c.cap);
					} else if (stage > c.stage) {
						c.stage = stage;
						fx(player, c.spell, CastFx.STAGE, c.power());
					}
				}
			} else {
				c.peakTicks++;
			}
			if (c.peakTicks >= HOLD_TICKS) player.releaseUsingItem();
		} else {
			float take = c.cost / 20.0F;
			if (!free && mana < take) {
				fx(player, c.spell, CastFx.FIZZLE, 0.0F);
				player.displayClientMessage(Component.translatable("suchki.magic.no_mana").withStyle(ChatFormatting.RED), true);
				player.releaseUsingItem();
				return;
			}
			spend(player, take, free);
			c.invested += take;
			c.channelTicks++;
			float power = 0.6F + (c.focus.channelCap - 0.6F) * Math.min(1.0F, c.channelTicks / (float) CHANNEL_RAMP);
			c.spell.channel.tick(new Cast(level, player, c.spell, power, spellBonus(player)), c.channelTicks);
			if (c.channelTicks % 20 == 0) giveXp(player, c.invested);
		}
	}

	private static void starve(ServerPlayer player, Charge c) {
		if (!c.starved) {
			c.starved = true;
			player.displayClientMessage(Component.translatable("suchki.magic.no_mana").withStyle(ChatFormatting.RED), true);
		}
	}

	private static void spend(ServerPlayer player, float amount, boolean free) {
		LAST_SPEND.put(player.getUUID(), player.level().getGameTime());
		if (!free) setMana(player, mana(player) - amount);
	}

	/** Отпустил ПКМ (или заклинание сорвалось само на пике). */
	static void release(ServerPlayer player) {
		Charge c = CHARGES.remove(player.getUUID());
		player.removeAttached(CASTING);
		if (c == null) return;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!c.spell.charged()) {
			giveXp(player, c.invested);
			setData(player, data(player).cooldown(c.spell.id, now + c.spell.cooldownTicks, now));
			fx(player, c.spell, CastFx.RELEASE, 0.0F);
			return;
		}
		if (!c.ready) {
			refund(player, c.invested * 0.5F);
			fx(player, c.spell, CastFx.FIZZLE, 0.0F);
			MagicFx.fizzle(level, player);
			return;
		}
		float power = c.power();
		Cast cast = new Cast(level, player, c.spell, power, spellBonus(player));
		if (!c.spell.action.cast(cast)) {
			refund(player, c.invested * 0.75F);
			fx(player, c.spell, CastFx.FIZZLE, 0.0F);
			player.displayClientMessage(Component.translatable("suchki.magic.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		setData(player, data(player).cooldown(c.spell.id, now + c.spell.cooldownTicks, now));
		fx(player, c.spell, CastFx.RELEASE, power);
		giveXp(player, c.invested);
	}

	/** Каст прерван (сменил предмет, умер, вышел): половина маны сгорает. */
	private static void abort(ServerPlayer player, Charge c) {
		player.removeAttached(CASTING);
		if (c.spell.charged()) refund(player, c.invested * 0.5F);
		fx(player, c.spell, CastFx.FIZZLE, 0.0F);
	}

	private static void refund(ServerPlayer player, float amount) {
		if (!free(player) && amount > 0) setMana(player, mana(player) + amount);
	}

	private static void giveXp(ServerPlayer player, float manaSpent) {
		float total = XP_LEFT.getOrDefault(player.getUUID(), 0.0F) + manaSpent / 4.0F;
		int xp = (int) total;
		XP_LEFT.put(player.getUUID(), total - xp);
		if (xp > 0) Skills.addXp(player, ROLE, xp);
	}

	/** Сколько маны осталось вложить, чтобы заклинание было готово (для самопроверки). */
	static Charge charge(Player player) {
		return CHARGES.get(player.getUUID());
	}

	public static float chargePower(Player player) {
		Charge c = CHARGES.get(player.getUUID());
		return c == null ? 0.0F : c.spell.charged() ? c.power() : 0.0F;
	}

	/** Доля урона, которую гасит Оберег спереди. */
	public static final float WARD_BLOCK = 0.7F;

	/**
	 * Оберег: пока игрок держит заклинание «ward», урон, пришедший спереди (в пределах 60° от взгляда),
	 * снижается на 70%; каждая погашенная единица урона стоит 2 маны. Не хватает маны — гасит сколько может.
	 */
	public static float ward(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damage) {
		if (damage <= 0 || !"ward".equals(player.getAttached(CASTING))) return damage;
		Vec3 from = source.getSourcePosition();
		if (from == null) return damage;
		Vec3 to = from.subtract(player.getEyePosition());
		if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(player.getViewVector(1.0F)) < Math.cos(Math.toRadians(60))) return damage;
		float blocked = damage * WARD_BLOCK;
		if (!free(player)) {
			blocked = Math.min(blocked, mana(player) / 2.0F);
			setMana(player, mana(player) - blocked * 2.0F);
			LAST_SPEND.put(player.getUUID(), player.level().getGameTime());
		}
		if (blocked > 0) {
			MagicFx.burst(player.level(), player.getEyePosition().add(player.getViewVector(1.0F).scale(0.8)),
					MagicParticles.spark(School.RESTORATION.light, 0.35F, 8), 6, 0.25, 0.05);
			MagicFx.sound(player.level(), player.position(), MagicSounds.WARD_HIT, 0.8F, 1.1F + player.getRandom().nextFloat() * 0.2F);
		}
		return damage - blocked;
	}

	// --- Тик сервера: восстановление маны и уборка брошенных кастов ---

	private static void serverTick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Charge> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			if (!p.isAlive() || !p.isUsingItem() || !(p.getUseItem().getItem() instanceof FocusItem)) {
				it.remove();
				abort(p, e.getValue());
			}
		}
		if (server.getTickCount() % 5 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!isMage(p) || CHARGES.containsKey(p.getUUID())) continue;
			long last = LAST_SPEND.getOrDefault(p.getUUID(), 0L);
			if (p.level().getGameTime() - last < REGEN_DELAY) continue;
			float max = maxMana(p);
			Float cur = p.getAttached(MANA);
			if (cur == null) {
				p.setAttached(MANA, max);
			} else if (cur < max) {
				setMana(p, cur + regenPerSecond(p) / 4.0F);
			} else if (cur > max) {
				setMana(p, max);
			}
		}
	}

	// --- Отправка событий клиентам ---

	static void fx(ServerPlayer player, Spell spell, byte kind, float power) {
		CastFx payload = new CastFx(player.getId(), spell.id, kind, power);
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) ServerPlayNetworking.send(other, payload);
		}
	}

	/** Тряска камеры у игроков в радиусе {@code radius} от точки (сила слабеет с расстоянием). */
	public static void shake(ServerLevel level, Vec3 at, float strength, int duration, double radius) {
		Shake payload = new Shake(at, strength, duration);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) <= radius * radius) ServerPlayNetworking.send(p, payload);
		}
	}
}
