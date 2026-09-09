package com.application.brightflix.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.brightflix.data.local.database.BrightflixDatabase
import com.application.brightflix.data.local.entity.RecentlyViewedEntity
import com.application.brightflix.domain.model.MovieType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Recently Viewed has two behaviours that are easy to get wrong and invisible until a user
 * notices: opening the same movie twice must not create two rows, and the list must not
 * grow without bound. Both are enforced here against a real SQLite database.
 */
@RunWith(RobolectricTestRunner::class)
class RecentlyViewedDaoTest {

    private lateinit var database: BrightflixDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BrightflixDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `re-viewing a movie updates it in place instead of duplicating it`() = runTest {
        val dao = database.recentlyViewedDao()

        dao.upsert(entity("tt1", "Batman Begins", viewedAt = 1_000))
        dao.upsert(entity("tt2", "Interstellar", viewedAt = 2_000))
        dao.upsert(entity("tt1", "Batman Begins", viewedAt = 3_000))

        val rows = dao.observeAll().first()

        assertEquals("re-viewing must not create a second row", 2, rows.size)
        assertEquals("the re-viewed movie must move to the top", "tt1", rows.first().imdbId)
        assertEquals(3_000L, rows.first().viewedAt)
    }

    @Test
    fun `rows are ordered most recently viewed first`() = runTest {
        val dao = database.recentlyViewedDao()

        dao.upsert(entity("tt1", "First", viewedAt = 100))
        dao.upsert(entity("tt2", "Second", viewedAt = 300))
        dao.upsert(entity("tt3", "Third", viewedAt = 200))

        assertEquals(
            listOf("tt2", "tt3", "tt1"),
            dao.observeAll().first().map { it.imdbId },
        )
    }

    @Test
    fun `trim keeps exactly the newest rows and discards the oldest`() = runTest {
        val dao = database.recentlyViewedDao()
        repeat(25) { index ->
            dao.upsert(entity("tt$index", "Movie $index", viewedAt = index.toLong()))
        }

        dao.trimTo(20)

        val rows = dao.observeAll().first()
        assertEquals(20, rows.size)
        assertEquals("tt24", rows.first().imdbId)
        assertTrue("the oldest entries must be gone", rows.none { it.imdbId == "tt0" })
        assertTrue(rows.none { it.imdbId == "tt4" })
        assertTrue("the newest entries must survive", rows.any { it.imdbId == "tt5" })
    }

    @Test
    fun `trim is a no-op when the table is under the limit`() = runTest {
        val dao = database.recentlyViewedDao()
        repeat(3) { dao.upsert(entity("tt$it", "Movie $it", viewedAt = it.toLong())) }

        dao.trimTo(20)

        assertEquals(3, dao.observeAll().first().size)
    }

    private fun entity(imdbId: String, title: String, viewedAt: Long) = RecentlyViewedEntity(
        imdbId = imdbId,
        title = title,
        year = "2005",
        type = MovieType.MOVIE,
        posterUrl = null,
        viewedAt = viewedAt,
    )
}
