package com.qwaicode.persiansubtitles.domain.prompt

import com.qwaicode.persiansubtitles.data.db.CueEntity
import com.qwaicode.persiansubtitles.data.prefs.AppSettings
import com.qwaicode.persiansubtitles.domain.ai.ContextBrief
import com.qwaicode.persiansubtitles.domain.ai.SubtitleDigest
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguage
import com.qwaicode.persiansubtitles.domain.lang.TargetLanguages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Builds the system instruction and the JSON payload sent to Gemini. */
object PromptBuilder {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * True when the user asked for their own prompt to be the only style instruction
     * and actually wrote one. A blank custom prompt falls back to the preset instead
     * of sending a request with no style at all.
     */
    fun customOnly(settings: AppSettings): Boolean =
        settings.promptMode == PromptMode.CUSTOM_ONLY && settings.customPrompt.isNotBlank()

    fun systemInstruction(
        settings: AppSettings,
        reviewMode: Boolean,
        brief: ContextBrief? = null,
    ): String {
        val preset = StylePresets.byId(settings.presetId)
        val customOnly = customOnly(settings)
        val lang = settings.language
        return buildString {
            appendLine("تو یک مترجم حرفه‌ای زیرنویس هستی و متن زیرنویس را به ${lang.promptName} روان ترجمه می‌کنی.")
            if (!lang.isPersian) {
                // Said once more in English: the instruction itself is Persian, and a
                // model must never mistake the language of the prompt for the target.
                appendLine("TARGET LANGUAGE: ${lang.nameEn} (${lang.nativeName}). Every translation must be written in ${lang.nameEn} only — never in Persian and never left in the source language.")
                appendLine("مثال‌های فارسی در بخش سبک فقط برای نشان دادن لحن‌اند؛ همان لحن را با معادل طبیعی در ${lang.promptName} پیاده کن.")
            }
            appendLine()
            if (customOnly) {
                // No preset at all: the user's prompt is the whole style instruction.
                appendLine("### سبک ترجمه — فقط دستور اختصاصی کاربر")
                appendLine(settings.customPrompt.trim())
            } else {
                appendLine("### سبک ترجمه")
                appendLine(preset.instruction)
                if (settings.customPrompt.isNotBlank()) {
                    appendLine()
                    appendLine("### دستور اختصاصی کاربر (بالاترین اولویت)")
                    appendLine(settings.customPrompt.trim())
                }
            }
            // The user's own terms come before the brief: where the two disagree,
            // the user is right and the model's reading of the film is a guess.
            UserGlossary.toPromptBlock(settings.userGlossary, lang.promptName).takeIf { it.isNotBlank() }?.let { block ->
                appendLine()
                appendLine(block)
            }
            // The result of reading the whole file once. It is repeated in every
            // batch on purpose: that is the only way batch 40 knows the same names
            // and the same level of politeness as batch 1.
            brief?.toPromptBlock(languageName = lang.promptName)?.takeIf { it.isNotBlank() }?.let { block ->
                appendLine()
                appendLine(block)
                appendLine("در تمام ترجمه به این شناخت پایبند باش تا کل فایل یکدست بماند.")
            }
            appendLine()
            appendLine("### قواعد الزامی")
            appendLine("1. ورودی یک آرایه JSON از خطوط زیرنویس با شناسه «id» است. برای هر id دقیقاً یک ترجمه برگردان؛ نه بیشتر، نه کمتر.")
            appendLine("2. خروجی فقط و فقط یک آرایه JSON معتبر به شکل [{\"id\":1,\"${lang.jsonKey}\":\"...\"}] باشد. هیچ توضیح، مقدمه، بلوک کد یا متن اضافه ننویس.")
            appendLine("3. خطوط را با هم ترکیب نکن و یک خط را به چند خط تقسیم نکن؛ ترتیب و شماره‌ها باید حفظ شود.")
            appendLine("4. شکستن خط داخل یک زیرنویس را با \\n نگه دار.")
            appendLine("5. تگ‌های قالب‌بندی مثل <i>، </i>، {\\an8} و نشانه ♪ را دست‌نخورده در جای خودشان بگذار.")
            if (lang.isPersian) {
                appendLine("6. اعداد، زمان‌ها و واحدها را درست منتقل کن و از اعداد فارسی در متن استفاده نکن.")
            } else {
                appendLine("6. اعداد، زمان‌ها و واحدها را درست منتقل کن و ارقام را با ارقام لاتین (0 تا 9) بنویس.")
            }
            if (settings.keepProperNames) {
                appendLine("7. نام افراد، مکان‌ها، برندها و عنوان فیلم‌ها را ترجمه نکن و تغییر نده.")
            } else if (lang.isPersian) {
                appendLine("7. نام‌های خاص را در صورت داشتن معادل رایج فارسی، فارسی بنویس.")
            } else {
                appendLine("7. نام‌های خاص را در صورت داشتن معادل رایج در ${lang.promptName}، به همان شکل رایج بنویس.")
            }
            appendLine("8. اگر خطی فقط تبلیغ، نام کانال یا کپی‌رایت است، همان متن را بدون تغییر برگردان.")
            if (lang.isPersian) {
                appendLine("9. هرگز متن اصلی انگلیسی را به‌جای ترجمه برنگردان؛ همه چیز باید فارسی باشد.")
            } else {
                appendLine("9. هرگز متن اصلی را به‌جای ترجمه برنگردان؛ همه چیز باید به ${lang.promptName} باشد.")
            }
            // The file itself is written in logical order and the exporter adds the
            // direction marks. A model that "helpfully" moves the period to the
            // front or inserts RLM characters breaks exactly that.
            appendLine("10. علامت پایان جمله (نقطه، ؟، !) را در انتهای جمله بنویس، نه ابتدای آن، و هیچ کاراکتر کنترل جهت متن (مانند RLM یا RLE) اضافه نکن.")
            appendLine("11. ترجمهٔ بخشی که داخل کروشه یا پرانتز است (مانند نام گوینده یا توضیح صدا) را هم داخل همان کروشه بگذار و جای کروشه‌ها را عوض نکن.")
            if (reviewMode) {
                appendLine()
                appendLine("### حالت بازبینی")
                appendLine("این خطوط در مرحله قبل ناقص، خالی یا مشکوک ترجمه شده‌اند. با دقت بیشتری ترجمه کن و مطمئن شو هیچ بخشی جا نمی‌افتد.")
            }
        }
    }

