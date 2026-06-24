package net.primal.android.networking.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton
import net.primal.core.utils.serialization.CommonJson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkingModule {

    @Provides
    @ElementsIntoSet
    fun emptyInterceptorsSet(): Set<Interceptor> = emptySet()

    private fun OkHttpClient.Builder.withInterceptors(interceptors: Collection<Interceptor>) =
        apply {
            interceptors.forEach { addInterceptor(it) }
        }

    @Provides
    @Singleton
    fun unauthenticatedOkHttpClient(interceptors: Set<@JvmSuppressWildcards Interceptor>) =
        OkHttpClient.Builder()
            .withInterceptors(interceptors)
            .build()

    @Provides
    @Singleton
    fun unauthenticatedRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // b2r fork (Sprint 1.2): default base host moved off Primal. b2r is
            // serverless; any relative-path call against this client now targets
            // a non-resolving ".invalid" host instead of primal.net. Remaining
            // absolute-URL Primal calls (media/blossom CDN) are removed in later
            // sprints as their features are repointed at the P2P/local cache.
            .baseUrl("https://disabled.b2r.invalid")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(CommonJson.asConverterFactory("application/json".toMediaType()))
            .build()
}
