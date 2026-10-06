package com.suchki.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Снаряд заклинания: светящееся ядро со следом; что делает при попадании — решает само заклинание. */
public class SpellProjectile extends ThrowableProjectile {
	private static final EntityDataAccessor<String> SPELL = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
	/** Сколько тиков живёт снаряд, если ни во что не попал. */
	private static final int MAX_LIFE = 100;

	private float bonus = 1.0F;
	private int life;
	private double gravity;

	public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
		super(type, level);
	}

	/** Выпустить снаряд заклинания из руки мага по взгляду. */
	public static SpellProjectile shoot(Cast c, float speed) {
		SpellProjectile p = new SpellProjectile(MagicEntities.SPELL_PROJECTILE, c.level);
		p.setOwner(c.caster);
		p.entityData.set(SPELL, c.spell.id);
		p.entityData.set(POWER, c.power);
		p.bonus = c.bonus;
		Vec3 from = c.hand();
		p.setPos(from.x, from.y - 0.2, from.z);
		Vec3 dir = c.look();
		p.shoot(dir.x, dir.y, dir.z, speed * (0.85F + 0.15F * c.power), 0.0F);
		c.level.addFreshEntity(p);
		return p;
	}

	/** Снаряд заклинания из произвольной точки с заданной скоростью (метеоры и т. п.). */
	public static SpellProjectile spawn(Cast c, Vec3 pos, Vec3 velocity) {
		SpellProjectile p = new SpellProjectile(MagicEntities.SPELL_PROJECTILE, c.level);
		p.setOwner(c.caster);
		p.entityData.set(SPELL, c.spell.id);
		p.entityData.set(POWER, c.power);
		p.bonus = c.bonus;
		p.setPos(pos.x, pos.y, pos.z);
		p.setDeltaMovement(velocity);
		c.level.addFreshEntity(p);
		return p;
	}

	public SpellProjectile gravity(double g) {
		this.gravity = g;
		return this;
	}

	public Spell spell() {
		return Spells.get(entityData.get(SPELL));
	}

	public float power() {
		return entityData.get(POWER);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SPELL, "");
		builder.define(POWER, 1.0F);
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
		if (spell == null || life > MAX_LIFE) {
			discard();
			return;
		}
		// Огонь гаснет в воде.
		if (spell.school == School.DESTRUCTION && isInWater() && !"ice_shard".equals(spell.id)) {
			MagicFx.burst((ServerLevel) level(), position(), ParticleTypes.CLOUD, 6, 0.1, 0.02);
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		Entity owner = getOwner();
		if (owner != null && (Minions.isOwnedBy(e, owner) || e == owner)) return false;
		return super.canHitEntity(e);
	}

	@Override
	protected void onHit(HitResult result) {
		if (!(level() instanceof ServerLevel level)) return;
		Spell spell = spell();
		if (spell != null && spell.hit != null && getOwner() instanceof ServerPlayer caster) {
			spell.hit.hit(new Cast(level, caster, spell, power(), bonus), this, result);
		}
		discard();
	}

	/** След снаряда (только на клиенте): светящиеся искры цвета школы и «материя» заклинания. */
	private void trail(Spell spell) {
		Vec3 p = position();
		Vec3 v = getDeltaMovement();
		int color = spell.school.color;
		float size = spell.projectileSize * (0.8F + 0.2F * power());
		for (int i = 0; i < 2; i++) {
			double t = i * 0.5;
			level().addParticle(MagicParticles.spark(color, size * 0.7F, 10), p.x - v.x * t, p.y - v.y * t + 0.15, p.z - v.z * t,
					(random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);
		}
		switch (spell.id) {
			case "firebolt", "fireball" -> {
				level().addParticle(ParticleTypes.FLAME, p.x, p.y + 0.15, p.z, -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
				if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.15, p.z, 0, 0.01, 0);
			}
			case "meteor_shower" -> {
				level().addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.3, p.z, 0, 0.02, 0);
				level().addParticle(ParticleTypes.FLAME, p.x, p.y + 0.3, p.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
				level().addParticle(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, 0, 0, 0);
			}
			case "ice_shard" -> {
				level().addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y + 0.15, p.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
			}
			case "smite" -> level().addParticle(ParticleTypes.END_ROD, p.x, p.y + 0.15, p.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
			case "shadow_bolt" -> {
				level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.15, p.z, 0, 0, 0);
				if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL, p.x, p.y + 0.15, p.z, 0, 0.01, 0);
			}
			case "paralyze" -> level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.15, p.z, (random.nextDouble() - 0.5) * 0.2,
					(random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
			default -> {
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putString("spell", entityData.get(SPELL));
		out.putFloat("power", power());
		out.putFloat("bonus", bonus);
		out.putInt("life", life);
		out.putDouble("gravity", gravity);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		entityData.set(SPELL, in.getStringOr("spell", ""));
		entityData.set(POWER, in.getFloatOr("power", 1.0F));
		bonus = in.getFloatOr("bonus", 1.0F);
		life = in.getIntOr("life", 0);
		gravity = in.getDoubleOr("gravity", 0.0);
	}
}