    /**
     * The instruction for the final proofreading pass.
     *
     * This request is not a translation: the Persian text already exists and is
     * mostly right. What is asked for is the work of a copy editor — repair the
     * spelling, finish the sentence that was cut off, translate the English word that
     * was left standing, remove the Latin letter that slipped between Persian ones —
     * and *nothing else*. A model that rewrites a correct line here would undo the
     * user's own edits, which is why the instruction says so three times.
     */
    fun polishInstruction(settings: AppSettings): String {
        val lang = settings.language
        return if (lang.isPersian) persianPolishInstruction(settings) else genericPolishInstruction(settings, lang)
    }

    /**
     * The same copy-editor brief for any other target language. There is no local
     * normaliser for those languages, so the model is the only proofreader — which is
     * why the "do not rewrite a correct line" rule is just as strict.
     */
    private fun genericPolishInstruction(settings: AppSettings, lang: TargetLanguage): String = buildString {
        appendLine("تو یک ویراستار حرفه‌ای زیرنویس به ${lang.promptName} هستی. متن‌های زیر قبلاً ترجمه شده‌اند و وظیفهٔ تو ترجمهٔ دوباره نیست، بلکه اصلاح نگارشی و ویرایشی آن‌ها است.")
        appendLine("TARGET LANGUAGE: ${lang.nameEn} (${lang.nativeName}). Keep every line in ${lang.nameEn}.")
        appendLine()
        appendLine("### چه چیزی را اصلاح کن")
        appendLine("1. غلط‌های املایی، دستوری و تایپی در ${lang.promptName}.")
        appendLine("2. هر کلمه یا بخشی که به زبان مبدأ مانده و باید به ${lang.promptName} ترجمه شود.")
        appendLine("3. حروف یا کلماتی از خط و زبان دیگر (مثلاً فارسی) که اشتباهی در متن مانده‌اند.")
        appendLine("4. جمله‌های ناقص یا بریده را کامل کن، با تکیه بر متن اصلی همان خط.")
        appendLine("5. فاصله‌گذاری و جای درست علائم نگارشی طبق قواعد ${lang.promptName}.")
        appendLine("6. کلمه‌های تکراری پشت‌سرهم را یکی کن.")
        if (settings.keepProperNames) {
            appendLine("7. نام افراد، مکان‌ها و برندها را به همان شکل اصلی دست‌نخورده بگذار.")
        } else {
            appendLine("7. نام‌های خاص را در صورت داشتن معادل رایج در ${lang.promptName}، به همان شکل بنویس.")
        }
        appendLine()
        appendLine("### چه چیزی را دست نزن")
        appendLine("- اگر خطی درست است، دقیقاً همان متن را برگردان؛ بازنویسی سلیقه‌ای ممنوع است.")
        appendLine("- لحن، سبک و انتخاب واژه‌ها را تغییر نده و جمله را کوتاه یا بلند نکن.")
        appendLine("- تگ‌های قالب‌بندی (<i>، </i>، {\\an8}) و نشانهٔ ♪ را سر جای خودشان نگه دار.")
        appendLine("- شکستن خط داخل زیرنویس را با \\n حفظ کن و خطوط را با هم ترکیب نکن.")
        appendLine("- هیچ کاراکتر کنترل جهت متن (RLM، RLE و مانند آن) اضافه نکن.")
        appendLine()
        UserGlossary.toPromptBlock(settings.userGlossary, settings.language.promptName).takeIf { it.isNotBlank() }?.let { block ->
            appendLine(block)
            appendLine()
        }
        appendLine("خروجی فقط و فقط یک آرایه JSON معتبر به شکل [{\"id\":1,\"${lang.jsonKey}\":\"...\"}] باشد؛ برای هر id دقیقاً یک خط، بدون توضیح و بدون بلوک کد.")
    }

