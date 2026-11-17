const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_1";
const EDITOR_UID = "editor_uid_2";
const REVIEWER_UID = "reviewer_uid_3";
const VIEWER_UID = "viewer_uid_4";
const OUTSIDER_UID = "outsider_uid_5";
const WS_ID = "ws_primary_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedWorkspaceWithRoles() {
  const now = new Date(Date.now() - 5000);
  const memberUids = [OWNER_UID, EDITOR_UID, REVIEWER_UID, VIEWER_UID];
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("workspaces").doc(WS_ID).set({
      name: "English Grade 12",
      description: "Main school curriculum",
      ownerId: OWNER_UID,
      memberUids,
      allowViewerExport: false,
      allowEditorImport: true,
      nextVocabSequence: 2,
      createdAt: now,
      updatedAt: now,
    });
    const roles = [
      { uid: OWNER_UID, role: "OWNER", name: "Owner" },
      { uid: EDITOR_UID, role: "EDITOR", name: "Editor" },
      { uid: REVIEWER_UID, role: "REVIEWER", name: "Reviewer" },
      { uid: VIEWER_UID, role: "VIEWER", name: "Viewer" },
    ];
    for (const m of roles) {
      await db.collection("workspaces").doc(WS_ID).collection("members").doc(m.uid).set({
        workspaceId: WS_ID,
        ownerId: OWNER_UID,
        userId: m.uid,
        displayName: m.name,
        email: `${m.uid}@example.com`,
        role: m.role,
        status: "ACTIVE",
        invitedBy: OWNER_UID,
        createdAt: now,
        updatedAt: now,
      });
    }
  });
}

test("Unauthenticated user: denied all reads and writes", async () => {
  await seedWorkspaceWithRoles();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("workspaces").doc(WS_ID).get());
  await assertFails(unauth.collection("workspaces").doc(WS_ID).collection("words").get());
  await assertFails(unauth.collection("users").doc(OWNER_UID).get());
});

test("User profile: isolated strictly to owner UID", async () => {
  const now = new Date(Date.now() - 2000);
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "أحمد المعلم",
      themePreference: "LIGHT",
      aiEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(outsiderDb.collection("users").doc(OWNER_UID).get());
  await assertFails(outsiderDb.collection("users").doc(OWNER_UID).update({ displayName: "Hacked" }));
});

