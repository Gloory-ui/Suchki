package com.suchki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Настройки мода: config/suchki.json. Создаётся при первом запуске со значениями по умолчанию,
 * недостающие поля дописываются при следующем запуске.
 */
public class SuchkiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("suchki.json");

	public static SuchkiConfig INSTANCE = new SuchkiConfig();

	/** Новые максимальные уровни (только через наковальню). Ключ — id зачарования. */
	public Map<String, Integer> maxLevels = defaultMaxLevels();

	// --- Перки высшего уровня ---
	public int fortuneGemLevel = 5;
	/** Шанс дополнительного самоцвета из руды при Удаче на максимуме. */
	public double fortuneGemChance = 0.05;
	public int lootingHeadLevel = 5;
	/** Шанс выпадения головы моба при Добыче на максимуме. */
	public double lootingHeadChance = 0.10;
	public int unbreakingSaveLevel = 10;
	public int featherFallingPerkLevel = 8;
	/** Высота (в блоках), падение ниже которой не наносит урона при Невесомости на максимуме. */
	public double featherFallingSafeHeight = 10.0;
	public int protectionPerkLevel = 10;
	/** Снижение урона за каждый предмет брони с Защитой на максимуме (4 предмета = 10%). */
	public double protectionPerkPerPiece = 0.025;
	/** Шёлковое касание добывает спавнеры. */
	public boolean silkTouchSpawners = true;

	// --- Наковальня ---
	/** Потолок цены на наковальне в уровнях опыта (вместо «Слишком дорого!»). */
	public int anvilMaxCost = 30;
	/** На сколько растёт штраф за повторную работу (в ванили — удвоение). */
	public int anvilPenaltyStep = 1;

	// --- Зелья ---
	/** Сколько исходных зелий можно смешать в одном. */
	public int maxPotionsInMix = 3;

	private static Map<String, Integer> defaultMaxLevels() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("minecraft:sharpness", 10);
		m.put("minecraft:smite", 10);
		m.put("minecraft:bane_of_arthropods", 10);
		m.put("minecraft:protection", 10);
		m.put("minecraft:fire_protection", 10);
		m.put("minecraft:blast_protection", 10);
		m.put("minecraft:projectile_protection", 10);
		m.put("minecraft:efficiency", 10);
		m.put("minecraft:unbreaking", 10);
		m.put("minecraft:fortune", 5);
		m.put("minecraft:looting", 5);
		m.put("minecraft:power", 10);
		m.put("minecraft:feather_falling", 8);
		m.put("minecraft:fire_aspect", 5);
		m.put("minecraft:respiration", 5);
		m.put("minecraft:depth_strider", 5);
		m.put("minecraft:impaling", 6);
		m.put("minecraft:riptide", 6);
		return m;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader r = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					SuchkiConfig loaded = GSON.fromJson(r, SuchkiConfig.class);
					if (loaded != null) INSTANCE = loaded;
				}
			}
			if (INSTANCE.maxLevels == null) INSTANCE.maxLevels = defaultMaxLevels();
			save();
		} catch (IOException | RuntimeException e) {
			Suchki.LOGGER.error("Не удалось прочитать {}, использую значения по умолчанию", PATH, e);
			INSTANCE = new SuchkiConfig();
		}
	}

	public static void save() throws IOException {
		Files.createDirectories(PATH.getParent());
		try (Writer w = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
			GSON.toJson(INSTANCE, w);
		}
	}
}
