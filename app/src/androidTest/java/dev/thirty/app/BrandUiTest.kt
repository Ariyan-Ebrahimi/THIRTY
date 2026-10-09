package dev.thirty.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.thirty.app.ui.components.BrandHeader
import dev.thirty.app.ui.theme.ThirtyTheme
import org.junit.Rule
import org.junit.Test

class BrandUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun brandHeader_showsThirty() {
        composeRule.setContent {
            ThirtyTheme(darkTheme = true) { BrandHeader() }
        }
        composeRule.onNodeWithText("THIRTY").assertIsDisplayed()
        composeRule.onNodeWithText("30 DAYS. ONE CHANGE.").assertIsDisplayed()
    }
}
