package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SmartMoneyManagerApp
import com.example.ui.viewmodel.FinanceViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart Money Manager", appName)
  }

  @Test
  fun `launch main activity`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assert(activity != null)
      }
    }
  }

  @Test
  fun `full login flow and dashboard rendering`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = FinanceViewModel(context)

    composeTestRule.setContent {
      SmartMoneyManagerApp(viewModel = viewModel)
    }

    println("BEFORE SELECT_BHAVESH:")
    try { println(composeTestRule.onRoot().printToString()) } catch (e: Exception) { e.printStackTrace() }

    // Step 1: Select Profile ("Bhavesh")
    composeTestRule.onNodeWithTag("select_bhavesh_button").performClick()
    composeTestRule.waitForIdle()

    println("AFTER SELECT_BHAVESH:")
    try { println(composeTestRule.onRoot().printToString()) } catch (e: Exception) { e.printStackTrace() }

    // Step 2: Input PIN
    composeTestRule.onNodeWithTag("pin_input_field").performTextInput("1004")
    composeTestRule.waitForIdle()

    println("AFTER PIN INPUT:")
    try { println(composeTestRule.onRoot().printToString()) } catch (e: Exception) { e.printStackTrace() }

    // Step 3: Click Authenticate
    composeTestRule.onNodeWithTag("pin_submit_button").performClick()
    composeTestRule.waitForIdle()

    // Wait until login completes
    repeat(25) {
      if (viewModel.currentUser.value != null) return@repeat
      Thread.sleep(100)
      composeTestRule.mainClock.advanceTimeBy(100)
    }

    println("DIAGNOSTIC STATE:")
    println("currentUser: ${viewModel.currentUser.value}")
    println("loginError: ${viewModel.loginError.value}")
    println("activeMonthId: ${viewModel.activeMonthId.value}")
    println("isFirebaseConnected: ${viewModel.isFirebaseConnected.value}")

    composeTestRule.waitForIdle()

    // Verify we transitioned past the login screen (should see sheet or calendar tab)
    composeTestRule.onNodeWithTag("nav_tab_sheet").assertExists()
  }
}

