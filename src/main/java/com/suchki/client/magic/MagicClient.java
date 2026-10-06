package com.suchki.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.CastStyle;
import com.suchki.magic.Magic;
import com.suchki.magic.MagicEntities;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.MagicSounds;
import com.suchki.magic.Spell;
import com.suchki.magic.Spells;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент магии: регистрация отрисовки, колесо заклинаний (R), мультикаст (G), события каста, звуки, камера, HUD,
 * заклинательная фраза.
 */
public final class MagicClient {
	public static KeyMapping wheelKey;
	/** Поза своего каста в прошлом тике — чтобы сразу показать выпуск, не дожидаясь сервера. */
	private static Pose lastLocal;

	private MagicClient() {}

	public static void register(KeyMapping.Category category) {
		MagicParticlesClient.register();
		EntityRendererRegistry.register(MagicEntities.SPELL_PROJECTILE, SpellProjectileRenderer::new);
		MagicHud.register();
		wheelKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.suchki.spell_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
		ClientPlayNetworking.registerGlobalReceiver(Magic.CastFx.TYPE, (payload, ctx) -> onCastFx(payload));
		ClientPlayNetworking.registerGlobalReceiver(Magic.Shake.TYPE, (payload, ctx) ->
				CastCamera.shake(payload.at(), payload.strength(), payload.duration()));
		WorldRenderEvents.AFTER_ENTITIES.register(CastVisuals::render);
		// Круги, эффекты в мире (пакеты FxNet) — регистрирует CastVisuals; фразы и клавиши — свои классы.
		CastVisuals.register();
		IncantationHud.register();
		MagicKeys.register(category);
		ClientTickEvents.END_CLIENT_TICK.register(MagicClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			lastLocal = null;
			return;
		}
		while (wheelKey.consumeClick()) {
			if (mc.screen == null && Magic.isMage(p)) mc.setScreen(new SpellWheelScreen(wheelKey));
		}
		if (mc.isPaused()) return;
		predictRelease(p);
		CastVisuals.tick(mc.level);
		CastSounds.tick(mc.level);
		MagicHud.tick();
		IncantationHud.tick(mc);
	}

	/** Отпустил ПКМ — показываем выпуск (или срыв) сразу; сервер потом уточнит силу. */
	private static void predictRelease(LocalPlayer p) {
		Pose now = CastAnim.compute(p, 0);
		Pose prev = lastLocal;
		lastLocal = now != null && now.casting() ? now : null;
		if (prev == null || (now != null && now.casting())) return;
		Spell spell = prev.spell;
		if (spell.style == CastStyle.CHANNEL) {
			CastAnim.onRelease(p, spell, 1.0F, false);
			return;
		}
		boolean fizzle = prev.form < 1;
		// Сила к моменту выпуска — по пределу фокуса, которым колдовали (у посоха ученика 1.75, у архимага 2.25…).
		float cap = 1.0F + prev.over * (prev.cap - 1.0F);
		CastAnim.onRelease(p, spell, fizzle ? 0 : cap, fizzle);
		if (!fizzle) {
			CastCamera.localRelease(cap, prev.castArm == HumanoidArm.RIGHT ? 1 : -1, spell.style, spell.tier);
			CastSounds.release(p, spell, cap);
			IncantationHud.onRelease(spell);
		} else {
			IncantationHud.onFizzle();
		}
	}

	private static void onCastFx(Magic.CastFx fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity e = mc.level.getEntity(fx.entity());
		Spell spell = Spells.get(fx.spell());
		if (!(e instanceof AbstractClientPlayer p) || spell == null) return;
		boolean self = p == mc.player;
		switch (fx.kind()) {
			case Magic.CastFx.READY -> {
				CastSounds.ready(p, spell);
				burst(p, spell, 0.35F, 6);
			}
			case Magic.CastFx.STAGE -> {
				float stage = Math.max(0, fx.power() - 1);
				CastSounds.cue(p, MagicSounds.STAGE, 0.5F, 1.0F + 0.35F * stage);
				burst(p, spell, 0.25F, 4);
			}
			case Magic.CastFx.PEAK -> {
				CastSounds.cue(p, MagicSounds.PEAK, 0.7F, 1.15F);
				burst(p, spell, 0.6F, 10);
			}
			case Magic.CastFx.RELEASE -> {
				// Свой выпуск уже предсказан (камера, звук, выкрик) — второй раз не повторяем.
				boolean fresh = !self || !CastAnim.releasedRecently(p, spell);
				CastAnim.onRelease(p, spell, Math.max(1.0F, fx.power()), false);
				if (spell.style != CastStyle.CHANNEL) CastVisuals.releaseBurst(p, spell, fx.power());
				if (fresh) CastSounds.release(p, spell, fx.power());
				if (self && fresh && spell.style != CastStyle.CHANNEL) {
					CastCamera.localRelease(fx.power(), p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1, spell.style, spell.tier);
					IncantationHud.onRelease(spell);
				}
			}
			case Magic.CastFx.FIZZLE -> {
				CastAnim.onRelease(p, spell, 0, true);
				CastSounds.cue(p, MagicSounds.FIZZLE, 0.6F, 1.0F);
				if (self) IncantationHud.onFizzle();
			}
			default -> {
			}
		}
	}

	/** Искры у руки (готово, ступень, пик). */
	private static void burst(AbstractClientPlayer p, Spell spell, float size, int count) {
		Pose pose = CastAnim.compute(p, 0);
		if (pose == null) return;
		Vec3 hand = CastAnim.handPos(p, pose, 0, CastVisuals.firstPerson(p));
		var level = p.level();
		level.addParticle(MagicParticles.spark(spell.school.light, size, 5), hand.x, hand.y, hand.z, 0, 0, 0);
		for (int i = 0; i < count; i++) {
			double vx = (level.random.nextDouble() - 0.5) * 0.15, vy = (level.random.nextDouble() - 0.3) * 0.15,
					vz = (level.random.nextDouble() - 0.5) * 0.15;
			level.addParticle(MagicParticles.mote(spell.school.color, 0.08F, 12), hand.x, hand.y, hand.z, vx, vy, vz);
		}
	}
}
