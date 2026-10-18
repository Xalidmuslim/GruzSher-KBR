# ГрузШер КБР — reference preview, приёмка не завершена

## Что передано
- Ветка `feature/gruzsher-reference-accurate-premium`, база `2362dbffd13bc3ae005c44962bd92423d8a38c3e` (`feature/gruzsher-v3-4-native-auto-onboarding`).
- Исходники всех основных экранов на Jetpack Compose и оригинальные21 изображения: по6 ракурсов ГАЗелиNEXT, FordTransit, LADA Largus, LIVE-баннер и2 учебных документа.
- `ASSET_MANIFEST.md` и `generation/*.json`: происхождение, промпты, размеры и пути. Референсы не использованы как растровый интерфейс/вырезанные элементы.
- Проверенная по подписи debug APK: `/workspace/artifacts/GruzSher-KBR-reference-preview.apk`.
- Тестовая APK: `/workspace/artifacts/GruzSher-KBR-reference-tests.apk`.
- Изображения и provenance: `/workspace/artifacts/GruzSher-original-generated-assets.zip`.
- Исходный commit APK: `d7d63ac` (последующие коммиты добавляют документацию).

## Реализовано в исходниках
Общая визуальная система (зелёный, тёплый фон, мята, лаванда), каталог с фильтрами и картой/списком, синхронизация маркеров и карточек, фотографические карточки трёх моделей. Галерея6 фото каждой машины, полноэкранный просмотр и масштабирование, избранное и системный share. Карточки автопроверки учебных образцов,3 отказа/retry. Фотоосмотр4 сторон с просмотром и текстовым актом повреждения. Активный маршрут, ожидание/продление, сохраняемые тексты поддержки/происшествия. Фото/проверка салона и кузова перед виртуальным закрытием. Учебный чек с фиксированным временем/тарифом и оценкой. Архив чеков, отмена до начала движения, история, профиль, сохраняемые настройки и локальный журнал обращений.

Root Back сохраняет аренду, вкладки сохраняют позицию/фильтры/карту; архив чеков отделён от текущей сессии. Фактические тарифы каталога сохранены; исправлен суточный предел LIVE-расчёта. Существующие ключи SharedPreferences и миграция старого APPROVAL сохранены. Backend/API не изменены.

## Проверено
- `cd android && ./gradlew --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest`: успешно.
- Android JVM: **46 тестов, 0 failures/errors/skipped**, включая полный логический цикл для всех3 моделей, отказы документов, таймер, ожидание, продление, возврат, чек и рейтинг.
- Backend: **26 passed**, предупреждение deprecation Starlette/httpx; `node --check backend/app/admin.js` успешно.
- APK signature: `apksigner verify` успешно. SHA256 — `verification/APK-SHA256SUMS.txt`.
- Свежий независимый review нашёл3 ошибки архивной навигации/отмены; исходники исправлены. Реальная проверка этих маршрутов ещё не выполнена.

## Блокеры и непроверенное
**Это не финальная версия с подтверждённым совпадением дизайна.**

1. В облачном хосте нет `/dev/kvm`; `emulator -accel-check`: `KVM requires a CPU that supports vmx or svm`. Две попытки software emulator: первая загрузка ~447с, затем падение системных служб; повтор с увеличенными timeout/памятью не дал работающей установки приложения. Android installer: `Can't find service: package`, ранее `Broken pipe`. Instrumented runner **не выполнил ни одного теста** (tests=0), остановившись на установке APK.
2. OSM tile host отвечает CONNECT403. Домены `tile.openstreetmap.org`, `a/b/c.tile.openstreetmap.org` сохранены в черновике сети. Требуется сохранить настройки для применения; сохранение черновика само по себе не меняет runtime.
3. Нет реальных screenshots приложения, пар сравнения/diff и итеративной визуальной приёмки. Также не проверены fontScale1.3, маленький экран, landscape, IME, process death и Android back на APK. Точные оставшиеся визуальные расхождения установить без этих проверок нельзя.
4. Настройка языка сохраняется, но сами учебные сценарии пока русскоязычные; полноценная английская локализация не заявлена.

Таблица всех10 экранов: `REFERENCE_AUDIT.md`. Неполные сравнения явно помечены, изображения actual.png не подменены превью.

## Установка и продолжение приёмки
Отдельный package `ru.kbr.cargoshare.referencepreview`, versionName3.5.0-reference-preview, minSDK26, targetSDK35. Стабильную установленную версию APK не заменяет; её данные не удаляются. Данные другого package автоматически не импортируются.

```bash
adb install -r /workspace/artifacts/GruzSher-KBR-reference-preview.apk
adb shell am start -n ru.kbr.cargoshare.referencepreview/ru.kbr.cargoshare.MainActivity
cd /workspace/GruzSher-KBR/android
./gradlew connectedDebugAndroidTest
adb pull /sdcard/Android/data/ru.kbr.cargoshare.referencepreview/files/acceptance /workspace/gruzsher-runtime/acceptance-screens
cd ..
python3 tools/compare_references.py /workspace/gruzsher-runtime/acceptance-screens
```

Для продолжающего агента: сначала получить рабочий Android runtime и применить доступ к OSM. Затем выполнить инструментальные тесты, реально пройти поддержку/инциденты/ошибки, проверить сохранение после kill/rotation и Back, снять все10 экранов, открыть side-by-side, исправить расхождения, пересобрать/переснять до визуальной приёмки. Не считать существующий green build завершением ТЗ.

Настоящие SMS, OCR/KYC, банковские списания, замки, GPS грузовиков, серверная выдача и сообщения оператору по-прежнему не подключены. Все эти действия — честно маркированное локальное демо.
