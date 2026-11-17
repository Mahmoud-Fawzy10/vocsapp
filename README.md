# مفرداتي (Mufradati) — نظام إدارة المفردات الإنجليزية التعليمية

تطبيق أندرويد متكامل (Kotlin + Jetpack Compose + Material 3) بواجهة عربية أساسية تدعم RTL بالكامل، مع عرض صحيح للنصوص الإنجليزية باتجاه LTR. صُمم لإدارة المفردات الإنجليزية التعليمية والمناهج الدراسية ومراجع الكتب مع بنية تخزين هجينة (سحابية + محلية دون إنترنت) ومزامنة ذكية وحل التعارضات.

---

## المعمارية والتقنيات المستخدمة

- **الواجهة**: Jetpack Compose + Material 3، اتجاه `LayoutDirection.Rtl` للعربية ومكونات `LtrText` للنصوص الإنجليزية والرموز الصوتية، متكيف مع الهواتف والأجهزة اللوحية (`NavigationBar` / `NavigationRail`).
- **قاعدة البيانات السحابية**: Cloud Firestore (قاعدة مسماة `ai-studio-android-53ed7999-d60e-4636-b4c8-07a947f629fc`) مع قواعد أمان صارمة (`firestore.rules`) وفهارس (`firestore.indexes.json`).
- **التخزين المحلي والعمل دون إنترنت**: Room SQLite (`AppDatabase`) + DataStore Preferences (`UserPreferencesRepository`) لحفظ المفردات، الكتب، المراجع، المنهج، سجل الاستيراد، وطابور المزامنة (`sync_operations`).
- **المصادقة والأمان**: Firebase Authentication عبر Google Sign-In (`androidx.credentials.CredentialManager`) مع تفعيل Firebase App Check (`DebugAppCheckProviderFactory` للتطوير و`RecaptchaAppCheckProviderFactory` للإصدار).
- **النطق الصوتي**: Android `TextToSpeech` مع دعم اللكنة الأمريكية (US) والبريطانية (UK)، اختيار الصوت المتاح، التحكم في السرعة، وعرض النسخ الصوتي البديل (IPA).
- **الاستيراد والتصدير**: محرك مدمج لقراءة وكتابة ملفات `CSV (UTF-8 BOM)`، و`XLSX` متعددة الأوراق (OpenXML)، و`JSON`، و`PDF` ثنائي الاتجاه عبر `PdfDocument` + `StaticLayout`.
- **الذكاء الاصطناعي (اختياري)**: عبر Firebase AI Logic (`gemini-3.8-flash`)، **معطّل افتراضياً**، وتُوسم جميع مخرجاته كـ `aiGenerated = true, verified = false` وتُوجّه حصراً إلى قائمة المراجعة (`reviewQueue`) ولا تُنشر تلقائياً.

---

## الأدوار والصلاحيات (RBAC)

تُفرض الصلاحيات في طبقة التطبيق (`RbacGuard`) وفي قواعد أمان الخادم (`firestore.rules`) بالاعتماد على وثائق العضوية الموثقة في `/workspaces/{workspaceId}/members/{userId}`:

- **المالك (`OWNER`)**: إدارة المستخدمين والأدوار، إعدادات المساحة، الكتب والمناهج الرسمية، الاستيراد والتصدير، حذف المفردات، مراجعة سجل التدقيق.
- **المحرر (`EDITOR`)**: إضافة وتعديل المفردات والمرادفات والمتضادات والمشتقات والتعبيرات، ربط مواقع الكتب، وحل التعارضات، والاستيراد إذا سُمِح له.
- **المراجع (`REVIEWER`)**: مراجعة واعتماد أو رفض المقترحات في قائمة المراجعة (بما فيها مقترحات الذكاء الاصطناعي).
- **المشاهد (`VIEWER`)**: تصفح وبحث المفردات، مع منع التعديل والحذف، وتقييد التصدير وفق إعدادات المالك.

---

## المزامنة وحل التعارضات

- كل مفردة تحمل معرّفاً دائماً بصيغة `VOC-000001` ورقم نسخة (`version`).
- عند التعديل دون اتصال بالإنترنت، يُحفظ التعديل محلياً وتُضاف عملية متتالية في `sync_operations`.
- عند عودة الاتصال، يقوم `SyncEngine` بإجراء دمج ثلاثي (`ConflictEngine.evaluateThreeWayMerge`):
  - إذا عدّل المستخدمان حقولاً مختلفة، يتم الدمج التلقائي بأمان.
  - إذا عدّل المستخدمان الحقل نفسه بقيم مختلفة، تُحفظ كلتا النسختين ويُنشأ سجل `ConflictRecordEntity` في شاشة **حل التعارضات** للاختيار الحقل-بالحقل وتسجيل النتيجة في سجل التدقيق.

---

## الشاشات المتوفرة

1. تسجيل الدخول عبر Google (`AuthScreen`)
2. لوحة التحكم والإحصائيات (`DashboardScreen`)
3. مكتبة المفردات مع البحث المتقدم والتصفية والتحديد الجماعي (`VocabularyLibraryScreen`)
4. تفاصيل المفردة الشاملة والنطق والمنهج ومواقع الكتب (`WordDetailScreen`)
5. إضافة وتعديل المفردة مع كشف التكرار الفوري (`AddEditWordScreen`)
6. مركز الاستيراد وربط الأعمدة (`ImportCenterScreen`)
7. معاينة الاستيراد ومعالجة التكرارات (`ImportPreviewScreen`)
8. سجل عمليات الاستيراد (`ImportHistoryScreen`)
9. إدارة الكتب والمراجع والمنهج المفصول عن الملاحظات الشخصية (`BooksAndCurriculumScreen`)
10. قائمة المراجعة والاعتماد (`ReviewQueueScreen`)
11. شاشة حل تعارضات المزامنة (`ConflictResolutionScreen`)
12. إدارة المستخدمين والأدوار (`UserManagementScreen`)
13. إعدادات مساحة العمل والتفضيلات (`WorkspaceSettingsScreen`)
14. مركز التصدير والمشاركة (`ExportCenterScreen`)
15. مركز المزامنة وطابور العمليات (`SyncCenterScreen`)
16. سجل التدقيق غير القابل للتعديل (`AuditLogsScreen`)

---

## الاختبارات والتحقق

### 1. اختبارات قواعد أمان Firestore
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### 2. اختبارات الوحدة وRobolectric (أندرويد)
```bash
gradle :app:testDebugUnitTest
```
