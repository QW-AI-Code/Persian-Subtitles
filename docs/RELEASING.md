# انتشار نسخه / Releasing

## کاری که باید بکنی: هیچ

از این پس انتشار خودکار است. **هر push روی شاخهٔ `main`** ورک‌فلوی
`Persian Subtitles Android Build` را اجرا می‌کند و در پایان، سه APK را روی صفحهٔ
**Releases** می‌گذارد. نه لازم است تگ بزنی، نه لازم است چیزی را دستی اجرا کنی.

نتیجه روی صفحهٔ Releases:

```
Persian Subtitles v1.0.0            ← عنوان: فقط نام برنامه و نسخه
  Persian-Subtitles-v1.0.0-arm64-v8a.apk
  Persian-Subtitles-v1.0.0-armeabi-v7a.apk
  Persian-Subtitles-v1.0.0-universal.apk
  SHA256SUMS.txt
```

هر فایل جداگانه قابل دانلود است. هیچ ZIP و هیچ بخش Artifacts در کار نیست.

اگر بعداً همان نسخه را دوباره push کنی، ورک‌فلو فایل‌های همان انتشار را جایگزین
می‌کند (`--clobber`) و نسخه یا تگ عوض نمی‌شود.

## ترتیب مراحل ورک‌فلو

۱. `chmod +x ./gradlew` — اگر پروژه را به‌صورت ZIP در وب‌سایت گیت‌هاب آپلود کنی،
   بیت اجرایی فایل `gradlew` از بین می‌رود و بیلد با `Permission denied` می‌میرد.
۲. اعتبارسنجی همهٔ فایل‌های XML منابع.
۳. اجرای ۱۱۸ تست واحد.
۴. آماده‌سازی کلید امضا (اگر سکرت‌ها تنظیم شده باشند).
۵. `assembleRelease` و ساخت سه APK.
۶. تغییر نام فایل‌ها به `Persian-Subtitles-v<نسخه>-<abi>.apk` و ساخت `SHA256SUMS.txt`.
۷. انتشار با ابزار `gh` روی صفحهٔ Releases و چاپ فهرست فایل‌های منتشرشده در لاگ.

مرحلهٔ آخر، بعد از انتشار، نام دارایی‌ها را از خود API گیت‌هاب می‌خواند و چاپ
می‌کند. پس اگر چیزی منتشر نشده باشد، در لاگ دقیقاً دیده می‌شود.

## اگر بخواهی نسخهٔ دیگری منتشر کنی

نسخه از `app/build.gradle.kts` خوانده می‌شود (`appVersionName`). برای انتشار با
برچسب دیگر بدون تغییر کد:

**Actions → Persian Subtitles Android Build → Run workflow** و در کادر
`release_tag` مقدار دلخواه مثل `v1.1.0` را بنویس.

یا با تگ گیت:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

## اگر ورک‌فلو با خطای 403 شکست خورد

مخزن اجازهٔ نوشتن نمی‌دهد. مجوز `contents: write` داخل ورک‌فلو از سقفِ تنظیمات
مخزن بالاتر نمی‌رود:

**Settings → Actions → General → Workflow permissions →
«Read and write permissions» → Save**

بعد یک commit کوچک push کن تا ورک‌فلو دوباره اجرا شود.

## امضا با کلید خودت (اختیاری)

بدون این سکرت‌ها APKها با کلید debug امضا می‌شوند؛ نصب می‌شوند اما برای انتشار
عمومی مناسب نیستند. در **Settings → Secrets and variables → Actions**:

| سکرت | مقدار |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | خروجی `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | رمز فایل keystore |
| `ANDROID_KEY_ALIAS` | نام کلید |
| `ANDROID_KEY_PASSWORD` | رمز کلید |

```bash
keytool -genkey -v -keystore release.jks -alias persian-subtitles \
  -keyalg RSA -keysize 2048 -validity 10000
```

> فایل keystore و رمزهایش را گم نکن؛ در غیر این صورت نمی‌توانی برای کاربرانی که
> نسخهٔ فعلی را نصب کرده‌اند به‌روزرسانی منتشر کنی.

---

# English

Publishing is automatic. **Every push to `main`** runs the single
`Persian Subtitles Android Build` workflow, which tests, builds and then attaches
three separate APKs plus `SHA256SUMS.txt` to the **Releases** page under the title
`Persian Subtitles v1.0.0` — app name and version, nothing else.

Nothing is uploaded as a workflow artifact, so nothing arrives as a ZIP.
Re-pushing the same version replaces the assets of that same release
(`gh release upload --clobber`) and leaves the tag and version untouched.

To publish a different tag without touching the code, use
**Actions → Persian Subtitles Android Build → Run workflow** and set
`release_tag`, or push a `v*` tag.

If a run fails with HTTP 403, the repository forbids writing: set
**Settings → Actions → General → Workflow permissions** to *Read and write
permissions* — a workflow cannot grant itself more than the repository allows.
