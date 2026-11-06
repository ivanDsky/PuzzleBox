package com.ivandsky.puzzlebox.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps the latest-release GET onto [GitHubRelease]: 2xx with non-blank `tag_name` and
 * `html_url` → value; any other status, IO failure, timeout or bad JSON → null.
 */
internal class GitHubHttpReleaseTransport(
    private val api: GitHubReleasesApi,
    private val json: Json,
) : GitHubReleaseTransport {

    override suspend fun latest(): GitHubRelease? = try {
        val response = api.latest()
        if (response.isSuccessful) {
            parse(response.body()?.use { it.string() }.orEmpty())
        } else {
            response.errorBody()?.close()
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    } catch (e: SerializationException) {
        null
    }

    // WHY: JsonElement, not a @Serializable DTO — no generated serializer for R8 to strip
    // and no serialization compiler plugin needed in this module.
    private fun parse(text: String): GitHubRelease? {
        val release = json.parseToJsonElement(text) as? JsonObject ?: return null
        val tag = release.string("tag_name")
        val url = release.string("html_url")
        return if (tag.isNullOrBlank() || url.isNullOrBlank()) null else GitHubRelease(tag, url)
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
