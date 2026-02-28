package com.eliasjunior.textpower.ui.ocr

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eliasjunior.textpower.ui.theme.TextPowerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class OcrScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appLaunch_showsOcrScreenAndPrimaryActions() {
        composeRule.setContent {
            TextPowerTheme {
                OcrScreen(
                    state = OcrUiState(),
                    onPickImage = {},
                    onScanDocument = {},
                    onRecognize = {},
                    onPlayText = {},
                    onPauseText = {},
                    onStopText = {},
                    onSetSpeechRate = {},
                    onSetPitch = {},
                    onSetVoiceName = {},
                    onCopyText = {},
                    onShareText = {},
                    onSaveSession = {},
                    onOpenSession = {},
                    onDeleteSession = {},
                    onClearHistory = {},
                    onSetPreprocessEnabled = {},
                    onSetFilterByBlocks = {},
                    onSetCleaningLevel = {},
                    onSetMultiPageScanEnabled = {}
                )
            }
        }

        composeRule.onNodeWithTag("ocr_title").assertIsDisplayed()
        composeRule.onNodeWithTag("pick_image_button").assertIsDisplayed()
        composeRule.onNodeWithTag("scan_document_button").assertIsDisplayed()
        composeRule.onNodeWithTag("recognize_text_button").assertIsDisplayed()
    }

    @Test
    fun multiPageToggle_isVisibleAndToggles() {
        val enabled = mutableStateOf(true)
        composeRule.setContent {
            TextPowerTheme {
                OcrScreen(
                    state = OcrUiState(multiPageScanEnabled = enabled.value),
                    onPickImage = {},
                    onScanDocument = {},
                    onRecognize = {},
                    onPlayText = {},
                    onPauseText = {},
                    onStopText = {},
                    onSetSpeechRate = {},
                    onSetPitch = {},
                    onSetVoiceName = {},
                    onCopyText = {},
                    onShareText = {},
                    onSaveSession = {},
                    onOpenSession = {},
                    onDeleteSession = {},
                    onClearHistory = {},
                    onSetPreprocessEnabled = {},
                    onSetFilterByBlocks = {},
                    onSetCleaningLevel = {},
                    onSetMultiPageScanEnabled = { enabled.value = it }
                )
            }
        }

        val toggleNode = composeRule.onNodeWithTag("multi_page_toggle").assertIsDisplayed()
        val wasOn = runCatching {
            toggleNode.assertIsOn()
            true
        }.getOrElse {
            toggleNode.assertIsOff()
            false
        }

        toggleNode.performClick()

        composeRule.waitForIdle()

        if (wasOn) {
            toggleNode.assertIsOff()
        } else {
            toggleNode.assertIsOn()
        }
    }
}
