package com.suchki.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.suchki.SuchkiConfig;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Общий «кинематографичный» слой магии. Он не меняет игровую механику: только строит многослойные печати,
 * школьные потоки частиц и кульминацию выпуска. Детализация автоматически падает от расстояния и в первом лице,
 * поэтому один и тот же эффект остаётся читаемым на слабом клиенте и не закрывает прицел самому магу.
 */
final class MagicSpectacle {
	private MagicSpectacle() {}

	static int detail(AbstractClientPlayer caster, AbstractClientPlayer viewer, boolean firstPerson) {
		int detail = SuchkiConfig.INSTANCE.magicVisualQuality();
		if (firstPerson) detail--;
		if (viewer != null) {
			double distance = caster.distanceToSqr(viewer);
			if (distance > 48 * 48) detail -= 2;
			else if (distance > 28 * 28) detail--;
		}
		return Math.max(0, detail);
	}

	/** Общий лимит частиц зарядки за тик: не даёт толпе магов перегрузить клиент. */
	static int particleBudget() {
		return switch (SuchkiConfig.INSTANCE.magicVisualQuality()) {
			case 0 -> 16;
			case 1 -> 40;
			case 2 -> 72;
			default -> 112;
		};
	}

	/** Частицы зарядки: общий вихрь плюс узнаваемое движение каждой школы. */
	static int chargeParticles(ClientLevel level, AbstractClientPlayer player, Pose pose, Vec3 hand, boolean firstPerson, int detail,
			int allowance) {
		if (allowance <= 0) return 0;
		Spell spell = pose.spell;
		RandomSource random = level.random;
		float energy = Mth.clamp(pose.form + pose.over * 0.8F + pose.hold * 0.4F, 0.1F, 2.5F);
		int count = detail == 0 ? 1 : 1 + detail + spell.tier / 2 + (int) (pose.over * 2.0F);
		if (firstPerson) count = Math.max(1, count / 2);
		count = Math.min(Math.min(10, count), allowance);
		float radius = (firstPerson ? 0.32F : 0.62F) + 0.12F * detail + 0.08F * pose.over;
		Vec3 up = new Vec3(0, 1, 0);

		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double height = random.nextDouble() - 0.5;
			Vec3 radial = new Vec3(Math.cos(angle), height * 0.8, Math.sin(angle)).normalize();
			Vec3 offset = radial.scale(radius * (0.45 + random.nextDouble() * 0.75));
			Vec3 velocity = schoolFlow(spell.school, radial, up, energy, i);
			Vec3 at = hand.add(offset);
			int color = (i & 1) == 0 ? spell.school.color : spell.school.light;
			float size = (0.045F + 0.012F * detail + 0.018F * pose.over) * (firstPerson ? 0.55F : 1.0F);
			level.addParticle(MagicParticles.mote(color, size, 9 + detail * 2), at.x, at.y, at.z,
					velocity.x, velocity.y, velocity.z);
		}

