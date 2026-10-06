# FancyNpcs — форк GulPvP

Форк монорепы [FancyInnovations/FancyPlugins](https://github.com/FancyInnovations/FancyPlugins). Поддерживаем только плагин
**FancyNpcs** (`plugins/fancynpcs-v2`), в который встроен бывший аддон FancyNpcsModel:

- кастомные модели NPC через **BetterModel** (атрибут `custom_model`, действия `play_animation_once` / `play_animation_loop`);
- голограммы над NPC через **DecentHolograms 2.10+** (display-модуль);
- нативный детект ресурспака и фолбэк моделей для игроков без пака;
- **hover-scale**: NPC плавно увеличивается, когда игрок наводит на него прицел, и плавно возвращается, когда отводит —
  только для этого игрока (своими пакетами `minecraft:scale`, остальные ничего не видят).

Все интеграции опциональные: нет BetterModel или DecentHolograms — соответствующая фича тихо выключена, NPC работают как в апстриме.

## Hover-scale

Настройки в `plugins/FancyNpcs/integrations.yml`, применяются по `/fancynpcs reload`:

| Ключ | По умолчанию | Что делает |
|------|--------------|------------|
| `hover_scale.enabled` | `true` | Включить эффект |
| `hover_scale.factor` | `1.15` | Во сколько раз растёт NPC |
| `hover_scale.duration_ticks` | `6` | Длина анимации (тики) |
| `hover_scale.max_distance` | `6.0` | С какой дистанции наводка считается |
| `hover_scale.grow_curve` | `ease_out_cubic` | Кривая роста |
| `hover_scale.shrink_curve` | `ease_out_cubic` | Кривая возврата |

Пресеты кривых: `linear`, `ease_in_sine`, `ease_out_sine`, `ease_in_out_sine`, `ease_in_quad`, `ease_out_quad`,
`ease_in_out_quad`, `ease_in_cubic`, `ease_out_cubic`, `ease_in_out_cubic`, `ease_in_quart`, `ease_out_quart`,
`ease_in_out_quart`, `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo`, `ease_in_back`, `ease_out_back`,
`ease_in_out_back`, `ease_out_elastic`, `ease_out_bounce` (формулы easings.net). `ease_out_back` даёт «пружинку»
с перелётом, `ease_out_elastic` / `ease_out_bounce` — резиновые.

Поштучно на NPC (атрибуты, таб-комплит работает):

```
/npc attribute <npc> set hover_scale 1.3      # свой множитель
/npc attribute <npc> set hover_scale off      # выключить эффект для этого NPC
/npc attribute <npc> set hover_curve ease_out_back   # своя кривая (и на рост, и на возврат)
```

NPC с моделью BetterModel эффект пропускают: в BetterModel 3.5 нет per-player scale (масштаб модели общий на всех
зрителей), а подменять чужие пакеты — костыль. Анимация шагает с частотой тиков (20 раз в секунду) — клиент не
интерполирует атрибут scale, поэтому на длинных анимациях лёгкая ступенчатость возможна.

## Ветки

| Ветка | Что это |
|-------|---------|
| `main` | Зеркало апстрима. Руками не коммитим, только синк. |
| `gul`  | Наша рабочая ветка = апстрим + интеграции. Отсюда собираем прод. |

## Синк с апстримом

```bash
git fetch upstream
git checkout main && git merge --ff-only upstream/main && git push origin main
git checkout gul && git merge main
```

Конфликты могут быть только в точках встраивания (список ниже) — весь наш код лежит в отдельном пакете
`de.oliver.fancynpcs.integrations` и с апстримом не пересекается. После мёржа обязательно собрать (см. ниже):
если апстрим добавил новый `implementation_*` модуль, компилятор сам потребует реализовать `Npc#getBukkitEntity()`.

Если апстрим допилит Lamp-команды и выкинет Cloud (`commands/oldCommands`), наши Cloud-команды
(`integrations/commands/cloud`) удаляются вместе с ними — Lamp-версии уже есть (`integrations/commands/lamp`).

## Точки встраивания в файлы апстрима

Это вся поверхность мёрж-конфликтов. Меняешь что-то в файлах апстрима — допиши сюда.

1. `fn-v2-api/.../api/Npc.java` — `getBukkitEntity()` (вместо рефлексии к полю `npc`).
2. `implementation_*/.../Npc_*.java` — реализация `getBukkitEntity()`.
3. `fn-v2-api/.../api/events/NpcCreateEvent.java` — `getSource()`: из какого NPC сделана копия (`/npc copy`).
4. `fn-v2-api/.../api/events/NpcRemovedEvent.java` — новое событие «NPC удалён» (любой причиной, не только `/npc remove`).
5. `NpcManagerImpl#removeNpc` — вызов `NpcRemovedEvent`.
6. `commands/{oldCommands,lampCommands}/npc/CopyCMD.java` — передают источник копии в `NpcCreateEvent`.
7. `plugins/fancynpcs-v2/build.gradle.kts` — compileOnly BetterModel / DecentHolograms (jitpack, только группа DH) / netty,
   optional-зависимости в `paper-plugin.yml`, суффикс версии из `GUL_VERSION`.
8. `FancyNpcs.java` — создание `IntegrationsManager`, регистрация команд, выключение.
9. `commands/{oldCommands,lampCommands}/FancyNpcsCMD.java` — `/fancynpcs reload` перезагружает интеграции.
10. `src/main/resources/languages/default.yml` — блок сообщений интеграций в конце `messages:`.

## Версия

`<VERSION апстрима>-gul.<GUL_VERSION>`, например `2.12.1+379-gul.1`. При каждом нашем релизе поднимаем
`plugins/fancynpcs-v2/GUL_VERSION`. Файл `VERSION` не трогаем — его ведёт апстрим.

## Сборка

```bash
./gradlew :plugins:fancynpcs-v2:shadowJar
```

Jar: `plugins/fancynpcs-v2/build/libs/FancyNpcs-<версия>.jar`. Сам gradle должен работать на **JDK 25**
(`JAVA_HOME` → JDK 25): API-модуль апстрима собирается с `release = 25` без тулчейна, на JDK 21 падает
с `release version 25 not supported`. Тулчейн 21 для 1.21.x-модулей gradle подтянет сам.
Первая сборка долгая: paperweight скачивает и патчит dev-бандлы под каждую поддерживаемую версию MC
(DNS-таймауты к `piston-meta.mojang.com` лечатся просто перезапуском — готовое берётся из кэша).

Если сборка падает с `Could not find net.kyori:adventure-text-serializer-ansi:.` — в `~/.m2` лежит голый POM paper-api
без gradle-метаданных, а апстрим ставит `mavenLocal()` первым. Лечится init-скриптом, который прячет `io.papermc`
из локальных репозиториев:

```kotlin
// no-local-paper.init.gradle.kts
allprojects {
    repositories.withType<MavenArtifactRepository>().configureEach {
        if (url.scheme == "file") {
            content { excludeGroupAndSubgroups("io.papermc") }
        }
    }
}
```

```bash
./gradlew :plugins:fancynpcs-v2:shadowJar --init-script no-local-paper.init.gradle.kts
```

## Переезд с аддона FancyNpcsModel

1. Удалить `FancyNpcsModel-*.jar` из `plugins/` (если оставить — форк ругнётся в лог и пропустит миграцию).
2. Заменить `FancyNpcs-*.jar` на jar форка.
3. При первом старте форк сам перенесёт данные из `plugins/FancyNpcsModel/`:
   - `config.yml` → `plugins/FancyNpcs/integrations.yml` (ключи без префикса `settings.`);
   - `holograms/` → `plugins/FancyNpcs/holograms/`;
   - `data/` (verified-packs и старые голограммы) → `plugins/FancyNpcs/data/`;
   - сама папка переименуется в `FancyNpcsModel.migrated` (архив, можно удалить после проверки).
4. `npcs.yml` не меняется: атрибут `custom_model` и действия анимаций называются так же.

Права поменялись на неймспейс FancyNpcs:

| Было | Стало |
|------|-------|
| `fancynpcsmodel.command.npc.custom_model` | `fancynpcs.command.npc.custom_model` (+ `fancynpcs.command.npc.play_animation` для анимаций) |
| `fancynpcsmodel.command.npc.hologram` | `fancynpcs.command.npc.hologram` |
| `fancynpcsmodel.commands.fancynpcsmodel.holograms.reload` | `fancynpcs.command.fancynpcs.holograms` (`/fancynpcs holograms reload`) |
| `fancynpcsmodel.commands.fancynpcsmodel.resourcepack` | `fancynpcs.command.fancynpcs.resourcepack` (`/fancynpcs resourcepack <player>`) |

`/fancynpcsmodel version` и `/fancynpcsmodel config ...` больше нет: версия — `/fancynpcs version`, перезагрузка
конфига интеграций — `/fancynpcs reload`.
