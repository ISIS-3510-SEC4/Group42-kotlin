const { initializeApp, cert } = require("firebase-admin/app");
const {
  getFirestore,
  FieldValue
} = require("firebase-admin/firestore");

const serviceAccount = require("./moviles-9132d-firebase-adminsdk-fbsvc-5e8ba5976c.json");

initializeApp({
  credential: cert(serviceAccount),
  projectId: "moviles-9132d"
});

const db = getFirestore();

const TIME_ZONE = "America/Bogota";
const PLATFORM = "KOTLIN";

const CORE_FEATURES = [
  "RECOMMENDATIONS",
  "VOTE",
  "UPCOMING_MEETINGS",
  "CHECK_IN",
  "FRIENDS"
];

function getCalendarWeekRange(timeZone) {
  const now = new Date();

  // Get the local date in America/Bogota.
  const localDateString = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(now);

  const [year, month, day] = localDateString
    .split("-")
    .map(Number);

  // Create a UTC date representing the local calendar date.
  const localDate = new Date(
    Date.UTC(year, month - 1, day)
  );

  // JavaScript: Sunday = 0, Monday = 1, ..., Saturday = 6.
  const dayOfWeek = localDate.getUTCDay();

  // Number of days since Monday.
  const daysSinceMonday =
    dayOfWeek === 0 ? 6 : dayOfWeek - 1;

  const monday = new Date(localDate);
  monday.setUTCDate(
    monday.getUTCDate() - daysSinceMonday
  );

  const nextMonday = new Date(monday);
  nextMonday.setUTCDate(
    nextMonday.getUTCDate() + 7
  );

  return {
    weekStartDate: monday.toISOString().slice(0, 10),
    weekEndDate: new Date(
      nextMonday.getTime() - 1
    ).toISOString().slice(0, 10),

    start: monday,
    end: nextMonday
  };
}

async function main() {
  const db = getFirestore();

  const {
    weekStartDate,
    weekEndDate,
    start,
    end
  } = getCalendarWeekRange(TIME_ZONE);

  const usage = {};

  for (const feature of CORE_FEATURES) {
    usage[feature] = 0;
  }

  const snapshot = await db.collection("analytics_events")
    .where("platform", "==", PLATFORM)
    .where("eventType", "==", "FEATURE_USED")
    .where("timestamp", ">=", start)
    .where("timestamp", "<", end)
    .get();

  let ignoredEvents = 0;

  for (const document of snapshot.docs) {
    const event = document.data();
    const metadata = event.metadata || {};
    const feature = metadata.feature;

    if (!CORE_FEATURES.includes(feature)) {
      ignoredEvents++;
      continue;
    }

    usage[feature]++;
  }

  const leastUsedCount =
    Math.min(...Object.values(usage));

  const leastUsedFeatures =
    Object.entries(usage)
      .filter(([, count]) => count === leastUsedCount)
      .map(([feature]) => feature);

  const resultId =
    `feature_usage_weekly_${PLATFORM}_${weekStartDate}`;

  const result = {
    businessQuestion:
      "Which core functionality is used the less per week?",

    metric:
      "weekly_feature_usage",

    platform: PLATFORM,

    timeZone: TIME_ZONE,

    weekStart: weekStartDate,

    weekEnd: weekEndDate,

    usage,

    leastUsedFeatures,

    leastUsedCount,

    totalFeatureUsage:
      Object.values(usage)
        .reduce((total, count) => total + count, 0),

    analyzedEventCount: snapshot.size,

    ignoredEvents,

    updatedAt:
      FieldValue.serverTimestamp()
  };

  await db.collection("analytics_results")
    .doc(resultId)
    .set(result);

  console.log("Weekly feature usage saved in analytics_results.");

  console.log({
    weekStart: weekStartDate,
    weekEnd: weekEndDate,
    usage,
    leastUsedFeatures,
    leastUsedCount,
    analyzedEventCount: snapshot.size,
    ignoredEvents
  });
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});