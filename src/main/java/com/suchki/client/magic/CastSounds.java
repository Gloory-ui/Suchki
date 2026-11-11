package com.suchki.client.magic;

import com.suchki.SuchkiConfig;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Звук каста: гул зарядки идёт за магом и поднимается в тоне по мере накопления (на пределе — выше всего);
 * у потоков свой гул (рёв пламени, жужжание луча, шёпот тьмы). Плюс сигналы готовности, ступеней, пика и срыва.
 */
public final class CastSounds {
	private static final Map<Integer, Loop> LOOPS = new HashMap<>();
	private static final Map<Integer, Loop> RESONANCE = new HashMap<>();

	private CastSounds() {}

	static SoundEvent loopFor(Spell spell) {
		if (spell.style != CastStyle.CHANNEL) return MagicSounds.CHARGE.get(spell.school);
		return switch (spell.id) {
			case "flames" -> MagicSounds.LOOP_FLAMES;
			case "drain_life" -> MagicSounds.LOOP_DRAIN;
			case "healing" -> MagicSounds.LOOP_HEAL;
			case "ward" -> MagicSounds.LOOP_WARD;
			case "telekinesis" -> MagicSounds.LOOP_WIND;
			default -> MagicSounds.LOOP_BEAM;
		};
	}

	static void tick(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		for (AbstractClientPlayer p : level.players()) {
			Pose pose = CastAnim.compute(p, 0);
			Loop loop = LOOPS.get(p.getId());
			if (pose != null && pose.casting()) {
				if (loop == null || loop.isStopped() || loop.spell != pose.spell) {
					if (loop != null) loop.end();
					Loop fresh = new Loop(p, pose.spell);
					LOOPS.put(p.getId(), fresh);
					mc.getSoundManager().play(fresh);
				}
			} else if (loop != null) {
				loop.end();
				LOOPS.remove(p.getId());
			}
			Loop resonance = RESONANCE.get(p.getId());
			boolean resonant = pose != null && pose.casting() && pose.spell.tier >= 3 && pose.style != CastStyle.CHANNEL;
			if (resonant) {
				SoundEvent sound = pose.spell.tier >= 5 ? MagicSounds.RESONANCE_HIGH : MagicSounds.RESONANCE_LOW;
				if (resonance == null || resonance.isStopped() || resonance.spell != pose.spell || resonance.event != sound) {
					if (resonance != null) resonance.end();
					Loop fresh = new Loop(p, pose.spell, sound, true);
					RESONANCE.put(p.getId(), fresh);
					mc.getSoundManager().play(fresh);
				}
			} else if (resonance != null) {
				resonance.end();
				RESONANCE.remove(p.getId());
			}
		}
		LOOPS.values().removeIf(Loop::isStopped);
		RESONANCE.values().removeIf(Loop::isStopped);
	}

	/** Остановить и забыть клиентские петли при выходе из мира или смене сервера. */
	static void stopAll() {
		LOOPS.values().forEach(Loop::stopNow);
		RESONANCE.values().forEach(Loop::stopNow);
		LOOPS.clear();
		RESONANCE.clear();
	}

	/** Короткий сигнал у руки (готово / ступень / пик / срыв). */
	static void cue(AbstractClientPlayer p, SoundEvent sound, float volume, float pitch) {
		Vec3 at = p.getEyePosition();
		boolean self = p == Minecraft.getInstance().player;
		float scale = SuchkiConfig.INSTANCE.magicSoundScale();
		p.level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, (self ? volume : volume * 0.7F) * scale, pitch, false);
	}

	/** Дополнительная кульминация для старших кругов и сильно накопленных заклинаний. */
	static void release(AbstractClientPlayer p, Spell spell, float power) {
		if (spell.tier < 3 && power < 1.5F) return;
		boolean mythic = spell.tier >= 5 || power >= 2.0F;
		float volume = mythic ? 0.82F : 0.58F;
		float pitch = mythic ? 0.92F + 0.04F * Math.min(2.5F, power) : 1.02F + 0.05F * Math.min(2.0F, power);
		cue(p, mythic ? MagicSounds.RELEASE_MYTHIC : MagicSounds.RELEASE_POWER, volume, pitch);
	}

	/** Гул, привязанный к магу. Громкость нарастает за полсекунды и плавно гаснет после конца каста. */
	static final class Loop extends AbstractTickableSoundInstance {
		final AbstractClientPlayer player;
		final Spell spell;
		/** Событие звука; отдельное имя не скрывает поле {@code Sound sound} базового класса. */
		final SoundEvent event;
		final boolean resonance;
		private boolean ending;
		private float level;

		Loop(AbstractClientPlayer player, Spell spell) {
			this(player, spell, loopFor(spell), false);
		}

		Loop(AbstractClientPlayer player, Spell spell, SoundEvent sound, boolean resonance) {
			super(sound, SoundSource.PLAYERS, player.getRandom());
			this.player = player;
			this.spell = spell;
			this.event = sound;
			this.resonance = resonance;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.pitch = 0.7F;
			this.x = player.getX();
			this.y = player.getY() + 1.2;
			this.z = player.getZ();
		}

		void end() {
			ending = true;
		}

		void stopNow() {
			stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (player.isRemoved()) {
				stop();
				return;
			}
			x = player.getX();
			y = player.getY() + 1.2;
			z = player.getZ();
			Pose pose = CastAnim.compute(player, 0);
			float target = 0.0F;
			if (!ending && pose != null && pose.casting()) {
				if (resonance) {
					float rank = Mth.clamp((spell.tier - 2) / 3.0F, 0.0F, 1.0F);
					target = (0.06F + 0.08F * rank) * (0.25F + 0.75F * pose.form) + 0.08F * pose.over;
					pitch = 0.72F + 0.08F * rank + 0.16F * pose.form + 0.12F * pose.over;
				} else if (spell.style == CastStyle.CHANNEL) {
					target = 0.55F + 0.35F * pose.channel;
					pitch = 0.85F + 0.25F * pose.channel;
				} else {
					target = 0.35F + 0.25F * pose.form + 0.35F * pose.over;
					pitch = 0.6F + 0.45F * pose.form + 0.45F * pose.over + 0.15F * pose.hold
							+ 0.03F * Mth.sin(player.tickCount * (0.5F + pose.hold));
				}
			}
			target *= SuchkiConfig.INSTANCE.magicSoundScale();
			level = Mth.approach(level, target, ending ? 0.08F : 0.06F);
			volume = Math.max(0.0F, level);
			if (ending && level <= 0.001F) stop();
		}
	}
}
