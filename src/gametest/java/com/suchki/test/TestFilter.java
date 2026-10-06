package com.suchki.test;

import java.util.Arrays;
import java.util.Locale;

/**
 * Какие клиентские тесты запускать: переменная окружения SUCHKI_TESTS — список через запятую
 * (ui, circles, hud, spells). Не задана — запускаются все.
 * Пример: SUCHKI_TESTS=circles xvfb-run -a bash ./gradlew runClientGameTest
 */
final class TestFilter {
	private TestFilter() {}

	static boolean enabled(String name) {
		String env = System.getenv("SUCHKI_TESTS");
		if (env == null || env.isBlank()) return true;
		return Arrays.stream(env.toLowerCase(Locale.ROOT).split(",")).map(String::trim).anyMatch(name::equals);
	}
}
