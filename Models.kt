package com.localshelf.app

data class SubtitleTrack(
    val uri: String,
    val label: String,
    val language: String?,
    val mimeType: String
)

data class LocalMedia(
    val uri: String,
    val fileName: String,
    val title: String,
    val durationMs: Long,
    val modified: Long,
    val year: Int? = null,
    val show: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList()
) {
    val isEpisode: Boolean get() = show != null && season != null && episode != null
}

data class ParsedName(
    val title: String,
    val year: Int? = null,
    val show: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null
)
