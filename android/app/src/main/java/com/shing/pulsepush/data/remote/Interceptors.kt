package com.shing.pulsepush.data.remote

import com.shing.pulsepush.data.preferences.ServerUrlHolder
import com.shing.pulsepush.data.preferences.TokenHolder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds `Authorization: Bearer <jwt>` to every request except the public
 * auth endpoints. The token comes from [TokenHolder], which UserPreferences
 * keeps in sync with DataStore.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenHolder: TokenHolder
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isAuthEndpoint = path.contains("/auth/login") || path.contains("/auth/register")
        val token = tokenHolder.current
        val request = if (!isAuthEndpoint && !token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}

/**
 * Dynamic base URL: Retrofit is built against a placeholder host; this
 * interceptor rewrites scheme / host / port (and any path prefix the user
 * included in the configured server URL) on every request, so changing the
 * server address in Settings takes effect immediately with no rebuild and
 * no new Retrofit instance.
 *
 * Example: configured server = https://example.com:8443/push/
 *   request https://push.example.com/api/v1/channels
 *   becomes https://example.com:8443/push/api/v1/channels
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverUrlHolder: ServerUrlHolder
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val base = serverUrlHolder.current.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val basePathSegments = base.pathSegments.filter { it.isNotEmpty() }
        val newUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .apply {
                if (basePathSegments.isNotEmpty()) {
                    // Rebuild path: <base prefix>/<original path>
                    val originalSegments = original.url.pathSegments.filter { it.isNotEmpty() }
                    while (pathSegmentCount() > 0) removePathSegment(0)
                    (basePathSegments + originalSegments).forEach { addPathSegment(it) }
                }
            }
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }

    private fun okhttp3.HttpUrl.Builder.pathSegmentCount(): Int = build().pathSegments.size
}
