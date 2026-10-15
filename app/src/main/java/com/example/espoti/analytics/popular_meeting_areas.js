// BQ: "What are the most frequently selected meeting areas and locations
// according to users' historical meeting data?"
//
// Run (same setup as recommendation_latency.js):  node popular_meeting_areas.js
//
// Sources (each meeting is counted once):
//  1) meetings/{id} with a decided placeId (not CANCELED) - the source of
//     truth. A meeting is created in VOTING by CreateMeetingViewModel and gets
//     its placeId when the voting closes (MeetingEventRepository.closeVotingIfDone).
//  2) PLACE_SELECTED events (one per vote) only when their meeting does not
//     exist in (1) - i.e. old events recorded before meetings were stored.
//     Events of existing meetings are skipped: decided ones are already
//     counted, VOTING ones have no place yet and CANCELED ones never happened.
// Ranks areas (city and ~1 km grid cell) and places, and stores the result in
// analytics_results/popular_meeting_areas.
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

// The area cell is computed ONLY here (the app just sends coordinates), so
// every source uses the same rounding. 2 decimals ~ 1.1 km.
// (0, 0) is the Location default for places without coordinates.
function areaCellOf(latitude, longitude) {
  if (typeof latitude !== "number" || typeof longitude !== "number") return null;
  if (latitude === 0 && longitude === 0) return null;
  return `${latitude.toFixed(2)},${longitude.toFixed(2)}`;
}

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
  let duplicateEvents = 0;

  const register = ({ placeId, userId, placeName, category, cityName, areaCell, address }) => {
    totalSelections++;
    const place = bump(places, placeId, { info: { placeId, placeName, category, cityName, address } });
    place.count++;
    if (userId) place.users.add(userId);

    if (cityName) {
      const city = bump(cities, cityName, { info: { cityName } });
      city.count++;
      if (userId) city.users.add(userId);
    }
    if (areaCell) {
      const cell = bump(cells, areaCell, { info: { areaCell, cityName } });
      cell.count++;
      if (userId) cell.users.add(userId);
    }
  };

  // 1) Historical meetings stored in Firestore with a chosen place.
  const meetings = await db.collection("meetings").get();
  const placeCache = new Map();
  // Every stored meeting: their PLACE_SELECTED events never count on their own.
  const knownMeetingIds = new Set(meetings.docs.map(document => document.id));
  let meetingSelections = 0;
  let votingMeetings = 0;

  for (const document of meetings.docs) {
    const meeting = document.data();
    if (meeting.status === "CANCELED") continue;
    if (meeting.status === "VOTING") {
      votingMeetings++;
      continue;
    }
    if (!meeting.placeId) continue;

    if (!placeCache.has(meeting.placeId)) {
      const snap = await db.collection("places").doc(meeting.placeId).get();
      placeCache.set(meeting.placeId, snap.exists ? snap.data() : null);
    }
    const place = placeCache.get(meeting.placeId);
    if (!place) continue;

    const loc = place.location || {};
    meetingSelections++;
    register({
      placeId: meeting.placeId,
      userId: meeting.creatorId,
      placeName: place.name,
      category: place.category,
      cityName: loc.cityName,
      areaCell: areaCellOf(loc.latitude, loc.longitude),
      address: loc.address
    });
  }

  // 2) PLACE_SELECTED events whose meeting is not stored (legacy events).
  const events = await db.collection("analytics_events")
    .where("eventType", "==", "PLACE_SELECTED")
    .get();

  for (const document of events.docs) {
    const event = document.data();
    if (!event.placeId) {
      ignoredEvents++;
      continue;
    }
    if (event.meetingId && knownMeetingIds.has(event.meetingId)) {
      duplicateEvents++;
      continue;
    }
    const m = event.metadata || {};
    register({
      placeId: event.placeId,
      userId: event.userId,
      placeName: m.placeName,
      category: m.category,
      cityName: m.cityName,
      areaCell: areaCellOf(m.latitude, m.longitude),
      address: m.address
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
    selectionsFromEvents: totalSelections - meetingSelections,
    duplicateEvents,
    votingMeetings,
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
