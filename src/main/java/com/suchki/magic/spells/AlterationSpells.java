package com.suchki.magic.spells;

import static com.suchki.magic.CastStyle.BOLT;
import static com.suchki.magic.CastStyle.CHANNEL;
import static com.suchki.magic.CastStyle.RITUAL;
import static com.suchki.magic.CastStyle.SELF;
import static com.suchki.magic.School.ALTERATION;

import com.suchki.magic.Cast;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicDamage;
import com.suchki.magic.MagicEffects;
import com.suchki.magic.MagicFx;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.SpellProjectile;
import com.suchki.magic.Zones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Изменение: тело мага и мир вокруг — броня, полёт, рывки, телекинез, паралич, землетрясение. */
public final class AlterationSpells {
	static final int BLUE = 0x4DA6FF;
	static final int STONE = 0xA9A9A9;

	private AlterationSpells() {}

	public static void register() {
		Spell.of("stoneflesh", ALTERATION, 1, SELF).mana(15).charge(10).cooldown(20).icon("cobblestone")
				.action(c -> {
					c.caster.addEffect(new MobEffectInstance(MagicEffects.STONESKIN, c.duration(1800), c.strong() ? 1 : 0), c.caster);
					Vec3 mid = Cast.center(c.caster);
					MagicFx.burst(c.level, mid, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 30, 0.35, 0.1);
					MagicFx.one(c.level, c.caster.position().add(0, 0.06, 0), MagicParticles.ring(STONE, 1.6F, 12), 0, 1, 0);
					MagicFx.sound(c.level, mid, MagicSounds.CAST_EARTH, 0.6F, 1.4F);
					return true;
				})
				.register();

		Spell.of("telekinesis", ALTERATION, 1, CHANNEL).mana(6).icon("lead").range(24)
				.channel((c, t) -> {
					Vec3 eye = c.eye();
					Vec3 look = c.look();
					Vec3 end = eye.add(look.scale(24));
					AABB box = new AABB(eye, end).inflate(3);
					int pulled = 0;
					for (Entity e : c.level.getEntities(c.caster, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
						Vec3 to = e.position().subtract(eye);
						double along = to.dot(look);
						if (along < 0 || along > 24 || to.subtract(look.scale(along)).lengthSqr() > 9) continue;
						Vec3 pull = eye.subtract(e.position()).normalize().scale(0.45);
						e.setDeltaMovement(pull);
						e.hurtMarked = true;
						if (e instanceof ItemEntity item) item.setNoPickUpDelay();
						if (t % 3 == 0) MagicFx.one(c.level, e.position().add(0, 0.2, 0), MagicParticles.spark(BLUE, 0.15F, 10), 0, 0, 0);
						pulled++;
					}
					// Небольших существ под прицелом тоже можно подтянуть.
					if (pulled == 0 && t % 2 == 0) {
						HitResult hit = c.aim(24, e -> e instanceof LivingEntity l && c.canHit(l) && l.getBbWidth() <= 1.0F && l.getMaxHealth() <= 30);
						if (hit instanceof EntityHitResult eh && !(eh.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
							Entity e = eh.getEntity();
							e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(eye.subtract(e.position()).normalize().scale(0.18)).add(0, 0.06, 0));
							e.hurtMarked = true;
						}
					}
				})
				.register();

		Spell.of("featherfall", ALTERATION, 2, SELF).mana(18).charge(10).cooldown(20).icon("feather")
				.action(c -> {
					int dur = c.duration(1200);
					c.caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0), c.caster);
					c.caster.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, dur, 0), c.caster);
					MagicFx.burst(c.level, Cast.center(c.caster), ParticleTypes.CLOUD, 12, 0.4, 0.02);
					MagicFx.burst(c.level, Cast.center(c.caster), MagicParticles.mote(0xFFFFFF, 0.12F, 24), 12, 0.4, 0.03);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.LOOP_WIND, 0.6F, 1.6F);
					return true;
				})
				.register();

		Spell.of("blink", ALTERATION, 2, SELF).mana(22).charge(8).cooldown(40).icon("chorus_fruit")
				.action(c -> {
					double dist = 10.0 * Math.sqrt(c.power);
					Vec3 eye = c.eye();
					Vec3 look = c.look();
					BlockHitResult hit = c.level.clip(new ClipContext(eye, eye.add(look.scale(dist)), ClipContext.Block.COLLIDER,
							ClipContext.Fluid.NONE, c.caster));
					Vec3 stop = hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation().subtract(look.scale(0.7));
					Vec3 feet = stop.subtract(0, c.caster.getEyeHeight(), 0);
					Vec3 dest = null;
					for (int back = 0; back < 8 && dest == null; back++) dest = c.safeSpot(feet.subtract(look.scale(back * 0.5)));
					if (dest == null || dest.distanceToSqr(c.caster.position()) < 1) return false;
					MagicFx.line(c.level, c.caster.position().add(0, 1, 0), dest.add(0, 1, 0), MagicParticles.spark(BLUE, 0.2F, 12), 0.35);
					Vec3 momentum = c.caster.getDeltaMovement();
					c.teleport(dest, BLUE);
					c.caster.setDeltaMovement(momentum);
					c.caster.hurtMarked = true;
					return true;
				})
				.register();

		Spell.of("deep_breath", ALTERATION, 3, SELF).mana(25).charge(14).cooldown(40).icon("heart_of_the_sea")
				.action(c -> {
					int dur = c.duration(3600);
					c.caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, dur, 0), c.caster);
					c.caster.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, dur, 0), c.caster);
					c.caster.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, dur, 0), c.caster);
					MagicFx.burst(c.level, Cast.center(c.caster), ParticleTypes.BUBBLE_POP, 20, 0.4, 0.05);
					MagicFx.burst(c.level, Cast.center(c.caster), MagicParticles.spark(0x5FD8FF, 0.2F, 18), 12, 0.4, 0.03);
					MagicFx.sound(c.level, c.caster.position(), MagicSounds.CAST_ARCANE, 0.8F, 1.2F);
					return true;
				})
				.register();

		Spell.of("paralyze", ALTERATION, 4, BOLT).mana(50).charge(22).cooldown(200).icon("cobweb").size(0.32F)
				.action(c -> {
					SpellProjectile.shoot(c, 1.6F);
					MagicFx.sound(c.level, c.hand(), MagicSounds.CAST_ARCANE, 0.9F, 1.5F);
					return true;
				})
				.hit((c, p, r) -> {
					if (r instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && c.canHit(t)) {
						if (!(t instanceof EnderDragon || t instanceof WitherBoss || t instanceof Warden)) {
							t.addEffect(new MobEffectInstance(MagicEffects.PARALYSIS, c.duration(100), 0), c.caster);
							MagicFx.one(c.level, t.position().add(0, 0.05, 0), MagicParticles.ring(BLUE, t.getBbWidth() + 0.6F, 20), 0, 1, 0);
						}
						c.hurt(t, c.amount(2.0F), MagicDamage.ARCANE, p);
					}
					Vec3 at = r.getLocation();
					MagicFx.burst(c.level, at, ParticleTypes.ELECTRIC_SPARK, 16, 0.25, 0.15);
					MagicFx.burst(c.level, at, MagicParticles.spark(BLUE, 0.4F, 12), 6, 0.15, 0.04);
					MagicFx.sound(c.level, at, MagicSounds.IMPACT_ARCANE, 0.8F, 1.0F);
				})
				.register();

		Spell.of("earthquake", ALTERATION, 5, RITUAL).mana(95).charge(40).cooldown(600).icon("heavy_core")
				.action(c -> {
					double r = c.radius(8.0);
					Vec3 center = c.caster.position();
					for (int wave = 0; wave < 3; wave++) {
						int w = wave;
						Zones.later(c, wave * 10 + 1, () -> quakeWave(c, center, r, w));
					}
					MagicFx.one(c.level, center.add(0, 0.06, 0), MagicParticles.rune(STONE, 2.5F, 40), 0, 1, 0);
					return true;
				})
				.register();
	}

	private static void quakeWave(Cast c, Vec3 center, double r, int wave) {
		for (LivingEntity e : c.enemiesAround(center.add(0, 1, 0), r)) {
			if (!e.onGround()) continue;
			c.hurt(e, c.amount(7.0F), MagicDamage.EARTH);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.duration(60), 2), c.caster);
			Cast.knock(e, center, 0.25, 0.55);
		}
		// Пыль от земли кольцами.
		for (int ring = 1; ring <= 4; ring++) {
			double rr = r * ring / 4.0;
			int points = (int) (rr * 5);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points + wave * 0.3;
				Vec3 p = center.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
				BlockState ground = c.level.getBlockState(BlockPos.containing(p).below());
				if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
				MagicFx.burst(c.level, p.add(0, 0.2, 0), new BlockParticleOption(ParticleTypes.BLOCK, ground), 3, 0.2, 0.15);
			}
		}
		MagicFx.one(c.level, center.add(0, 0.15, 0), MagicParticles.ring(STONE, (float) r, 14), 0, 1, 0);
		MagicFx.sound(c.level, center, MagicSounds.CAST_EARTH, 1.6F, 0.5F + wave * 0.1F);
		Magic.shake(c.level, center, 2.2F - wave * 0.4F, 16, r * 4);
	}
}
