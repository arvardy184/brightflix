package com.application.brightflix.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.application.brightflix.core.result.AppError
import com.application.brightflix.core.result.AppResult
import com.application.brightflix.core.result.RefreshState
import com.application.brightflix.domain.model.CuratedCollections
import com.application.brightflix.data.local.database.BrightflixDatabase
import com.application.brightflix.data.remote.MovieRemoteDataSource
import com.application.brightflix.data.remote.dto.SearchResponseDto
import com.application.brightflix.domain.model.MovieType
import com.application.brightflix.testing.FakeOmdbApi
import com.application.brightflix.testing.FakeTimeProvider
import com.application.brightflix.testing.detailResponseOf
import com.application.brightflix.testing.searchResponseOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The offline-first contract.
 *
 * Two behaviours matter most and are easy to regress:
 *
 *  1. A fresh cache must not issue a network request — this is what keeps the app inside
 *     OMDb's daily quota.
 *  2. A failed refresh must not destroy cached content — the user keeps seeing the movie
 *     rather than an error screen.
 */
@RunWith(RobolectricTestRunner::class)
class MovieRepositoryTest {

    private lateinit var database: BrightflixDatabase
    private lateinit var repository: MovieRepositoryImpl
    private val api = FakeOmdbApi()
    private val time = FakeTimeProvider()

