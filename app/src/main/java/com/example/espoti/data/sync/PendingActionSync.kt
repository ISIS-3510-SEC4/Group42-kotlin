package com.example.espoti.data.sync

import com.example.espoti.data.local.FriendRequestState
import com.example.espoti.data.local.PendingAction
import com.example.espoti.data.local.PendingActionStore
import com.example.espoti.data.local.StoredAction
import com.example.espoti.data.repository.FriendRequestSendOutcome
import com.example.espoti.data.repository.FriendshipRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.data.repository.MeetingEventRepository
import com.example.espoti.data.repository.MeetingPreparationService
import com.example.espoti.data.repository.PlaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout


/**
 * Sends the offline changes of one user to Firestore, oldest first. Kept apart
 * from [SyncWorker] so it can be unit-tested without WorkManager.
 *
 * A sent profile update is removed. A failed one stays and its attempts grow;
 * after [maxAttempts] it is dropped, preserving the existing profile behavior.
 *
 * Friend requests keep their final result locally for the Friends screen.
 * Completed or failed requests are skipped by subsequent sync runs.
 *
 * Each call has a timeout because a Firestore write only finishes when the
 * server confirms it: on a network that says "connected" but doesn't deliver,
 * it would otherwise wait forever.
 */
class PendingActionSync(
    private val store: PendingActionStore,
    private val userRepository: UserRepository,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val meetingRepository: MeetingEventRepository = MeetingEventRepository(),
    private val meetingPreparationService: MeetingPreparationService =
        MeetingPreparationService(
            meetingRepository = meetingRepository,
            placeRepository = PlaceRepository()
        ),
    private val friendshipRepository: FriendshipRepository =
        FriendshipRepository()
) {

    /** True when nothing is left to retry later. */
    suspend fun syncFor(uid: String): Boolean {
        var retryLater = false

        for (stored in store.pendingFor(uid)) {
            currentCoroutineContext().ensureActive()

            when (val action = stored.action) {
                is PendingAction.UpdateProfile -> {
                    val sent = sendProfile(action)

                    currentCoroutineContext().ensureActive()

                    when {
                        sent -> store.remove(stored.id)

                        stored.attempts + 1 >= maxAttempts ->
                            store.remove(stored.id)

                        else -> {
                            store.recordFailure(stored.id)
                            retryLater = true
                        }
                    }
                }

                is PendingAction.SendFriendRequest -> {
                    // Keep delivery results until the user dismisses them.
                    // Only pending requests may be sent.
                    if (action.state == FriendRequestState.PENDING) {
                        val finished = syncFriendRequest(stored, action)

                        if (!finished) {
                            retryLater = true
                        }
                    }
                }
                is PendingAction.CreateMeeting -> {
                    val result = try {
                        withTimeout(timeoutMillis) {
                            meetingRepository.createMeeting(action.meeting)
                        }
                    } catch (e: TimeoutCancellationException) {
                        null
                    }

                    currentCoroutineContext().ensureActive()

                    if (result?.isSuccess == true) {
                        // Firestore confirmed the meeting creation.
                        store.remove(stored.id)
                    } else {
                        // Keep the meeting pending. Never discard it
                        // just because it exceeded maxAttempts.
                        store.recordFailure(stored.id)
                        retryLater = true
                    }
                }
                is PendingAction.SubmitStartingLocation -> {
                    val result = try {
                        withTimeout(timeoutMillis) {
                            meetingRepository.saveStartingLocation(
                                meetingId = action.meetingId,
                                uid = action.ownerUid,
                                location = action.location
                            )
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }

                    currentCoroutineContext().ensureActive()

                    if (result?.isSuccess == true) {
                        val allCollected = result.getOrThrow()

                        // The location has been confirmed by Firestore.
                        // It must not be submitted again.
                        store.remove(stored.id)

                        if (allCollected) {
                            val preparationResult = try {
                                withTimeout(timeoutMillis) {
                                    meetingPreparationService.prepareRecommendations(
                                        action.meetingId
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }

                            if (preparationResult.isFailure) {
                                retryLater = true
                            }
                        }
                    } else {
                        store.recordFailure(stored.id)
                        retryLater = true
                    }
                }

                is PendingAction.CastVote -> {
                    val result = try {
                        withTimeout(timeoutMillis) {
                            meetingRepository.castVote(
                                meetingId = action.meetingId,
                                uid = action.ownerUid,
                                placeId = action.placeId
                            )
                        }
                    } catch (timeout: TimeoutCancellationException) {
                        null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        null
                    }

                    currentCoroutineContext().ensureActive()

                    if (result?.isSuccess == true) {
                        // Firestore confirmed the vote.
                        store.remove(stored.id)

                        // Try to close voting if this was the last required vote.
                        try {
                            withTimeout(timeoutMillis) {
                                meetingRepository.closeVotingIfDone(
                                    action.meetingId,
                                    System.currentTimeMillis()
                                )
                            }
                        } catch (timeout: TimeoutCancellationException) {
                            // The vote is already confirmed.
                            // A later meeting load can check whether voting should close.
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            // Do not resend an already-confirmed vote.
                        }
                    } else {
                        store.recordFailure(stored.id)
                        retryLater = true
                    }
                }
            }
        }

        return !retryLater
    }

    private suspend fun sendProfile(
        action: PendingAction.UpdateProfile
    ): Boolean = try {
        withTimeout(timeoutMillis) {
            userRepository.updateProfileFields(
                action.ownerUid,
                action.description,
                action.preferences
            )
        }.isSuccess
    } catch (timeout: TimeoutCancellationException) {
        false
    }

    private suspend fun syncFriendRequest(
        stored: StoredAction,
        action: PendingAction.SendFriendRequest
    ): Boolean {
        val result = try {
            withTimeout(timeoutMillis) {
                friendshipRepository.sendRequestSafely(
                    fromUid = action.ownerUid,
                    toUid = action.targetUid
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            Result.failure<FriendRequestSendOutcome>(timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure<FriendRequestSendOutcome>(error)
        }

        currentCoroutineContext().ensureActive()

        if (result.isFailure) {
            if (stored.attempts + 1 >= maxAttempts) {
                // Keep the failure visible instead of silently deleting it.
                store.updateFriendRequest(
                    stored.id,
                    action.copy(
                        state = FriendRequestState.FAILED,
                        resultMessage =
                            "Couldn't send the request to ${action.targetName}. Please try again."
                    )
                )

                return true
            }

            store.recordFailure(stored.id)
            return false
        }

        val outcome = result.getOrThrow()

        val message = when (outcome) {
            FriendRequestSendOutcome.SENT ->
                "Friend request sent to ${action.targetName}."

            FriendRequestSendOutcome.ALREADY_PENDING ->
                "There's already a pending request with ${action.targetName}."

            FriendRequestSendOutcome.ALREADY_FRIENDS ->
                "You're already friends with ${action.targetName}."

            FriendRequestSendOutcome.BLOCKED ->
                "You can't send a request to ${action.targetName} because there's a block."

            FriendRequestSendOutcome.USER_NOT_FOUND ->
                "The user ${action.targetName} is no longer available."
        }

        // These business outcomes cannot be solved by retrying the request.
        val rejected =
            outcome == FriendRequestSendOutcome.BLOCKED ||
                    outcome == FriendRequestSendOutcome.USER_NOT_FOUND

        store.updateFriendRequest(
            stored.id,
            action.copy(
                state = if (rejected) {
                    FriendRequestState.FAILED
                } else {
                    FriendRequestState.COMPLETED
                },
                resultMessage = message
            )
        )

        return true
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val TIMEOUT_MILLIS = 20_000L
    }
}