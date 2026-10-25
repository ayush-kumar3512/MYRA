package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.DeviceActionManager
import com.example.ui.AssistantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertEquals("MYRA", appName)
    }

    @Test
    fun `deviceActionManager rejects disallowed arbitrary apps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actionManager = DeviceActionManager(context)
        val result = actionManager.openApp("arbitrary_dangerous_binary_app")
        assertFalse(result.success)
    }

    @Test
    fun `assistantStatus enum states are complete`() {
        val states = AssistantStatus.values()
        assertNotNull(states.find { it == AssistantStatus.LISTENING })
        assertNotNull(states.find { it == AssistantStatus.SPEAKING })
        assertNotNull(states.find { it == AssistantStatus.IDLE })
        assertNotNull(states.find { it == AssistantStatus.CONNECTING })
    }
}
