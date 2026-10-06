package com.suchki.client.magic;

import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Звук каста: гул зарядки стихии идёт за магом и поднимается в тоне по мере накопления (на пределе — выше всего);
 * у потоков свой гул. Под заклинательную фразу (ранг 3+) — хор/шёпот. Пока строится круг — звонкие росчерки
 * (каждая шестая часть круга, всё выше), круг замкнулся — «готово»; выпуск тяжёлых и высших заклинаний — удар со
 * «шшингом». Чужие маги звучат тише, дальше 64 блоков — не звучат вовсе.
 */
public final class CastSounds {
	/** Сколько росчерков за время построения круга. */
	static final int DRAW_STEPS = 6;
	private static final double HEAR_RANGE = 64.0;

	/** Звуковое состояние одного мага. */
	private static final class Voice {
		Loop charge;
		Loop chant;
		Spell spell;
		int drawn;
	}

	private static final Map<Integer, Voice> VOICES = new HashMap<>();
	/** Когда в последний раз играл глитч (чтобы не трещал без конца). */
	private static long lastGlitch = -100;

	private CastSounds() {}

	static SoundEvent loopFor(Spell spell) {
		if (spell.style != CastStyle.CHANNEL) return MagicSounds.CHARGE.get(spell.school);
		return spell.loop != null ? spell.loop : MagicSounds.LOOP_BEAM;
	}

	/** Есть ли у заклинания фраза (в текущем языке). */
	static boolean hasChant(Spell spell) {
		if (!I18n.exists(spell.chantKey())) return false;
		return !I18n.get(spell.chantKey()).isBlank();
	}

	static void tick(ClientLevel level) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 me = mc.player != null ? mc.player.position() : null;
		for (AbstractClientPlayer p : level.players()) {
			boolean self = p == mc.player;
			Voice v = VOICES.get(p.getId());
			Pose pose = !self && me != null && p.position().distanceToSqr(me) > HEAR_RANGE * HEAR_RANGE ? null : CastAnim.compute(p, 0);
			if (pose != null && pose.casting()) {
				if (v == null) {
					v = new Voice();
					VOICES.put(p.getId(), v);
				}
				if (v.spell != pose.spell || v.charge == null || v.charge.isStopped()) {
					stopLoops(v);
					v.spell = pose.spell;
					v.drawn = 0;
					v.charge = new Loop(p, pose.spell, loopFor(pose.spell), false);
					mc.getSoundManager().play(v.charge);
					if (pose.spell.tier >= 3 && hasChant(pose.spell)) {
						v.chant = new Loop(p, pose.spell, MagicSounds.LOOP_CHANT, true);
						mc.getSoundManager().play(v.chant);
					}
				}
				// Росчерки круга: шесть звонов за построение, каждый выше прошлого.
				int step = Math.min(DRAW_STEPS, (int) Math.floor(pose.form * DRAW_STEPS + 1.0E-4F));
				if (step > v.drawn && step < DRAW_STEPS) {
					cue(p, MagicSounds.CIRCLE_DRAW, 0.32F + 0.03F * pose.spell.tier, 0.82F + 0.09F * step);
				}
				v.drawn = Math.max(v.drawn, step);
			} else if (v != null) {
				stopLoops(v);
				VOICES.remove(p.getId());
			}
		}
		VOICES.values().removeIf(v -> (v.charge == null || v.charge.isStopped()) && (v.chant == null || v.chant.isStopped()));
	}

	private static void stopLoops(Voice v) {
		if (v.charge != null) v.charge.end();
		if (v.chant != null) v.chant.end();
		v.charge = null;
		v.chant = null;
	}

	/** Короткий сигнал у руки (готово / ступень / пик / срыв). */
	static void cue(AbstractClientPlayer p, SoundEvent sound, float volume, float pitch) {
		Vec3 at = p.getEyePosition();
		boolean self = p == Minecraft.getInstance().player;
		p.level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, self ? volume : volume * 0.7F, pitch, false);
	}

	/** Круг замкнулся (пакет READY): звон замыкания поверх старого сигнала готовности. */
	static void ready(AbstractClientPlayer p, Spell spell) {
		cue(p, MagicSounds.CIRCLE_COMPLETE, 0.55F + 0.05F * spell.tier, 1.0F + 0.04F * (4 - spell.tier));
		cue(p, MagicSounds.READY, 0.3F, 1.5F);
	}

	/** Выпуск: у тяжёлых (4–5) — «шшинг» с ударом, у высших (6–7) — стена звука. Слышат все рядом. */
	static void release(AbstractClientPlayer p, Spell spell, float power) {
		if (spell.style == CastStyle.CHANNEL) return;
		if (spell.tier >= 6) {
			cue(p, MagicSounds.RELEASE_ULTIMATE, 1.0F, 1.0F);
		} else if (spell.tier >= 4) {
			cue(p, MagicSounds.RELEASE_HEAVY, 0.75F + 0.1F * (spell.tier - 4), 1.05F - 0.05F * (spell.tier - 4) + 0.03F * Math.max(0, power - 1));
		}
	}

	/** Глитч надписи у своего мага (не чаще раза в 5 тиков). */
	static void glitch(float strength) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		long now = mc.level.getGameTime();
		if (now - lastGlitch < 5) return;
		lastGlitch = now;
		float pitch = 0.85F + 0.4F * mc.level.random.nextFloat();
		cue(mc.player, MagicSounds.GLITCH, 0.18F + 0.25F * Math.min(1.0F, strength), pitch);
	}

	/** Гул (или хор фразы), привязанный к магу. Громкость нарастает за полсекунды и плавно гаснет после конца каста. */
	static final class Loop extends AbstractTickableSoundInstance {
		final AbstractClientPlayer player;
		final Spell spell;
		private final boolean chant;
		private final boolean self;
		private boolean ending;
		private float level;

		Loop(AbstractClientPlayer player, Spell spell, SoundEvent sound, boolean chant) {
			super(sound, SoundSource.PLAYERS, player.getRandom());
			this.player = player;
			this.spell = spell;
			this.chant = chant;
			this.self = player == Minecraft.getInstance().player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.pitch = chant ? 1.0F : 0.7F;
			this.x = player.getX();
			this.y = player.getY() + 1.2;
			this.z = player.getZ();
		}

		void end() {
			ending = true;
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
				if (chant) {
					// Хор вступает с первой строкой и густеет к концу фразы; у высоких рангов — громче и ниже.
					target = (0.18F + 0.32F * pose.form + 0.15F * pose.over) * (0.8F + 0.06F * spell.tier);
					pitch = 1.08F - 0.04F * spell.tier + 0.06F * pose.over;
				} else if (spell.style == CastStyle.CHANNEL) {
					target = 0.55F + 0.35F * pose.channel;
					pitch = 0.85F + 0.25F * pose.channel;
				} else {
					target = 0.35F + 0.25F * pose.form + 0.35F * pose.over;
					pitch = 0.6F + 0.45F * pose.form + 0.45F * pose.over + 0.15F * pose.hold
							+ 0.03F * Mth.sin(player.tickCount * (0.5F + pose.hold));
				}
			}
			if (!self) target *= 0.6F;
			level = Mth.approach(level, target, ending ? 0.08F : 0.06F);
			volume = Math.max(0.0F, level);
			if (ending && level <= 0.001F) stop();
		}
	}
}
