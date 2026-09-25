package com.example.espoti.viewmodel

import com.example.espoti.data.repository.AuthRepository
import com.example.espoti.data.repository.UserRepository
import com.example.espoti.model.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ============================================================================
// AUTH VIEW MODELS TEST
// ----------------------------------------------------------------------------
// Fake repositories replace Firebase, so nothing here touches the network.
// Dispatchers.Main is swapped for an unconfined test dispatcher so that
// viewModelScope.launch runs immediately.
// ============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private class FakeAuthRepository(
        private val registerResult: Result<String> = Result.success("uid-1"),
        private val loginResult: Result<String> = Result.success("uid-1")
    ) : AuthRepository() {
        var deleted = false
        override suspend fun register(email: String, password: String) = registerResult
        override suspend fun login(email: String, password: String) = loginResult
        override suspend fun deleteCurrentUser(): Result<Unit> {
            deleted = true
            return Result.success(Unit)
        }
    }

    private class FakeUserRepository(
        private val saveResult: Result<Unit> = Result.success(Unit)
    ) : UserRepository() {
        var saved: User? = null
        override suspend fun saveUser(user: User): Result<Unit> {
            saved = user
            return saveResult
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---- Login ----------------------------------------------------------

    @Test
    fun login_withBlankEmail_flagsEmailAndSkipsSuccess() {
        val vm = LoginViewModel(FakeAuthRepository())
        var succeeded = false
        vm.login { succeeded = true }
        assertTrue(vm.emailError.value)
        assertFalse(succeeded)
        assertEquals("Please enter your email.", vm.noticeMessage.value)
    }

    @Test
    fun login_withValidFields_callsSuccess() {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onEmailChange("ana@example.com")
        vm.onPasswordChange("secret")
        var succeeded = false
        vm.login { succeeded = true }
        assertTrue(succeeded)
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun login_whenFirebaseRejects_showsNoticeAndSkipsSuccess() {
        val vm = LoginViewModel(FakeAuthRepository(loginResult = Result.failure(RuntimeException("boom"))))
        vm.onEmailChange("ana@example.com")
        vm.onPasswordChange("secret")
        var succeeded = false
        vm.login { succeeded = true }
        assertFalse(succeeded)
        assertFalse(vm.isLoading.value)
        assertEquals("Something went wrong. Please try again.", vm.noticeMessage.value)
    }

    // ---- Register -------------------------------------------------------

    private fun RegisterViewModel.fillValidForm() {
        onEmailChange("ana@example.com")
        onConfirmEmailChange("ana@example.com")
        onPasswordChange("secret1")
        onConfirmPasswordChange("secret1")
    }

    @Test
    fun register_withMismatchedPasswords_flagsConfirmPassword() {
        val vm = RegisterViewModel(FakeAuthRepository(), FakeUserRepository())
        vm.onEmailChange("ana@example.com")
        vm.onConfirmEmailChange("ana@example.com")
        vm.onPasswordChange("secret1")
        vm.onConfirmPasswordChange("secret2")
        var succeeded = false
        vm.register { succeeded = true }
        assertTrue(vm.confirmPasswordError.value)
        assertFalse(succeeded)
    }

    @Test
    fun register_withValidFields_savesProfileAndCallsSuccess() {
        val users = FakeUserRepository()
        val vm = RegisterViewModel(FakeAuthRepository(), users)
        vm.fillValidForm()
        var succeeded = false
        vm.register { succeeded = true }
        assertTrue(succeeded)
        assertEquals("uid-1", users.saved?.id)
        assertEquals("ana", users.saved?.username)
        assertEquals(6, users.saved?.code?.length)
    }

    @Test
    fun register_whenProfileSaveFails_rollsBackAccount() {
        val auth = FakeAuthRepository()
        val vm = RegisterViewModel(auth, FakeUserRepository(Result.failure(RuntimeException("boom"))))
        vm.fillValidForm()
        var succeeded = false
        vm.register { succeeded = true }
        assertFalse(succeeded)
        assertTrue(auth.deleted)
        assertFalse(vm.isLoading.value)
    }
}
