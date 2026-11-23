package com.mojrucek.app.session

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.mojrucek.app.notification.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestReminderInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val original = GuestReminderEnvProvider.provider
    private val expires = 2_000_000_000_000L
    private val shown = mutableListOf<Int>()
    private var signedIn: String? = "uid-G"
    private var remembered: StoredSession? = StoredSession("usr", "GUEST", "GUEST", null, expires, 0, 0)

    @Before fun setUp() {
        GuestReminderEnvProvider.provider = { GuestReminderEnv({ signedIn }, { remembered }, { shown += it }) }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }
    @After fun tearDown() { GuestReminderEnvProvider.provider = original }

    private fun infos(days: Int) = WorkManager.getInstance(context).getWorkInfosForUniqueWork(GuestReminderWorker.uniqueName(days)).get()

    private fun run(uid: String = "uid-G", planned: Long = expires, days: Int = 3): ListenableWorker.Result = runBlocking {
        val w = TestListenableWorkerBuilder<GuestReminderWorker>(context)
            .setInputData(workDataOf(GuestReminderWorker.KEY_UID to uid, GuestReminderWorker.KEY_EXPIRES to planned, GuestReminderWorker.KEY_DAYS to days)).build()
        (w as CoroutineWorker).doWork()
    }

    @Test fun schedule_createsOneDelayedJobPerReminder_andReplacesOnReplan() {
        val s = WorkManagerGuestReminders(context, nowMs = { 1000 })
        s.schedule("uid-G", expires, 3, 61_000)
        s.schedule("uid-G", expires, 1, 121_000)
        s.schedule("uid-G", expires, 3, 61_000)   // planned again: still one

        assertEquals(1, infos(3).size)
        assertEquals(1, infos(1).size)
        assertEquals(WorkInfo.State.ENQUEUED, infos(3).single().state)
        assertTrue("nothing shown before its time", shown.isEmpty())
    }

    @Test fun cancelAll_removesBothPlannedReminders() {
        val s = WorkManagerGuestReminders(context)
        s.schedule("uid-G", expires, 3, System.currentTimeMillis() + 60_000)
        s.schedule("uid-G", expires, 1, System.currentTimeMillis() + 120_000)
        s.cancelAll()
        assertTrue(infos(3).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(infos(1).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test fun theJob_showsTheReminder_whenStillRight() {
        assertEquals(ListenableWorker.Result.success(), run(days = 3))
        assertEquals(listOf(3), shown)
    }

    @Test fun theJob_staysQuiet_whenAnotherAccountOrLinkedOrNothingKnown() {
        signedIn = "someone-else"; run()
        signedIn = null; run()
        signedIn = "uid-G"; remembered = StoredSession("usr", "REGISTERED", "FREE", null, null, 0, 0); run()
        remembered = null; run()
        remembered = StoredSession("usr", "GUEST", "GUEST", null, expires + 5, 0, 0); run()
        assertTrue(shown.toString(), shown.isEmpty())
    }

    @Test fun theJob_withNoInput_doesNothing() = runBlocking {
        val w = TestListenableWorkerBuilder<GuestReminderWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), (w as CoroutineWorker).doWork())
        assertTrue(shown.isEmpty())
    }

    @Test fun theOwnChannelExists() {
        NotificationHelper.createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = context.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationHelper.GUEST_CHANNEL_ID)
            assertNotNull(ch)
            assertEquals("Гостински период", ch.name.toString())
        }
    }
}
