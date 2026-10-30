package com.example.espoti.data.local

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local file storage for an unfinished friend-code search.
 * A draft never authorizes searching or sending a friend request.
 */
interface FriendSearchDraftStore {
    suspend fun read(ownerUid: String): String
    suspend fun save(ownerUid: String, query: String)

    companion object {
        const val MAX_LENGTH = 64
    }
}

/**
 * One private UTF-8 file per account.
 * Temporary-file replacement avoids exposing a partially written draft.
 * Disk operations run on Dispatchers.IO.
 */
class FileFriendSearchDraftStore(
    private val directory: File
) : FriendSearchDraftStore {

    private val mutex = Mutex()

    override suspend fun read(ownerUid: String): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = fileFor(ownerUid)

                if (!file.exists()) {
                    ""
                } else {
                    check(file.length() <= 1024L) {
                        "The saved draft is too large."
                    }

                    file.readText(Charsets.UTF_8)
                        .take(FriendSearchDraftStore.MAX_LENGTH)
                }
            }
        }

    override suspend fun save(
        ownerUid: String,
        query: String
    ) {
        require(query.length <= FriendSearchDraftStore.MAX_LENGTH)

        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = fileFor(ownerUid)

                if (query.isEmpty()) {
                    Files.deleteIfExists(target.toPath())
                } else {
                    check(directory.isDirectory || directory.mkdirs()) {
                        "Couldn't create the draft directory."
                    }

                    val temporary = File(
                        directory,
                        "${target.name}.tmp"
                    )

                    try {
                        FileOutputStream(temporary).use { output ->
                            output.write(query.toByteArray(Charsets.UTF_8))
                            output.fd.sync()
                        }

                        try {
                            Files.move(
                                temporary.toPath(),
                                target.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } catch (
                            unsupported: AtomicMoveNotSupportedException
                        ) {
                            Files.move(
                                temporary.toPath(),
                                target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    } finally {
                        Files.deleteIfExists(temporary.toPath())
                    }
                }
            }
        }
    }

    private fun fileFor(ownerUid: String): File {
        require(ownerUid.isNotBlank())

        // Account ids never become filesystem paths directly.
        val key = MessageDigest.getInstance("SHA-256")
            .digest(ownerUid.toByteArray(Charsets.UTF_8))
            .joinToString("") {
                (it.toInt() and 0xff)
                    .toString(16)
                    .padStart(2, '0')
            }

        return File(directory, "$key.txt")
    }
}

/** Stand-in for ViewModel unit tests without an Android Context. */
class InMemoryFriendSearchDraftStore : FriendSearchDraftStore {
    private val drafts = mutableMapOf<String, String>()

    override suspend fun read(ownerUid: String): String =
        drafts[ownerUid].orEmpty()

    override suspend fun save(
        ownerUid: String,
        query: String
    ) {
        require(query.length <= FriendSearchDraftStore.MAX_LENGTH)

        if (query.isEmpty()) {
            drafts.remove(ownerUid)
        } else {
            drafts[ownerUid] = query
        }
    }
}