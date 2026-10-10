package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.SUMMON;
import static com.suchki.magic.spells.SpellKit.DOWN;
import static com.suchki.magic.spells.SpellKit.UP;

import com.suchki.magic.Anchor;
import com.suchki.magic.Cast;
import com.suchki.magic.CastStyle;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicItems;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Minions;
import com.suchki.magic.School;
import com.suchki.magic.SpectralItem;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Targeting;
import com.suchki.magic.Terrain;
import com.suchki.magic.Trail;
import com.suchki.magic.Zones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Стихия призыва: призрачное оружие и инструменты, фамильяр и скакун, проецируемые клинки за спиной, теневые солдаты,
 * Рыцарь Могильного Покоя, Врата Тысячи Сокровищ, Бесконечное Кладбище Клинков и Тысячерукий Бодхисаттва.
 * Призванные — союзники мага ({@link Minions}): исчезают по времени, бьют его врагов. Призрачное оружие сначала
 * проступает контуром из искр и только потом «твердеет»; большие призывы собираются на глазах — врата открываются
 * парами, мечи встают из земли, ладони божества вспыхивают кругами одна за другой.
 */
public final class SummonSpells {
	/** Бирюза призыва: круги, искры, туман. */
	static final int TEAL = 0x3FE0D0;
	static final int MIST = 0xD8FFFA;
	/** Сине-белые проецируемые клинки. */
	static final int BLADE = 0x9FD8FF;
	static final int BLADE_LIGHT = 0xEAF6FF;
	/** Фиолетовая тень солдат. */
	static final int SHADOW = 0x7A2FD0;
	static final int SHADOW_LIGHT = 0xD8B8FF;
	/** Могильный зелёный огонь рыцаря и чёрный круг под ним. */
	static final int GRAVE = 0x4CFF88;
	static final int GRAVE_LIGHT = 0xC8FFD8;
	static final int GRAVE_BLACK = 0x140A1A;
	/** Золото сокровищницы и бодхисаттвы. */
	static final int GOLD = 0xFFD04A;
	static final int GOLD_LIGHT = 0xFFF4C8;
	/** Закатный пепел кладбища клинков. */
	static final int EMBER = 0xFF8A3A;
	static final int DUSK = 0xFFC890;

	// Призрачный клинок и инструмент: время, кому из союзников достаётся копия мультикаста.
	private static final int BLADE_TICKS = 2400;
	private static final int TOOL_TICKS = 3600;
	private static final double GIFT_R = 6.0;
	private static final double TOOL_REACH = 6.0;
	// Фамильяр: время, сколько волков у мага, их сила.
	private static final int FAMILIAR_TICKS = 1800;
	private static final int MAX_FAMILIARS = 3;
	private static final double FAMILIAR_HP = 30.0;
	private static final double FAMILIAR_ATTACK = 6.0;
	// Скакун: время, скорость, прыжок, здоровье, сколько тиков держится на воде.
	private static final int STEED_TICKS = 6000;
	private static final double STEED_SPEED = 0.32;
	private static final double STEED_JUMP = 1.0;
	private static final double STEED_HP = 30.0;
	private static final int STEED_WATER = 40;
	// Парящие клинки: время, сколько мечей, перезапуск, дальность, удары.
	private static final int BLADES_TICKS = 300;
	private static final int BLADES_BASE = 5;
	private static final int BLADES_PER_COPY = 3;
	private static final int BLADES_MAX = 14;
	private static final int BLADES_EVERY = 20;
	private static final int BLADES_REFORM = 60;
	private static final double BLADES_RANGE = 20.0;
	private static final double BLADES_GUARD = 8.0;
	private static final float BLADES_SPEED = 2.0F;
	private static final float BLADES_DMG = 7.0F;
	private static final float BLADES_MELEE = 5.0F;
	// Теневые солдаты: время, предел на мага, прибавка здоровья.
	private static final int SHADOW_TICKS = 1200;
	private static final int MAX_SHADOWS = 6;
	// Рыцарь Могильного Покоя: подъём, время, вызов на себя.
	private static final int KNIGHT_RISE = 20;
	private static final int KNIGHT_TICKS = 1200;
	private static final int KNIGHT_TAUNT_EVERY = 40;
	private static final double KNIGHT_TAUNT_R = 10.0;
	private static final int MAX_LESSER_KNIGHTS = 3;
	// Врата Тысячи Сокровищ: разовая плата, предел потока, врата, выстрелы, урон и его потолок в секунду.
	private static final int GATE_UPFRONT = 60;
	private static final int GATE_MAX = 100;
	private static final int GATE_PORTALS = 12;
	private static final int GATE_COPY_PORTALS = 6;
	private static final int GATE_FIRE_EVERY = 20;
	private static final float GATE_SPEED = 2.8F;
	private static final float GATE_DMG = 4.0F;
	private static final float GATE_HEAVY_DMG = 6.0F;
	private static final float GATE_CAP = 8.0F;
	private static final double GATE_SEEK = 6.0;
	private static final double GATE_BURST = 1.5;
	private static final int GATE_STUCK = 40;
	// Бесконечное Кладбище Клинков: радиус, время, мечи, залп, павшие.
	private static final double GRAVE_R = 20.0;
	private static final int GRAVE_TICKS = 300;
	private static final int GRAVE_COPY_TICKS = 60;
	private static final int GRAVE_SWORDS = 40;
	private static final int GRAVE_PER_SECOND = 4;
	private static final int GRAVE_VOLLEY = 20;
	private static final int GRAVE_VOLLEY_CD = 60;
	private static final double GRAVE_VOLLEY_R = 3.0;
	private static final int GRAVE_RISEN_TICKS = 1200;
	private static final int GRAVE_MAX_RISEN = 6;
	private static final float GRAVE_DMG = 7.0F;
	private static final float GRAVE_VOLLEY_DMG = 5.0F;
	private static final float GRAVE_CAP = 14.0F;
	private static final double GRAVE_PUSH = 0.8;
	// Тысячерукий Бодхисаттва: ладони, ритм, разброс, радиусы ударов, «Нулевая длань», конец.
	private static final int PALMS = 24;
	private static final int PALM_EVERY = 3;
	private static final int PALM_START = 2;
	private static final double PALM_SPREAD = 12.0;
	private static final double PALM_R = 3.0;
	private static final float PALM_DMG = 14.0F;
	private static final int ZERO_AT = 80;
	private static final double ZERO_R = 5.0;
	private static final float ZERO_DMG = 60.0F;
	private static final int BODHI_END = 100;

	/** Метки призванных (по ним считаем предел «не больше N» и узнаём своих). */
	public static final String FAMILIAR = "suchki_familiar";
	public static final String SHADE = "suchki_shade";
	public static final String KNIGHT = "suchki_knight";
	public static final String LESSER_KNIGHT = "suchki_knight_lesser";
	public static final String RISEN = "suchki_risen";
	public static final String STATUE = "suchki_bodhisattva";
	/** Рыцарь ещё не пережил смертельный удар. */
	private static final String UNDYING_ONCE = "suchki_undying_once";

	/** Парящие клинки каждого мага. */
	private static final Map<UUID, Blades> BLADES = new HashMap<>();
	/** Врата каждой копии потока (маг#копия). */
	private static final Map<String, Gate> GATES = new HashMap<>();
	/** Кладбище клинков каждого мага. */
	private static final Map<UUID, Grave> GRAVES = new HashMap<>();
	/** Сколько урона уже получила цель в эту секунду (потолки Врат и Кладбища). */
	private static final Map<String, Dealt> DEALT = new HashMap<>();
	private static boolean hooked;

	private SummonSpells() {}

	public static void register() {
		hook();

		// --- Ранг 1: Начальный ---

		// Призрачный Клинок: меч в свободную руку на 2 минуты; копия мультикаста — союзнику рядом.
		Spell.of("bound_blade", SUMMON, 1, SELF).anchor(Anchor.HAND).mana(12).charge(10).cooldown(40).range(GIFT_R).multicast(4)
				.icon("diamond_sword")
				.action(SummonSpells::boundBlade)
				.register();

		// Фамильяр: волк выпрыгивает из круга у ног (два при силе ×1,2), не больше трёх на мага.
		Spell.of("familiar", SUMMON, 1, CastStyle.SUMMON).anchor(Anchor.FEET).mana(14).charge(14).cooldown(200).range(4)
				.icon("bone")
				.action(SummonSpells::familiar)
				.register();

		// --- Ранг 2: Средний ---

		// Призрачный Инструмент: кирка, топор или лопата под блок в прицеле; мультикаст — ещё и остальные.
		Spell.of("bound_tool", SUMMON, 2, SELF).anchor(Anchor.HAND).mana(16).charge(12).cooldown(40).range(TOOL_REACH).multicast(3)
				.icon("diamond_pickaxe")
				.action(SummonSpells::boundTool)
				.register();

		// Призрачный Скакун: конь из тумана, маг сразу в седле; не боится высоты, пару секунд бежит по воде.
		Spell.of("spectral_steed", SUMMON, 2, CastStyle.SUMMON).anchor(Anchor.FEET).mana(22).charge(16).cooldown(100).range(GIFT_R)
				.icon("saddle")
				.action(SummonSpells::steed)
				.register();

		// --- Ранг 3: Продвинутый ---

		// Проекция: Парящие Клинки — веер мечей за спиной сам летит во врагов и возвращается.
		Spell.of("spirit_blades", SUMMON, 3, CastStyle.SUMMON).anchor(Anchor.BACK).mana(32).charge(18).cooldown(240).range(BLADES_RANGE)
				.icon("iron_sword").model("iron_sword").size(0.28F).trail(Trail.HOLY)
				.action(SummonSpells::spiritBlades)
				.hit(SummonSpells::bladeHit)
				.register();

		// Теневые Солдаты: из тени у прицела встают солдаты с мечами.
		Spell.of("raise_dead", SUMMON, 3, CastStyle.SUMMON).anchor(Anchor.TARGET).mana(40).charge(22).cooldown(600).range(24)
				.icon("rotten_flesh")
				.action(SummonSpells::raiseDead)
				.register();

		// --- Ранг 4: Святой ---

		// Рыцарь Могильного Покоя: нежить-страж, вызывает врагов на себя и раз переживает смерть.
		Spell.of("death_knight", SUMMON, 4, CastStyle.SUMMON).anchor(Anchor.TARGET).layers(2).mana(62).charge(36).cooldown(600)
				.range(32).icon("wither_skeleton_skull")
				.action(SummonSpells::deathKnight)
				.register();

		// --- Ранг 5: Королевский ---

		// Врата Тысячи Сокровищ: пока держишь, золотые врата за спиной мечут призрачное оружие.
		Spell.of("treasury_gate", SUMMON, 5, CHANNEL).anchor(Anchor.BACK).mana(16).charge(0).cooldown(900).range(96).destroys()
				.loop(MagicSounds.LOOP_CHANT).icon("golden_sword").model("golden_sword").size(0.3F).trail(Trail.HOLY)
				.channel(SummonSpells::gate)
				.hit(SummonSpells::gateHit)
				.register();

		// --- Ранг 6: Императорский ---

		// Бесконечное Кладбище Клинков: мир вокруг мага становится холмом мечей.
		Spell.of("endless_blade_graveyard", SUMMON, 6, RITUAL).anchor(Anchor.FEET).layers(3).mana(180).charge(84).cooldown(3600)
				.range(GRAVE_R).icon("netherite_sword").model("iron_sword").size(0.3F).trail(Trail.FIRE)
				.action(SummonSpells::graveyard)
				.hit(SummonSpells::graveHit)
				.register();

		// --- Ранг 7: Божественный ---

		// Тысячерукий Бодхисаттва: золотое божество за спиной — ливень ладоней и «Нулевая длань».
		Spell.of("thousand_hand_bodhisattva", SUMMON, 7, RITUAL).anchor(Anchor.BACK).layers(3).mana(300).charge(120).cooldown(9000)
				.range(96).destroys().icon("gold_block")
				.action(SummonSpells::bodhisattva)
				.register();
	}

