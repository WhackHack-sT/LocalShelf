package com.localshelf.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "3gp", "flv")
private val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "ttml")

suspend fun scanAllMedia(context: Context, roots: Set<String>): List<LocalMedia> = withContext(Dispatchers.IO) {
    val out = mutableListOf<LocalMedia>()
    roots.forEach { raw ->
        runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(raw))?.let { scanDirectory(context, it, out) }
        }
    }
    out.distinctBy { it.uri }.sortedBy { it.title.lowercase(Locale.getDefault()) }
}

private fun scanDirectory(context: Context, node: DocumentFile, out: MutableList<LocalMedia>) {
    if (!node.isDirectory) return
    val children = runCatching { node.listFiles().toList() }.getOrDefault(emptyList())
    val subtitleFiles = children.filter { it.isFile && extensionOf(it.name) in subtitleExtensions }

    children.filter { it.isFile && isVideo(it) }.forEach { video ->
        val name = video.name ?: return@forEach
        val parsed = parseMediaName(name)
        val matchingSubs = subtitleFiles
            .filter { subtitleMatches(video.name.orEmpty(), parsed, it.name.orEmpty()) }
            .mapNotNull { subtitleTrack(it) }

        out += LocalMedia(
            uri = video.uri.toString(),
            fileName = name,
            title = parsed.title,
            durationMs = readDuration(context, video.uri),
            modified = video.lastModified(),
            year = parsed.year,
            show = parsed.show,
            season = parsed.season,
            episode = parsed.episode,
            episodeTitle = parsed.episodeTitle,
            subtitles = matchingSubs
        )
    }

    children.filter { it.isDirectory }.forEach { scanDirectory(context, it, out) }
}

private fun isVideo(file: DocumentFile): Boolean {
    return file.type?.startsWith("video/") == true || extensionOf(file.name) in videoExtensions
}

private fun extensionOf(name: String?): String = name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
private fun stemOf(name: String): String = name.substringBeforeLast('.').lowercase(Locale.ROOT)

private fun subtitleMatches(videoName: String, parsedVideo: ParsedName, subtitleName: String): Boolean {
    val videoStem = stemOf(videoName)
    val subStem = stemOf(subtitleName)
    if (subStem == videoStem || subStem.startsWith("$videoStem.")) return true

    if (parsedVideo.show != null && parsedVideo.season != null && parsedVideo.episode != null) {
        val parsedSub = parseMediaName(subtitleName)
        return parsedSub.show.equals(parsedVideo.show, ignoreCase = true) &&
            parsedSub.season == parsedVideo.season && parsedSub.episode == parsedVideo.episode
    }
    return false
}

private fun subtitleTrack(file: DocumentFile): SubtitleTrack? {
    val name = file.name ?: return null
    val ext = extensionOf(name)
    val mime = when (ext) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        "ttml" -> "application/ttml+xml"
        else -> return null
    }
    val language = detectLanguage(name)
    return SubtitleTrack(
        uri = file.uri.toString(),
        label = language?.uppercase(Locale.ROOT) ?: name,
        language = language,
        mimeType = mime
    )
}

private fun detectLanguage(name: String): String? {
    val tokens = stemOf(name).split('.', '-', '_', ' ')
    val aliases = mapOf(
        "en" to "en", "eng" to "en",
        "es" to "es", "spa" to "es",
        "fr" to "fr", "fre" to "fr", "fra" to "fr",
        "de" to "de", "ger" to "de", "deu" to "de",
        "it" to "it", "ita" to "it",
        "pt" to "pt", "por" to "pt",
        "ja" to "ja", "jpn" to "ja",
        "ko" to "ko", "kor" to "ko"
    )
    return tokens.asReversed().firstNotNullOfOrNull { aliases[it] }
}

fun parseMediaName(filename: String): ParsedName {
    val base = filename.substringBeforeLast('.').trim()
    val episodePatterns = listOf(
        Regex("(?i)^(.*?)[ ._-]+S(\\d{1,2})E(\\d{1,3})(?:[ ._-]+(.*))?$"),
        Regex("(?i)^(.*?)[ ._-]+(\\d{1,2})x(\\d{1,3})(?:[ ._-]+(.*))?$"),
        Regex("(?i)^(.*?)[ ._-]+Season[ ._-]*(\\d{1,2})[ ._-]+Episode[ ._-]*(\\d{1,3})(?:[ ._-]+(.*))?$")
    )

    for (pattern in episodePatterns) {
        val match = pattern.find(base) ?: continue
        val show = cleanReleaseTitle(match.groupValues[1])
        val season = match.groupValues[2].toIntOrNull()
        val episode = match.groupValues[3].toIntOrNull()
        val epTitle = cleanReleaseTitle(match.groupValues.getOrElse(4) { "" }).takeIf { it.isNotBlank() }
        return ParsedName(
            title = epTitle ?: "Episode ${episode ?: ""}".trim(),
            show = show,
            season = season,
            episode = episode,
            episodeTitle = epTitle
        )
    }

    val yearMatch = Regex("(?i)^(.*?)[ ._-]+((?:19|20)\\d{2})(?:[ ._-]+.*)?$").find(base)
    if (yearMatch != null) {
        return ParsedName(
            title = cleanReleaseTitle(yearMatch.groupValues[1]),
            year = yearMatch.groupValues[2].toIntOrNull()
        )
    }

    return ParsedName(title = cleanReleaseTitle(base))
}

private fun cleanReleaseTitle(input: String): String {
    var value = input
        .replace('_', ' ')
        .replace('.', ' ')
        .replace(Regex("[\\[\\](){}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    val releaseTag = Regex("(?i)\\s+(?:2160p|1080p|720p|480p|bluray|blu-ray|web[- .]?dl|webrip|hdrip|brrip|dvdrip|remux|x264|x265|h264|h265|hevc|av1|aac|dts|truehd|atmos)(?:\\s+.*)?$")
    value = value.replace(releaseTag, "").trim()
    return value.split(' ').joinToString(" ") { token ->
        if (token.length <= 2 && token.all { it.isUpperCase() }) token
        else token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

private fun readDuration(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}
