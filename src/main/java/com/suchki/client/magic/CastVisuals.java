package com.suchki.client.magic;

import com.suchki.client.magic.CastAnim.Pose;
import com.suchki.magic.Anchor;
import com.suchki.magic.CastStyle;
import com.suchki.magic.CircleLayout;
import com.suchki.magic.FxNet;
import com.suchki.magic.MagicParticles;
import com.suchki.magic.School;
import com.suchki.magic.Spell;
import com.suchki.magic.Targeting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Каст на клиенте у всех колдующих игроков (до 160 блоков): магические круги строятся штрих за штрихом там, где
 * велит якорь заклинания — у посоха, под ногами, на земле у прицела (до 128 блоков), в небе над целью, рядом
 * от посоха к цели, веером за спиной, по орбите; копии мультикаста — по {@link CircleLayout}. Слияние рисует
 * по кругу на стихию. Поток с лучом — луч из каждого круга к цели. После выпуска круги вспыхивают и гаснут
 * на месте, а круги у цели, в небе и в линию ещё держатся — удар выходит из них.
 *
 * <p>Здесь же регистрируется приём эффектов мира ({@link FxNet}) и вспышка экрана; кадр собирается в
 * {@link CircleBatch} и сдаётся одним пакетом на тип отрисовки.
 */
public final class CastVisuals {
	/** Дальше этого чужие круги не рисуются. */
	public static final double MAX_DISTANCE = 160.0;
	private static final float TAU = (float) (Math.PI * 2);

	private CastVisuals() {}

	/** Круг, нарисованный у игрока в прошлом кадре (для призраков после выпуска и проверок). */
	public record Drawn(Vec3 center, Vec3 normal, Vec3 right, float radius, float progress, School school, List<School> combo, int tier,
			int variant, int color, int light, int layers, float gap, float underlay, Anchor anchor) {}

	/** Что видно у игрока сейчас: главный круг и сколько всего кругов. */
	public record Placement(Vec3 center, Vec3 normal, float radius, int circles, Anchor anchor, float progress) {}

	/** Состояние каста игрока между кадрами. */
	private static final class Live {
		boolean casting;
		Spell spell;
		float lastForm;
		float doneAt = -1;
		float lastTime;
		Targeting.Aim aim;
		List<Drawn> drawn = new ArrayList<>();
		Placement main;
		boolean channel;
	}

	private static final Map<Integer, Live> LIVE = new HashMap<>();
	private static CircleBatch batch;
	private static final CircleRenderer.Params P = new CircleRenderer.Params();

	static boolean firstPerson(AbstractClientPlayer p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON && mc.getCameraEntity() == p;
	}

	/** Где у игрока стоит главный круг (null — сейчас не колдует). */
	public static Placement lastPlacement(int entityId) {
		Live l = LIVE.get(entityId);
		return l == null || !l.casting ? null : l.main;
	}

	/** Сколько вершин магии сдано в прошлом кадре (для проверки нагрузки). */
	public static int lastFrameVertices() {
		return batch == null ? 0 : batch.lastVertices();
	}

	/** Все круги игрока в прошлом кадре. */
	public static List<Drawn> lastCircles(int entityId) {
		Live l = LIVE.get(entityId);
		return l == null || !l.casting ? List.of() : List.copyOf(l.drawn);
	}

	// ---------------------------------------------------------------- регистрация

