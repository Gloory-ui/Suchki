package com.suchki.magic;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;

/**
 * Заклинание: что оно стоит, как заряжается, где строится круг и что делает. Все заклинания описаны в
 * {@code magic.spells.*} и собраны в {@link Spells}. Одинаковы на сервере и клиенте (клиент берёт отсюда цену,
 * время, цвет, тип, якорь круга).
 *
 * <p>Ранг ({@link #tier}) — как в «Реинкарнации безработного»: 1 Начальный, 2 Средний, 3 Продвинутый, 4 Святой,
 * 5 Королевский, 6 Императорский, 7 Божественный.
 */
public final class Spell {
	public static final int MAX_TIER = 7;
	/** Какой уровень Мага нужен для ранга 1…7 (индекс — ранг). */
	public static final int[] TIER_LEVEL = {0, 1, 6, 12, 20, 28, 38, 46};

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
	/** Дальность прицела/луча (сервер и клиент считают точку прицела по ней). */
	public final double range;
	/** Предмет, который рисуется внутри снаряда (вращается), или null — только свечение. */
	public final Identifier projectileModel;
	/** Размер светящегося ядра снаряда. */
	public final float projectileSize;
	/** След снаряда. */
	public final Trail trail;
	/** Поток рисуется лучом от руки до цели. */
	public final boolean beam;
	/** Где строится круг. */
	public final Anchor anchor;
	/** Сколько кругов в стопке (большие заклинания — несколько ярусов). */
	public final int layers;
	/** Радиус главного круга в блоках (0 — по рангу и якорю). */
	public final float circleRadius;
	/** Высота круга над целью для {@link Anchor#SKY}. */
	public final float skyHeight;
	/** Стихии-составляющие для слияния ({@link School#FUSION}); у обычных заклинаний — пусто. */
	public final List<School> combo;
	/** Может ломать блоки (если это разрешено в настройках). Для подсказки и самопроверки. */
	public final boolean destroys;
	/** Сколько копий можно выпустить мультикастом (1 — нельзя). */
	public final int maxCopies;
	/** Звук потока (крутится, пока держишь) или null — общий звук луча. */
	public final SoundEvent loop;
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
		this.trail = b.trail;
		this.beam = b.beam;
		this.anchor = b.anchor != null ? b.anchor : defaultAnchor(b.style);
		this.layers = b.layers > 0 ? b.layers : (b.tier >= 6 ? 3 : b.tier >= 4 ? 2 : 1);
		this.circleRadius = b.circleRadius;
		this.skyHeight = b.skyHeight;
		this.combo = List.copyOf(b.combo);
		this.destroys = b.destroys;
		this.maxCopies = b.maxCopies > 0 ? b.maxCopies : (b.tier >= MAX_TIER || b.style == CastStyle.SELF ? 1 : 4);
		this.loop = b.loop;
		this.action = b.action;
		this.channel = b.channel;
		this.hit = b.hit;
	}

	private static Anchor defaultAnchor(CastStyle style) {
		return switch (style) {
			case BOLT, CHANNEL -> Anchor.HAND;
			case SELF, RITUAL, SUMMON -> Anchor.FEET;
		};
	}

	public Component displayName() {
		return Component.translatable("spell.suchki." + id);
	}

	public Component description() {
		return Component.translatable("spell.suchki." + id + ".desc");
	}

	/** Ключ перевода заклинательной фразы (строки через «|»); у заклинаний первого ранга фразы обычно нет. */
	public String chantKey() {
		return "spell.suchki." + id + ".chant";
	}

	public Item iconItem() {
		return BuiltInRegistries.ITEM.getValue(icon);
	}

	public int requiredLevel() {
		return TIER_LEVEL[Math.max(1, Math.min(MAX_TIER, tier))];
	}

	public boolean charged() {
		return style.charged();
	}

	public boolean fusion() {
		return !combo.isEmpty();
	}

	/** Есть ли у заклинания попадание снаряда. */
	public boolean projectile() {
		return hit != null;
	}

	/** Радиус главного круга в блоках (свой или по рангу и якорю). */
	public float circleRadius() {
		if (circleRadius > 0) return circleRadius;
		return switch (anchor) {
			case HAND -> 0.35F + 0.08F * tier;
			case FEET -> 0.9F + 0.35F * tier;
			case TARGET -> 1.2F + 0.8F * tier;
			case SKY -> 2.0F + 1.2F * tier;
			case LINE -> 0.6F + 0.15F * tier;
			case BACK -> 0.45F + 0.05F * tier;
			case ORBIT -> 0.5F + 0.06F * tier;
		};
	}

	/** Высота небесного круга над целью. */
	public float skyHeight() {
		return skyHeight > 0 ? skyHeight : 10.0F + 2.0F * tier;
	}

	/** Цвет круга и эффектов: у слияния — смесь стихий-составляющих. */
	public int color() {
		if (combo.isEmpty()) return school.color;
		return blend(combo.stream().mapToInt(s -> s.color).toArray());
	}

	public int lightColor() {
		if (combo.isEmpty()) return school.light;
		return blend(combo.stream().mapToInt(s -> s.light).toArray());
	}

	static int blend(int[] colors) {
		int r = 0, g = 0, b = 0;
		for (int c : colors) {
			r += (c >> 16) & 0xFF;
			g += (c >> 8) & 0xFF;
			b += c & 0xFF;
		}
		int n = Math.max(1, colors.length);
		return (r / n) << 16 | (g / n) << 8 | (b / n);
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
		private double range = 32;
		private String model;
		private float size = 0.35F;
		private Trail trail = Trail.PLAIN;
		private boolean beam;
		private Anchor anchor;
		private int layers;
		private float circleRadius;
		private float skyHeight;
		private List<School> combo = List.of();
		private boolean destroys;
		private int maxCopies;
		private SoundEvent loop;
		private Action action;
		private Channel channel;
		private Hit hit;

		private Builder(String id, School school, int tier, CastStyle style) {
			if (tier < 1 || tier > MAX_TIER) throw new IllegalArgumentException("ранг " + tier + " у заклинания " + id);
			this.id = id;
			this.school = school;
			this.tier = tier;
			this.style = style;
		}

		public Builder mana(int v) { mana = v; return this; }
		public Builder charge(int ticks) { charge = ticks; return this; }
		public Builder cooldown(int ticks) { cooldown = ticks; return this; }
		public Builder icon(String item) { icon = item.contains(":") ? item : "minecraft:" + item; return this; }
		public Builder range(double v) { range = Math.min(v, Magic.MAX_RANGE); return this; }
		public Builder model(String item) { model = item.contains(":") ? item : "minecraft:" + item; return this; }
		public Builder size(float v) { size = v; return this; }
		public Builder trail(Trail t) { trail = t; return this; }
		public Builder beam() { beam = true; return this; }
		public Builder anchor(Anchor a) { anchor = a; return this; }
		public Builder layers(int n) { layers = n; return this; }
		public Builder circle(float radius) { circleRadius = radius; return this; }
		public Builder sky(float height) { skyHeight = height; return this; }
		public Builder combo(School... parts) { combo = List.of(parts); return this; }
		public Builder destroys() { destroys = true; return this; }
		public Builder multicast(int max) { maxCopies = Math.max(1, max); return this; }
		public Builder noMulticast() { maxCopies = 1; return this; }
		public Builder loop(SoundEvent sound) { loop = sound; return this; }
		public Builder action(Action a) { action = a; return this; }
		public Builder channel(Channel c) { channel = c; return this; }
		public Builder hit(Hit h) { hit = h; return this; }

		public Spell register() {
			if (school == School.FUSION && combo.size() < 2) throw new IllegalStateException("у слияния " + id + " нет стихий");
			if (school != School.FUSION && !combo.isEmpty()) throw new IllegalStateException("стихии у не-слияния " + id);
			Spell spell = new Spell(this);
			if (spell.style == CastStyle.CHANNEL ? channel == null : action == null) {
				throw new IllegalStateException("у заклинания " + id + " нет действия");
			}
			Spells.add(spell);
			return spell;
		}
	}
}
