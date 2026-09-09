package com.application.brightflix.data.remote.mapper

import com.application.brightflix.data.remote.dto.MovieDetailDto
import com.application.brightflix.data.remote.dto.RatingDto
import com.application.brightflix.data.remote.dto.SearchItemDto
import com.application.brightflix.domain.model.MovieType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mapper boundary is where OMDb's wire conventions stop.
 *
 * OMDb uses the literal string "N/A" as its null value across nearly every field. If that
 * leaks past this layer, every screen has to defend against it and the UI ends up
 * rendering "N/A" to users. These tests pin that normalisation down.
 */
class MovieMappersTest {

    // region search item

    @Test
    fun `poster of N slash A becomes null`() {
        val movie = searchItem(poster = "N/A").toDomainOrNull()

        assertNotNull(movie)
        assertNull(movie!!.posterUrl)
    }

    @Test
    fun `a real poster url is preserved`() {
        val movie = searchItem(poster = "https://img.example/p.jpg").toDomainOrNull()

        assertEquals("https://img.example/p.jpg", movie?.posterUrl)
    }

    @Test
    fun `blank poster becomes null`() {
        assertNull(searchItem(poster = "   ").toDomainOrNull()?.posterUrl)
    }

    @Test
    fun `known type maps to its enum`() {
        assertEquals(MovieType.MOVIE, searchItem(type = "movie").toDomainOrNull()?.type)
        assertEquals(MovieType.SERIES, searchItem(type = "series").toDomainOrNull()?.type)
    }

    @Test
    fun `unrecognised type degrades to UNKNOWN instead of throwing`() {
        assertEquals(MovieType.UNKNOWN, searchItem(type = "podcast").toDomainOrNull()?.type)
    }

    @Test
    fun `item without an imdb id is dropped because identity is required`() {
        assertNull(searchItem(imdbId = null).toDomainOrNull())
        assertNull(searchItem(imdbId = "").toDomainOrNull())
    }

    @Test
    fun `item without a title is dropped because it cannot be rendered`() {
        assertNull(searchItem(title = null).toDomainOrNull())
        assertNull(searchItem(title = "N/A").toDomainOrNull())
    }

    // endregion

    // region detail scalars

    @Test
    fun `runtime string parses to minutes`() {
        assertEquals(142, detail(runtime = "142 min").toDomainOrNull()?.runtimeMinutes)
    }

    @Test
    fun `unparseable runtime becomes null rather than throwing`() {
        assertNull(detail(runtime = "N/A").toDomainOrNull()?.runtimeMinutes)
        assertNull(detail(runtime = "some minutes").toDomainOrNull()?.runtimeMinutes)
        assertNull(detail(runtime = null).toDomainOrNull()?.runtimeMinutes)
    }

    @Test
    fun `imdb votes strip thousands separators`() {
        assertEquals(2_845_132, detail(imdbVotes = "2,845,132").toDomainOrNull()?.imdbVotes)
    }

    @Test
    fun `imdb rating parses as a decimal`() {
        assertEquals(9.0, detail(imdbRating = "9.0").toDomainOrNull()?.imdbRating!!, 0.001)
    }

    @Test
    fun `metascore parses as an integer and tolerates absence`() {
        assertEquals(74, detail(metascore = "74").toDomainOrNull()?.metascore)
        assertNull(detail(metascore = "N/A").toDomainOrNull()?.metascore)
    }

    @Test
    fun `plot of N slash A becomes null so the section can be omitted`() {
        assertNull(detail(plot = "N/A").toDomainOrNull()?.plot)
    }

    @Test
    fun `box office of N slash A becomes null`() {
        assertNull(detail(boxOffice = "N/A").toDomainOrNull()?.boxOffice)
    }

    // endregion

    // region detail lists

    @Test
    fun `genres are split and trimmed`() {
        assertEquals(
            listOf("Action", "Crime", "Drama"),
            detail(genre = "Action, Crime, Drama").toDomainOrNull()?.genres,
        )
    }

    @Test
    fun `a list field of N slash A becomes an empty list not a list containing N slash A`() {
        assertEquals(emptyList<String>(), detail(genre = "N/A").toDomainOrNull()?.genres)
        assertEquals(emptyList<String>(), detail(actors = "N/A").toDomainOrNull()?.cast)
    }

    @Test
    fun `empty segments in a comma list are dropped`() {
        assertEquals(
            listOf("Action", "Drama"),
            detail(genre = "Action, , Drama,").toDomainOrNull()?.genres,
        )
    }

    @Test
    fun `cast and writers are split into lists`() {
        val mapped = detail(
            actors = "Christian Bale, Michael Caine",
            writer = "Bob Kane, David S. Goyer",
        ).toDomainOrNull()

        assertEquals(listOf("Christian Bale", "Michael Caine"), mapped?.cast)
        assertEquals(listOf("Bob Kane", "David S. Goyer"), mapped?.writers)
    }

    // endregion

    // region ratings

    @Test
    fun `ratings are mapped and incomplete entries are dropped`() {
        val mapped = detail(
            ratings = listOf(
                RatingDto(source = "Internet Movie Database", value = "8.2/10"),
                RatingDto(source = "Rotten Tomatoes", value = null),
                RatingDto(source = null, value = "74%"),
            ),
        ).toDomainOrNull()

        assertEquals(1, mapped?.ratings?.size)
        assertEquals("Internet Movie Database", mapped?.ratings?.first()?.source)
        assertEquals("8.2/10", mapped?.ratings?.first()?.value)
    }

    @Test
    fun `absent ratings become an empty list`() {
        assertEquals(emptyList<Any>(), detail(ratings = null).toDomainOrNull()?.ratings)
    }

    // endregion

    @Test
    fun `a detail response with every optional field absent still maps`() {
        val mapped = MovieDetailDto(imdbId = "tt0372784", title = "Batman Begins").toDomainOrNull()

        assertNotNull(mapped)
        assertEquals("tt0372784", mapped!!.imdbId)
        assertNull(mapped.plot)
        assertNull(mapped.runtimeMinutes)
        assertEquals(emptyList<String>(), mapped.genres)
    }

    private fun searchItem(
        imdbId: String? = "tt0372784",
        title: String? = "Batman Begins",
        year: String? = "2005",
        type: String? = "movie",
        poster: String? = "https://img.example/p.jpg",
    ) = SearchItemDto(imdbId = imdbId, title = title, year = year, type = type, poster = poster)

    private fun detail(
        imdbId: String? = "tt0372784",
        title: String? = "Batman Begins",
        year: String? = "2005",
        runtime: String? = "140 min",
        genre: String? = "Action, Crime, Drama",
        actors: String? = "Christian Bale",
        writer: String? = "Bob Kane",
        plot: String? = "A tale of a bat.",
        metascore: String? = "70",
        imdbRating: String? = "8.2",
        imdbVotes: String? = "1,000",
        boxOffice: String? = "$206,863,479",
        ratings: List<RatingDto>? = emptyList(),
    ) = MovieDetailDto(
        imdbId = imdbId,
        title = title,
        year = year,
        runtime = runtime,
        genre = genre,
        actors = actors,
        writer = writer,
        plot = plot,
        metascore = metascore,
        imdbRating = imdbRating,
        imdbVotes = imdbVotes,
        boxOffice = boxOffice,
        ratings = ratings,
        type = "movie",
    )
}
