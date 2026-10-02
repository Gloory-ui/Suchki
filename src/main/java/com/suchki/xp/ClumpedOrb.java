package com.suchki.xp;

import java.util.Map;

/** Шар опыта, в котором склеено несколько шаров разной ценности (основано на Clumps, MIT). */
public interface ClumpedOrb {
	Map<Integer, Integer> suchki$getClumped();

	void suchki$setClumped(Map<Integer, Integer> map);
}
