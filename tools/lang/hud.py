"""Тексты интерфейса мага: колесо заклинаний, всплывашка мультикаста, значки HUD.

Формат как у всех модулей tools/lang/: LANG = {"ключ": ("ru", "en")}; gen_magic.py сливает их в lang-файлы.
"""

LANG = {
    "suchki.wheel.no_multicast": ("Без мультикаста", "No multicast"),
    "suchki.wheel.locked": ("Фокус не выдерживает ранг %s", "Your focus can't handle rank %s"),
}