	/** Приём кругов и эффектов мира, вспышка экрана, сброс при выходе из мира. */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FxNet.CircleFx.TYPE, (payload, ctx) -> CircleRenderer.receive(payload, CircleRenderer.now(0)));
		ClientPlayNetworking.registerGlobalReceiver(FxNet.WorldFx.TYPE, (payload, ctx) -> WorldFxRenderer.add(payload));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("suchki", "screen_flash"),
				WorldFxRenderer::renderFlash);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(CastVisuals::clear));
	}

	/** Убрать все круги и эффекты (выход из мира, тесты). */
	public static void clear() {
		LIVE.clear();
		CircleRenderer.clear();
		WorldFxRenderer.clear();
	}

	// ---------------------------------------------------------------- тик: прицел чужих игроков и частицы

	static void tick(ClientLevel level) {
		CircleRenderer.tick();
		WorldFxRenderer.tick(level);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		RandomSource rnd = level.random;
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayer p : level.players()) {
			seen.add(p.getId());
			if (p.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) continue;
			Pose pose = CastAnim.compute(p, 0);
			if (pose == null || !pose.casting()) continue;
			Spell spell = pose.spell;
			Live live = LIVE.computeIfAbsent(p.getId(), k -> new Live());
			// Свой прицел считается каждый кадр, чужой — раз в тик.
			if (p != mc.player && needsAim(spell, pose)) live.aim = Targeting.aim(level, p, spell.range);
			if (p.distanceToSqr(mc.player) > 48 * 48) continue;
			int color = spell.color(), light = spell.lightColor();
			boolean fp = firstPerson(p);
			Vec3 hand = CastAnim.handPos(p, pose, 0, fp);
			// Спираль искр, стягивающихся к руке: чем больше накоплено, тем гуще.
			int n = pose.style == CastStyle.CHANNEL ? 1 : 1 + (int) (2 * pose.form + 3 * pose.over + 2 * pose.hold);
			if (fp) n = (n + 1) / 2;
			float radius = fp ? 0.35F : 0.75F;
			for (int i = 0; i < n; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double b = (rnd.nextDouble() - 0.5) * Math.PI;
				Vec3 off = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(radius);
				Vec3 at = hand.add(off);
				Vec3 v = off.scale(-0.11).add(off.cross(new Vec3(0, 1, 0)).scale(0.04));
				float size = (0.05F + 0.03F * pose.over) * (fp ? 0.4F : 1.0F);
				level.addParticle(MagicParticles.mote(rnd.nextBoolean() ? color : light, size, 9), at.x, at.y, at.z, v.x, v.y, v.z);
			}
			// Над кругами на земле поднимаются искры (чем больше накоплено — тем больше).
			for (Drawn d : live.drawn) {
				if (d.underlay <= 0 || rnd.nextFloat() > 0.35F + 0.5F * pose.over) continue;
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = d.radius * Math.sqrt(rnd.nextDouble());
				level.addParticle(MagicParticles.mote(rnd.nextBoolean() ? d.color : d.light, 0.08F + 0.05F * pose.over, 20),
						d.center.x + Math.cos(a) * r, d.center.y + 0.05, d.center.z + Math.sin(a) * r, 0, 0.03 + 0.06 * pose.over, 0);
			}
			if (pose.hold > 0.3F && rnd.nextFloat() < pose.hold) {
				level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z,
						(rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
			}
		}
		LIVE.keySet().removeIf(id -> !seen.contains(id));
	}

	private static boolean needsAim(Spell spell, Pose pose) {
		return pose.anchor().targeted() || spell.beam;
	}

	/** Вспышка в руке при выпуске (видят все). */
	static void releaseBurst(AbstractClientPlayer p, Spell spell, float power) {
		ClientLevel level = (ClientLevel) p.level();
		Pose pose = new Pose();
		pose.spell = spell;
		pose.style = spell.style;
		pose.form = 1;
		pose.castArm = p.getMainArm();
		pose.staff = true;
		Vec3 hand = CastAnim.handPos(p, pose, 0, firstPerson(p));
		Vec3 look = p.getViewVector(1.0F);
		int color = spell.color(), light = spell.lightColor();
		level.addParticle(MagicParticles.spark(light, 0.5F + 0.25F * power, 5), hand.x, hand.y, hand.z, 0, 0, 0);
		if (spell.anchor == Anchor.HAND || spell.anchor == Anchor.LINE) {
			level.addParticle(MagicParticles.ring(color, 0.9F + 0.4F * power, 9), hand.x, hand.y, hand.z, look.x, look.y, look.z);
		} else if (spell.anchor == Anchor.FEET) {
			Vec3 feet = p.position().add(0, 0.08, 0);
			level.addParticle(MagicParticles.ring(color, 2.0F + 1.2F * power, 12), feet.x, feet.y, feet.z, 0, 1, 0);
		}
		for (int i = 0; i < 10; i++) {
			Vec3 v = look.scale(0.15).add((level.random.nextDouble() - 0.5) * 0.2, (level.random.nextDouble() - 0.5) * 0.2,
					(level.random.nextDouble() - 0.5) * 0.2);
			level.addParticle(MagicParticles.spark(color, 0.12F, 10), hand.x, hand.y, hand.z, v.x, v.y, v.z);
		}
	}

	// ---------------------------------------------------------------- кадр

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (batch == null) batch = new CircleBatch();
		CircleBatch b = batch;
		b.begin(mc.gameRenderer.getMainCamera(), CircleRenderer.now(pt), pt);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		for (AbstractClientPlayer p : mc.level.players()) {
			Live live = LIVE.get(p.getId());
			if (p.position().distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE) {
				if (live != null) live.casting = false;
				continue;
			}
			Pose pose = CastAnim.compute(p, pt);
			boolean casting = pose != null && pose.casting();
			if (casting) {
				if (live == null) {
					live = new Live();
					LIVE.put(p.getId(), live);
				}
				drawCasting(b, p, pose, pt, live);
			} else if (live != null && live.casting) {
				releaseGhosts(live, pose);
				live.casting = false;
				live.drawn.clear();
			}
			if (pose != null) handGlow(b, p, pose, pt);
		}
		CircleRenderer.renderWorld(b);
		WorldFxRenderer.render(b);
		b.flush(ctx.consumers(), ctx.matrices().last());
		CircleRenderer.endFrame();
	}

	/** Сияние руки/навершия. */
	private static void handGlow(CircleBatch b, AbstractClientPlayer p, Pose pose, float pt) {
		boolean fp = firstPerson(p);
		float fade = pose.casting() ? pose.weight : Math.max(0, 1 - pose.release * 1.6F);
		if (fade <= 0.01F) return;
		Vec3 hand = CastAnim.handPos(p, pose, pt, fp);
		int color = pose.spell.color(), light = pose.spell.lightColor();
		float g = pose.glow();
		float x = b.rx(hand.x), y = b.ry(hand.y), z = b.rz(hand.z);
		if (fp) {
			b.billboard(b.spark, x, y, z, 0.035F + 0.05F * g, 0, CircleRenderer.argb(light, 0.55F * fade));
			b.billboard(b.spark, x, y, z, 0.08F + 0.08F * g, 0, CircleRenderer.argb(color, 0.3F * fade));
		} else {
			b.billboard(b.spark, x, y, z, 0.1F + 0.14F * g, 0, CircleRenderer.argb(light, 0.75F * fade));
			b.billboard(b.spark, x, y, z, 0.22F + 0.24F * g, 0, CircleRenderer.argb(color, 0.35F * fade));
			if (pose.casting() && pose.over > 0.05F) {
				b.billboard(b.flare, x, y, z, 0.25F + 0.35F * pose.over, b.time * 0.05F, CircleRenderer.argb(light, 0.5F * pose.over * fade));
			}
		}
	}

	/** Круги одного колдующего игрока. */
	private static void drawCasting(CircleBatch b, AbstractClientPlayer p, Pose pose, float pt, Live live) {
		Minecraft mc = Minecraft.getInstance();
		Spell spell = pose.spell;
		boolean channel = spell.style == CastStyle.CHANNEL;
		// Новый каст (другое заклинание или время пошло заново) — сбросить отметку «дорисован».
		if (!live.casting || live.spell != spell || pose.time < live.lastTime - 0.5F) live.doneAt = -1;
		live.casting = true;
		live.spell = spell;
		live.channel = channel;
		live.lastTime = pose.time;
		live.lastForm = pose.form;
		live.drawn.clear();

		boolean fp = firstPerson(p);
		Vec3 hand = CastAnim.handPos(p, pose, pt, fp);
		Vec3 look = p.getViewVector(pt);
		Vec3 feet = p.getPosition(pt);
		Anchor anchor = pose.anchor();
		if (needsAim(spell, pose) && (p == mc.player || live.aim == null)) {
			live.aim = Targeting.aim(p.level(), p, p.getEyePosition(pt), look, spell.range, Targeting.living(p));
		}
		Vec3 point = live.aim != null ? live.aim.point() : p.getEyePosition(pt).add(look.scale(Math.min(spell.range, 24)));
		Vec3 ground = live.aim != null ? live.aim.ground() : point;

		// Прорисовка: у заряжаемых — формирование, у потоков — быстрый набросок за 8 тиков.
		float progress = channel ? Mth.clamp(pose.time / 8.0F, 0, 1) : pose.form;
		float drawTicks = channel ? 8 : pose.form > 0.001F && pose.form < 1 ? pose.time / pose.form : 20;
		if (progress >= 1 && live.doneAt < 0) live.doneAt = pose.time;
		float since = live.doneAt >= 0 ? pose.time - live.doneAt : -1;
		float power = channel ? pose.channel : pose.over;
		float tremble = channel ? 0 : pose.hold;
		float spin = b.time * 0.012F + (since > 0 ? since * (0.035F + 0.05F * power) : 0) + p.getId() * 0.7F;
		float alpha = Math.min(1, pose.weight * 1.3F);

		int copies = Math.max(1, pose.copies);
		CircleLayout.Placement main = CircleLayout.main(spell, hand, look, feet, point, ground);
		// «Вправо» — по повороту головы: определено и когда маг смотрит строго вверх или вниз (круг не дёргается).
		float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		int circles = 0;
		List<Vec3> origins = new ArrayList<>();
		switch (anchor) {
			case HAND -> {
				// От первого лица круг дальше и меньше, чуть к руке с посохом — чтобы не закрывать прицел.
				float k = fp ? 0.5F : 1;
				Vec3 c0 = main.center();
				if (fp) {
					float side = pose.castArm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
					c0 = p.getEyePosition(pt).add(look.scale(1.7)).add(right.scale(0.32 * side)).add(CircleLayout.up(look).scale(-0.12));
				}
				for (int i = 0; i < copies; i++) {
					Vec3 off = CircleLayout.copyOffset(spell, look, i, copies).scale(fp ? 0.55 : 1);
					float r = main.radius() * (copies > 1 ? 0.8F : 1) * k;
					Vec3 c = c0.add(off);
					circle(b, live, spell, pose, c, look, right, r, progress, drawTicks, since, power, tremble, spin + i, alpha * (fp ? 0.55F : 1),
							i, spell.layers, r * 0.5F, 0, anchor);
					origins.add(c);
					circles++;
				}
			}
			case FEET, TARGET, SKY -> {
				boolean sky = anchor == Anchor.SKY;
				for (int i = 0; i < copies; i++) {
					CircleLayout.Placement pl = CircleLayout.copy(spell, main, look, i, copies);
					float gap = sky ? -pl.radius() * 0.18F : pl.radius() * 0.22F;
					circle(b, live, spell, pose, pl.center(), pl.normal(), null, pl.radius(), progress, drawTicks, since, power, tremble, spin + i * 1.3F,
							alpha, i, spell.layers, Math.abs(gap), sky ? 0 : 1, anchor);
					origins.add(pl.center());
					circles++;
				}
			}
			case LINE -> {
				int layers = Math.max(2, spell.layers);
				for (int i = 0; i < copies; i++) {
					Vec3 off = CircleLayout.copyOffset(spell, look, i, copies);
					Vec3 from = hand.add(off), to = point.add(off);
					Vec3 dir = to.subtract(from);
					dir = dir.lengthSqr() < 1.0E-6 ? look : dir.normalize();
					Vec3 last = from;
					for (int k = 0; k < layers; k++) {
						Vec3 c = CircleLayout.lineStep(from, to, k, layers);
						float r = main.radius() * (0.6F + 0.75F * k / (layers - 1)) * (copies > 1 ? 0.8F : 1);
						float start = 0.45F * k / (layers - 1);
						float pk = Mth.clamp((progress - start) / 0.55F, 0, 1);
						float sk = since >= 0 ? since : -1;
						// Круги «ствола» складываются по линии взгляда — от первого лица их свет надо приглушить.
						float a = alpha * nearFade(b, c, r) * (fp ? 0.45F : 1);
						circle(b, live, spell, pose, c, dir, right, r, pk, drawTicks * 0.55F, k == layers - 1 ? sk : -1, power, tremble,
								spin * (k % 2 == 0 ? 1 : -1) + k, a, i * 7 + k, 1, 0, 0, anchor);
						last = c;
						circles++;
					}
					origins.add(last);
				}
			}
			case BACK -> {
				int count = Math.min(9, 2 + spell.tier);
				float spread = 1.4F + 0.32F * count;
				for (int i = 0; i < copies; i++) {
					Vec3 off = CircleLayout.copyOffset(spell, look, i, copies);
					for (int k = 0; k < count; k++) {
						Vec3 c = CircleLayout.backSlot(feet, look, k, count, spread).add(off);
						float r = main.radius() * 0.75F * (copies > 1 ? 0.8F : 1) * (0.85F + 0.15F * (float) Math.sin(k * 1.3F));
						float start = 0.4F * k / Math.max(1, count - 1);
						float pk = Mth.clamp((progress - start) / 0.6F, 0, 1);
						circle(b, live, spell, pose, c, look, right, r, pk, drawTicks * 0.6F, since, power, tremble, spin + k * 0.9F, alpha, i * 11 + k, 1,
								0, 0, anchor);
						origins.add(c);
						circles++;
					}
				}
			}
			case ORBIT -> {
				int count = Math.max(copies, 2 + spell.tier / 2);
				for (int k = 0; k < count; k++) {
					float ang = TAU * k / count + b.time * 0.05F;
					Vec3 radial = new Vec3(Math.cos(ang), 0, Math.sin(ang));
					Vec3 c = feet.add(0, 1.15 + 0.25 * Math.sin(ang * 2 + b.time * 0.08), 0).add(radial.scale(1.5 + 0.1 * spell.tier));
					float start = 0.4F * k / count;
					float pk = Mth.clamp((progress - start) / 0.6F, 0, 1);
					circle(b, live, spell, pose, c, radial, null, main.radius(), pk, drawTicks * 0.6F, since, power, tremble, spin + k, alpha, k, 1, 0, 0,
							anchor);
					origins.add(c);
					circles++;
				}
			}
		}

		// Небесный круг: тонкая нить света к точке удара.
		if (anchor == Anchor.SKY && progress > 0.3F) {
			for (Vec3 o : origins) {
				Vec3 g = new Vec3(o.x, ground.y, o.z);
				WorldFxRenderer.beamShape(b, o, g, 0.05F + 0.06F * power, 0, spell.color(), spell.lightColor(), b.time, 0.25F + 0.35F * power,
						1);
			}
		}

		// Поток с лучом: луч из каждого круга (или от руки) к цели.
		if (channel && spell.beam) {
			float w = (0.07F + 0.11F * pose.channel + 0.015F * spell.tier) * (fp ? 0.6F : 1);
			if (origins.isEmpty()) origins.add(hand);
			for (Vec3 o : origins) {
				WorldFxRenderer.beamShape(b, o, point, w, 0, spell.color(), spell.lightColor(), b.time, Math.min(1, pose.weight * 1.2F),
						1);
			}
		}

		Drawn first = live.drawn.isEmpty() ? null : live.drawn.get(0);
		live.main = first == null ? null : new Placement(first.center, first.normal, first.radius, circles, anchor, progress);
	}

	/** Круги у самой камеры (первый круг «ствола» от первого лица) не должны слепить. */
	private static float nearFade(CircleBatch b, Vec3 c, float r) {
		float dx = b.rx(c.x), dy = b.ry(c.y), dz = b.rz(c.z);
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		return Mth.clamp((d - 0.4F) / (0.8F + r), 0.15F, 1);
	}

	private static void circle(CircleBatch b, Live live, Spell spell, Pose pose, Vec3 center, Vec3 normal, Vec3 right, float radius, float progress,
			float drawTicks, float since, float power, float tremble, float spin, float alpha, int variant, int layers, float gap, float underlay,
			Anchor anchor) {
		CircleRenderer.Params c = P.reset().at(center, normal).right(right).style(spell.school, spell.tier, spell.color(), spell.lightColor());
		c.combo = spell.combo;
		c.radius = radius;
		c.variant = variant + spell.id.hashCode();
		c.progress = progress;
		c.drawTicks = drawTicks;
		c.sinceDone = progress >= 1 ? since : -1;
		c.power = power;
		c.tremble = tremble;
		c.spin = spin;
		c.alpha = alpha;
		c.layers = layers;
		c.layerGap = gap;
		c.underlay = underlay;
		CircleRenderer.draw(b, c);
		live.drawn.add(new Drawn(center, normal, right, radius, progress, spell.school, spell.combo, spell.tier, c.variant, spell.color(),
				spell.lightColor(), layers, gap, underlay, anchor));
	}

	/** Выпуск: круги вспыхивают и гаснут на месте; у цели, в небе и в линию ещё держатся. Срыв — круги рассыпаются. */
	private static void releaseGhosts(Live live, Pose pose) {
		boolean fizzle = pose != null ? pose.fizzle : !live.channel && live.lastForm < 1;
		float now = CircleRenderer.now(0);
		for (Drawn d : live.drawn) {
			if (d.progress <= 0.02F) continue;
			CircleRenderer.Inst in = new CircleRenderer.Inst();
			in.pos = d.center;
			in.normal = d.normal;
			in.right = d.right;
			in.radius = d.radius;
			in.school = d.school;
			in.combo = d.combo;
			in.tier = d.tier;
			in.color = d.color;
			in.light = d.light;
			in.layers = d.layers;
			in.gap = d.gap;
			in.underlay = d.underlay;
			in.seed = d.variant;
			in.ghost = true;
			in.fizzle = fizzle;
			in.start = now;
			in.draw = 0;
			boolean linger = d.anchor == Anchor.TARGET || d.anchor == Anchor.SKY || d.anchor == Anchor.LINE;
			if (fizzle) {
				in.hold = 0;
				in.fade = 7;
				in.fixedProgress = d.progress;
			} else if (live.channel) {
				in.hold = 0;
				in.fade = 6;
			} else {
				in.hold = linger ? 22 : 3;
				in.fade = linger ? 12 : 8;
			}
			// У слияния и многоярусных кругов свет и так складывается — вспышка слабее, чтобы не выгорало в белое.
			if (!d.combo.isEmpty() || d.layers > 2) in.flareAmount = 0.7F;
			if (live.channel && !fizzle) {
				in.alpha = 0.7F;
				in.flareAmount = 0;
			}
			CircleRenderer.add(in);
		}
	}
}
