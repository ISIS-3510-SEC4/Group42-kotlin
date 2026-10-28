package com.example.espoti.viewmodel

import com.example.espoti.data.connectivity.ConnectivityObserver
import com.example.espoti.data.repository.AccountRepository
import com.example.espoti.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// DELETE ACCOUNT VIEW MODEL TEST
// ----------------------------------------------------------------------------
// Password re-check -> Firestore data -> Auth account -> local data, in that
// order; any failing step stops the rest. Offline nothing is attempted.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private val uid = "uid-1"

    /** Records which steps ran, in order. */
    private val steps = mutableListOf<String>()

    private inner class FakeAuth(
        private val reauthResult: Result<Unit> = Result.success(Unit),
        private val deleteResult: Result<Unit> = Result.success(Unit)
    ) : AuthRepository(authProvider = { error("no auth in tests") }) {
        override val currentUserId: String? = uid
        override suspend fun reauthenticate(password: String): Result<Unit> {
            steps += "reauth:$password"
            return reauthResult
        }
        override suspend fun deleteCurrentUser(): Result<Unit> {
            steps += "deleteAuth"
            return deleteResult
        }
        override fun logout() {
            steps += "logout"
        }
    }

    private inner class FakeAccount(
        private val result: Result<Int> = Result.success(2)
    ) : AccountRepository(dbProvider = { error("no Firestore in tests") }) {
        override suspend fun deleteUserData(uid: String): Result<Int> {
            steps += "deleteData:$uid"
            return result
        }
    }

    private class FakeConnectivity(online: Boolean) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    private fun viewModel(
        auth: AuthRepository = FakeAuth(),
        account: AccountRepository = FakeAccount(),
        connectivity: ConnectivityObserver = FakeConnectivity(true)
    ) = DeleteAccountViewModel(
        authRepository = auth,
        accountRepository = account,
        connectivity = connectivity,
        forgetLocalData = { steps += "forgetLocal:$it" }
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirm_deletesEverythingInOrder_andNavigates() {
        val vm = viewModel()
        var deleted = false
        vm.onDeleteClick()
        vm.onConfirm("secret") { deleted = true }

        assertEquals(
            listOf("reauth:secret", "deleteData:uid-1", "deleteAuth", "forgetLocal:uid-1", "logout"),
            steps
        )
        assertTrue(deleted)
        assertFalse(vm.showDialog.value)
        assertFalse(vm.isDeleting.value)
    }

    @Test
    fun passwordCheckFails_deletesNothing() {
        // Firebase's own exceptions can't be built on the JVM (they need
        // android.text.TextUtils), so a generic failure stands in for them.
        val vm = viewModel(auth = FakeAuth(reauthResult = Result.failure(IllegalStateException("bad"))))
        var deleted = false
        vm.onDeleteClick()
        vm.onConfirm("bad") { deleted = true }

        assertEquals(listOf("reauth:bad"), steps)
        assertEquals("Something went wrong. Please try again.", vm.error.value)
        assertFalse(deleted)
        assertTrue(vm.showDialog.value)
    }

    @Test
    fun firestoreFailure_keepsAuthAccount() {
        val vm = viewModel(account = FakeAccount(Result.failure(IllegalStateException("boom"))))
        vm.onDeleteClick()
        vm.onConfirm("secret") {}

        assertEquals(listOf("reauth:secret", "deleteData:uid-1"), steps)
        assertEquals("Couldn't delete your data. Please try again.", vm.error.value)
    }

    @Test
    fun authDeleteFailure_saysDataWasDeleted() {
        val vm = viewModel(auth = FakeAuth(deleteResult = Result.failure(IllegalStateException("boom"))))
        vm.onDeleteClick()
        vm.onConfirm("secret") {}

        assertFalse("forgetLocal:uid-1" in steps)
        assertEquals(
            "Your data was deleted, but your account couldn't be closed. Please try again.",
            vm.error.value
        )
    }

    @Test
    fun blankPassword_isRejected() {
        val vm = viewModel()
        vm.onDeleteClick()
        vm.onConfirm("  ") {}

        assertTrue(steps.isEmpty())
        assertEquals("Please enter your password.", vm.error.value)
    }

    @Test
    fun offline_nothingIsAttempted_andMessageClearsOnReconnection() {
        val connectivity = FakeConnectivity(false)
        val vm = viewModel(connectivity = connectivity)
        vm.onDeleteClick()
        assertEquals(OfflineMessages.DELETE_ACCOUNT, vm.error.value)

        vm.onConfirm("secret") {}
        assertTrue(steps.isEmpty())

        connectivity.isOnline.value = true
        assertNull(vm.error.value)
    }

    @Test
    fun dismiss_closesDialog() {
        val vm = viewModel()
        vm.onDeleteClick()
        vm.onDismiss()
        assertFalse(vm.showDialog.value)
    }
}
