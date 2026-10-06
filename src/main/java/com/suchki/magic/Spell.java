package com.suchki.magic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;

/**
 * Заклинание: что оно стоит, как заряжается и что делает. Все заклинания описаны в {@code magic.spells.*}
 * и собраны в {@link Spells}. Одинаковы на сервере и клиенте (клиент берёт отсюда цену, время, цвет, тип).
 */
public final class Spell {
	/** Какой уровень Мага нужен для круга I…V (индекс — круг). */
	public static final int[] TIER_LEVEL = {0, 1, 8, 16, 26, 36};

	/** Выпуск заряженного заклинания. false — не получилось (нет цели и т. п.): мана частично вернётся. */
	public interface Action {
		boolean cast(Cast c);
	}

	/** Поток: вызывается каждый тик, пока маг держит каст. {@code ticks} — сколько тиков уже идёт поток. */
	public interface Channel {
		void tick(Cast c, int ticks);
	}

	/** Попадание снаряда этого заклинания. */
	public interface Hit {
		void hit(Cast c, SpellProjectile projectile, HitResult result);
	}

	public final String id;
	public final School school;
	public final int tier;
	public final CastStyle style;
	/** Цена (для потока — в секунду). */
	public final int mana;
	/** Время формирования в тиках (до скорости каста). */
	public final int chargeTicks;
	public final int cooldownTicks;
	public final Identifier icon;
	/** Дальность прицела/луча (для эффектов на клиенте и проверок на сервере). */
	public final double range;
	/** Предмет, который рисуется внутри снаряда (вращается), или null — только свечение. */
	public final Identifier projectileModel;
	/** Размер светящегося ядра снаряда. */
	public final float projectileSize;
	/** Поток рисуется лучом от руки до цели. */
	public final boolean beam;
	final Action action;
	final Channel channel;
	final Hit hit;

	private Spell(Builder b) {
		this.id = b.id;
		this.school = b.school;
		this.tier = b.tier;
		this.style = b.style;
		this.mana = b.mana;
		this.chargeTicks = b.charge;
		this.cooldownTicks = b.cooldown;
		this.icon = Identifier.parse(b.icon);
		this.range = b.range;
		this.projectileModel = b.model == null ? null : Identifier.parse(b.model);
		this.projectileSize = b.size;
		this.beam = b.beam;
		this.action = b.action;
		this.channel = b.channel;
		this.hit = b.hit;
	}

	public Component displayName() {
		return Component.translatable("spell.suchki." + id);
	}

	public Component description() {
		return Component.translatable("spell.suchki." + id + ".desc");
	}

	public Item iconItem() {
		return BuiltInRegistries.ITEM.getValue(icon);
	}

	public int requiredLevel() {
		return TIER_LEVEL[tier];
	}

	public boolean charged() {
		return style.charged();
	}

	public static Builder of(String id, School school, int tier, CastStyle style) {
		return new Builder(id, school, tier, style);
	}

	public static final class Builder {
		private final String id;
		private final School school;
		private final int tier;
		private final CastStyle style;
		private int mana = 10;
		private int charge = 10;
		private int cooldown = 10;
		private String icon = "minecraft:book";
		private double range = 24;
		private String model;
		private float size = 0.35F;
		private boolean beam;
		private Action action;
		private Channel channel;
		private Hit hit;

		private Builder(String id, School school, int tier, CastStyle style) {
			this.id = id;
			this.school = school;
			this.tier = tier;
			this.style = style;
		}

		public Builder mana(int v) { mana = v; return this; }
		public Builder charge(int ticks) { charge = ticks; return this; }
		public Builder cooldown(int ticks) { cooldown = ticks; return this; }
		public Builder icon(String item) { icon = item.contains(":") ? item : "minecraft:" + item; return this; }
		public Builder range(double v) { range = v; return this; }
		public Builder model(String item) { model = item.contains(":") ? item : "minecraft:" + item; return this; }
		public Builder size(float v) { size = v; return this; }
		public Builder beam() { beam = true; return this; }
		public Builder action(Action a) { action = a; return this; }
		public Builder channel(Channel c) { channel = c; return this; }
		public Builder hit(Hit h) { hit = h; return this; }

		public Spell register() {
			Spell spell = new Spell(this);
			if (spell.style == CastStyle.CHANNEL ? channel == null : action == null) {
				throw new IllegalStateException("у заклинания " + id + " нет действия");
			}
			Spells.add(spell);
			return spell;
		}
	}
}
