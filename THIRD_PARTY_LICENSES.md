# Сторонний код в Suchki

Части Suchki основаны на коде других модов. Их лицензии разрешают это при сохранении уведомлений ниже.

## Clumps

- Источник: https://github.com/jaredlll08/Clumps (ветка 1.21.11)
- Где в Suchki: `com.suchki.mixin.ExperienceOrbMixin`, `com.suchki.mixin.ExperienceOrbAccessor`, `com.suchki.xp.ClumpedOrb`
- Изменения: убрана платформенная обвязка и события, добавлены радиус склейки и притяжение к игроку из конфига.

```
The MIT License (MIT)

Copyright (c) 2021 Jaredlll08

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AppleSkin

- Источник: https://github.com/squeek502/AppleSkin (ветка 1.21.11-fabric)
- Где в Suchki: пакет `com.suchki.appleskin`, текстуры `assets/appleskin/textures`
- Изменения: исходники переведены с маппингов Yarn на Mojang, настройки перенесены в config/suchki.json
  (без Cloth Config/Jankson), убраны интеграции с REI/JEI/Mod Menu, сетевые id перенесены в пространство `suchki`.
- Лицензия: Unlicense (общественное достояние) — указание авторства не требуется, оставлено из уважения.

## ClientSort (а в нём — Mouse Wheelie и Inventory Management)

- Источник: https://github.com/TerminalMC/ClientSort (ветка mc1.21.11)
- Где в Suchki: пакет `com.suchki.clientsort`, ресурсы `assets/clientsort`, `resourcepacks/clientsort-dark-mode`
- Изменения: пакет `dev.terminalmc.clientsort` → `com.suchki.clientsort`; убраны экран настроек через Cloth Config
  и интеграции с Mod Menu, EMI, SuperMartijn642's Core Lib; общие и Fabric-части сведены в один мод.
- Лицензия ClientSort: Apache License 2.0 — полный текст в `licenses/ClientSort-Apache-2.0.txt`.
- Внутри ClientSort есть код:
  - Mouse Wheelie — Copyright 2022 Siphalor, Apache License 2.0;
  - Inventory Management — Copyright (c) 2021 Evan Steinkerchner (Roundaround), MIT
    (текст: `assets/clientsort/license/inventory-management/LICENSE.txt`).