    private fun persianPolishInstruction(settings: AppSettings): String = buildString {
        appendLine("تو یک ویراستار حرفه‌ای زیرنویس فارسی هستی. متن‌های زیر قبلاً ترجمه شده‌اند و وظیفهٔ تو ترجمهٔ دوباره نیست، بلکه اصلاح نگارشی و ویرایشی آن‌ها است.")
        appendLine()
        appendLine("### چه چیزی را اصلاح کن")
        appendLine("1. غلط‌های املایی و تایپی فارسی.")
        appendLine("2. هر کلمه یا حرف انگلیسی که ترجمه نشده و باید فارسی شود.")
        appendLine("3. حروف لاتین یا عربی که اشتباهی میان حروف فارسی مانده‌اند (مثل ك و ي عربی).")
        appendLine("4. جمله‌های ناقص، بریده یا بی‌فعل را کامل کن، با تکیه بر متن انگلیسی همان خط.")
        appendLine("5. فاصله و نیم‌فاصله (مثل «می‌رود» نه «می رود») و جای درست علائم نگارشی.")
        appendLine("6. کلمه‌های تکراری پشت‌سرهم را یکی کن.")
        if (settings.keepProperNames) {
            appendLine("7. نام افراد، مکان‌ها و برندها را به همان شکل لاتین دست‌نخورده بگذار.")
        } else {
            appendLine("7. نام‌های خاص را در صورت داشتن معادل رایج فارسی، فارسی بنویس.")
        }
        appendLine()
        appendLine("### چه چیزی را دست نزن")
        appendLine("- اگر خطی درست است، دقیقاً همان متن را برگردان؛ بازنویسی سلیقه‌ای ممنوع است.")
        appendLine("- لحن، سبک و انتخاب واژه‌ها را تغییر نده و جمله را کوتاه یا بلند نکن.")
        appendLine("- تگ‌های قالب‌بندی (<i>، </i>، {\\an8}) و نشانهٔ ♪ را سر جای خودشان نگه دار.")
        appendLine("- شکستن خط داخل زیرنویس را با \\n حفظ کن و خطوط را با هم ترکیب نکن.")
        appendLine("- هیچ کاراکتر کنترل جهت متن (RLM، RLE و مانند آن) اضافه نکن و علامت پایان جمله را در انتهای جمله بگذار.")
        appendLine()
        UserGlossary.toPromptBlock(settings.userGlossary, settings.language.promptName).takeIf { it.isNotBlank() }?.let { block ->
            appendLine(block)
            appendLine()
        }
        appendLine("خروجی فقط و فقط یک آرایه JSON معتبر به شکل [{\"id\":1,\"fa\":\"...\"}] باشد؛ برای هر id دقیقاً یک خط، بدون توضیح و بدون بلوک کد.")
    }

