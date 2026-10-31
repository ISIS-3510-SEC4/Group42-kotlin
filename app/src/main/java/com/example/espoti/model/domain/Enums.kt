package com.example.espoti.model.domain

// Enums from the "Modelo de Dominio" component diagram.
// Stored in Firestore by name (Firestore maps enums to/from strings).

enum class ActivityType { COFFEE, PARK, RESTAURANT, BAR, CINEMA, SPORT, OTHER }

enum class FriendshipStatus { PENDING, ACCEPTED, BLOCKED }

enum class Rating(val stars: Int) { ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5) }

enum class PlaceCategory { RESTAURANT, CAFFE, BAR }

// VOTING: created, participants still voting the place (see VotingRules).
enum class StatusMeeting { VOTING, UPCOMING, PREVIOUS, CANCELED, COLLECTING_LOCATIONS, PREPARING_RECOMMENDATIONS, }

enum class UserType { FRIEND, ADMIN }

// How a meeting's place was decided: by the group voting (VotingRules) or by
// the organizer choosing it for everybody (MeetingEventRepository.chooseDirectly).
enum class DecisionMethod { VOTING, DIRECT }
