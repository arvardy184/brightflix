package com.application.brightflix.domain.model

/**
 * The home screen's curated rows.
 *
 * Lives in `domain` rather than `data` because it is product content, not a data source —
 * and because domain use cases need it, and `domain` must never import from `data`.
 *
 * Three, deliberately: OMDb returns 10 results per query, so each extra row costs a request
 * on every cold refresh, and a focused home screen reads better than a long one.
 *
 * Each query was chosen because it returns a coherent set. Broad single words tend to
 * return unrelated titles that make a row look like noise.
 */
object CuratedCollections {

    val ALL: List<CuratedCollection> = listOf(
        CuratedCollection(
            id = "caped_crusaders",
            title = "Caped Crusaders",
            query = "batman",
            type = MovieType.MOVIE,
        ),
        CuratedCollection(
            id = "galaxy_far_away",
            title = "A Galaxy Far, Far Away",
            query = "star wars",
            type = MovieType.MOVIE,
        ),
        CuratedCollection(
            id = "middle_earth",
            title = "Return to Middle-earth",
            query = "lord of the rings",
            type = MovieType.MOVIE,
        ),
    )
}
