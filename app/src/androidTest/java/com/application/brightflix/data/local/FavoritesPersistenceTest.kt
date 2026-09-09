package com.application.brightflix.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.application.brightflix.data.local.database.BrightflixDatabase
import com.application.brightflix.data.local.entity.FavoriteMovieEntity
import com.application.brightflix.data.local.entity.RecentlyViewedEntity
import com.application.brightflix.domain.model.MovieType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies persistence against real on-device SQLite, on disk rather than in memory.
 *
 * This is the one thing the JVM suite cannot prove: that favorites genuinely **survive the
 * database being closed and reopened**, which is what "favorites persist after an app
 * restart" actually means to a user. Robolectric's SQLite is a faithful stand-in for query
 * semantics but is not the device's storage.
 *
 * NOTE: this test was written but has **not been executed** — no emulator or device was
 * available in the environment where this project was built. Run it with:
 *
 *     ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FavoritesPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "persistence_test.db"

    private fun openDatabase(): BrightflixDatabase =
        Room.databaseBuilder(context, BrightflixDatabase::class.java, databaseName).build()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun favoritesSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val first = openDatabase()
        first.favoriteDao().insert(
            FavoriteMovieEntity(
                imdbId = "tt0468569",
                title = "The Dark Knight",
                year = "2008",
                type = MovieType.MOVIE,
                posterUrl = null,
                addedAt = 1_000L,
            ),
        )
        first.close()

        // A fresh instance, as if the process had been killed and relaunched.
        val second = openDatabase()
        val favorites = second.favoriteDao().observeAll().first()
        second.close()

        assertEquals(1, favorites.size)
        assertEquals("The Dark Knight", favorites.first().title)
    }

    @Test
    fun recentlyViewedDeduplicatesAcrossSessions() = runBlocking {
        val first = openDatabase()
        first.recentlyViewedDao().upsert(recentlyViewed("tt1", viewedAt = 1_000L))
        first.close()

        val second = openDatabase()
        second.recentlyViewedDao().upsert(recentlyViewed("tt1", viewedAt = 2_000L))
        val entries = second.recentlyViewedDao().observeAll().first()
        second.close()

        assertEquals("re-viewing must not create a second row", 1, entries.size)
        assertEquals(2_000L, entries.first().viewedAt)
    }

    @Test
    fun theDatabaseFileIsActuallyCreatedOnDisk() = runBlocking {
        val database = openDatabase()
        database.favoriteDao().insert(
            FavoriteMovieEntity(
                imdbId = "tt1",
                title = "Anything",
                year = "2005",
                type = MovieType.MOVIE,
                posterUrl = null,
                addedAt = 1L,
            ),
        )
        database.close()

        val file = File(context.getDatabasePath(databaseName).absolutePath)
        assertTrue("the database must be written to disk, not held in memory", file.exists())
    }

    private fun recentlyViewed(imdbId: String, viewedAt: Long) = RecentlyViewedEntity(
        imdbId = imdbId,
        title = "Movie $imdbId",
        year = "2005",
        type = MovieType.MOVIE,
        posterUrl = null,
        viewedAt = viewedAt,
    )
}
