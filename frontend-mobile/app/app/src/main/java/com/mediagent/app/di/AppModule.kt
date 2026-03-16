package com.mediagent.app.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mediagent.app.data.api.ApiKeyInterceptor
import com.mediagent.app.data.api.ChetanaApiService
import com.mediagent.app.data.repository.AuthRepository
import com.mediagent.app.data.repository.ChatRepository
import com.mediagent.app.data.repository.FamilyRepository
import com.mediagent.app.data.repository.FollowUpRepository
import com.mediagent.app.data.repository.InsightRepository
import com.mediagent.app.data.repository.LiveChatRepository
import com.mediagent.app.data.repository.LiveFamilyRepository
import com.mediagent.app.data.repository.LiveInsightRepository
import com.mediagent.app.data.repository.LiveUploadRepository
import com.mediagent.app.data.repository.MockAuthRepository
import com.mediagent.app.data.repository.LiveFollowUpRepository
import com.mediagent.app.data.repository.UploadRepository
import com.mediagent.app.BuildConfig
import com.mediagent.app.util.MockDataLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val BASE_URL = BuildConfig.CHETANA_BASE_URL
    private val API_KEY = BuildConfig.CHETANA_API_KEY

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideMockDataLoader(
        @ApplicationContext context: Context,
        json: Json,
    ): MockDataLoader = MockDataLoader(context, json)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(API_KEY))
            .apply { if (BuildConfig.DEBUG) addInterceptor(logging) }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ChetanaApiService =
        retrofit.create(ChetanaApiService::class.java)

    // Mock — no live login/OTP endpoint
    @Provides
    @Singleton
    fun provideAuthRepository(loader: MockDataLoader): AuthRepository =
        MockAuthRepository(loader)

    // LIVE — GET /families/{fid}/members, GET /reports, GET /observations
    @Provides
    @Singleton
    fun provideFamilyRepository(api: ChetanaApiService): FamilyRepository =
        LiveFamilyRepository(api)

    // LIVE — GET /families/{fid}/members/{mid}/insights
    @Provides
    @Singleton
    fun provideInsightRepository(api: ChetanaApiService): InsightRepository =
        LiveInsightRepository(api)

    // LIVE — GET/PATCH /families/{fid}/members/{mid}/followups
    @Provides
    @Singleton
    fun provideFollowUpRepository(api: ChetanaApiService): FollowUpRepository =
        LiveFollowUpRepository(api)

    // LIVE — POST /families/{fid}/members/{mid}/chat
    @Provides
    @Singleton
    fun provideChatRepository(api: ChetanaApiService): ChatRepository =
        LiveChatRepository(api)

    // LIVE — Upload flow: presigned URL, S3 PUT, trigger processing, poll status, delete
    @Provides
    @Singleton
    fun provideUploadRepository(api: ChetanaApiService): UploadRepository =
        LiveUploadRepository(api)
}
