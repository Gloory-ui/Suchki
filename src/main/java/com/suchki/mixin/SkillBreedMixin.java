package com.suchki.mixin;

import com.suchki.skill.Perk;
import com.suchki.skill.Skills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Фермер: опыт за разведение и шанс двойняшек. */
@Mixin(Animal.class)
public abstract class SkillBreedMixin {
	@Unique private static final ThreadLocal<ServerPlayer> SUCHKI_BREEDER = new ThreadLocal<>();

	@Inject(method = "spawnChildFromBreeding", at = @At("HEAD"))
	private void suchki$rememberBreeder(ServerLevel level, Animal partner, CallbackInfo ci) {
		ServerPlayer cause = ((Animal) (Object) this).getLoveCause();
		SUCHKI_BREEDER.set(cause != null ? cause : partner.getLoveCause());
	}

	@Inject(method = "spawnChildFromBreeding", at = @At("TAIL"))
	private void suchki$twins(ServerLevel level, Animal partner, CallbackInfo ci) {
		ServerPlayer player = SUCHKI_BREEDER.get();
		SUCHKI_BREEDER.remove();
		if (player == null) return;
		Skills.addXp(player, "farmer", 5);
		if (!Skills.roll(player, Perk.TWIN_BREED)) return;
		Animal self = (Animal) (Object) this;
		AgeableMob twin = self.getBreedOffspring(level, partner);
		if (twin == null) return;
		twin.setBaby(true);
		twin.snapTo(self.getX(), self.getY(), self.getZ(), 0.0F, 0.0F);
		level.addFreshEntityWithPassengers(twin);
	}
}
