const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validTaskListPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Personal",
    colorHex: "#38BDF8",
    sortOrder: 0,
    defaultList: true,
    deleted: false,
    updatedAtMillis: Date.now(),
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validTaskPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    listId: "list_default",
    title: "Finish AnyTask Liquid Glass UI",
    notes: "Verify light and dark glass blur",
    priority: "HIGH",
    completed: false,
    dueDateMillis: Date.now() + 86400000,
    hasDueTime: true,
    reminderOffsetMinutes: 15,
    sortOrder: 0,
    subtasks: [
      { id: "sub_1", title: "Specular border", completed: true, sortOrder: 0 },
      { id: "sub_2", title: "Widget preview", completed: false, sortOrder: 1 },
    ],
    deleted: false,
    updatedAtMillis: Date.now(),
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write taskLists or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("taskLists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID))
  );
});

test("Owner can list empty subcollections, create, update, and delete taskLists and tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Empty subcollection list must succeed (no existing() references on allow list)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("taskLists").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Create task list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("taskLists").doc("list_default").set(
      validTaskListPayload(ALICE_UID)
    )
  );

  // Create task without @DocumentId 'id' key
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(
      validTaskPayload(ALICE_UID)
    )
  );

  // Partial completion toggle update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      completedAtMillis: Date.now(),
      updatedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reorder partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      sortOrder: 3,
      updatedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete task
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete()
  );
});

test("Cross-user read and write attempts are rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("tasks").doc("bob_task")
      .set(validTaskPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").delete()
  );
});

test("Invalid payloads, ghost fields, future timestamps, and @DocumentId id fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // @DocumentId 'id' field in payload must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_bad_id").set({
      ...validTaskPayload(ALICE_UID),
      id: "task_bad_id",
    })
  );

  // Ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_ghost").set({
      ...validTaskPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  // Invalid priority enum must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_bad_prio").set(
      validTaskPayload(ALICE_UID, { priority: "URGENT" })
    )
  );

  // Tampered future timestamp must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_future").set(
      validTaskPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Immutable createdAt mutation on update must fail
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_imm").set(
      validTaskPayload(ALICE_UID)
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_imm").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