		int cadence = Math.max(2, 7 - detail);
		int used = count;
		if (used < allowance && player.tickCount % cadence == 0) {
			spawnSchoolAccent(level, player, pose, hand, firstPerson, detail);
			used++;
		}
		return used;
	}

	private static Vec3 schoolFlow(School school, Vec3 radial, Vec3 up, float energy, int index) {
		Vec3 tangent = radial.cross(up);
		return switch (school) {
			case DESTRUCTION -> radial.scale(-0.075 * energy).add(tangent.scale(0.045)).add(0, 0.035 * energy, 0);
			case RESTORATION -> radial.scale(-0.035).add(tangent.scale(0.075 * energy)).add(0, 0.055, 0);
			case LIGHT -> radial.scale(0.055 * energy).add(0, 0.025, 0);
			case DARK -> radial.scale(-0.13 * energy).add(tangent.scale(-0.025));
			case ALTERATION -> radial.scale(-0.055).add(tangent.scale((index & 1) == 0 ? 0.1 : -0.1));
			case ILLUSION -> tangent.scale((index & 1) == 0 ? 0.11 : -0.11).add(radial.scale(-0.035));
			case CONJURATION -> radial.scale(-0.065).add(tangent.scale(0.13 * energy)).add(0, 0.02, 0);
		};
	}

	private static void spawnSchoolAccent(ClientLevel level, AbstractClientPlayer player, Pose pose, Vec3 hand, boolean firstPerson,
			int detail) {
		Spell spell = pose.spell;
		RandomSource random = level.random;
		float size = (0.08F + 0.02F * detail) * (firstPerson ? 0.55F : 1.0F);
		if (spell.school == School.CONJURATION || pose.style == CastStyle.SUMMON) {
			Vec3 center = pose.style == CastStyle.SUMMON ? CastVisuals.summonSpot(player, 0) : hand;
			double angle = random.nextDouble() * Math.PI * 2.0;
			Vec3 at = center.add(Math.cos(angle) * (0.7 + pose.form * 0.5), 0.06, Math.sin(angle) * (0.7 + pose.form * 0.5));
			level.addParticle(MagicParticles.glyph(spell.school.color, size, 22), at.x, at.y, at.z, 0, 0.025, 0);
			return;
		}
		if (spell.school == School.RESTORATION || spell.school == School.LIGHT) {
			double angle = player.tickCount * 0.28 + random.nextDouble() * Math.PI;
			Vec3 at = player.position().add(Math.cos(angle) * 0.55, 0.15 + random.nextDouble() * 1.5, Math.sin(angle) * 0.55);
			level.addParticle(MagicParticles.glyph(spell.school.light, size, 24), at.x, at.y, at.z, 0, 0.035, 0);
			return;
		}
		Vec3 velocity = new Vec3((random.nextDouble() - 0.5) * 0.14, (random.nextDouble() - 0.25) * 0.12,
				(random.nextDouble() - 0.5) * 0.14);
		level.addParticle(MagicParticles.spark(spell.school.light, size * 1.35F, 10), hand.x, hand.y, hand.z,
				velocity.x, velocity.y, velocity.z);
	}

	/** Вспышка выпуска: несколько волн и школьный акцент, масштабированные рангом и накопленной силой. */
	static void releaseParticles(AbstractClientPlayer player, Spell spell, float power, Vec3 hand, Vec3 look, boolean firstPerson,
			int detail) {
		ClientLevel level = (ClientLevel) player.level();
		float strength = Mth.clamp(power, 1.0F, 2.5F);
		boolean ground = spell.style == CastStyle.SELF || spell.style == CastStyle.RITUAL;
		Vec3 origin = ground ? player.position().add(0, 0.08, 0) : hand;
		Vec3 normal = ground ? new Vec3(0, 1, 0) : look;
		float baseRadius = ground ? 1.6F + 0.35F * spell.tier : 0.7F + 0.16F * spell.tier;
		baseRadius *= 0.75F + 0.25F * strength;
		if (firstPerson && !ground) baseRadius *= 0.55F;

		level.addParticle(MagicParticles.spark(spell.school.light, 0.45F + 0.18F * strength, 6), hand.x, hand.y, hand.z, 0, 0, 0);
		int waves = 1 + (detail >= 2 ? 1 : 0) + (detail >= 3 && spell.tier >= 4 ? 1 : 0);
		for (int i = 0; i < waves; i++) {
			float radius = baseRadius * (1.0F + i * 0.28F);
			level.addParticle(MagicParticles.ring(i == waves - 1 ? spell.school.light : spell.school.color, radius, 9 + i * 3),
					origin.x, origin.y + i * 0.015, origin.z, normal.x, normal.y, normal.z);
		}
		if (detail >= 2) {
			level.addParticle(MagicParticles.rune(spell.school.color, baseRadius * 0.72F, 13 + spell.tier * 2),
					origin.x, origin.y + 0.025, origin.z, normal.x, normal.y, normal.z);
		}

		ParticleOptions accent = releaseAccent(spell.school);
		int count = Math.min(34, 8 + detail * 5 + spell.tier * 2 + Math.round((strength - 1.0F) * 6.0F));
		if (firstPerson) count = Math.max(5, count / 2);
		RandomSource random = level.random;
		for (int i = 0; i < count; i++) {
			Vec3 jitter = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.35, random.nextDouble() - 0.5);
			Vec3 velocity = (ground ? jitter.add(0, 0.5, 0) : jitter.add(look.scale(0.9))).normalize()
					.scale(0.08 + random.nextDouble() * 0.22 * strength);
			level.addParticle(i % 3 == 0 ? accent : MagicParticles.spark(spell.school.color, 0.09F, 11),
					origin.x, origin.y + (ground ? 0.06 : 0), origin.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private static ParticleOptions releaseAccent(School school) {
		return switch (school) {
			case DESTRUCTION -> ParticleTypes.FLAME;
			case RESTORATION -> ParticleTypes.HAPPY_VILLAGER;
			case LIGHT -> ParticleTypes.END_ROD;
			case DARK -> ParticleTypes.REVERSE_PORTAL;
			case ALTERATION -> ParticleTypes.ENCHANT;
			case ILLUSION -> ParticleTypes.WITCH;
			case CONJURATION -> ParticleTypes.PORTAL;
		};
	}

	/**
	 * Многослойная печать: два встречных диска, концентрические орбиты и отдельные глифы. На минимальной
	 * детализации остаётся один читаемый диск; дополнительные слои появляются только при запасе качества.
	 */
	static void sigil(PoseStack stack, MultiBufferSource buffers, Vec3 camera, Vec3 at, Vec3 normal, Spell spell,
			float radius, float spinDegrees, float alpha, float charge, float over, int detail) {
		if (alpha <= 0.01F || radius <= 0.01F) return;
		Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : normal.normalize();
		float pulse = 0.94F + 0.06F * Mth.sin(spinDegrees * 0.07F + spell.tier);
		float tierScale = 1.0F + 0.035F * Math.max(0, spell.tier - 1);
		float r = radius * pulse * tierScale;
		int color = spell.school.color;
		int light = spell.school.light;

		stack.pushPose();
		stack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		stack.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) n.x, (float) n.y, (float) n.z));
		layer(stack, buffers, MagicRender.RUNE, r, spinDegrees, color, alpha, 0.0F);
		if (detail >= 1) {
			layer(stack, buffers, MagicRender.RUNE_INNER, r * (0.72F + 0.08F * charge), -spinDegrees * 1.55F,
					MagicRender.lighten(color, 0.28F), alpha * 0.72F, 0.003F);
		}
		if (detail >= 2) {
			layer(stack, buffers, MagicRender.RING, r * (1.05F + 0.05F * over), spinDegrees * 0.22F, light,
					alpha * (0.42F + 0.16F * charge), 0.006F);
			drawGlyphOrbit(stack, buffers, spell, r * 1.02F, spinDegrees * 0.42F, alpha * 0.78F,
					4 + Math.min(3, spell.tier));
		}
		if (detail >= 3) {
			layer(stack, buffers, MagicRender.RING, r * (1.22F + 0.06F * charge), -spinDegrees * 0.16F,
					MagicRender.lighten(color, 0.5F), alpha * (0.24F + 0.18F * over), -0.004F);
			layer(stack, buffers, MagicRender.RUNE_INNER, r * 0.43F, spinDegrees * 2.1F, light,
					alpha * (0.34F + 0.2F * charge), 0.009F);
		}
		stack.popPose();
	}

	private static void layer(PoseStack stack, MultiBufferSource buffers, Identifier texture, float radius, float spinDegrees,
			int color, float alpha, float z) {
		stack.pushPose();
		stack.translate(0, 0, z);
		stack.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
		MagicRender.quad(buffers.getBuffer(MagicRender.glow(texture)), stack.last(), radius,
				MagicRender.argb(color, Mth.clamp(alpha, 0.0F, 1.0F)));
		stack.popPose();
	}

	private static void drawGlyphOrbit(PoseStack stack, MultiBufferSource buffers, Spell spell, float radius, float spinDegrees,
			float alpha, int count) {
		int offset = Math.floorMod(spell.id.hashCode(), MagicRender.GLYPHS.length);
		float size = Math.max(0.035F, radius * (0.07F + 0.006F * spell.tier));
		for (int i = 0; i < count; i++) {
			float angle = spinDegrees + 360.0F * i / count;
			stack.pushPose();
			stack.mulPose(Axis.ZP.rotationDegrees(angle));
			stack.translate(0, radius, 0.012F);
			stack.mulPose(Axis.ZP.rotationDegrees(180.0F - angle));
			Identifier glyph = MagicRender.GLYPHS[(offset + i * 3) % MagicRender.GLYPHS.length];
			MagicRender.quad(buffers.getBuffer(MagicRender.glow(glyph)), stack.last(), size,
					MagicRender.argb(i % 2 == 0 ? spell.school.light : spell.school.color, alpha));
			stack.popPose();
		}
	}
}