    private val eightDays = TimeUnit.DAYS.toMillis(8)
    private val oneDay = TimeUnit.DAYS.toMillis(1)
    private val twoDays = TimeUnit.DAYS.toMillis(2)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BrightflixDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = MovieRepositoryImpl(
            remoteDataSource = MovieRemoteDataSource(api = api, apiKey = "test-key"),
            movieDetailDao = database.movieDetailDao(),
            collectionDao = database.collectionDao(),
            timeProvider = time,
        )
    }

    @After
    fun tearDown() = database.close()

    // region detail caching

    @Test
    fun `a cache inside its TTL issues no network request`() = runTest {
        api.detailResponse = detailResponseOf("tt1", "Batman Begins")
        repository.refreshMovieDetail("tt1", force = false)
        assertEquals(1, api.detailCallCount)

        time.advanceBy(oneDay) // TTL is 7 days
        repository.refreshMovieDetail("tt1", force = false)

        assertEquals("a fresh cache must not hit the network", 1, api.detailCallCount)
    }

    @Test
    fun `a cache past its TTL triggers a refresh`() = runTest {
        api.detailResponse = detailResponseOf("tt1", "Batman Begins")
        repository.refreshMovieDetail("tt1", force = false)

        time.advanceBy(eightDays)
        repository.refreshMovieDetail("tt1", force = false)

        assertEquals(2, api.detailCallCount)
    }

    @Test
    fun `force bypasses a fresh TTL so pull-to-refresh always works`() = runTest {
        api.detailResponse = detailResponseOf("tt1", "Batman Begins")
        repository.refreshMovieDetail("tt1", force = false)

        repository.refreshMovieDetail("tt1", force = true)

        assertEquals(2, api.detailCallCount)
    }

    @Test
    fun `a failed refresh keeps cached content and reports the failure alongside it`() = runTest {
        api.detailResponse = detailResponseOf("tt1", "Batman Begins")
        repository.refreshMovieDetail("tt1", force = false)

        api.throwOnCall = UnknownHostException("offline")
        val result = repository.refreshMovieDetail("tt1", force = true)

        assertEquals(AppResult.Failure(AppError.Offline), result)

        val cached = repository.observeMovieDetail("tt1").first()
        assertNotNull("content must survive a failed refresh", cached.data)
        assertEquals("Batman Begins", cached.data?.title)
        assertEquals(RefreshState.Failed(AppError.Offline), cached.refresh)
        assertNotNull("staleness needs a timestamp to render", cached.lastUpdatedAt)
    }

    @Test
    fun `a failed refresh with no cache surfaces no data and the error`() = runTest {
        api.throwOnCall = UnknownHostException("offline")

        repository.refreshMovieDetail("tt404", force = false)

        val cached = repository.observeMovieDetail("tt404").first()
        assertNull(cached.data)
        assertEquals(RefreshState.Failed(AppError.Offline), cached.refresh)
    }

    @Test
    fun `a successful refresh clears a previous failure state`() = runTest {
        api.throwOnCall = UnknownHostException("offline")
        repository.refreshMovieDetail("tt1", force = false)

        api.throwOnCall = null
        api.detailResponse = detailResponseOf("tt1", "Batman Begins")
        repository.refreshMovieDetail("tt1", force = true)

        val cached = repository.observeMovieDetail("tt1").first()
        assertEquals(RefreshState.Idle, cached.refresh)
        assertEquals("Batman Begins", cached.data?.title)
    }

    // endregion

    // region collections

    @Test
    fun `collections are null before the first refresh and populated after`() = runTest {
        val collectionId = CuratedCollections.ALL.first().id

        assertNull(
            "never-fetched must be distinguishable from fetched-and-empty",
            repository.observeCollection(collectionId).first().data,
        )

        api.searchResponse = searchResponseOf("tt1" to "Batman Begins", "tt2" to "The Dark Knight")
        repository.refreshCollections(force = false)

        val cached = repository.observeCollection(collectionId).first()
        assertEquals(2, cached.data?.size)
        assertNotNull(cached.lastUpdatedAt)
    }

    @Test
    fun `a fetched but empty collection is not mistaken for never-fetched`() = runTest {
        val collectionId = CuratedCollections.ALL.first().id
        api.searchResponse = SearchResponseDto(search = emptyList(), totalResults = "0", response = "True")

        repository.refreshCollections(force = false)

        val cached = repository.observeCollection(collectionId).first()
        assertEquals(emptyList<Any>(), cached.data)
        assertNotNull(cached.lastUpdatedAt)
    }

    @Test
    fun `a collection refresh replaces rows rather than accumulating them`() = runTest {
        val collectionId = CuratedCollections.ALL.first().id

        api.searchResponse = searchResponseOf("tt1" to "First", "tt2" to "Second")
        repository.refreshCollections(force = false)

        api.searchResponse = searchResponseOf("tt3" to "Third")
        repository.refreshCollections(force = true)

        val data = repository.observeCollection(collectionId).first().data
        assertEquals(1, data?.size)
        assertEquals("tt3", data?.first()?.imdbId)
    }

    @Test
    fun `collection ordering from the api is preserved`() = runTest {
        val collectionId = CuratedCollections.ALL.first().id
        api.searchResponse = searchResponseOf("ttC" to "C", "ttA" to "A", "ttB" to "B")

        repository.refreshCollections(force = false)

        assertEquals(
            listOf("ttC", "ttA", "ttB"),
            repository.observeCollection(collectionId).first().data?.map { it.imdbId },
        )
    }

    @Test
    fun `a fresh collection cache issues no requests`() = runTest {
        api.searchResponse = searchResponseOf("tt1" to "Batman Begins")
        repository.refreshCollections(force = false)
        val callsAfterFirstRefresh = api.searchCallCount

        repository.refreshCollections(force = false)

        assertEquals(callsAfterFirstRefresh, api.searchCallCount)
    }

    @Test
    fun `a stale collection cache refreshes`() = runTest {
        api.searchResponse = searchResponseOf("tt1" to "Batman Begins")
        repository.refreshCollections(force = false)
        val callsAfterFirstRefresh = api.searchCallCount

        time.advanceBy(twoDays) // collection TTL is 24 hours
        repository.refreshCollections(force = false)

        assertTrue(api.searchCallCount > callsAfterFirstRefresh)
    }

    @Test
    fun `one failing collection does not fail the whole home screen`() = runTest {
        // Only the first collection's query resolves; the others fall through to a failure.
        val healthy = CuratedCollections.ALL.first()
        api.responsesByQuery[healthy.query] = searchResponseOf("tt1" to "Batman Begins")
        api.searchResponse = SearchResponseDto(response = "False", error = "Something broke.")

        val result = repository.refreshCollections(force = false)

        assertTrue("partial success must still be a success", result is AppResult.Success)
        assertEquals(1, repository.observeCollection(healthy.id).first().data?.size)
    }

    @Test
    fun `a total collection failure is reported as a failure`() = runTest {
        api.throwOnCall = UnknownHostException("offline")

        val result = repository.refreshCollections(force = false)

        assertEquals(AppResult.Failure(AppError.Offline), result)
    }

    // endregion

    // region search

    @Test
    fun `search results are deliberately never written to the database`() = runTest {
        api.searchResponse = searchResponseOf("tt99" to "Some Search Hit")

        repository.search("batman", page = 1, type = MovieType.MOVIE)

        CuratedCollections.ALL.forEach { collection ->
            assertNull(
                "search must not populate the collection cache",
                repository.observeCollection(collection.id).first().data,
            )
        }
        assertNull(repository.observeMovieDetail("tt99").first().data)
    }

    // endregion

    @Test
    fun `surprise me returns null when nothing is cached`() = runTest {
        assertNull(repository.randomCachedMovie())
    }

    @Test
    fun `surprise me draws from cached collections and needs no network`() = runTest {
        api.searchResponse = searchResponseOf("tt1" to "Batman Begins")
        repository.refreshCollections(force = false)
        val callsBefore = api.searchCallCount

        val random = repository.randomCachedMovie()

        assertNotNull(random)
        assertEquals("Surprise Me must not cost an API request", callsBefore, api.searchCallCount)
    }
}
