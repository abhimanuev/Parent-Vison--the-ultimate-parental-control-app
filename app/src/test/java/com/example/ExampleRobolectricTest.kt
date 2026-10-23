package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GuardianRepository
import com.example.data.model.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ScreenTime Guardian", appName)
  }

  @Test
  fun `test guardian repository rule management and pin verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = GuardianRepository(context)

    // Test default pin
    assertTrue(repo.verifyPin("1234"))
    assertFalse(repo.verifyPin("0000"))

    // Test adding and unblocking rule
    repo.setAppRestricted("com.konami.pesam", "eFootball", true)
    val rule = repo.getRule("com.konami.pesam")
    assertTrue(rule != null && rule.isRestricted)

    repo.markAppBlocked("com.konami.pesam")
    val blockedRule = repo.getRule("com.konami.pesam")
    assertTrue(blockedRule != null && blockedRule.isBlocked)

    repo.unblockApp("com.konami.pesam")
    val unblockedRule = repo.getRule("com.konami.pesam")
    assertTrue(unblockedRule != null && !unblockedRule.isBlocked)
  }
}