test("Workspace creation & self-role escalation prevention", async () => {
  const now = new Date(Date.now() - 2000);
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Atomic batch creating workspace + OWNER member succeeds
  const batch = ownerDb.batch();
  const wsRef = ownerDb.collection("workspaces").doc("ws_new");
  const memRef = wsRef.collection("members").doc(OWNER_UID);
  batch.set(wsRef, {
    name: "Arabic-English Dept",
    ownerId: OWNER_UID,
    memberUids: [OWNER_UID],
    allowViewerExport: false,
    allowEditorImport: true,
    nextVocabSequence: 1,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(memRef, {
    workspaceId: "ws_new",
    ownerId: OWNER_UID,
    userId: OWNER_UID,
    displayName: "Owner",
    role: "OWNER",
    status: "ACTIVE",
    invitedBy: OWNER_UID,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Outsider cannot assign themselves OWNER or EDITOR on ws_new
  await assertFails(
    outsiderDb.collection("workspaces").doc("ws_new").collection("members").doc(OUTSIDER_UID).set({
      workspaceId: "ws_new",
      ownerId: OWNER_UID,
      userId: OUTSIDER_UID,
      displayName: "Attacker",
      role: "OWNER",
      status: "ACTIVE",
      invitedBy: OUTSIDER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("RBAC on words: Editor can create/update, Viewer/Reviewer cannot write, only Owner can delete", async () => {
  await seedWorkspaceWithRoles();
  const now = new Date(Date.now() - 3000);
  const memberUids = [OWNER_UID, EDITOR_UID, REVIEWER_UID, VIEWER_UID];

  const editorDb = testEnv.authenticatedContext(EDITOR_UID).firestore();
  const reviewerDb = testEnv.authenticatedContext(REVIEWER_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  const wordPayload = {
    workspaceId: WS_ID,
    memberUids,
    humanId: "VOC-000001",
    word: "Abandon",
    normalizedWord: "abandon",
    partOfSpeech: "VERB",
    primaryMeaning: "يهجر / يتخلى عن",
    englishDefinition: "To leave behind completely.",
    arabicExplanation: "ترك الشيء أو التخلي عنه نهائياً",
    exampleSentences: ["They had to abandon the ship."],
    version: 1,
    createdBy: EDITOR_UID,
    updatedBy: EDITOR_UID,
    createdAt: now,
    updatedAt: now,
  };

  // Editor creates word v1
  await assertSucceeds(
    editorDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").set(wordPayload)
  );

  // Viewer cannot create or update
  await assertFails(
    viewerDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_2").set({
      ...wordPayload,
      createdBy: VIEWER_UID,
      updatedBy: VIEWER_UID,
    })
  );
  await assertFails(
    viewerDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").update({
      primaryMeaning: "tampered",
      version: 2,
      updatedBy: VIEWER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reviewer cannot edit words directly
  await assertFails(
    reviewerDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").update({
      primaryMeaning: "tampered by reviewer",
      version: 2,
      updatedBy: REVIEWER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Editor updates word with version increment (1 -> 2)
  await assertSucceeds(
    editorDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").update({
      primaryMeaning: "يتخلى عن / يترك",
      version: 2,
      updatedBy: EDITOR_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Editor cannot hard-delete; Owner can
  await assertFails(editorDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").delete());
  await assertSucceeds(ownerDb.collection("workspaces").doc(WS_ID).collection("words").doc("w_1").delete());

  // Query alignment test: filtered by memberUids succeeds, unfiltered fails
  await assertSucceeds(
    viewerDb.collection("workspaces").doc(WS_ID).collection("words").where("memberUids", "array-contains", VIEWER_UID).get()
  );
  await assertFails(
    viewerDb.collection("workspaces").doc(WS_ID).collection("words").get()
  );
});

test("ReviewQueue & Curriculum & AuditLog guards", async () => {
  await seedWorkspaceWithRoles();
  const now = new Date(Date.now() - 3000);
  const memberUids = [OWNER_UID, EDITOR_UID, REVIEWER_UID, VIEWER_UID];
  const editorDb = testEnv.authenticatedContext(EDITOR_UID).firestore();
  const reviewerDb = testEnv.authenticatedContext(REVIEWER_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  // Editor cannot mark USER_OBSERVATION as VERIFIED_OFFICIAL
  await assertFails(
    editorDb.collection("workspaces").doc(WS_ID).collection("curriculumEntries").doc("c_bad").set({
      workspaceId: WS_ID,
      memberUids,
      wordId: "w_1",
      entryType: "USER_OBSERVATION",
      verificationStatus: "VERIFIED_OFFICIAL",
      version: 1,
      createdBy: EDITOR_UID,
      updatedBy: EDITOR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Editor submits AI suggestion into reviewQueue (PENDING, verified=false)
  await assertSucceeds(
    editorDb.collection("workspaces").doc(WS_ID).collection("reviewQueue").doc("rq_1").set({
      workspaceId: WS_ID,
      memberUids,
      targetWordId: "w_1",
      targetWordText: "Abandon",
      sourceType: "AI_SUGGESTION",
      suggestionType: "SYNONYMS",
      proposedPayload: "{\"synonyms\":[\"forsake\",\"desert\"]}",
      summaryArabic: "اقتراح مرادفات بالذكاء الاصطناعي",
      aiGenerated: true,
      verified: false,
      status: "PENDING",
      createdBy: EDITOR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Editor cannot approve their own review item; Reviewer can
  await assertFails(
    editorDb.collection("workspaces").doc(WS_ID).collection("reviewQueue").doc("rq_1").update({
      status: "APPROVED",
      verified: true,
      reviewedBy: EDITOR_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    reviewerDb.collection("workspaces").doc(WS_ID).collection("reviewQueue").doc("rq_1").update({
      status: "APPROVED",
      verified: true,
      reviewedBy: REVIEWER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // AuditLog is append-only (cannot update or delete even as Owner)
  await assertSucceeds(
    ownerDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").set({
      workspaceId: WS_ID,
      memberUids,
      actorUserId: OWNER_UID,
      actorName: "Owner",
      actorRole: "OWNER",
      action: "CREATE_WORD",
      entityType: "WORD",
      entityId: "w_1",
      summary: "Created VOC-000001",
      createdAt: now,
    })
  );
  await assertFails(ownerDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").delete());
  await assertFails(ownerDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").update({ summary: "altered" }));
});