    /**
     * The payload of the polish pass: the English original, the Persian translation
     * as it stands, and what the local scanner found wrong with it — the model works
     * far more precisely when it is told what to look for.
     */
    fun polishPayload(items: List<PolishItem>, translationKey: String = "fa"): String {
        val payload = buildJsonObject {
            put("lines_to_fix", buildJsonArray {
                items.forEach { item ->
                    add(buildJsonObject {
                        put("id", item.id)
                        put("en", item.source)
                        put(translationKey, item.translated)
                        if (item.issues.isNotBlank()) put("problems", item.issues)
                    })
                }
            })
        }
        return payload.toString()
    }

    /** One line handed to the polish pass. */
    data class PolishItem(
        val id: Int,
        val source: String,
        val translated: String,
        /** Persian description of what the scanner found, or blank. */
        val issues: String,
    )

    /**
     * The instruction for the one request that reads the whole subtitle before the
     * translation starts. It asks for a JSON object, not prose, because the answer
     * is stored and put into every later prompt.
     */
    fun analysisInstruction(language: TargetLanguage = TargetLanguages.persian): String = buildString {
        appendLine("تو یک ویراستار و سرمترجم زیرنویس هستی. قبل از شروع ترجمه، کل زیرنویس یک فیلم یا سریال را می‌خوانی و یک «شناسنامهٔ ترجمه» می‌سازی تا مترجم بعدی همهٔ بخش‌ها را یکدست و دقیق ترجمه کند.")
        appendLine()
        appendLine("### آنچه باید تشخیص بدهی")
        appendLine("1. موضوع و خط داستانی کلی، ژانر و فضای اثر (مثلاً درام خانوادگی، تریلر جنایی، کمدی).")
        appendLine("2. شخصیت‌های اصلی: نام، معادل فارسی پیشنهادی برای نام، و نقش/نسبتشان با هم.")
        appendLine("3. سطح زبان و نحوهٔ خطاب: محاوره یا رسمی، «تو» یا «شما» — با توجه به رابطهٔ شخصیت‌ها.")
        appendLine("4. لحن کلی اثر (سرد، طنزآمیز، احساسی، خشن).")
        appendLine("5. اصطلاح‌ها، اسم مکان‌ها، عنوان‌های شغلی و واژه‌های تکرارشونده‌ای که باید در کل فایل یکسان ترجمه شوند، همراه معادل فارسی.")
        appendLine("6. هر نکتهٔ خاصی که مترجم باید بداند (بازی زبانی، لهجه، ارجاع فرهنگی، جنسیت گویندهٔ نامشخص).")
        appendLine("7. مناسب‌ترین لحن ترجمه برای کل اثر را فقط از میان فهرست زیر انتخاب کن و شناسهٔ لاتین آن را در «tone_preset» بنویس، و در «tone_reason» در یک جملهٔ کوتاه بگو چرا:")
        appendLine(StylePresets.catalogForPrompt())
        appendLine()
        appendLine("### قواعد")
        appendLine("- متن ورودی گزیده‌ای از سراسر زیرنویس است؛ نشانهٔ ${SubtitleDigest.GAP} یعنی بخشی از فیلم حذف شده و نباید دو طرف آن را یک صحنه فرض کنی.")
        appendLine("- محتوای بزرگسال، خشونت یا دشنام را هم بی‌قضاوت توصیف کن؛ این‌ها دیالوگ فیلم است و برای انتخاب لحن لازم است.")
        appendLine("- خروجی فقط و فقط یک شیء JSON معتبر باشد، بدون توضیح و بدون بلوک کد، دقیقاً با این کلیدها:")
        appendLine("{\"title\":\"\",\"genre\":\"\",\"setting\":\"\",\"summary\":\"\",\"tone\":\"\",\"formality\":\"\",\"tone_preset\":\"\",\"tone_reason\":\"\",\"characters\":[{\"name\":\"\",\"persian\":\"\",\"note\":\"\"}],\"glossary\":[{\"source\":\"\",\"persian\":\"\"}],\"notes\":\"\"}")
        appendLine("- مقدار همهٔ فیلدها فارسی باشد، به‌جز «name» و «source» که همان متن اصلی می‌مانند و «tone_preset» که فقط یکی از شناسه‌های لاتین فهرست بالاست.")
        if (!language.isPersian) {
            // The key names stay "persian" so stored briefs keep loading; only their
            // meaning changes with the target language.
            appendLine("- زبان مقصد ترجمه ${language.promptName} است: در فیلدهای «persian» (شخصیت‌ها و اصطلاح‌ها) شکل درست همان نام یا اصطلاح را به ${language.promptName} بنویس، نه به فارسی. بقیهٔ فیلدها فارسی بمانند.")
        }
        appendLine("- «summary» حداکثر سه جمله. «characters» حداکثر ۱۰ شخصیت. «glossary» حداکثر ۲۰ مورد.")
    }

