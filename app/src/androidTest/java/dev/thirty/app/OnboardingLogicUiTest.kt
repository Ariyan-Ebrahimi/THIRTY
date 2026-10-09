package dev.thirty.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.ui.screens.onboarding.CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingLogicUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categories_containExpected() {
        assertEquals(7, CATEGORIES.size)
        assert(CATEGORIES.contains("Fitness"))
    }

    @Test
    fun titleValidation_emptyFails() {
        assertEquals("Please enter your goal.", ChallengeLogic.validateTitle("  "))
        assertNull(ChallengeLogic.validateTitle("Read 20 minutes"))
    }
}
