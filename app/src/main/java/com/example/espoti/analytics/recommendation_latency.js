const {
  initializeApp,
  applicationDefault
} = require("firebase-admin/app");

const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

initializeApp({
  credential: applicationDefault(),
  projectId: "moviles-9132d"
});

async function main() {
  const db = getFirestore();

  const source = "FIRESTORE";
  const platform = "KOTLIN";
  const recommendationScope = "USER_PREVIEW";

  const snapshot = await db.collection("analytics_events")
    .where("platform", "==", platform)
    .get();

  const requests = new Map();
  const displayed = [];

  for (const document of snapshot.docs) {
    const event = document.data();
    const metadata = event.metadata || {};

    if (metadata.source !== source) continue;

    if (metadata.recommendationScope !== recommendationScope) {
      continue;
    }

    if (
      typeof metadata.requestId !== "string" ||
      metadata.requestId.length === 0
    ) {
      continue;
    }

    if (event.eventType === "RECOMMENDATION_REQUESTED") {
      requests.set(metadata.requestId, event);
    }

    if (event.eventType === "RECOMMENDATION_DISPLAYED") {
      displayed.push({
        ...event,
        documentId: document.id
      });
    }
  }

  // Stable order in case duplicate displayed events exist.
  displayed.sort((a, b) =>
    a.documentId.localeCompare(b.documentId)
  );

  const countedRequests = new Set();
  let totalDurationMs = 0;
  let ignoredDisplayedEvents = 0;

  for (const event of displayed) {
    const requestId = event.metadata.requestId;
    const requested = requests.get(requestId);

    const validDuration =
      Number.isSafeInteger(event.durationMs) &&
      event.durationMs >= 0;

    const matchingRequest =
      requested &&
      requested.userId === event.userId &&
      requested.sessionId === event.sessionId &&
      requested.meetingId === event.meetingId;

    if (
      !validDuration ||
      !matchingRequest ||
      countedRequests.has(requestId)
    ) {
      ignoredDisplayedEvents++;
      continue;
    }

    countedRequests.add(requestId);
    totalDurationMs += event.durationMs;
  }

  const sampleCount = countedRequests.size;

  const averageDurationMs =
    sampleCount === 0
      ? null
      : totalDurationMs / sampleCount;

  const resultId =
    "recommendation_latency_KOTLIN_FIRESTORE_USER_PREVIEW";

  const result = {
    businessQuestion:
      "What is the average time required for the app to calculate and display a meeting point recommendation?",
    metric: "recommendation_request_to_display",
    source,
    platform,
    recommendationScope,
    measurementStatus: "FIRESTORE_VALIDATION",
    sampleCount,
    totalDurationMs,
    averageDurationMs,
    averageDurationSeconds:
      averageDurationMs === null
        ? null
        : averageDurationMs / 1000,
    ignoredDisplayedEvents,
    updatedAt: FieldValue.serverTimestamp()
  };

  await db.collection("analytics_results")
    .doc(resultId)
    .set(result);

  console.log("Resultado guardado en analytics_results");
  console.log({
    sampleCount,
    averageDurationMs,
    ignoredDisplayedEvents
  });
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});