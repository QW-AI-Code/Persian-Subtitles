package com.qwaicode.persiansubtitles.domain.prompt

/** A ready-made translation tone the user can pick in the Style tab. */
data class StylePreset(
    val id: String,
    val title: String,
    val description: String,
    val instruction: String,
)

object StylePresets {

    val all: List<StylePreset> = listOf(
        StylePreset(
            id = "fluent",
            title = "روان و طبیعی (پیش‌فرض)",
            description = "ترجمه‌ای که مثل فارسی نوشته‌شده خوانده می‌شود؛ مناسب بیشتر فیلم‌ها و سریال‌ها.",
            instruction = "ترجمه باید روان، طبیعی و بدون بوی ترجمه باشد. جمله‌ها را فارسی‌فکرشده بنویس، نه کلمه‌به‌کلمه. طول جمله‌ها را برای خواندن سریع روی پرده کوتاه نگه دار.",
        ),
        StylePreset(
            id = "literal",
            title = "دقیق و وفادار",
            description = "نزدیک‌ترین ترجمه به متن اصلی؛ برای مستند، آموزش و محتوای فنی.",
            instruction = "ترجمه باید دقیق و وفادار به متن اصلی باشد. چیزی به متن اضافه یا از آن کم نکن و معادل‌های فنی را درست و یکدست ترجمه کن.",
        ),
        StylePreset(
            id = "colloquial",
            title = "عامیانه و محاوره‌ای",
            description = "زبان گفتاری روزمره؛ برای فیلم‌های خانوادگی، کمدی و سریال‌های امروزی.",
            instruction = "از فارسی گفتاری و محاوره‌ای استفاده کن («می‌خوام»، «بریم»، «چی‌کار») و لحن شخصیت‌ها را خودمانی نگه دار، اما بی‌ادبی و فحش رکیک را ملایم کن.",
        ),
        StylePreset(
            id = "cinematic",
            title = "سینمایی و ادبی",
            description = "لحن باوقار و تصویری؛ برای درام، تاریخی و فیلم‌های فاخر.",
            instruction = "لحن ترجمه سینمایی، باوقار و کمی ادبی باشد. از واژه‌های تصویری و آهنگین استفاده کن ولی جمله‌ها را ساده و قابل خواندن نگه دار.",
        ),
        StylePreset(
            id = "formal",
            title = "رسمی و اداری",
            description = "زبان کتابی و مؤدب؛ برای خبر، سخنرانی و محتوای سازمانی.",
            instruction = "ترجمه را با زبان رسمی و کتابی بنویس، از شکل کامل فعل‌ها استفاده کن و از اصطلاحات عامیانه پرهیز کن.",
        ),
        StylePreset(
            id = "comedy",
            title = "طنز و کمدی",
            description = "شوخی‌ها را بومی‌سازی می‌کند تا برای مخاطب فارسی خنده‌دار بماند.",
            instruction = "شوخی‌ها، جوک‌ها و بازی‌های زبانی را برای مخاطب فارسی‌زبان بومی‌سازی کن تا خنده‌دار بمانند؛ در صورت لزوم معادل فرهنگی نزدیک بگذار، نه ترجمه تحت‌اللفظی.",
        ),
        StylePreset(
            id = "documentary",
            title = "مستند و علمی",
            description = "لحن راوی مستند با اصطلاحات علمی درست.",
            instruction = "با لحن راوی مستند ترجمه کن: شفاف، آرام و بی‌احساسات اضافه. اصطلاحات علمی را با معادل رایج فارسی و در صورت نیاز با ذکر شکل انگلیسی در پرانتز بنویس.",
        ),
        StylePreset(
            id = "anime",
            title = "انیمه و انیمیشن",
            description = "لحن پرانرژی و جوان‌پسند، مناسب انیمه و کارتون.",
            instruction = "لحن پرانرژی، جوان‌پسند و احساسی باشد. القاب و اصطلاحات رایج انیمه را حفظ کن و صداهای احساسی را به معادل فارسی طبیعی برگردان.",
        ),
        StylePreset(
            id = "crime",
            title = "جنایی و اکشن",
            description = "لحن تند و کوتاه با ملایم‌سازی الفاظ رکیک.",
            instruction = "لحن تند، کوتاه و پرتنش باشد؛ جمله‌ها بریده و ضربه‌ای. فحش‌های رکیک را به معادل‌های ملایم فارسی («لعنتی»، «آشغال») تبدیل کن.",
        ),
        StylePreset(
            id = "simple",
            title = "ساده برای همه سن‌ها",
            description = "واژه‌های آسان و جمله‌های کوتاه؛ مناسب کودکان و خانواده.",
            instruction = "از واژه‌های ساده و جمله‌های کوتاه استفاده کن تا برای کودکان و نوجوانان هم قابل فهم باشد و هیچ عبارت زشتی در ترجمه نیاید.",
        ),
    )

    val default: StylePreset get() = all.first()

    fun byId(id: String): StylePreset = all.firstOrNull { it.id == id } ?: default

    /**
     * Resolves what the model answered for "which tone fits this film" to one of the
     * presets, or null. Models do not always return the bare id: they add quotes,
     * change the case, or answer with the Persian title instead — all of that is
     * accepted, anything else is rejected rather than guessed.
     */
    fun find(raw: String?): StylePreset? {
        val value = raw?.trim()?.trim('"', '\'', '`', '«', '»', '.', '،')?.trim()?.lowercase()
        if (value.isNullOrBlank()) return null
        all.firstOrNull { it.id == value }?.let { return it }
        all.firstOrNull { it.title.lowercase() == value }?.let { return it }
        // "cinematic (سینمایی)" or "سینمایی و ادبی" — match on the id or the title's first word.
        all.firstOrNull { value.startsWith(it.id) }?.let { return it }
        return all.firstOrNull { preset ->
            val head = preset.title.substringBefore(' ').lowercase()
            head.length >= 3 && value.startsWith(head)
        }
    }

    /** The list the analysis prompt offers the model: `id: short description`. */
    fun catalogForPrompt(): String = all.joinToString("\n") { "- ${it.id}: ${it.title} — ${it.description}" }

    /** Quick chips shown above the custom prompt field. */
    val customExamples: List<String> = listOf(
        "با لحن کمدی و شیرین ترجمه کن",
        "محاوره تهرانی امروزی باشد",
        "اصطلاحات فوتبالی را درست ترجمه کن",
        "خیلی کوتاه و مناسب خواندن سریع باشد",
        "لحن ترسناک و دلهره‌آور داشته باشد",
        "اسم‌های خاص را انگلیسی بنویس",
    )
}