    fun analysisPayload(fileName: String?, totalCues: Int, digest: String): String {
        val payload = buildJsonObject {
            if (!fileName.isNullOrBlank()) put("file_name", fileName)
            put("total_cues", totalCues)
            put("excerpt", digest)
        }
        return payload.toString()
    }

    /** JSON payload: the cues to translate plus a little already-translated context. */
    fun batchPayload(
        cues: List<CueEntity>,
        context: List<CueEntity>,
        translationKey: String = "fa",
    ): String {
        val payload = buildJsonObject {
            if (context.isNotEmpty()) {
                put("context_already_translated", buildJsonArray {
                    context.sortedBy { it.id }.forEach { cue ->
                        add(buildJsonObject {
                            put("id", cue.id)
                            put("en", cue.source)
                            put(translationKey, cue.translated ?: "")
                        })
                    }
                })
            }
            put("lines_to_translate", buildJsonArray {
                cues.forEach { cue ->
                    add(buildJsonObject {
                        put("id", cue.id)
                        put("text", cue.source)
                    })
                }
            })
        }
        return payload.toString()
    }

    /**
     * Parses the model answer into id -> Persian text.
     * Tolerates code fences, an object wrapper and a "1| text" style fallback.
     */
    fun parseResponse(raw: String): Map<Int, String> {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val fromJson = runCatching { parseJson(cleaned) }.getOrNull()
        if (!fromJson.isNullOrEmpty()) return fromJson

        // Fallback: plain lines such as "12| متن" or "12: متن"
        val result = mutableMapOf<Int, String>()
        val lineRegex = Regex("""^\s*(\d{1,6})\s*[|:\-–]\s*(.+)$""")
        cleaned.lineSequence().forEach { line ->
            val m = lineRegex.find(line) ?: return@forEach
            val id = m.groupValues[1].toIntOrNull() ?: return@forEach
            result[id] = m.groupValues[2].trim().trim('"')
        }
        return result
    }

    private fun parseJson(text: String): Map<Int, String> {
        val element: JsonElement = json.parseToJsonElement(text)
        val array: JsonArray = when {
            element is JsonArray -> element
            element is JsonObject -> element.values.firstOrNull { it is JsonArray }?.jsonArray
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        val result = mutableMapOf<Int, String>()
        array.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val id = (obj["id"] ?: obj["index"] ?: obj["n"])?.jsonPrimitive?.intOrNull ?: return@forEach
            val value = (obj["fa"] ?: obj["text"] ?: obj["translation"] ?: obj["translated"])
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: return@forEach
            if (value.isNotBlank()) result[id] = value.trim()
        }
        return result
    }
}