	private static void hook() {
		if (hooked) return;
		hooked = true;
		// Рыцарь переживает первый смертельный удар: остаётся с 1 здоровья во вспышке зелёного пламени.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!entity.getTags().contains(UNDYING_ONCE)) return true;
			entity.removeTag(UNDYING_ONCE);
			entity.setHealth(1.0F);
			if (entity.level() instanceof ServerLevel level) knightSaved(level, entity);
			return false;
		});
		// Павшие на Кладбище Клинков встают тенями мага.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!GRAVES.isEmpty() && entity instanceof Mob mob) graveFallen(mob);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	/** Забыть все клинки, врата и кладбища (остановка сервера и самопроверка). */
	public static void reset() {
		BLADES.values().forEach(b -> b.zone.stop());
		GRAVES.values().forEach(g -> g.zone.stop());
		BLADES.clear();
		GATES.clear();
		GRAVES.clear();
		DEALT.clear();
	}

	// =====================================================================================================
	// Ранг 1
	// =====================================================================================================

	private static boolean boundBlade(Cast c) {
		Player to = recipient(c);
		if (to == null) return c.copy > 0;
		ItemStack blade = spectral(c, MagicItems.SPECTRAL_BLADE, BLADE_TICKS);
		blade.enchant(enchantment(c, Enchantments.SHARPNESS), c.power >= 1.2F ? 5 : 3);
		SpellKit.release(c);
		Vec3 hand = Cast.handOf(to);
		Vec3 dir = flat(to.getViewVector(1.0F)).scale(0.35).add(UP).normalize();
		Vec3 side = right(to);
		// Контур клинка проступает снизу вверх, потом твердеет с бликом.
		for (int i = 0; i < 4; i++) {
			int step = i;
			Zones.later(c, i, () -> {
				double from = step * 0.25, till = (step + 1) * 0.25;
				outline(c.level, hand, dir, side, 1.1, from, till, MagicParticles.spark(TEAL, 0.12F, 12));
			});
		}
		Zones.later(c, 4, () -> {
			MagicFx.burst(c.level, hand.add(dir.scale(0.6)), MagicParticles.spark(MIST, 0.3F, 10), 10, 0.1, 0.08);
			MagicFx.burst(c.level, hand.add(dir.scale(0.6)), ParticleTypes.END_ROD, 6, 0.1, 0.05);
			MagicFx.sound(c.level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
			MagicFx.sound(c.level, hand, SoundEvents.TRIDENT_RETURN, 0.7F, 1.8F);
		});
		return give(c, to, blade, true);
	}

	private static boolean familiar(Cast c) {
		int count = c.copy > 0 ? 1 : c.power >= 1.2F ? 2 : 1;
		SpellKit.release(c);
		Vec3 forward = flat(c.look());
		int made = 0;
		for (int i = 0; i < count; i++) {
			Wolf wolf = EntityType.WOLF.create(c.level, EntitySpawnReason.MOB_SUMMONED);
			if (wolf == null) continue;
			Vec3 want = c.caster.position().add(forward.scale(1.2)).add(c.right().scale((i - (count - 1) * 0.5) * 1.5 + c.copy * 1.2));
			Vec3 spot = spot(c, wolf, want);
			wolf.snapTo(spot.x, spot.y, spot.z, c.caster.getYRot(), 0);
			wolf.setCustomName(c.spell.displayName());
			wolf.addTag(FAMILIAR);
			Minions.summon(c, wolf, Math.round(FAMILIAR_TICKS * c.power));
			// Приручение сбрасывает здоровье волка — силу ставим после призыва.
			setBase(wolf, Attributes.MAX_HEALTH, FAMILIAR_HP);
			wolf.setHealth((float) FAMILIAR_HP);
			setBase(wolf, Attributes.ATTACK_DAMAGE, FAMILIAR_ATTACK);
			setBase(wolf, Attributes.MOVEMENT_SPEED, base(wolf, Attributes.MOVEMENT_SPEED) * 1.2);
			// Прыжок из дымки вперёд.
			wolf.setDeltaMovement(forward.scale(0.45).add(0, 0.35, 0));
			wolf.hurtMarked = true;
			MagicFx.burst(c.level, spot.add(0, 0.5, 0), ParticleTypes.POOF, 14, 0.4, 0.05);
			MagicFx.cloud(c.level, spot.add(0, 0.4, 0), MagicParticles.mote(TEAL, 0.25F, 30), 18, 0.5, 0.3, 0.5, 0.02);
			wolf.playAmbientSound();
			made++;
		}
		MagicFx.sound(c.level, c.caster.position(), SoundEvents.WOLF_SHAKE, 0.8F, 0.9F);
		dismissOld(c, FAMILIAR, MAX_FAMILIARS);
		return made > 0;
	}

	// =====================================================================================================
	// Ранг 2
	// =====================================================================================================

	private static boolean boundTool(Cast c) {
		List<Item> order = toolOrder(c);
		if (c.copy >= order.size()) return true;
		ItemStack stack = spectral(c, order.get(c.copy), TOOL_TICKS);
		stack.enchant(enchantment(c, Enchantments.EFFICIENCY), c.power >= 1.2F ? 5 : 3);
		SpellKit.release(c);
		// Осколки света по спирали стягиваются в руку и складываются в инструмент.
		Vec3 hand = c.caster.getEyePosition().add(c.look().scale(0.8)).add(0, -0.35, 0).add(c.right().scale(0.25 * (c.copy - 1)));
		for (int t = 0; t < 6; t++) {
			int step = t;
			Zones.later(c, t, () -> {
				double r = 1.2 * (1.0 - step / 6.0);
				for (int k = 0; k < 6; k++) {
					double a = k * Math.PI / 3 + step * 0.6;
					Vec3 p = hand.add(c.right().scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
					MagicFx.one(c.level, p, MagicParticles.spark(k % 2 == 0 ? TEAL : MIST, 0.14F, 6), 0, 0, 0);
				}
			});
		}
		Zones.later(c, 6, () -> {
			MagicFx.burst(c.level, hand, MagicParticles.spark(MIST, 0.3F, 10), 12, 0.15, 0.06);
			MagicFx.sound(c.level, hand, SoundEvents.CHAIN_PLACE, 0.9F, 1.6F);
			MagicFx.sound(c.level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
		});
		return give(c, c.caster, stack, false);
	}

	/** Какой инструмент нужен блоку под прицелом — первым, остальные следом (для копий мультикаста). */
	private static List<Item> toolOrder(Cast c) {
		Item first = MagicItems.SPECTRAL_PICKAXE;
		HitResult hit = c.level.clip(new ClipContext(c.eye(), c.eye().add(c.look().scale(TOOL_REACH)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, c.caster));
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = c.level.getBlockState(bh.getBlockPos());
			if (state.is(BlockTags.MINEABLE_WITH_AXE)) first = MagicItems.SPECTRAL_AXE;
			else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) first = MagicItems.SPECTRAL_SHOVEL;
		}
		List<Item> out = new ArrayList<>(List.of(first));
		for (Item i : List.<Item>of(MagicItems.SPECTRAL_PICKAXE, MagicItems.SPECTRAL_AXE, MagicItems.SPECTRAL_SHOVEL)) {
			if (i != first) out.add(i);
		}
		return out;
	}

	private static boolean steed(Cast c) {
		Player rider = recipient(c);
		if (rider == null) return c.copy > 0;
		Horse horse = EntityType.HORSE.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) return false;
		SpellKit.release(c);
		Vec3 spot = spot(c, horse, rider.position().add(flat(rider.getViewVector(1.0F)).scale(1.0)));
		horse.snapTo(spot.x, spot.y, spot.z, rider.getYRot(), 0);
		horse.setTamed(true);
		horse.setOwner(rider);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setCustomName(c.spell.displayName());
		setBase(horse, Attributes.MOVEMENT_SPEED, STEED_SPEED);
		setBase(horse, Attributes.JUMP_STRENGTH, STEED_JUMP);
		setBase(horse, Attributes.MAX_HEALTH, STEED_HP);
		setBase(horse, Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
		horse.setHealth((float) STEED_HP);
		int life = Math.round(STEED_TICKS * c.power);
		Minions.summon(c, horse, life);
		rider.startRiding(horse);
		// Конь собирается из тумана.
		MagicFx.cloud(c.level, spot.add(0, 1.0, 0), MagicParticles.mote(TEAL, 0.35F, 30), 40, 0.8, 0.6, 0.8, 0.02);
		MagicFx.cloud(c.level, spot.add(0, 0.8, 0), ParticleTypes.CLOUD, 20, 0.7, 0.5, 0.7, 0.02);
		MagicFx.sound(c.level, spot, SoundEvents.HORSE_AMBIENT, 1.0F, 0.7F);
		MagicFx.sound(c.level, spot, SoundEvents.HORSE_GALLOP, 0.8F, 0.6F);
		int[] water = {0};
		Zones.add(c, spot, 1.0, life, (z, age) -> steedTick(c, horse, water, z, age));
		return true;
	}

	/** Скакун: светящиеся следы копыт и бег по воде до 2 с подряд (сбрасывается на суше). */
	private static void steedTick(Cast c, Horse horse, int[] water, Zones.Zone z, int age) {
		if (horse.isRemoved() || !horse.isAlive()) {
			z.stop();
			return;
		}
		z.center = horse.position();
		Vec3 v = horse.getDeltaMovement();
		if (horse.isInWater() && horse.isVehicle()) {
			if (water[0]++ < STEED_WATER) {
				horse.setDeltaMovement(v.x * 1.05, Math.max(v.y, 0.14), v.z * 1.05);
				horse.hurtMarked = true;
				if (age % 2 == 0) MagicFx.burst(c.level, horse.position(), ParticleTypes.SPLASH, 6, 0.4, 0.05);
			}
		} else if (horse.onGround()) {
			water[0] = 0;
		}
		if (age % 5 == 0 && horse.onGround() && v.horizontalDistanceSqr() > 0.004) {
			MagicFx.one(c.level, horse.position().add(0, 0.05, 0), MagicParticles.spark(TEAL, 0.3F, 40), 0, 0, 0);
		}
	}

	// =====================================================================================================
	// Ранг 3
	// =====================================================================================================

	/** Парящие клинки мага: сколько мечей, когда каждый снова готов (в тиках зоны), до какого тика держатся. */
	private static final class Blades {
		final ServerPlayer caster;
		Cast cast;
		Zones.Zone zone;
		int count;
		final int[] ready = new int[BLADES_MAX];
		int until;
		LivingEntity lastMelee;
		int lastMeleeStamp;

		Blades(Cast c) {
			this.caster = c.caster;
			this.cast = c;
			this.lastMelee = c.caster.getLastHurtMob();
			this.lastMeleeStamp = c.caster.getLastHurtMobTimestamp();
		}

		int age() {
			return zone == null ? 0 : zone.age();
		}

		int readyIndex() {
			for (int i = 0; i < count; i++) if (ready[i] <= age()) return i;
			return -1;
		}
	}

	private static boolean spiritBlades(Cast c) {
		SpellKit.release(c);
		Blades b = BLADES.get(c.caster.getUUID());
		if (c.copy > 0) {
			// Копия мультикаста: ещё три меча в веер (не больше 14).
			if (b == null) return true;
			int add = Math.min(BLADES_PER_COPY, BLADES_MAX - b.count);
			for (int i = 0; i < add; i++) b.ready[b.count + i] = b.age() + 3 * (i + 1);
			b.count += add;
			return true;
		}
		if (b != null) {
			// Повторное заклинание: веер обновляется — все мечи снова готовы, время заново.
			b.cast = c;
			b.until = b.age() + BLADES_TICKS;
			b.count = Math.max(b.count, BLADES_BASE);
			for (int i = 0; i < b.count; i++) b.ready[i] = Math.min(b.ready[i], b.age() + 2 * i);
			MagicFx.sound(c.level, c.caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F);
			return true;
		}
		Blades nb = new Blades(c);
		nb.count = BLADES_BASE;
		nb.until = BLADES_TICKS;
		// Мечи проступают по одному, каждый следующий — выше звоном.
		for (int i = 0; i < nb.count; i++) nb.ready[i] = 3 * i + 2;
		nb.zone = Zones.add(c, c.caster.position(), BLADES_RANGE, Integer.MAX_VALUE, (z, age) -> bladesTick(nb, z, age));
		BLADES.put(c.caster.getUUID(), nb);
		MagicFx.sound(c.level, c.caster.position(), MagicSounds.SUMMON, 0.8F, 1.3F);
		return true;
	}

	private static void bladesTick(Blades b, Zones.Zone z, int age) {
		ServerPlayer p = b.caster;
		if (age >= b.until || !p.isAlive() || p.isRemoved() || p.level() != b.cast.level) {
			for (int i = 0; i < b.count; i++) MagicFx.burst(b.cast.level, bladePos(b, i), MagicParticles.spark(BLADE, 0.2F, 12), 6, 0.2, 0.03);
			MagicFx.sound(b.cast.level, p.position(), MagicSounds.VANISH, 0.6F, 1.4F);
			z.stop();
			BLADES.remove(p.getUUID(), b);
			return;
		}
		z.center = p.position();
		ServerLevel level = b.cast.level;
		// Мечи веером за спиной: готовые — яркие, восстанавливающиеся — бледный контур; проступил — звон и блик.
		for (int i = 0; i < b.count; i++) {
			if (b.ready[i] == age) {
				Vec3 at = bladePos(b, i);
				MagicFx.burst(level, at, ParticleTypes.END_ROD, 4, 0.1, 0.03);
				MagicFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.2F + 0.08F * i);
			}
		}
		if (age % 3 == 0) {
			for (int i = 0; i < b.count; i++) {
				boolean on = b.ready[i] <= age;
				ParticleOptions part = on ? MagicParticles.spark(BLADE, 0.11F, 5) : MagicParticles.mote(BLADE, 0.06F, 4);
				swordGlyph(level, bladePos(b, i), bladeDir(b, i), bladeSide(b, i), 0.95, part, on ? 0.16 : 0.32);
			}
		}
		// Удар мага в ближнем бою — следом бьёт готовый меч.
		LivingEntity melee = p.getLastHurtMob();
		int stamp = p.getLastHurtMobTimestamp();
		if (melee != null && (melee != b.lastMelee || stamp != b.lastMeleeStamp)) {
			b.lastMelee = melee;
			b.lastMeleeStamp = stamp;
			int i = b.readyIndex();
			if (i >= 0 && b.cast.isFoe(melee)) meleeStrike(b, i, melee);
		}
		// Раз в секунду — меч во врага под прицелом (или в того, кто напал на мага вблизи).
		if (age % BLADES_EVERY == 0) {
			int i = b.readyIndex();
			LivingEntity t = i < 0 ? null : bladeTarget(b);
			if (t != null) launchBlade(b, i, t);
		}
	}

	private static LivingEntity bladeTarget(Blades b) {
		ServerPlayer p = b.caster;
		Targeting.Aim aim = Targeting.aim(p.level(), p, p.getEyePosition(), p.getViewVector(1.0F), BLADES_RANGE, Targeting.living(p));
		if (aim.entity() instanceof LivingEntity l && b.cast.isFoe(l)) return l;
		LivingEntity attacker = p.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && b.cast.isFoe(attacker) && attacker.distanceTo(p) <= BLADES_GUARD
				&& p.tickCount - p.getLastHurtByMobTimestamp() < 100) {
			return attacker;
		}
		return null;
	}

	private static void launchBlade(Blades b, int i, LivingEntity target) {
		Vec3 from = bladePos(b, i);
		Vec3 dir = Cast.center(target).subtract(from).normalize();
		SpellProjectile.spawn(aimed(b.cast, target), from, dir.scale(BLADES_SPEED)).homing(0.12).pierce(1).life(40);
		b.ready[i] = b.age() + BLADES_REFORM;
		MagicFx.line(b.cast.level, from, from.add(dir.scale(1.5)), MagicParticles.spark(BLADE_LIGHT, 0.15F, 6), 0.25);
		MagicFx.sound(b.cast.level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.5F);
	}

	private static void meleeStrike(Blades b, int i, LivingEntity target) {
		Vec3 from = bladePos(b, i);
		Vec3 to = Cast.center(target);
		b.cast.hurt(target, b.cast.amount(BLADES_MELEE), MagicDamage.ARCANE);
		b.ready[i] = b.age() + BLADES_REFORM;
		MagicFx.line(b.cast.level, from, to, MagicParticles.spark(BLADE_LIGHT, 0.14F, 6), 0.3);
		MagicFx.slash(b.cast.level, to.subtract(to.subtract(from).normalize().scale(0.8)), to.subtract(from).normalize(), 1.4F, 1.6F,
				BLADE, BLADE_LIGHT, 6);
		MagicFx.sound(b.cast.level, to, SoundEvents.TRIDENT_HIT, 0.8F, 1.3F);
	}

	private static void bladeHit(Cast c, SpellProjectile proj, HitResult result) {
		Vec3 at = result.getLocation();
		if (result instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity e && c.isFoe(e)) {
			c.hurt(e, c.amount(BLADES_DMG), MagicDamage.ARCANE, proj);
			Cast.knock(e, at.subtract(proj.getDeltaMovement()), 0.3, 0.1);
		}
		MagicFx.burst(c.level, at, MagicParticles.spark(BLADE, 0.25F, 12), 10, 0.15, 0.1);
		MagicFx.burst(c.level, at, ParticleTypes.CRIT, 6, 0.1, 0.2);
		MagicFx.sound(c.level, at, SoundEvents.ANVIL_LAND, 0.25F, 1.9F);
	}

	/** Точка {@code i}-го меча: веер за плечами (до 7 в ряд, дальше — второй, шире). */
	private static Vec3 bladePos(Blades b, int i) {
		double[] fan = fan(b, i);
		Vec3 back = flat(b.caster.getViewVector(1.0F)).scale(-0.7);
		Vec3 r = right(b.caster);
		return b.caster.position().add(0, 1.3, 0).add(back).add(r.scale(Math.sin(fan[0]) * fan[1])).add(0, Math.cos(fan[0]) * fan[1] * 0.8, 0);
	}

	private static Vec3 bladeDir(Blades b, int i) {
		double[] fan = fan(b, i);
		return right(b.caster).scale(Math.sin(fan[0])).add(0, Math.cos(fan[0]), 0).normalize();
	}

	private static Vec3 bladeSide(Blades b, int i) {
		double[] fan = fan(b, i);
		return right(b.caster).scale(Math.cos(fan[0])).add(0, -Math.sin(fan[0]), 0).normalize();
	}

	/** Угол (от вертикали) и радиус меча в веере. */
	private static double[] fan(Blades b, int i) {
		int row = i < 7 ? 0 : 1;
		int inRow = row == 0 ? Math.min(7, b.count) : b.count - 7;
		int k = row == 0 ? i : i - 7;
		double t = inRow <= 1 ? 0 : k / (double) (inRow - 1) * 2 - 1;
		double spread = Math.toRadians(row == 0 ? 25 + 9 * Math.min(6, inRow - 1) : 30 + 8 * Math.min(6, inRow - 1));
		return new double[] {t * spread, row == 0 ? 1.1 : 1.9};
	}

	private static boolean raiseDead(Cast c) {
		Vec3 at = c.aimGround();
		int count = c.copy > 0 ? 1 : c.power >= 1.2F ? 3 : 2;
		SpellKit.release(c);
		// Тень растекается лужей, над ней — клубы чёрного дыма.
		MagicFx.circle(c.level, School.DARK, 3, SHADOW, SHADOW_LIGHT, at.add(0, 0.06, 0), UP, 2.6F, 1, 0, 10, 24, 14, 0.2F);
		MagicFx.cloud(c.level, at.add(0, 0.1, 0), ParticleTypes.SQUID_INK, 40, 1.4, 0.05, 1.4, 0.02);
		MagicFx.soundFar(c.level, at, MagicSounds.RUMBLE, 32, 0.7F);
		MagicFx.sound(c.level, at, SoundEvents.EVOKER_PREPARE_SUMMON, 0.8F, 0.6F);
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count + c.copy;
			Vec3 want = at.add(Math.cos(a) * (count > 1 ? 1.4 : 0), 0, Math.sin(a) * (count > 1 ? 1.4 : 0));
			int delay = 8 + 4 * i;
			MagicFx.pillar(c.level, want, 0.5F, 3.0F, 0x1A0A2A, SHADOW, delay + 10);
			Zones.later(c, delay, () -> spawnShade(c, want));
		}
		return true;
	}

	private static void spawnShade(Cast c, Vec3 want) {
		Husk h = EntityType.HUSK.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (h == null) return;
		Vec3 spot = spot(c, h, want);
		h.snapTo(spot.x, spot.y, spot.z, c.caster.getYRot(), 0);
		setBase(h, Attributes.MAX_HEALTH, base(h, Attributes.MAX_HEALTH) * 1.2);
		h.setHealth(h.getMaxHealth());
		equip(h, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ItemStack hood = new ItemStack(Items.LEATHER_HELMET);
		hood.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1C1028));
		equip(h, EquipmentSlot.HEAD, hood);
		h.setCustomName(c.spell.displayName());
		h.addTag(SHADE);
		Minions.summon(c, h, Math.round(SHADOW_TICKS * c.power));
		dismissOld(c, SHADE, MAX_SHADOWS);
		MagicFx.cloud(c.level, spot.add(0, 1.0, 0), ParticleTypes.LARGE_SMOKE, 24, 0.4, 0.8, 0.4, 0.03);
		MagicFx.burst(c.level, spot.add(0, 1.6, 0), MagicParticles.spark(SHADOW, 0.25F, 30), 8, 0.15, 0.01);
		MagicFx.sound(c.level, spot, SoundEvents.HUSK_AMBIENT, 0.9F, 0.6F);
		MagicFx.sound(c.level, spot, SoundEvents.CHAIN_PLACE, 0.8F, 0.7F);
	}

	// =====================================================================================================
	// Ранг 4
	// =====================================================================================================

	private static boolean deathKnight(Cast c) {
		Vec3 at = c.aimGround();
		boolean lesser = c.copy > 0;
		SpellKit.release(c);
		float r = lesser ? 1.8F : 2.5F;
		// Чёрный круг и зелёный внутри; земля трескается и плюётся могильным огнём.
		MagicFx.circle(c.level, School.DARK, 4, GRAVE_BLACK, 0x5A3F7A, at.add(0, 0.06, 0), UP, r, 1, 0, 10, KNIGHT_RISE + 6, 14, 0.15F);
		MagicFx.circle(c.level, School.SUMMON, 4, GRAVE, GRAVE_LIGHT, at.add(0, 0.1, 0), UP, r * 0.72F, 1, 0, 14, KNIGHT_RISE, 14, -0.35F);
		BlockState ground = c.level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? net.minecraft.world.level.block.Blocks.DIRT
				.defaultBlockState() : ground);
		SpellKit.repeat(c, 5, 4, i -> {
			Vec3 p = SpellKit.inDisc(c.level.random, at, r * 0.8);
			MagicFx.burst(c.level, p.add(0, 0.2, 0), dirt, 10, 0.3, 0.1);
			MagicFx.cloud(c.level, p.add(0, 0.3, 0), ParticleTypes.SOUL_FIRE_FLAME, 8, 0.15, 0.3, 0.15, 0.05);
			MagicFx.sound(c.level, p, SoundEvents.GRAVEL_BREAK, 0.8F, 0.6F);
		});
		MagicFx.soundFar(c.level, at, MagicSounds.RUMBLE, 48, 0.6F);
		Zones.later(c, KNIGHT_RISE, () -> riseKnight(c, at, lesser));
		return true;
	}

	private static void riseKnight(Cast c, Vec3 at, boolean lesser) {
		WitherSkeleton k = EntityType.WITHER_SKELETON.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (k == null) return;
		setBase(k, Attributes.SCALE, lesser ? 1.2 : 1.6);
		setBase(k, Attributes.MAX_HEALTH, lesser ? 40 : 80);
		setBase(k, Attributes.ARMOR, lesser ? 6 : 12);
		setBase(k, Attributes.ATTACK_DAMAGE, lesser ? 7 : 10);
		setBase(k, Attributes.KNOCKBACK_RESISTANCE, 0.6);
		k.setHealth(k.getMaxHealth());
		equip(k, EquipmentSlot.MAINHAND, new ItemStack(lesser ? Items.IRON_SWORD : Items.NETHERITE_SWORD));
		if (!lesser) equip(k, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		k.setCustomName(c.spell.displayName());
		k.addTag(lesser ? LESSER_KNIGHT : KNIGHT);
		k.addTag(UNDYING_ONCE);
		Vec3 spot = spot(c, k, at);
		k.snapTo(spot.x, spot.y, spot.z, c.caster.getYRot(), 0);
		k.setYHeadRot(c.caster.getYRot());
		if (lesser) dismissOld(c, LESSER_KNIGHT, MAX_LESSER_KNIGHTS - 1);
		else dismissOld(c, KNIGHT, maxKnights(c) - 1);
		Minions.summon(c, k, KNIGHT_TICKS);
		// Из земли — рука, потом весь рыцарь сквозь туман, глаза горят.
		float s = lesser ? 1.2F : 1.6F;
		MagicFx.pillar(c.level, spot, 0.9F * s, 4.0F * s, GRAVE, GRAVE_LIGHT, 18);
		MagicFx.shockwave(c.level, spot.add(0, 0.15, 0), UP, 0.4F, 4.5F * s, GRAVE, 14);
		MagicFx.cloud(c.level, spot.add(0, 1.0, 0), ParticleTypes.SOUL, 20, 0.6, 1.0, 0.6, 0.04);
		MagicFx.cloud(c.level, spot.add(0, 0.8, 0), ParticleTypes.LARGE_SMOKE, 20, 0.8, 0.6, 0.8, 0.02);
		MagicFx.burst(c.level, spot.add(0, 2.2 * s, 0), MagicParticles.spark(GRAVE, 0.3F, 30), 10, 0.2, 0.01);
		MagicFx.soundFar(c.level, spot, SoundEvents.BELL_BLOCK, 48, 0.5F);
		MagicFx.sound(c.level, spot, SoundEvents.WITHER_SKELETON_AMBIENT, 1.4F, 0.5F);
		MagicFx.sound(c.level, spot, SoundEvents.SKELETON_AMBIENT, 1.0F, 0.6F);
		Magic.shake(c.level, spot, lesser ? 0.3F : 0.6F, 12, 24);
		Zones.add(c, spot, KNIGHT_TAUNT_R, KNIGHT_TICKS, (z, age) -> knightTick(c, k, z, age));
	}

	/** Рыцарь вызывает врагов на себя раз в 2 с, тлеет зелёным, а в конце рассыпается душами. */
	private static void knightTick(Cast c, WitherSkeleton k, Zones.Zone z, int age) {
		if (k.isRemoved() || !k.isAlive()) {
			z.stop();
			return;
		}
		z.center = k.position();
		if (age % KNIGHT_TAUNT_EVERY == 1) taunt(c, k);
		if (age % 8 == 0) MagicFx.one(c.level, k.getEyePosition(), MagicParticles.spark(GRAVE, 0.18F, 10), 0, 0.01, 0);
		if (age == KNIGHT_TICKS - 1) {
			MagicFx.cloud(c.level, Cast.center(k), ParticleTypes.SOUL, 30, 0.5, 1.0, 0.5, 0.05);
			MagicFx.sound(c.level, k.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.7F);
		}
	}

	/** Враги в 10 блоках бросают свои цели и идут на рыцаря. */
	public static int taunt(Cast c, LivingEntity knight) {
		int n = 0;
		for (LivingEntity e : c.around(knight.position(), KNIGHT_TAUNT_R, x -> x instanceof Mob && c.isEnemy(x) && !boss(x))) {
			Mob m = (Mob) e;
			if (m.getTarget() == knight) continue;
			m.setTarget(knight);
			MagicFx.line(c.level, knight.getEyePosition(), m.getEyePosition(), MagicParticles.spark(GRAVE, 0.12F, 8), 0.6);
			n++;
		}
		if (n > 0) MagicFx.sound(c.level, knight.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 1.0F, 0.7F);
		return n;
	}

	private static void knightSaved(ServerLevel level, LivingEntity e) {
		Vec3 c = Cast.center(e);
		MagicFx.cloud(level, c, ParticleTypes.SOUL_FIRE_FLAME, 40, 0.5, 0.9, 0.5, 0.08);
		MagicFx.burst(level, c, MagicParticles.spark(GRAVE, 0.4F, 18), 20, 0.5, 0.1);
		MagicFx.shockwave(level, e.position().add(0, 0.1, 0), UP, 0.3F, 4.0F, GRAVE, 14);
		MagicFx.sound(level, c, SoundEvents.TOTEM_USE, 0.8F, 0.6F);
		MagicFx.sound(level, c, SoundEvents.WITHER_SKELETON_HURT, 1.0F, 0.5F);
	}

	private static int maxKnights(Cast c) {
		return Magic.mageLevel(c.caster) >= 40 ? 2 : 1;
	}

	// =====================================================================================================
	// Ранг 5: Врата Тысячи Сокровищ
	// =====================================================================================================

	/** Одни врата: где висят, куда смотрят, когда стреляют. */
	private record Portal(Vec3 pos, Vec3 normal, int[] next) {}

	/** Врата одной копии потока. */
	private static final class Gate {
		final List<Portal> portals = new ArrayList<>();
		int last;
		int shots;
	}

	private static void gate(Cast c, int ticks) {
		if (ticks > GATE_MAX) {
			// Сокровищница не держится дольше 5 с.
			if (c.copy == 0 && ticks == GATE_MAX + 1) c.caster.releaseUsingItem();
			return;
		}
		String key = c.caster.getUUID() + "#" + c.copy;
		Gate g = GATES.get(key);
		if (ticks == 1 || g == null || ticks <= g.last) {
			g = new Gate();
			GATES.put(key, g);
			if (c.copy == 0) {
				// Первые врата открываются разовым всплеском маны.
				if (!c.caster.hasInfiniteMaterials()) c.caster.setAttached(Magic.MANA, Math.max(0.0F, Magic.mana(c.caster) - GATE_UPFRONT));
				SpellKit.release(c);
				MagicFx.soundFar(c.level, c.caster.position(), MagicSounds.RISER_SHORT, 48, 1.3F);
				MagicFx.cloud(c.level, c.caster.position().add(0, 1.0, 0), MagicParticles.mote(GOLD, 0.2F, 30), 30, 1.2, 1.0, 1.2, 0.03);
			}
		}
		g.last = ticks;
		int total = c.copy == 0 ? GATE_PORTALS : GATE_COPY_PORTALS;
		// По двое врат каждые 4 тика.
		while (g.portals.size() < total && ticks - 1 >= g.portals.size() / 2 * 4) openPortal(c, g, g.portals.size(), ticks);
		for (Portal p : g.portals) {
			if (ticks == p.next()[0]) {
				firePortal(c, g, p);
				p.next()[0] += GATE_FIRE_EVERY;
			}
		}
	}

	private static void openPortal(Cast c, Gate g, int k, int ticks) {
		Vec3 pos;
		Vec3 normal;
		if (c.copy == 0) {
			// Дугой за спиной: пары врат от середины к краям, на разной высоте.
			int pair = k / 2;
			double side = k % 2 == 0 ? -1 : 1;
			double a = Math.toRadians(side * (12 + pair * 13));
			Vec3 fwd = flat(c.look());
			pos = c.caster.position().add(c.right().scale(Math.sin(a) * 4.0)).add(fwd.scale(-(1.0 + 1.6 * (1 - Math.cos(a)))))
					.add(0, 1.6 + ((pair * 37 + k * 11) % 7) / 7.0 * 3.4, 0);
			normal = c.look();
		} else {
			// Копия: дуга врат сбоку от цели, бьют внутрь.
			Vec3 aim = c.aimPoint();
			double a = Math.atan2(c.right().z, c.right().x) + Math.toRadians((k - 2.5) * 22 + (c.copy % 2 == 0 ? 180 : 0));
			pos = aim.add(Math.cos(a) * 7.0, 3.0 + (k % 3) * 1.3, Math.sin(a) * 7.0);
			normal = aim.subtract(pos).normalize();
		}
		float r = 0.6F + 0.4F * ((k * 5) % 4) / 3.0F;
		MagicFx.circle(c.level, School.SUMMON, 5, GOLD, GOLD_LIGHT, pos, normal, r, 1, 0, 6, Math.max(10, GATE_MAX - ticks + 6), 10, 0.6F);
		MagicFx.ring(c.level, pos, r, MagicParticles.spark(GOLD, 0.15F, 14), 10, 0.02);
		MagicFx.sound(c.level, pos, MagicSounds.CIRCLE_DRAW, 0.5F, 1.4F);
		MagicFx.sound(c.level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F + 0.06F * k);
		g.portals.add(new Portal(pos, normal, new int[] {ticks + 6 + (k * 7) % 14}));
	}

	private static void firePortal(Cast c, Gate g, Portal p) {
		Vec3 aim = c.aimPoint();
		LivingEntity foe = nearestFoe(c, aim, GATE_SEEK);
		Vec3 to = foe != null ? Cast.center(foe) : aim;
		Vec3 dir = to.subtract(p.pos());
		if (dir.lengthSqr() < 1.0E-4) dir = c.look();
		dir = dir.normalize();
		boolean heavy = g.shots++ % 6 == 5;
		SpellProjectile proj = SpellProjectile.spawn(foe != null ? aimed(c, foe) : c, p.pos().add(dir.scale(0.3)), dir.scale(GATE_SPEED))
				.homing(foe != null ? 0.1 : 0).life(60);
		proj.scale(heavy ? 1.6F : 1.0F);
		MagicFx.burst(c.level, p.pos(), MagicParticles.spark(GOLD, 0.22F, 10), 6, 0.2, 0.05);
		MagicFx.sound(c.level, p.pos(), SoundEvents.TRIDENT_RETURN, 0.6F, 1.6F + c.level.random.nextFloat() * 0.3F);
		MagicFx.sound(c.level, p.pos(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.7F);
	}

	private static void gateHit(Cast c, SpellProjectile proj, HitResult result) {
		LivingEntity e = result instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l ? l : null;
		gateStrike(c, result.getLocation(), proj.getDeltaMovement(), e, proj.scale() > 1.3F, proj);
	}

	/**
	 * Попадание оружия из Врат: 4 урона (тяжёлое — 6), не больше 8 в секунду по одной цели; оружие торчит 2 с и
	 * рассыпается золотом, тяжёлое ещё и взрывается (воронка 1,5, если разрешено). Открыто для самопроверки.
	 */
	public static float gateStrike(Cast c, Vec3 at, Vec3 velocity, LivingEntity e, boolean heavy, Entity direct) {
		float dealt = 0;
		if (e != null && c.isFoe(e)) {
			dealt = capped(c, e, c.amount(heavy ? GATE_HEAVY_DMG : GATE_DMG), c.amount(GATE_CAP));
			if (dealt > 0) c.hurt(e, dealt, MagicDamage.ARCANE, direct == null ? c.caster : direct);
			Vec3 push = velocity.lengthSqr() < 1.0E-4 ? Vec3.ZERO : velocity.normalize();
			e.push(push.x * 0.4, 0.1, push.z * 0.4);
			e.hurtMarked = true;
		}
		Vec3 dir = velocity.lengthSqr() < 1.0E-4 ? DOWN : velocity.normalize();
		MagicFx.burst(c.level, at, MagicParticles.spark(GOLD, 0.25F, 12), 10, 0.15, 0.12);
		MagicFx.burst(c.level, at, ParticleTypes.CRIT, 6, 0.1, 0.2);
		MagicFx.sound(c.level, at, SoundEvents.TRIDENT_HIT, 0.8F, 1.1F);
		// Оружие торчит в цели, потом рассыпается золотой пылью.
		if (e == null) {
			SpellKit.repeat(c, GATE_STUCK / 10, 10, i -> MagicFx.line(c.level, at.subtract(dir.scale(0.9)), at,
					MagicParticles.spark(GOLD, 0.1F, 12), 0.15));
		}
		Zones.later(c, GATE_STUCK, () -> MagicFx.burst(c.level, at, MagicParticles.mote(GOLD_LIGHT, 0.15F, 20), 8, 0.2, 0.03));
		if (heavy) {
			MagicFx.sphere(c.level, at, 0.3F, (float) GATE_BURST * 1.3F, GOLD, GOLD_LIGHT, 8);
			MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION, 1, 0, 0);
			MagicFx.sound(c.level, at, MagicSounds.EXPLOSION, 0.7F, 1.4F);
			Terrain.crater(c, at, GATE_BURST, 0.8, Terrain.Rim.RUBBLE);
		}
		return dealt;
	}

	// =====================================================================================================
	// Ранг 6: Бесконечное Кладбище Клинков
	// =====================================================================================================

	private static final class Grave {
		final Cast cast;
		final Vec3 center;
		final List<Vec3> swords = new ArrayList<>();
		final List<Mob> risen = new ArrayList<>();
		Zones.Zone zone;
		int until = GRAVE_TICKS;
		double rate = 1.0;
		double pending;
		int volleyCd;
		int rising;
		List<LivingEntity> inside = List.of();

		Grave(Cast c, Vec3 center) {
			this.cast = c;
			this.center = center;
		}
	}

	private static boolean graveyard(Cast c) {
		Grave old = GRAVES.get(c.caster.getUUID());
		if (c.copy > 0) {
			// Копия мультикаста: больше мечей и дольше мир.
			if (old != null) {
				old.until += GRAVE_COPY_TICKS;
				old.rate += 0.25;
			}
			return true;
		}
		if (old != null) {
			old.zone.stop();
			GRAVES.remove(c.caster.getUUID());
		}
		SpellKit.release(c);
		ServerLevel level = c.level;
		Vec3 center = c.caster.position();
		Grave g = new Grave(c, center);
		RandomSource rnd = RandomSource.create(c.caster.getUUID().getLeastSignificantBits() ^ level.getGameTime());
		for (int i = 0; i < GRAVE_SWORDS; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double d = 3.0 + Math.sqrt(rnd.nextDouble()) * (GRAVE_R - 4.0);
			Vec3 p = center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
			g.swords.add(Targeting.ground(level, c.caster, p.add(0, 4, 0)));
		}
		// Огненная черта бежит по земле и замыкается кольцом.
		SpellKit.repeat(c, 10, 2, i -> {
			double a0 = Math.PI * 2 * i / 10.0;
			for (int k = 0; k <= 12; k++) {
				double a = a0 + Math.PI * 2 / 10.0 * k / 12.0;
				Vec3 p = center.add(Math.cos(a) * GRAVE_R, 0.2, Math.sin(a) * GRAVE_R);
				MagicFx.one(level, p, ParticleTypes.FLAME, 0, 0.05, 0);
				MagicFx.one(level, p.add(0, 0.3, 0), MagicParticles.spark(EMBER, 0.3F, 30), 0, 0.03, 0);
			}
			if (i < 5) {
				Vec3 ray = c.right().scale(GRAVE_R * (i + 1) / 5.0);
				MagicFx.line(level, center.add(c.right().scale(GRAVE_R * i / 5.0)).add(0, 0.2, 0), center.add(ray).add(0, 0.2, 0),
						ParticleTypes.FLAME, 0.5);
			}
			MagicFx.sound(level, center, SoundEvents.BLAZE_SHOOT, 0.6F, 0.6F + 0.05F * i);
		});
		int life = GRAVE_TICKS + 3 * GRAVE_COPY_TICKS + 10;
		// Сумеречный купол мира и шестерни в небе.
		Zones.later(c, 20, () -> {
			MagicFx.barrier(level, center, (float) GRAVE_R, EMBER, DUSK, g.until - 20);
			MagicFx.circle(level, School.SUMMON, 6, EMBER, DUSK, center.add(0, 16, 0), DOWN, 14.0F, 1, 0, 30, g.until - 50, 20, 0.03F);
			MagicFx.circle(level, School.SUMMON, 6, 0xB05A20, DUSK, center.add(0, 17.5, 0), DOWN, 8.0F, 1, 0, 24, g.until - 50, 20, -0.06F);
			MagicFx.circle(level, School.SUMMON, 6, EMBER, 0xFFFFFF, center.add(0, 19, 0), DOWN, 4.0F, 1, 0, 18, g.until - 50, 20, 0.1F);
			MagicFx.soundFar(level, center, MagicSounds.DOMAIN, 96, 0.8F);
			MagicFx.flash(level, center, 40, 0.3F, EMBER, 10);
		});
		// Мечи встают из земли пачками по восемь, с блеском и хором «шииинг».
		for (int b = 0; b < 5; b++) {
			int batch = b;
			Zones.later(c, 20 + b * 4, () -> {
				for (int k = batch * 8; k < batch * 8 + 8; k++) {
					Vec3 p = g.swords.get(k);
					MagicFx.burst(level, p.add(0, 0.6, 0), ParticleTypes.END_ROD, 3, 0.1, 0.1);
					MagicFx.burst(level, p.add(0, 0.2, 0), ParticleTypes.LAVA, 1, 0.1, 0);
				}
				MagicFx.soundFar(level, center, SoundEvents.TRIDENT_RETURN, 64, 0.7F + 0.12F * batch);
				MagicFx.sound(level, center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.6F + 0.15F * batch);
			});
		}
		g.zone = Zones.add(c, center, GRAVE_R, life, (z, age) -> graveTick(g, z, age));
		GRAVES.put(c.caster.getUUID(), g);
		return true;
	}

	private static void graveTick(Grave g, Zones.Zone z, int age) {
		Cast c = g.cast;
		ServerLevel level = c.level;
		ServerPlayer p = c.caster;
		if (age >= g.until || !p.isAlive() || p.isRemoved() || p.level() != level) {
			graveFinale(g);
			z.stop();
			GRAVES.remove(p.getUUID(), g);
			return;
		}
		// Мечи в земле (по половине за раз) и пепел в воздухе.
		if (age >= 20 && age % 5 == 0) {
			for (int i = (age / 5) % 2; i < g.swords.size(); i += 2) {
				Vec3 s = g.swords.get(i);
				double tilt = ((i * 53) % 11 - 5) * 0.04;
				swordGlyph(level, s.add(0, 1.1, 0), new Vec3(tilt, -1, tilt * 0.5).normalize(), new Vec3(1, 0, 0), 1.1,
						MagicParticles.spark(0xD8DCE8, 0.1F, 11), 0.22);
			}
		}
		if (age % 4 == 0) {
			MagicFx.cloud(level, g.center.add(0, 3, 0), ParticleTypes.WHITE_ASH, 30, GRAVE_R * 0.6, 2.5, GRAVE_R * 0.6, 0.01);
			MagicFx.cloud(level, g.center.add(0, 1.5, 0), MagicParticles.mote(EMBER, 0.12F, 30), 6, GRAVE_R * 0.6, 1.5, GRAVE_R * 0.6, 0.02);
		}
		if (age % 10 == 0) MagicFx.ring(level, g.center.add(0, 0.2, 0), GRAVE_R, ParticleTypes.SMALL_FLAME, 36, 0);
		if (age % 60 == 0) MagicFx.sound(level, g.center, MagicSounds.GUST, 0.8F, 0.5F);
		// Граница мира: враги, что пытаются уйти, отлетают назад.
		if (age % 2 == 0) {
			for (LivingEntity e : c.around(g.center, GRAVE_R + 2, x -> c.isFoe(x) && !boss(x))) {
				Vec3 d = new Vec3(e.getX() - g.center.x, 0, e.getZ() - g.center.z);
				double dist = d.length();
				if (dist < GRAVE_R - 1.0 || dist > GRAVE_R + 1.5 || dist < 1.0E-3) continue;
				Vec3 in = d.scale(-1 / dist);
				e.setDeltaMovement(in.scale(GRAVE_PUSH).add(0, 0.15, 0));
				e.hurtMarked = true;
				MagicFx.burst(level, Cast.center(e), MagicParticles.spark(EMBER, 0.25F, 10), 6, 0.3, 0.05);
			}
		}
		if (age % 5 == 0) g.inside = c.foesAround(g.center, GRAVE_R);
		if (age % 20 == 0) {
			for (LivingEntity e : g.inside) {
				if (e.isAlive()) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), p);
			}
			if (p.position().distanceTo(g.center) <= GRAVE_R) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0));
		}
		// Мечи сами летят во врагов: 4 в секунду (больше — с копиями мультикаста).
		if (age >= 20) {
			g.pending += GRAVE_PER_SECOND * g.rate / 20.0;
			int n = 0;
			while (g.pending >= 1.0) {
				g.pending -= 1.0;
				List<LivingEntity> alive = g.inside.stream().filter(LivingEntity::isAlive).toList();
				if (alive.isEmpty()) break;
				graveSword(g, alive.get((age / 5 + n++) % alive.size()));
			}
		}
		// Маг присел — залп с неба на прицел.
		if (g.volleyCd > 0) g.volleyCd--;
		if (p.isShiftKeyDown() && g.volleyCd <= 0 && age >= 20) {
			g.volleyCd = GRAVE_VOLLEY_CD;
			volley(g);
		}
	}

	private static void graveSword(Grave g, LivingEntity t) {
		Cast c = g.cast;
		Vec3 s = g.swords.get(c.level.random.nextInt(g.swords.size()));
		Vec3 from = s.add(0, 1.6, 0);
		Vec3 dir = Cast.center(t).subtract(from).normalize();
		SpellProjectile.spawn(aimed(c, t), from, dir.scale(1.8)).homing(0.15).pierce(1).life(40);
		MagicFx.burst(c.level, s.add(0, 0.4, 0), ParticleTypes.FLAME, 4, 0.1, 0.05);
		MagicFx.sound(c.level, from, SoundEvents.TRIDENT_RETURN, 0.5F, 1.8F);
	}

	/** Залп: 20 мечей падают с неба на прицел за секунду. */
	private static void volley(Grave g) {
		Cast c = g.cast;
		ServerPlayer p = c.caster;
		Targeting.Aim aim = Targeting.aim(c.level, p, p.getEyePosition(), p.getViewVector(1.0F), GRAVE_R * 2, Targeting.living(p));
		Vec3 ground = aim.ground();
		MagicFx.circle(c.level, School.SUMMON, 6, EMBER, DUSK, ground.add(0, 12, 0), DOWN, 3.5F, 1, 0, 6, 20, 10, 0.3F);
		MagicFx.soundFar(c.level, ground, SoundEvents.TRIDENT_RETURN, 48, 0.6F);
		SpellKit.repeat(c, GRAVE_VOLLEY, 1, i -> {
			Vec3 at = SpellKit.inDisc(c.level.random, ground, GRAVE_VOLLEY_R);
			Vec3 from = at.add(c.level.random.nextGaussian() * 0.4, 12, c.level.random.nextGaussian() * 0.4);
			SpellProjectile.spawn(c, from, at.subtract(from).normalize().scale(2.2)).life(30).scale(0.9F);
		});
	}

	private static void graveHit(Cast c, SpellProjectile proj, HitResult result) {
		LivingEntity e = result instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l ? l : null;
		graveStrike(c, result.getLocation(), e, proj.scale() < 0.95F, proj);
	}

	/**
	 * Меч Кладбища попал: 7 урона (не больше 14 в секунду по цели); меч залпа — 5 урона всем в 1,5 блока.
	 * Открыто для самопроверки.
	 */
	public static float graveStrike(Cast c, Vec3 at, LivingEntity e, boolean volley, Entity direct) {
		float total = 0;
		Entity src = direct == null ? c.caster : direct;
		if (volley) {
			for (LivingEntity f : c.foesAround(at, 1.5)) {
				float d = c.amount(GRAVE_VOLLEY_DMG);
				if (c.hurt(f, d, MagicDamage.ARCANE, src)) total += d;
			}
			MagicFx.burst(c.level, at, ParticleTypes.LAVA, 2, 0.1, 0);
		} else if (e != null && c.isFoe(e)) {
			float d = capped(c, e, c.amount(GRAVE_DMG), c.amount(GRAVE_CAP));
			if (d > 0 && c.hurt(e, d, MagicDamage.ARCANE, src)) total = d;
		}
		MagicFx.burst(c.level, at, MagicParticles.spark(EMBER, 0.25F, 12), 8, 0.15, 0.1);
		MagicFx.burst(c.level, at, ParticleTypes.CRIT, 5, 0.1, 0.2);
		MagicFx.sound(c.level, at, SoundEvents.TRIDENT_HIT, 0.7F, 0.9F + c.level.random.nextFloat() * 0.3F);
		return total;
	}

	/** Конец мира: оставшиеся мечи летят в ближайших врагов, мир трескается и осыпается стеклом. */
	private static void graveFinale(Grave g) {
		Cast c = g.cast;
		ServerLevel level = c.level;
		List<LivingEntity> foes = c.foesAround(g.center, GRAVE_R + 2);
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(g.center)));
		if (!foes.isEmpty()) {
			for (int i = 0; i < 12; i++) graveSword(g, foes.get(i % foes.size()));
		}
		MagicFx.shockwave(level, g.center.add(0, 0.5, 0), UP, 2.0F, (float) GRAVE_R * 1.4F, DUSK, 20);
		MagicFx.sphere(level, g.center.add(0, 2, 0), (float) GRAVE_R, (float) GRAVE_R * 1.15F, EMBER, 0xFFFFFF, 12);
		MagicFx.flash(level, g.center, 48, 0.45F, 0xFFFFFF, 12);
		MagicFx.cloud(level, g.center.add(0, 6, 0), MagicParticles.spark(0xFFFFFF, 0.3F, 30), 80, GRAVE_R * 0.6, 5, GRAVE_R * 0.6, 0.1);
		for (int i = 0; i < 4; i++) {
			int k = i;
			Zones.later(c, i * 3, () -> MagicFx.soundFar(level, g.center, SoundEvents.GLASS_BREAK, 96, 0.5F + 0.15F * k));
		}
		MagicFx.soundFar(level, g.center, MagicSounds.SHATTER, 96, 0.6F);
		Magic.shake(level, g.center, 1.2F, 16, 40);
	}

	/** Моб пал внутри кладбища — через секунду он встаёт тенью мага. */
	private static void graveFallen(Mob dead) {
		if (Minions.isSummon(dead) || boss(dead) || !(dead instanceof Enemy) || !(dead.level() instanceof ServerLevel level)) return;
		for (Grave g : GRAVES.values()) {
			if (g.cast.level != level || dead.position().distanceTo(g.center) > GRAVE_R + 0.5) continue;
			g.risen.removeIf(m -> m.isRemoved() || !m.isAlive());
			Vec3 at = dead.position();
			EntityType<?> type = dead.getType();
			ItemStack weapon = dead.getMainHandItem().copy();
			if (g.risen.size() + g.rising >= GRAVE_MAX_RISEN) return;
			g.rising++;
			// Лужа тени глотает павшего — и через секунду выплёвывает солдата.
			MagicFx.circle(level, School.DARK, 3, SHADOW, SHADOW_LIGHT, at.add(0, 0.06, 0), UP, 1.3F, 1, 0, 8, 14, 10, 0.3F);
			MagicFx.cloud(level, at.add(0, 0.1, 0), ParticleTypes.SQUID_INK, 20, 0.6, 0.05, 0.6, 0.02);
			Zones.later(g.cast, 20, () -> {
				g.rising--;
				Mob m = riseFallen(g, type, at, weapon);
				if (m != null) g.risen.add(m);
			});
			return;
		}
	}

	/** Тень павшего: тот же вид, +50% здоровья, на 60 с. */
	private static Mob riseFallen(Grave g, EntityType<?> type, Vec3 at, ItemStack weapon) {
		Cast c = g.cast;
		if (!(type.create(c.level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob m)) return null;
		Vec3 spot = spot(c, m, at);
		m.snapTo(spot.x, spot.y, spot.z, c.caster.getYRot(), 0);
		setBase(m, Attributes.MAX_HEALTH, base(m, Attributes.MAX_HEALTH) * 1.5);
		m.setHealth(m.getMaxHealth());
		if (!weapon.isEmpty()) equip(m, EquipmentSlot.MAINHAND, weapon);
		m.setCustomName(c.spell.displayName());
		m.addTag(RISEN);
		Minions.summon(c, m, GRAVE_RISEN_TICKS);
		MagicFx.cloud(c.level, spot.add(0, 1.0, 0), ParticleTypes.LARGE_SMOKE, 20, 0.4, 0.8, 0.4, 0.03);
		MagicFx.burst(c.level, m.getEyePosition(), MagicParticles.spark(SHADOW, 0.3F, 30), 8, 0.15, 0.01);
		MagicFx.sound(c.level, spot, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.6F);
		return m;
	}

	// =====================================================================================================
	// Ранг 7: Тысячерукий Бодхисаттва
	// =====================================================================================================

	private static boolean bodhisattva(Cast c) {
		SpellKit.release(c);
		ServerLevel level = c.level;
		Vec3 fwd = flat(c.look());
		Vec3 right = c.right();
		Vec3 feet = c.caster.position();
		Vec3 target = c.aimGround();
		Vec3 base = feet.subtract(fwd.scale(4.5));
		IronGolem statue = statue(c, base);
		Vec3 heart = base.add(0, 6.5, 0);
		// Ореол за головой божества — три яруса, и веер из 12 кругов-ладоней, вспыхивающих по одному.
		MagicFx.circle(level, c.spell, base.add(0, 9.5, 0).subtract(fwd.scale(0.8)), fwd, 5.0F, 3, 0.5F, 16, BODHI_END - 10, 20);
		List<Vec3> palms = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			int n = i;
			double a = Math.toRadians(15 + 150.0 * i / 11.0);
			Vec3 p = heart.add(right.scale(Math.cos(a) * 7.5)).add(0, Math.sin(a) * 5.0 - 1.0, 0).subtract(fwd.scale(0.4));
			palms.add(p);
			Zones.later(c, i, () -> {
				MagicFx.circle(level, School.SUMMON, 7, GOLD, GOLD_LIGHT, p, fwd, 1.3F, 1, 0, 4, BODHI_END - 16, 14, 0.4F);
				MagicFx.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F + 0.06F * n);
			});
		}
		MagicFx.pillar(level, base, 3.0F, 12.0F, GOLD, GOLD_LIGHT, 24);
		MagicFx.soundFar(level, feet, MagicSounds.RELEASE_ULTIMATE, 160, 1.0F);
		MagicFx.soundFar(level, feet, SoundEvents.BELL_BLOCK, 128, 0.5F);
		Magic.shake(level, feet, 1.5F, 20, 48);
		// Ливень ладоней: каждые 3 тика — удар из следующего круга.
		for (int i = 0; i < PALMS; i++) {
			int n = i;
			Zones.later(c, PALM_START + i * PALM_EVERY, () -> palm(c, palms.get((n * 5) % palms.size()), target, n));
		}
		// «Нулевая длань»: свет стягивается к груди статуи — и выстрел.
		SpellKit.repeat(c, 4, 2, i -> MagicFx.burst(level, heart, MagicParticles.spark(GOLD_LIGHT, 0.4F, 10), 20, 2.5 - 0.5 * i, -0.15));
		Zones.later(c, ZERO_AT - 8, () -> {
			MagicFx.core(level, heart.add(fwd.scale(2)), 1.2F, 2.5F, GOLD, GOLD_LIGHT, 10);
			MagicFx.soundFar(level, heart, MagicSounds.RISER_SHORT, 96, 1.4F);
			MagicFx.soundFar(level, heart, SoundEvents.BELL_RESONATE, 96, 1.0F);
		});
		Zones.later(c, ZERO_AT, () -> zeroHand(c, heart.add(fwd.scale(2)), target));
		// Статуя рассыпается золотыми искрами.
		Zones.later(c, ZERO_AT + 10, () -> {
			if (statue != null) {
				MagicFx.cloud(level, Cast.center(statue), MagicParticles.mote(GOLD, 0.35F, 40), 120, 2.5, 5.0, 2.5, 0.08);
				MagicFx.cloud(level, Cast.center(statue), ParticleTypes.END_ROD, 40, 2.5, 5.0, 2.5, 0.05);
				statue.discard();
			}
			MagicFx.soundFar(level, base, SoundEvents.BELL_RESONATE, 96, 0.6F);
		});
		return true;
	}

	private static IronGolem statue(Cast c, Vec3 at) {
		IronGolem g = EntityType.IRON_GOLEM.create(c.level, EntitySpawnReason.MOB_SUMMONED);
		if (g == null) return null;
		g.setPlayerCreated(true);
		g.setNoAi(true);
		g.setNoGravity(true);
		g.setInvulnerable(true);
		g.setSilent(true);
		g.setGlowingTag(true);
		g.setCustomName(c.spell.displayName());
		g.addTag(STATUE);
		setBase(g, Attributes.SCALE, 4.0);
		g.snapTo(at.x, at.y, at.z, c.caster.getYRot(), 0);
		g.setYHeadRot(c.caster.getYRot());
		g.yBodyRot = c.caster.getYRot();
		Minions.summon(c, g, BODHI_END + 20);
		return g;
	}

	/** Ладонь бьёт в точку рядом с прицелом (лучше — во врага): 14 урона в радиусе 3, отбрасывание, воронка 2. */
	private static void palm(Cast c, Vec3 from, Vec3 target, int n) {
		List<LivingEntity> foes = c.foesAround(target, PALM_SPREAD);
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
		Vec3 to;
		if (!foes.isEmpty()) {
			LivingEntity e = foes.get(n % foes.size());
			to = e.position();
		} else {
			to = Targeting.ground(c.level, c.caster, SpellKit.inDisc(c.level.random, target, PALM_SPREAD).add(0, 6, 0));
		}
		int travel = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 6.0));
		MagicFx.comet(c.level, from, to, 1.8F, GOLD, GOLD_LIGHT, travel + 2);
		MagicFx.sound(c.level, from, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F + 0.02F * n);
		Zones.later(c, travel, () -> palmSlam(c, to, n));
	}

	private static void palmSlam(Cast c, Vec3 at, int n) {
		for (LivingEntity e : c.foesAround(at.add(0, 1.0, 0), PALM_R)) {
			c.hurt(e, c.amount(PALM_DMG), MagicDamage.ARCANE);
			Cast.knock(e, at, 1.0, -0.1);
		}
		MagicFx.shockwave(c.level, at.add(0, 0.2, 0), UP, 0.5F, 6.0F, GOLD, 12);
		MagicFx.sphere(c.level, at.add(0, 0.5, 0), 0.5F, 3.0F, GOLD, GOLD_LIGHT, 8);
		MagicFx.burst(c.level, at.add(0, 0.5, 0), MagicParticles.spark(GOLD, 0.4F, 16), 16, 0.8, 0.15);
		MagicFx.burst(c.level, at, ParticleTypes.EXPLOSION, 2, 0.6, 0);
		MagicFx.soundFar(c.level, at, MagicSounds.EXPLOSION, 64, 0.7F + 0.02F * n);
		Magic.shake(c.level, at, 0.5F, 6, 24);
		Terrain.crater(c, at, 2.0, 0.5, Terrain.Rim.RUBBLE);
	}

	/** «Нулевая длань»: золотой луч из груди божества — 60 святого урона в радиусе 5 и воронка 7. */
	private static void zeroHand(Cast c, Vec3 from, Vec3 to) {
		ServerLevel level = c.level;
		MagicFx.beam(level, from, to, 4.0F, GOLD, GOLD_LIGHT, 24);
		MagicFx.beam(level, from, to, 1.6F, 0xFFFFFF, 0xFFFFFF, 20);
		for (LivingEntity e : c.foesAround(to.add(0, 1.0, 0), ZERO_R)) {
			c.hurt(e, c.amount(ZERO_DMG), MagicDamage.HOLY);
			Cast.knock(e, to, 1.5, 0.6);
		}
		MagicFx.sphere(level, to, 1.0F, 9.0F, GOLD, GOLD_LIGHT, 18);
		MagicFx.shockwave(level, to.add(0, 0.3, 0), UP, 1.0F, 18.0F, GOLD_LIGHT, 20);
		MagicFx.pillar(level, to, 4.0F, 30.0F, GOLD, GOLD_LIGHT, 20);
		MagicFx.flash(level, to, 96, 0.8F, GOLD_LIGHT, 16);
		MagicFx.burst(level, to, ParticleTypes.EXPLOSION_EMITTER, 3, 2.0, 0);
		MagicFx.soundFar(level, from, MagicSounds.BEAM_BLAST, 160, 0.9F);
		MagicFx.soundFar(level, to, MagicSounds.EXPLOSION_HUGE, 192, 0.8F);
		Magic.shake(level, to, 3.0F, 30, 96);
		Terrain.crater(c, to, 7.0, 5.0 / 7.0, Terrain.Rim.RUBBLE);
	}

	// =====================================================================================================
	// Помощники
	// =====================================================================================================

	/** Кому достаётся дар копии: главная — магу, остальные — ближайшим игрокам в 6 блоках (нет — никому). */
	private static Player recipient(Cast c) {
		if (c.copy == 0) return c.caster;
		List<Player> near = new ArrayList<>(c.level.getEntitiesOfClass(Player.class, c.caster.getBoundingBox().inflate(GIFT_R),
				p -> p != c.caster && p.isAlive() && !p.isSpectator()));
		near.sort(Comparator.comparingDouble(p -> p.distanceToSqr(c.caster)));
		return c.copy - 1 < near.size() ? near.get(c.copy - 1) : null;
	}

	private static ItemStack spectral(Cast c, Item item, int ticks) {
		ItemStack stack = new ItemStack(item);
		stack.set(MagicItems.EXPIRES, c.level.getGameTime() + ticks);
		return stack;
	}

	private static Holder<Enchantment> enchantment(Cast c, ResourceKey<Enchantment> key) {
		return c.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** Убрать старый призрачный предмет того же вида и выдать новый (меч — в свободную вторую руку). */
	private static boolean give(Cast c, Player to, ItemStack stack, boolean offhand) {
		var inv = to.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() == stack.getItem()) inv.setItem(i, ItemStack.EMPTY);
		}
		boolean ok;
		if (offhand && to.getOffhandItem().isEmpty() && SpectralItem.is(stack)) {
			to.setItemSlot(EquipmentSlot.OFFHAND, stack);
			ok = true;
		} else {
			ok = inv.add(stack);
		}
		if (!ok) return false;
		MagicFx.burst(c.level, Cast.center(to), MagicParticles.spark(TEAL, 0.25F, 16), 16, 0.4, 0.04);
		MagicFx.sound(c.level, to.position(), MagicSounds.SUMMON, 0.7F, 1.6F);
		return true;
	}

	/** Цель для снаряда: копия каста, у которой прицел — это существо (на него наводится снаряд). */
	private static Cast aimed(Cast c, LivingEntity t) {
		Vec3 center = Cast.center(t);
		return new Cast(c.level, c.caster, c.spell, c.power, c.bonus, c.copy, c.copies, null,
				new Targeting.Aim(new EntityHitResult(t, center), center, t, t.position(), false));
	}

	private static LivingEntity nearestFoe(Cast c, Vec3 at, double r) {
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity e : c.foesAround(at, r)) {
			double d = e.distanceToSqr(at);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		return best;
	}

	/** Боссов не таскаем и не воскрешаем. */
	private static boolean boss(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	/** Сколько урона ещё можно нанести цели в эту секунду (потолок {@code perSecond}). */
	private static float capped(Cast c, LivingEntity e, float want, float perSecond) {
		long sec = c.level.getGameTime() / 20;
		if (DEALT.size() > 512) DEALT.values().removeIf(d -> d.second < sec);
		Dealt d = DEALT.computeIfAbsent(c.spell.id + e.getUUID(), k -> new Dealt());
		if (d.second != sec) {
			d.second = sec;
			d.amount = 0;
		}
		float give = Math.min(want, perSecond - d.amount);
		if (give <= 0.01F) return 0;
		d.amount += give;
		return give;
	}

	private static final class Dealt {
		long second = Long.MIN_VALUE;
		float amount;
	}

	/** Отпустить старых призванных с меткой {@code tag}, оставив {@code keep} самых новых. */
	private static void dismissOld(Cast c, String tag, int keep) {
		List<Mob> mine = new ArrayList<>(c.level.getEntities(EntityTypeTest.forClass(Mob.class),
				m -> m.isAlive() && m.getTags().contains(tag) && Minions.isOwnedBy(m, c.caster)));
		mine.sort(Comparator.comparingLong(m -> {
			Minions.Summon s = m.getAttached(Minions.SUMMON);
			return s == null ? 0L : s.expires();
		}));
		for (Iterator<Mob> it = mine.iterator(); it.hasNext() && mine.size() > keep; ) {
			Mob m = it.next();
			it.remove();
			MagicFx.poof(c.level, m, TEAL);
			m.discard();
		}
	}

	/** Свободное место для существа у точки (кольцами вокруг неё) или у мага. */
	private static Vec3 spot(Cast c, Mob mob, Vec3 at) {
		for (int ring = 0; ring <= 3; ring++) {
			int dirs = ring == 0 ? 1 : 8;
			for (int i = 0; i < dirs; i++) {
				double a = Math.PI * 2 * i / dirs;
				for (int dy = 0; dy <= 2; dy++) {
					Vec3 p = at.add(Math.cos(a) * ring * 1.2, dy, Math.sin(a) * ring * 1.2);
					if (c.level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(p))) return p;
				}
			}
		}
		return c.caster.position();
	}

	private static void equip(Mob m, EquipmentSlot slot, ItemStack stack) {
		m.setItemSlot(slot, stack);
		m.setDropChance(slot, 0.0F);
	}

	private static double base(LivingEntity e, Holder<Attribute> a) {
		AttributeInstance i = e.getAttribute(a);
		return i == null ? 0 : i.getBaseValue();
	}

	private static void setBase(LivingEntity e, Holder<Attribute> a, double v) {
		AttributeInstance i = e.getAttribute(a);
		if (i != null) i.setBaseValue(v);
	}

	/** Горизонтальное направление взгляда. */
	private static Vec3 flat(Vec3 look) {
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 right(Entity e) {
		float yaw = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
		return new Vec3(-net.minecraft.util.Mth.cos(yaw), 0, -net.minecraft.util.Mth.sin(yaw));
	}

	/** Меч из частиц: рукоять у {@code base}, остриё по {@code dir}, гарда поперёк по {@code side}. */
	private static void swordGlyph(ServerLevel level, Vec3 base, Vec3 dir, Vec3 side, double len, ParticleOptions p, double step) {
		MagicFx.line(level, base.add(dir.scale(0.12 * len)), base.add(dir.scale(len)), p, step);
		MagicFx.line(level, base.add(side.scale(-0.2 * len)), base.add(side.scale(0.2 * len)), p, step);
		MagicFx.one(level, base.subtract(dir.scale(0.18 * len)), p, 0, 0, 0);
	}

	/** Контур клинка (две кромки и гарда) — участок от доли {@code from} до {@code till} длины. */
	private static void outline(ServerLevel level, Vec3 base, Vec3 dir, Vec3 side, double len, double from, double till, ParticleOptions p) {
		for (double t = from; t <= till + 1.0E-6; t += 0.06) {
			double w = 0.07 * (1.0 - Math.max(0, t - 0.8) / 0.2);
			Vec3 a = base.add(dir.scale(len * t));
			MagicFx.one(level, a.add(side.scale(w)), p, 0, 0, 0);
			MagicFx.one(level, a.subtract(side.scale(w)), p, 0, 0, 0);
		}
		if (from == 0) MagicFx.line(level, base.add(side.scale(-0.22)), base.add(side.scale(0.22)), p, 0.07);
	}
}
