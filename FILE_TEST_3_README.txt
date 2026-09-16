YAMP FILE TEST 3 — временная диагностическая сборка

Цель: отделить проблему сборки/установки APK от HTML→Android моста.

После запуска ОБЯЗАТЕЛЬНО должна быть видна красная верхняя панель:
YAMP ANDROID SHELL — FILE TEST 3
и кнопка TEST FILE PICKER.

TEST FILE PICKER вызывает openBackupDocument() напрямую из Kotlin, без HTML и JavaScript.

Интерпретация:
1) Нет красной панели -> установлен/собран не этот Android-код.
2) Панель есть, TEST FILE PICKER открывает проводник -> Android/SAF исправны; неисправен путь HTML -> Android.
3) Панель есть, Toast появляется, но проводник не открывается -> проблема непосредственно Android picker/Activity.

Также versionCode=3, versionName=0.1-file-test3. GitHub Actions artifact: YAMP-FILE-TEST-3.
Это временная диагностика; после локализации проблемы панель удалить.
