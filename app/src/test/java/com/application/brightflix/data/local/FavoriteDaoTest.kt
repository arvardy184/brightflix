package com.application.brightflix.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.brightflix.data.local.database.BrightflixDatabase
import com.application.brightflix.data.local.entity.FavoriteMovieEntity
import com.application.brightflix.domain.model.MovieType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Favorites are the app's single source of truth for favorite state — every screen derives
 * its heart icons from [FavoriteDao.observeIds]. These tests cover that contract.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

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
    fun `an added favorite is observable by id`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity("tt0468569", "The Dark Knight", addedAt = 1_000))

        assertEquals(listOf("tt0468569"), dao.observeIds().first())
        assertTrue(dao.exists("tt0468569"))
    }

    @Test
    fun `removing a favorite removes it from the observed ids`() = runTest {
        val dao = database.favoriteDao()
        dao.insert(entity("tt1", "Kept", addedAt = 1_000))
        dao.insert(entity("tt2", "Removed", addedAt = 2_000))

        dao.delete("tt2")

        assertEquals(listOf("tt1"), dao.observeIds().first())
        assertFalse(dao.exists("tt2"))
    }

    @Test
    fun `favorites are listed most recently added first`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity("tt1", "Oldest", addedAt = 100))
        dao.insert(entity("tt2", "Newest", addedAt = 300))
        dao.insert(entity("tt3", "Middle", addedAt = 200))

        assertEquals(
            listOf("tt2", "tt3", "tt1"),
            dao.observeAll().first().map { it.imdbId },
        )
    }

    @Test
    fun `favoriting the same movie twice does not duplicate it`() = runTest {
        val dao = database.favoriteDao()

        dao.insert(entity("tt1", "Batman Begins", addedAt = 100))
        dao.insert(entity("tt1", "Batman Begins", addedAt = 200))

        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `exists reports false for an unknown movie`() = runTest {
        assertFalse(database.favoriteDao().exists("tt-does-not-exist"))
    }

    private fun entity(imdbId: String, title: String, addedAt: Long) = FavoriteMovieEntity(
        imdbId = imdbId,
        title = title,
        year = "2008",
        type = MovieType.MOVIE,
        posterUrl = null,
        addedAt = addedAt,
    )
}
