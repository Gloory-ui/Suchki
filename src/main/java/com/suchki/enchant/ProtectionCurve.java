package com.suchki.enchant;

import com.suchki.SuchkiConfig;

/** Снижение урона от очков защитных чар (сумма по всей броне). */
public final class ProtectionCurve {
	/** Ванильный потолок очков: 20 очков = 80%. */
	public static final float VANILLA_CAP = 20.0F;

	private ProtectionCurve() {}

	/**
	 * До 20 очков — ванильные 4% за очко. Сверх них оставшиеся 20% делятся пополам каждые
	 * {@link SuchkiConfig#protectionHalfStep} очков: 40 очков ≈ 88%, 80 очков ≈ 96%.
	 */
	public static double reduction(double points) {
		if (points <= VANILLA_CAP) return Math.max(0.0, points) / 25.0;
		double over = points - VANILLA_CAP;
		return 0.8 + 0.2 * (1.0 - Math.pow(0.5, over / SuchkiConfig.INSTANCE.protectionHalfStep));
	}
}
