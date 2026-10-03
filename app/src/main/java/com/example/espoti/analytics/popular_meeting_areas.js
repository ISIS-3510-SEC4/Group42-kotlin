// BQ: "What are the most frequently selected meeting areas and locations
// according to users' historical meeting data?"
//
// Run (same setup as recommendation_latency.js):  node popular_meeting_areas.js
// Reads PLACE_SELECTED events (written by CreateMeetingViewModel.onVoteConfirmed)
// and meetings that already have a placeId, ranks areas (city and ~1 km grid
// cell) and places, and stores the result in analytics_results.
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

const TOP_N = 10;

function bump(map, key, init) {
  if (!map.has(key)) map.set(key, { count: 0, users: new Set(), ...init });
  return map.get(key);
}

function rank(map) {
  return [...map.entries()]
    .map(([key, v]) => ({
      key,
      selections: v.count,
      distinctUsers: v.users.size,
      ...v.info
    }))
    .sort((a, b) => b.selections - a.selections || a.key.localeCompare(b.key))
    .slice(0, TOP_N);
}

async function main() {
  const db = getFirestore();

  const cities = new Map();
  const cells = new Map();
  const places = new Map();
  let totalSelections = 0;
  let ignoredEvents = 0;

  const register = ({ placeId, userId, placeName, category, cityName, areaCell, address }) => {
    if (!placeId) {
      ignoredEvents++;
      return;
    }
    totalSelections++;
    const place = bump(places, placeId, { info: { placeId, placeName, category, cityName, address } });
    place.count++;
    place.users.add(userId);

    if (cityName) {
      const city = bump(cities, cityName, { info: { cityName } });
      city.count++;
      city.users.add(userId);
    }
    if (areaCell) {
      const cell = bump(cells, areaCell, { info: { areaCell, cityName } });
      cell.count++;
      cell.users.add(userId);
    }
  };

  // 1) Selections tracked by the app when a user votes a recommended place.
  const events = await db.collection("analytics_events")
    .where("eventType", "==", "PLACE_SELECTED")
    .get();

  for (const document of events.docs) {
    const event = document.data();
    const m = event.metadata || {};
    register({
      placeId: event.placeId,
      userId: event.userId,
      placeName: m.placeName,
      category: m.category,
      cityName: m.cityName,
      areaCell: m.areaCell,
      address: m.address
    });
  }

  // 2) Historical meetings stored in Firestore with a chosen place.
  const meetings = await db.collection("meetings").get();
  const placeCache = new Map();
  let meetingSelections = 0;

  for (const document of meetings.docs) {
    const meeting = document.data();
    if (!meeting.placeId || meeting.status === "CANCELED") continue;

    if (!placeCache.has(meeting.placeId)) {
      const snap = await db.collection("places").doc(meeting.placeId).get();
      placeCache.set(meeting.placeId, snap.exists ? snap.data() : null);
    }
    const place = placeCache.get(meeting.placeId);
    if (!place) continue;

    const loc = place.location || {};
    const hasCoords = typeof loc.latitude === "number" && typeof loc.longitude === "number";
    meetingSelections++;
    register({
      placeId: meeting.placeId,
      userId: meeting.creatorId,
      placeName: place.name,
      category: place.category,
      cityName: loc.cityName,
      areaCell: hasCoords ? `${loc.latitude.toFixed(2)},${loc.longitude.toFixed(2)}` : null,
      address: loc.address
    });
  }

  // Firestore rejects `undefined`, and places/events may lack some fields.
  const clean = value => JSON.parse(JSON.stringify(value));

  const result = {
    businessQuestion:
      "What are the most frequently selected meeting areas and locations according to users' historical meeting data?",
    metric: "meeting_area_selection_frequency",
    totalSelections,
    selectionsFromMeetings: meetingSelections,
    ignoredEvents,
    topCities: clean(rank(cities)),
    topAreas: clean(rank(cells)),
    topPlaces: clean(rank(places)),
    updatedAt: FieldValue.serverTimestamp()
  };

  await db.collection("analytics_results")
    .doc("popular_meeting_areas")
    .set(result);

  console.log("Resultado guardado en analytics_results/popular_meeting_areas");
  console.log(JSON.stringify({ ...result, updatedAt: undefined }, null, 2));
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
