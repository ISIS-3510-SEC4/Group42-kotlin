package com.example.espoti.data.local

data class MeetingDraft(
    val id: String,
    val ownerUid: String,

    val whatToDo: String = "",
    val date: String? = null,
    val time: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    val invitedFriendIds: Set<String> = emptySet(),

    val votingDeadlineMillis: Long? = null,

    val createdAt: Long,
    val updatedAt: Long
)
