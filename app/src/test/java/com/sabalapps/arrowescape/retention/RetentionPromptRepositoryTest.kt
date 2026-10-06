package com.sabalapps.arrowescape.retention

import com.sabalapps.arrowescape.progress.InMemoryProgressStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPromptRepositoryTest {

    private val store = InMemoryProgressStore()

    @Test
    fun `a fresh install has never asked anything`() {
        assertEquals(RetentionPromptState(), RetentionPromptRepository(store).state.value)
    }

    @Test
    fun `showing a prompt is recorded with when and at which level count`() {
        val repo = RetentionPromptRepository(store)
        repo.recordPromptShown(nowMs = 5_000L, completionCount = 6)

        val state = repo.state.value
        assertEquals(1, state.promptCount)
        assertEquals(5_000L, state.lastPromptAtMs)
        assertEquals(6, state.lastPromptCompletionCount)
        assertTrue(state.ratePromptShown)
        assertFalse("showing a prompt is not acting on it", state.rateActionTapped)
        assertFalse(state.shareActionTapped)
    }

    @Test
    fun `the count grows with every prompt shown`() {
        val repo = RetentionPromptRepository(store)
        repo.recordPromptShown(1L, 1)
        repo.recordPromptShown(2L, 2)
        assertEquals(2, repo.state.value.promptCount)
        assertEquals(2L, repo.state.value.lastPromptAtMs)
    }

    @Test
    fun `rate and share actions are recorded separately and stick`() {
        val repo = RetentionPromptRepository(store)
        repo.recordRateActionTapped()
        assertTrue(repo.state.value.rateActionTapped)
        assertFalse(repo.state.value.shareActionTapped)
        repo.recordShareActionTapped()
        assertTrue(repo.state.value.shareActionTapped)
        repo.recordRateActionTapped()
        assertTrue("it never un-sets", repo.state.value.rateActionTapped)
    }

    @Test
    fun `everything survives a restart`() {
        RetentionPromptRepository(store).apply {
            recordPromptShown(9_000L, 12)
            recordRateActionTapped()
            recordShareActionTapped()
        }
        val reloaded = RetentionPromptRepository(store).state.value
        assertEquals(1, reloaded.promptCount)
        assertEquals(9_000L, reloaded.lastPromptAtMs)
        assertEquals(12, reloaded.lastPromptCompletionCount)
        assertTrue(reloaded.ratePromptShown && reloaded.rateActionTapped && reloaded.shareActionTapped)
    }

    @Test
    fun `a corrupt record reads as never asked, and is cleared`() {
        for (bad in listOf("", "garbage", "2|0|0|0|0|0|0", "1|-1|0|0|0|0|0", "1|0|-5|0|0|0|0", "1|0|0|0|x|0|0", "1|0|0|0|0|0")) {
            val broken = InMemoryProgressStore(mapOf("retention_prompts" to bad))
            assertEquals("for '$bad'", RetentionPromptState(), RetentionPromptRepository(broken).state.value)
            assertNull("for '$bad'", broken.getString("retention_prompts"))
        }
    }

    @Test
    fun `there is no field that claims a rating was submitted`() {
        // Android cannot tell us; the record must not pretend to know.
        val names = RetentionPromptState::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { it.contains("hasrated") || it.contains("israted") || it.contains("submitted") })
    }
}
