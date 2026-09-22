package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var shoppingDao: ShoppingDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shoppingDao = db.shoppingDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testChecklistInsertAndDelete() = runBlocking {
        val checklist = Checklist(
            name = "Test Checklist",
            familyId = "family_test_123",
            isPrivate = false
        )
        val id = shoppingDao.insertChecklist(checklist)
        assertTrue(id > 0)

        val retrieved = shoppingDao.getChecklistById(id.toInt())
        assertNotNull(retrieved)
        assertEquals("Test Checklist", retrieved?.name)
        assertEquals("family_test_123", retrieved?.familyId)

        shoppingDao.deleteChecklist(retrieved!!)
        val retrievedAfterDelete = shoppingDao.getChecklistById(id.toInt())
        assertNull(retrievedAfterDelete)
    }
}
