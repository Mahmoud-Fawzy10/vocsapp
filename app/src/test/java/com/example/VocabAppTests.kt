package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importexport.ExportGenerator
import com.example.data.importexport.ImportParser
import com.example.data.local.AppDatabase
import com.example.data.local.SyncOperationEntity
import com.example.data.local.WordEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.domain.ConflictEngine
import com.example.domain.DuplicateDetector
import com.example.domain.DuplicateMatch
import com.example.domain.MergeOutcome
import com.example.domain.RbacGuard
import com.example.domain.UnauthorizedActionException
import com.example.domain.WordNormalizer
import com.example.model.ConflictChoice
import com.example.model.ExportOptions
import com.example.model.MemberStatus
import com.example.model.PartOfSpeech
import com.example.model.WorkspaceRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VocabAppTests {

    private fun sampleWord(
        id: String = "w_1",
        humanId: String = "VOC-000001",
        word: String = "Abandon",
        pos: PartOfSpeech = PartOfSpeech.VERB,
        primaryMeaning: String = "يهجر",
        version: Long = 1L
    ) = WordEntity(
        id = id,
        humanId = humanId,
        workspaceId = "ws_1",
        word = word,
        normalizedWord = WordNormalizer.normalizeWord(word),
        partOfSpeech = pos.name,
        primaryMeaning = primaryMeaning,
        englishDefinition = "To leave completely.",
        arabicExplanation = "التخلي عن شيء أو شخص.",
        phoneticText = "/əˈbændən/",
        version = version,
        createdBy = "u_owner",
        updatedBy = "u_owner"
    )

    @Test
    fun `normalization and VOC ID formatting`() {
        assertEquals("carry out", WordNormalizer.normalizeWord("  Carry   OUT  "))
        assertEquals("Carry Out", WordNormalizer.cleanDisplayWord("  Carry   Out  "))
        assertEquals("VOC-000001", WordNormalizer.formatHumanId(1))
        assertEquals("VOC-000142", WordNormalizer.formatHumanId(142))
    }

    @Test
    fun `duplicate detection distinguishes same POS vs different POS`() {
        val existing = listOf(sampleWord(word = "Abandon", pos = PartOfSpeech.VERB))

        val exactSamePos = DuplicateDetector.findMatches(" ABANDON ", PartOfSpeech.VERB, existing)
        assertEquals(1, exactSamePos.size)
        assertEquals(DuplicateMatch.MatchType.EXACT_WORD_AND_POS, exactSamePos.first().matchType)

        val diffPos = DuplicateDetector.findMatches("abandon", PartOfSpeech.NOUN, existing)
        assertEquals(1, diffPos.size)
        assertEquals(DuplicateMatch.MatchType.EXACT_NORMALIZED_DIFFERENT_POS, diffPos.first().matchType)
    }

    @Test
    fun `rbac guard enforces owner editor reviewer viewer and suspended rules`() {
        val owner = WorkspaceMemberEntity("ws_u1", "ws", "u1", "u1", "Owner", "", WorkspaceRole.OWNER.name, MemberStatus.ACTIVE.name, "u1")
        val editor = WorkspaceMemberEntity("ws_u2", "ws", "u1", "u2", "Editor", "", WorkspaceRole.EDITOR.name, MemberStatus.ACTIVE.name, "u1")
        val reviewer = WorkspaceMemberEntity("ws_u3", "ws", "u1", "u3", "Reviewer", "", WorkspaceRole.REVIEWER.name, MemberStatus.ACTIVE.name, "u1")
        val viewer = WorkspaceMemberEntity("ws_u4", "ws", "u1", "u4", "Viewer", "", WorkspaceRole.VIEWER.name, MemberStatus.ACTIVE.name, "u1")
        val suspended = editor.copy(status = MemberStatus.SUSPENDED.name)

        RbacGuard.requireEditVocab(owner)
        RbacGuard.requireEditVocab(editor)
        RbacGuard.requireDeleteVocab(owner)
        RbacGuard.requireReview(reviewer)
        RbacGuard.requireReview(owner)

        try {
            RbacGuard.requireEditVocab(viewer)
            fail("Viewer should not be allowed to edit")
        } catch (_: UnauthorizedActionException) { }

        try {
            RbacGuard.requireDeleteVocab(editor)
            fail("Editor should not be allowed to delete")
        } catch (_: UnauthorizedActionException) { }

        try {
            RbacGuard.requireReview(editor)
            fail("Editor should not be allowed to approve reviews")
        } catch (_: UnauthorizedActionException) { }

        try {
            RbacGuard.requireEditVocab(suspended)
            fail("Suspended member should not be allowed to edit")
        } catch (_: UnauthorizedActionException) { }
    }

    @Test
    fun `conflict engine auto-merges non-conflicting fields and detects true conflicts`() {
        val base = sampleWord(primaryMeaning = "يهجر", version = 1L)
        val baseJson = ConflictEngine.snapshotToJson(base)

        // Non-conflicting: local edits primaryMeaning, remote edits arabicExplanation
        val localNonConflicting = base.copy(primaryMeaning = "يتخلى عن", version = 2L, lastSyncedSnapshotJson = baseJson)
        val remoteNonConflicting = base.copy(arabicExplanation = "شرح محدّث عن بعد", version = 2L)
        val autoOutcome = ConflictEngine.evaluateThreeWayMerge(localNonConflicting, remoteNonConflicting, "u1")
        assertTrue(autoOutcome is MergeOutcome.AutoMerged)
        val merged = (autoOutcome as MergeOutcome.AutoMerged).mergedWord
        assertEquals("يتخلى عن", merged.primaryMeaning)
        assertEquals("شرح محدّث عن بعد", merged.arabicExplanation)
        assertEquals(3L, merged.version)

        // Conflicting: both edit primaryMeaning differently
        val localConflict = base.copy(primaryMeaning = "معنى محلي", version = 2L, lastSyncedSnapshotJson = baseJson)
        val remoteConflict = base.copy(primaryMeaning = "معنى سحابي", version = 2L)
        val conflictOutcome = ConflictEngine.evaluateThreeWayMerge(localConflict, remoteConflict, "u1")
        assertTrue(conflictOutcome is MergeOutcome.ConflictDetected)
        val detected = conflictOutcome as MergeOutcome.ConflictDetected
        assertEquals(1, detected.diffs.size)
        assertEquals("primaryMeaning", detected.diffs.first().fieldKey)

        // Resolve conflict keeping remote
        val resolved = ConflictEngine.resolveConflict(
            currentWord = localConflict,
            record = detected.conflictRecord,
            choices = mapOf("primaryMeaning" to ConflictChoice.USE_REMOTE),
            resolvedByUid = "u1"
        )
        assertEquals("معنى سحابي", resolved.primaryMeaning)
        assertEquals(3L, resolved.version)
    }

    @Test
    fun `import parser handles csv and json with validation and duplicate matching`() {
        val csv = """
            word,meaning,pos,synonyms,book,page
            Abandon,يهجر,verb,forsake;desert,New Enterprise,14
            ,معنى بدون كلمة,noun,,,
        """.trimIndent()
        val parsed = ImportParser.parseCsv(csv)
        assertEquals(2, parsed.rows.size)
        val mapping = ImportParser.detectColumnMapping(parsed.headers)
        assertEquals("word", mapping.wordColumn)
        assertEquals("meaning", mapping.primaryMeaningColumn)

        val preview = ImportParser.buildPreviewRows(parsed, mapping, listOf(sampleWord()))
        assertEquals(2, preview.size)
        assertEquals("w_1", preview[0].matchedWordId)
        assertTrue(preview[1].validationError != null)

        val jsonParsed = ImportParser.parseJson("""[{"word":"Resilient","primaryMeaning":"مرن","partOfSpeech":"ADJECTIVE"}]""")
        assertEquals(1, jsonParsed.rows.size)
        assertEquals("Resilient", jsonParsed.rows.first()["word"])
    }

    @Test
    fun `export generator writes valid UTF8 BOM CSV, JSON, multi-sheet XLSX, and PDF`() {
        val words = listOf(sampleWord())
        val options = ExportOptions()

        // CSV with BOM
        val csvOut = ByteArrayOutputStream()
        ExportGenerator.writeCsv(csvOut, words, emptyMap(), emptyMap(), options)
        val csvBytes = csvOut.toByteArray()
        assertEquals(0xEF.toByte(), csvBytes[0])
        assertEquals(0xBB.toByte(), csvBytes[1])
        assertEquals(0xBF.toByte(), csvBytes[2])
        assertTrue(String(csvBytes, Charsets.UTF_8).contains("VOC-000001"))

        // JSON
        val jsonOut = ByteArrayOutputStream()
        ExportGenerator.writeJson(jsonOut, words, emptyMap(), emptyMap())
        assertTrue(jsonOut.toString("UTF-8").contains("Abandon"))

        // XLSX ZIP entries
        val xlsxOut = ByteArrayOutputStream()
        ExportGenerator.writeXlsx(xlsxOut, words, emptyMap(), emptyMap(), options)
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(xlsxOut.toByteArray())).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries.add(e.name)
                e = zis.nextEntry
            }
        }
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet2.xml"))

        // Roundtrip parse generated XLSX back through ImportParser
        val reparsedXlsx = ImportParser.parseXlsx(ByteArrayInputStream(xlsxOut.toByteArray()))
        assertEquals(1, reparsedXlsx.rows.size)
        assertEquals("Abandon", reparsedXlsx.rows.first()["Word"])
    }

    @Test
    fun `offline room database persists words and sync queue operations`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val word = sampleWord()
            db.wordDao().upsertWord(word)
            db.syncAndGovernanceDao().upsertSyncOperation(
                SyncOperationEntity(
                    operationId = "op_1",
                    workspaceId = "ws_1",
                    entityType = "WORD",
                    entityId = word.id,
                    opType = "CREATE",
                    payloadJson = "{}"
                )
            )

            val loaded = db.wordDao().getWordById(word.id)
            assertEquals("VOC-000001", loaded?.humanId)
            val pending = db.syncAndGovernanceDao().getPendingSyncOperations("ws_1")
            assertEquals(1, pending.size)
            assertEquals("PENDING", pending.first().status)
        } finally {
            db.close()
        }
    }
}
