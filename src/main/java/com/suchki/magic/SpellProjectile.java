package com.suchki.magic;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд заклинания: светящееся ядро со следом ({@link Spell#trail}); что делает при попадании — решает само
 * заклинание ({@link Spell#hit}). Умеет пробивать цели, наводиться, отскакивать, падать по дуге.
 */
public class SpellProjectile extends ThrowableProjectile {
	private static final EntityDataAccessor<String> SPELL = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
	/** Множитель размера ядра (огромный метеор, маленькие осколки). */
	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
	/** Сколько тиков живёт снаряд, если ни во что не попал (хватает на 128 блоков). */
	private static final int MAX_LIFE = 160;

	private float bonus = 1.0F;
	private int life;
	private int maxLife = MAX_LIFE;
	private double gravity;
	/** Сколько существ ещё можно пробить насквозь. */
	private int pierce;
	/** Сколько раз ещё отскочить от блока. */
	private int bounce;
	/** Сила наведения (0 — летит прямо). */
	private double homing;
	private UUID homingTarget;
	/** Кого уже пробил (второй раз не задевает). */
	private final Set<UUID> pierced = new HashSet<>();

	public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
		super(type, level);
	}

	/** Выпустить снаряд заклинания из круга этой копии (или руки мага) по направлению копии. */
	public static SpellProjectile shoot(Cast c, float speed) {
		return shoot(c, speed, c.direction());
	}

	/** Выпустить снаряд из руки (круга) в заданном направлении. */
	public static SpellProjectile shoot(Cast c, float speed, Vec3 dir) {
		SpellProjectile p = create(c);
		Vec3 from = c.hand();
		p.setPos(from.x, from.y - 0.2, from.z);
		p.shoot(dir.x, dir.y, dir.z, speed * (0.85F + 0.15F * c.power), 0.0F);
		if (c.target().entity() != null) p.homingTarget = c.target().entity().getUUID();
		c.level.addFreshEntity(p);
		return p;
	}

	/** Снаряд заклинания из произвольной точки с заданной скоростью (метеоры, копья из кругов и т. п.). */
	public static SpellProjectile spawn(Cast c, Vec3 pos, Vec3 velocity) {
		SpellProjectile p = create(c);
		p.setPos(pos.x, pos.y, pos.z);
		p.setDeltaMovement(velocity);
		if (c.target().entity() != null) p.homingTarget = c.target().entity().getUUID();
		c.level.addFreshEntity(p);
		return p;
	}

	private static SpellProjectile create(Cast c) {
		SpellProjectile p = new SpellProjectile(MagicEntities.SPELL_PROJECTILE, c.level);
		p.setOwner(c.caster);
		p.entityData.set(SPELL, c.spell.id);
		p.entityData.set(POWER, c.power);
		p.bonus = c.bonus;
		return p;
	}

	public SpellProjectile gravity(double g) {
		this.gravity = g;
		return this;
	}

	/** Пробивает насквозь до {@code n} существ (каждое получает удар). */
	public SpellProjectile pierce(int n) {
		this.pierce = n;
		return this;
	}

	/** Отскакивает от блоков {@code n} раз. */
	public SpellProjectile bounce(int n) {
		this.bounce = n;
		return this;
	}

	/** Наводится на цель под прицелом (или ближайшего врага); {@code strength} 0.05…0.3. */
	public SpellProjectile homing(double strength) {
		this.homing = strength;
		return this;
	}

	public SpellProjectile scale(float s) {
		entityData.set(SCALE, s);
		return this;
	}

	public SpellProjectile life(int ticks) {
		this.maxLife = ticks;
		return this;
	}

	public Spell spell() {
		return Spells.get(entityData.get(SPELL));
	}

	public float power() {
		return entityData.get(POWER);
	}

	public float scale() {
		return entityData.get(SCALE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SPELL, "");
		builder.define(POWER, 1.0F);
		builder.define(SCALE, 1.0F);
	}

	@Override
	protected double getDefaultGravity() {
		return gravity;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isOnFire() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		life++;
		Spell spell = spell();
		if (level().isClientSide()) {
			if (spell != null) trail(spell);
			return;
		}
		if (spell == null || life > maxLife) {
			discard();
			return;
		}
		// Огонь гаснет в воде.
		if ((spell.trail == Trail.FIRE || spell.trail == Trail.METEOR) && isInWater()) {
			MagicFx.burst((ServerLevel) level(), position(), ParticleTypes.CLOUD, 6, 0.1, 0.02);
			discard();
			return;
		}
		if (homing > 0) steer();
	}

	private void steer() {
		Entity target = homingTarget != null && level() instanceof ServerLevel sl ? sl.getEntity(homingTarget) : null;
		if (!(target instanceof LivingEntity l) || !l.isAlive()) return;
		Vec3 v = getDeltaMovement();
		double speed = v.length();
		Vec3 to = l.position().add(0, l.getBbHeight() * 0.5, 0).subtract(position());
		if (to.lengthSqr() < 1.0E-4 || speed < 1.0E-4) return;
		Vec3 dir = v.scale(1.0 / speed).lerp(to.normalize(), homing).normalize();
		setDeltaMovement(dir.scale(speed));
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		Entity owner = getOwner();
		if (owner != null && (Minions.isOwnedBy(e, owner) || e == owner)) return false;
		if (pierced.contains(e.getUUID())) return false;
		return super.canHitEntity(e);
	}

	@Override
	protected void onHit(HitResult result) {
		if (!(level() instanceof ServerLevel level)) return;
		Spell spell = spell();
		if (spell != null && spell.hit != null && getOwner() instanceof ServerPlayer caster) {
			spell.hit.hit(new Cast(level, caster, spell, power(), bonus), this, result);
		}
		if (result instanceof EntityHitResult e && pierce > 0) {
			pierce--;
			pierced.add(e.getEntity().getUUID());
			return;
		}
		if (result instanceof BlockHitResult b && bounce > 0) {
			bounce--;
			Vec3 v = getDeltaMovement();
			Vec3 n = Vec3.atLowerCornerOf(b.getDirection().getUnitVec3i());
			setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))).scale(0.8));
			setPos(b.getLocation().add(n.scale(0.2)));
			return;
		}
		discard();
	}

	/** След снаряда (только на клиенте): светящиеся искры цвета заклинания и «материя» стихии. */
	private void trail(Spell spell) {
		Vec3 p = position();
		Vec3 v = getDeltaMovement();
		int color = spell.color();
		float size = spell.projectileSize * scale() * (0.8F + 0.2F * power());
		for (int i = 0; i < 2; i++) {
			double t = i * 0.5;
			level().addParticle(MagicParticles.spark(color, size * 0.7F, 10), p.x - v.x * t, p.y - v.y * t + 0.15, p.z - v.z * t,
					(random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);
		}
		double y = p.y + 0.15;
		switch (spell.trail) {
			case FIRE -> {
				level().addParticle(ParticleTypes.FLAME, p.x, y, p.z, -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
				if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, p.x, y, p.z, 0, 0.01, 0);
			}
			case METEOR -> {
				for (int i = 0; i < 2; i++) {
					level().addParticle(ParticleTypes.LARGE_SMOKE, p.x + jitter(0.4), y + 0.15, p.z + jitter(0.4), 0, 0.02, 0);
					level().addParticle(ParticleTypes.FLAME, p.x + jitter(0.3), y + 0.15, p.z + jitter(0.3), jitter(0.1), 0.05, jitter(0.1));
				}
				level().addParticle(ParticleTypes.LAVA, p.x, y + 0.15, p.z, 0, 0, 0);
			}
			case BLACK_FIRE -> {
				level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, y, p.z, -v.x * 0.08, -v.y * 0.08, -v.z * 0.08);
				level().addParticle(MagicParticles.spark(0x1A0628, size * 0.9F, 12), p.x, y, p.z, 0, 0, 0);
			}
			case ICE -> level().addParticle(ParticleTypes.SNOWFLAKE, p.x, y, p.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
			case WATER -> {
				level().addParticle(ParticleTypes.SPLASH, p.x, y, p.z, 0, 0, 0);
				if (random.nextBoolean()) level().addParticle(ParticleTypes.BUBBLE_POP, p.x, y, p.z, 0, 0, 0);
			}
			case WIND -> level().addParticle(ParticleTypes.CLOUD, p.x, y, p.z, -v.x * 0.05 + jitter(0.03), jitter(0.03), -v.z * 0.05 + jitter(0.03));
			case STONE -> {
				if (random.nextInt(2) == 0) {
					level().addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
							net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), p.x, y, p.z, 0, 0, 0);
				}
			}
			case LIGHTNING -> level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, y, p.z, jitter(0.2), jitter(0.2), jitter(0.2));
			case HOLY -> level().addParticle(ParticleTypes.END_ROD, p.x, y, p.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
			case SHADOW -> {
				level().addParticle(ParticleTypes.SMOKE, p.x, y, p.z, 0, 0, 0);
				if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL, p.x, y, p.z, 0, 0.01, 0);
			}
			case BLOOD -> level().addParticle(MagicParticles.spark(0xB0102A, size * 0.6F, 14), p.x, y, p.z, 0, -0.02, 0);
			case VOID -> {
				level().addParticle(ParticleTypes.REVERSE_PORTAL, p.x, y, p.z, jitter(0.05), jitter(0.05), jitter(0.05));
				level().addParticle(MagicParticles.spark(0x0A0418, size, 10), p.x, y, p.z, 0, 0, 0);
			}
			case STARS -> level().addParticle(MagicParticles.mote(MagicRenderColors.lighten(color, 0.6F), size * 0.4F, 18), p.x + jitter(0.3),
					y + jitter(0.3), p.z + jitter(0.3), 0, 0, 0);
			case PETALS -> level().addParticle(ParticleTypes.CHERRY_LEAVES, p.x, y, p.z, 0, 0, 0);
			case MIND -> level().addParticle(ParticleTypes.WITCH, p.x, y, p.z, 0, 0, 0);
			case SPIRIT -> level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, y, p.z, 0, 0.01, 0);
			default -> {
			}
		}
	}

	private double jitter(double s) {
		return (random.nextDouble() - 0.5) * 2 * s;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putString("spell", entityData.get(SPELL));
		out.putFloat("power", power());
		out.putFloat("scale", scale());
		out.putFloat("bonus", bonus);
		out.putInt("life", life);
		out.putInt("max_life", maxLife);
		out.putDouble("gravity", gravity);
		out.putInt("pierce", pierce);
		out.putInt("bounce", bounce);
		out.putDouble("homing", homing);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		entityData.set(SPELL, in.getStringOr("spell", ""));
		entityData.set(POWER, in.getFloatOr("power", 1.0F));
		entityData.set(SCALE, in.getFloatOr("scale", 1.0F));
		bonus = in.getFloatOr("bonus", 1.0F);
		life = in.getIntOr("life", 0);
		maxLife = in.getIntOr("max_life", MAX_LIFE);
		gravity = in.getDoubleOr("gravity", 0.0);
		pierce = in.getIntOr("pierce", 0);
		bounce = in.getIntOr("bounce", 0);
		homing = in.getDoubleOr("homing", 0.0);
	}
}
