# Постоянная подпись Android APK

Для первой пробной сборки secrets НЕ обязательны. Для выпуска, который можно обновлять без удаления приложения, нужен один и тот же постоянный ключ подписи.

**Если у вас уже есть ключ исходного приложения — используйте его.** Новый ключ не сделает обновление совместимым с APK, подписанным старым ключом. Потеря ключа может лишить возможности выпускать совместимые обновления. Сохраните резервную копию отдельно от репозитория. Ключи/пароли не отправляйте в чат и не коммитьте в Git.

## 1. Создайте ключ, если прежнего нет

Вариант через Android Studio: откройте каталог `android/` → Build → Generate Signed Bundle / APK → APK → Create new. Сохраните keystore **за пределами папки репозитория**, запишите alias и оба пароля. Не публикуйте keystore на GitHub.

Альтернатива через `keytool` из JDK 17, PowerShell на Windows:

```powershell
New-Item -ItemType Directory -Force "$HOME\.aleatorika-keys" | Out-Null
keytool -genkeypair -v -storetype JKS -keystore "$HOME\.aleatorika-keys\aleatorika-release.jks" -alias aleatorika -keyalg RSA -keysize 3072 -validity 10000
```

Пароли вводятся в приглашениях keytool. Если команда не найдена, установите JDK 17 и добавьте его bin в PATH либо используйте Android Studio. На вопрос о пароле ключа можно использовать тот же пароль, что у keystore, но в GitHub всё равно заполните оба поля.

Для macOS/Linux:

```bash
mkdir -p "$HOME/.aleatorika-keys"
keytool -genkeypair -v -storetype JKS -keystore "$HOME/.aleatorika-keys/aleatorika-release.jks" -alias aleatorika -keyalg RSA -keysize 3072 -validity 10000
```

## 2. Закодируйте keystore в Base64

Base64 — НЕ шифрование. Относитесь к результату как к самому приватному ключу.

PowerShell: команда скопирует значение в буфер обмена, а не в репозиторий:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.aleatorika-keys\aleatorika-release.jks")) | Set-Clipboard
```

Если используете собственный существующий ключ, замените путь. На Linux можно выполнить `base64 -w 0 /ПУТЬ/К/КЛЮЧУ.jks` и скопировать результат, не сохраняя его в Git. Не вставляйте Base64 в README, workflow или чат.

## 3. Добавьте четыре repository secrets

Только в Android-репозитории: **Settings → Secrets and variables → Actions → New repository secret**.

| Точное имя secret | Значение |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-содержимое файла keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Пароль хранилища keystore |
| `ANDROID_KEY_ALIAS` | Alias ключа, например `aleatorika` |
| `ANDROID_KEY_PASSWORD` | Пароль конкретного ключа, даже если совпадает с паролем хранилища |

Используйте **Secrets**, не Variables. Значения не должны попадать в файлы проекта. Не нужен отдельный токен GitHub: workflow использует встроенный GITHUB_TOKEN.

Подпись пропускается для pull request. Всё равно разрешайте изменения workflow и запуск кода с secrets только доверенным людям.

## 4. Проверьте и выпустите

Запустите **Actions → Android - build APK and release → Run workflow → main**. В Artifacts должен появиться `Aleatorika-1.0.apk` — без суффиксов test/unsigned. Workflow выполняет zipalign и проверку apksigner. После этого отправьте новый тег `v1.0.0` или следующий свободный: релиз создастся автоматически.

Не сравнивайте этот APK с тестовым по одному номеру версии: важны и версия, и сертификат подписи. Тестовый APK из прошлых сборок может требовать отдельного устройства/профиля для безопасной проверки без потери статистики.
