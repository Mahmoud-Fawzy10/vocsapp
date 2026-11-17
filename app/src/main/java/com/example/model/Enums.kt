package com.example.model

enum class WorkspaceRole(val arabicLabel: String, val englishLabel: String) {
    OWNER("مالك المساحة", "Owner"),
    EDITOR("محرر", "Editor"),
    REVIEWER("مراجع", "Reviewer"),
    VIEWER("مشاهد", "Viewer");

    fun canManageUsers(): Boolean = this == OWNER
    fun canManageSettings(): Boolean = this == OWNER
    fun canManageBooks(): Boolean = this == OWNER
    fun canEditVocab(): Boolean = this == OWNER || this == EDITOR
    fun canDeleteVocab(): Boolean = this == OWNER
    fun canReview(): Boolean = this == OWNER || this == REVIEWER
    fun canResolveConflicts(): Boolean = this == OWNER || this == EDITOR
    fun canImport(allowEditorImport: Boolean): Boolean =
        this == OWNER || (this == EDITOR && allowEditorImport)
    fun canExport(allowViewerExport: Boolean): Boolean =
        this != VIEWER || allowViewerExport

    companion object {
        fun safeValueOf(raw: String?): WorkspaceRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIEWER
    }
}

enum class MemberStatus(val arabicLabel: String) {
    ACTIVE("نشط"),
    SUSPENDED("معلّق");

    companion object {
        fun safeValueOf(raw: String?): MemberStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

enum class PartOfSpeech(val arabicLabel: String, val englishLabel: String) {
    NOUN("اسم", "Noun"),
    VERB("فعل", "Verb"),
    ADJECTIVE("صفة", "Adjective"),
    ADVERB("ظرف / حال", "Adverb"),
    PREPOSITION("حرف جر", "Preposition"),
    CONJUNCTION("أداة ربط", "Conjunction"),
    PRONOUN("ضمير", "Pronoun"),
    INTERJECTION("صيغة تعجب", "Interjection"),
    PHRASAL_VERB("فعل مركب", "Phrasal Verb"),
    IDIOM("تعبير اصطلاحي", "Idiom"),
    OTHER("أخرى", "Other");

    companion object {
        fun safeValueOf(raw: String?): PartOfSpeech {
            val cleaned = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it.name.equals(cleaned, ignoreCase = true) ||
                    it.englishLabel.equals(cleaned, ignoreCase = true) ||
                    it.arabicLabel == cleaned
            } ?: OTHER
        }
    }
}

enum class DerivativeForm(val arabicLabel: String, val englishLabel: String) {
    NOUN("اسم", "Noun"),
    VERB("فعل", "Verb"),
    ADJECTIVE("صفة", "Adjective"),
    ADVERB("ظرف", "Adverb"),
    PARTICIPLE("اسم فاعل / مفعول", "Participle"),
    OTHER("صيغة أخرى", "Other");

    companion object {
        fun safeValueOf(raw: String?): DerivativeForm =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    it.englishLabel.equals(raw, ignoreCase = true)
            } ?: OTHER
    }
}

enum class ExpressionType(val arabicLabel: String, val englishLabel: String) {
    PHRASAL_VERB("فعل مركب", "Phrasal Verb"),
    IDIOM("تعبير اصطلاحي", "Idiom"),
    COLLOCATION("متلازمة لفظية", "Collocation"),
    COMMON_EXPRESSION("تعبير شائع", "Common Expression");

    companion object {
        fun safeValueOf(raw: String?): ExpressionType =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) ||
                    it.englishLabel.equals(raw, ignoreCase = true)
            } ?: COMMON_EXPRESSION
    }
}

enum class NoteCategory(val arabicLabel: String) {
    PERSONAL("ملاحظة شخصية"),
    TEACHER("ملاحظة المعلم"),
    GRAMMAR("ملاحظة نحوية"),
    USAGE_WARNING("تحذير استخدام"),
    COMMON_MISTAKE("خطأ شائع");

    companion object {
        fun safeValueOf(raw: String?): NoteCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PERSONAL
    }
}

enum class CurriculumEntryType(val arabicLabel: String) {
    OFFICIAL_CURRICULUM("معلومة منهج رسمية"),
    USER_OBSERVATION("ملاحظة تعليمية شخصية");

    companion object {
        fun safeValueOf(raw: String?): CurriculumEntryType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER_OBSERVATION
    }
}

enum class VerificationStatus(val arabicLabel: String) {
    VERIFIED_OFFICIAL("موثق رسمياً"),
    PENDING_REVIEW("قيد المراجعة"),
    PERSONAL_NOTE("ملاحظة شخصية (غير رسمية)"),
    UNVERIFIED("غير موثق");

    companion object {
        fun safeValueOf(raw: String?): VerificationStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNVERIFIED
    }
}

enum class SyncState(val arabicLabel: String) {
    ONLINE_SYNCED("متصل ومتزامن"),
    SYNCHRONIZING("جاري المزامنة..."),
    OFFLINE("غير متصل بالإنترنت"),
    PENDING_CHANGES("تغييرات معلّقة للمزامنة"),
    SYNC_ERROR("خطأ في المزامنة"),
    CONFLICT_REQUIRES_REVIEW("يوجد تعارض يتطلب المراجعة")
}

enum class SyncOpType(val arabicLabel: String) {
    CREATE("إنشاء"),
    UPDATE("تعديل"),
    DELETE("حذف")
}

enum class SyncStatus(val arabicLabel: String) {
    SYNCED("متزامن"),
    PENDING("معلّق"),
    ERROR("فشل"),
    CONFLICT("تعارض")
}

enum class DuplicateStrategy(val arabicLabel: String) {
    SKIP("تخطي المكرر"),
    MERGE("دمج التفاصيل دون مسح"),
    UPDATE_EXISTING("تحديث السجل الحالي")
}

enum class ReviewSourceType(val arabicLabel: String) {
    AI_SUGGESTION("اقتراح ذكاء اصطناعي"),
    IMPORT_PROPOSAL("مقترح استيراد")
}

enum class ReviewStatus(val arabicLabel: String) {
    PENDING("بانتظار المراجعة"),
    APPROVED("معتمد"),
    REJECTED("مرفوض")
}

enum class ExportFormat(val arabicLabel: String, val extension: String, val mimeType: String) {
    PDF("ملف PDF منسّق", "pdf", "application/pdf"),
    XLSX("ملف Excel / Google Sheets (XLSX)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("ملف CSV (UTF-8)", "csv", "text/csv"),
    JSON("نسخة احتياطية JSON", "json", "application/json")
}

enum class ExportScope(val arabicLabel: String) {
    ALL("جميع المفردات"),
    SELECTED("المفردات المحددة"),
    FILTERED("نتائج التصفية الحالية"),
    BY_BOOK("كتاب محدد"),
    BY_UNIT("وحدة محددة"),
    BY_LESSON("درس محدد"),
    BY_PART_OF_SPEECH("قسم كلام محدد")
}

enum class WordSortOrder(val arabicLabel: String) {
    ALPHABETICAL_ASC("أبجدياً (A → Z)"),
    ALPHABETICAL_DESC("أبجدياً (Z → A)"),
    UPDATED_DESC("الأحدث تعديلاً"),
    CREATED_DESC("الأحدث إضافةً")
}

enum class ThemePreference(val arabicLabel: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الداكن");

    companion object {
        fun safeValueOf(raw: String?): ThemePreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}
