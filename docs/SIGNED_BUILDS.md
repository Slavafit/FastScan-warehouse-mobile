# Подписанные сборки FastScan

GitHub Actions автоматически собирает подписанные APK и AAB после каждого
изменения ветки `main`. Ключ подписи и пароли хранятся только в GitHub Secrets.

## 1. Создание постоянного ключа

В Android Studio откройте:

```text
Build → Generate Signed Bundle / APK → Android App Bundle → Create new
```

Создайте ключ вне папки проекта, например:

```text
C:\AndroidKeys\fastscan-upload.jks
```

Рекомендуемые параметры:

```text
Alias: fastscan_upload
Validity: не менее 25 лет
```

Сохраните файл, alias и оба пароля минимум в двух защищённых местах. Не
добавляйте `.jks` в Git и не отправляйте его в чат.

## 2. Преобразование ключа в Base64

В PowerShell выполните команду, заменив путь на свой:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\AndroidKeys\fastscan-upload.jks")) | Set-Clipboard
```

Строка будет скопирована в буфер обмена и не появится на экране.

## 3. GitHub Secrets

Откройте репозиторий:

```text
Settings → Secrets and variables → Actions → New repository secret
```

Создайте четыре секрета:

```text
FASTSCAN_KEYSTORE_BASE64   Base64 из буфера обмена
FASTSCAN_KEYSTORE_PASSWORD   пароль файла .jks
FASTSCAN_KEY_ALIAS   alias ключа
FASTSCAN_KEY_PASSWORD   пароль ключа
```

## 4. Получение сборки

После push в `main` откройте:

```text
Actions → Build signed Android artifacts → последний запуск
```

Внизу страницы будет artifact вида:

```text
FastScan-4.39-signed
```

Внутри находятся подписанные APK, AAB и `SHA256SUMS.txt`. Artifact хранится
30 дней. Workflow также можно запустить вручную кнопкой `Run workflow`.

## 5. Однократная подготовка Google Play

Google Play Developer Publishing API работает только после того, как для
приложения хотя бы один APK или AAB был загружен вручную через Play Console.
Для первого релиза:

1. Откройте внутреннее тестирование в Play Console.
2. Создайте релиз и вручную загрузите подписанный `app-release.aab`.
3. Настройте Play App Signing и завершите создание внутреннего релиза.
4. Следующая автоматическая версия должна иметь новый `versionCode`.

В Google Cloud для проекта сервисного аккаунта включите **Google Play Android
Developer API**. В Play Console сервисному аккаунту предоставьте доступ к
приложению `com.slavafit.fastscan` и право выпускать приложения в тестовые
треки.

Создайте JSON-ключ сервисного аккаунта. Его содержимое целиком добавьте в
GitHub как repository secret:

```text
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
```

JSON-файл нельзя добавлять в репозиторий или передавать в логи.

## 6. Публикация во внутреннее тестирование

Есть два способа запустить публикацию:

### Ручной запуск

Откройте:

```text
Actions → Build signed Android artifacts → Run workflow
```

Включите параметр `publish_to_play` и запустите workflow. После тестов и
сборки подписанный AAB будет загружен в трек `internal` со статусом
`completed`.

### Автоматический запуск по тегу

Сначала увеличьте `versionCode` и `versionName` в `app/build.gradle`, затем
создайте тег, точно совпадающий с `versionName`:

```powershell
git tag v4.40
git push origin v4.40
```

Тег `v4.40` принимается только для `versionName "4.40"`. Push тега запускает
сборку и публикацию во внутреннее тестирование автоматически.

Каждый AAB, загружаемый в Google Play, должен иметь `versionCode`, который ещё
не использовался. Production-релиз лучше продвигать из проверенного внутреннего
релиза через Play Console.

## Важные замечания

- При каждой новой версии необходимо увеличивать `versionCode`.
- Первый переход с debug-подписи на постоянную потребует удаления debug-версии.
- После перехода новые APK с этим ключом устанавливаются поверх предыдущих.
- После установки приложения из Google Play обновляйте его через Google Play:
  Play App Signing может использовать другой пользовательский ключ.
- Никогда не публикуйте ключ и пароли в репозитории или логах сборки.
