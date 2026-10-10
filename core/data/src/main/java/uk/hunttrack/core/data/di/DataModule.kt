package uk.hunttrack.core.data.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import uk.hunttrack.core.data.db.AudioPackDao
import uk.hunttrack.core.data.db.BoundaryDao
import uk.hunttrack.core.data.db.CallJournalDao
import uk.hunttrack.core.data.db.CalendarDayDao
import uk.hunttrack.core.data.db.CullDao
import uk.hunttrack.core.data.db.EmergencyDao
import uk.hunttrack.core.data.db.HunTracKDatabase
import uk.hunttrack.core.data.db.HuntingPermissionDao
import uk.hunttrack.core.data.db.MarkerDao
import uk.hunttrack.core.data.db.OfflineMapDao
import uk.hunttrack.core.data.db.RouteDao
import uk.hunttrack.core.data.db.TideCacheDao
import uk.hunttrack.core.data.db.UserCallDao
import uk.hunttrack.core.data.db.WeatherCacheDao
import uk.hunttrack.core.data.weather.OpenMeteoApi
import uk.hunttrack.core.designsystem.sound.SoundManager

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HunTracKDatabase =
        Room.databaseBuilder(context, HunTracKDatabase::class.java, "hunttrack.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMarkerDao(db: HunTracKDatabase): MarkerDao = db.markerDao()
    @Provides fun provideRouteDao(db: HunTracKDatabase): RouteDao = db.routeDao()
    @Provides fun provideOfflineMapDao(db: HunTracKDatabase): OfflineMapDao = db.offlineMapDao()
    @Provides fun provideWeatherCacheDao(db: HunTracKDatabase): WeatherCacheDao = db.weatherCacheDao()
    @Provides fun provideCallJournalDao(db: HunTracKDatabase): CallJournalDao = db.callJournalDao()
    @Provides fun provideUserCallDao(db: HunTracKDatabase): UserCallDao = db.userCallDao()
    @Provides fun provideAudioPackDao(db: HunTracKDatabase): AudioPackDao = db.audioPackDao()
    @Provides fun provideHuntingPermissionDao(db: HunTracKDatabase): HuntingPermissionDao = db.huntingPermissionDao()
    @Provides fun provideBoundaryDao(db: HunTracKDatabase): BoundaryDao = db.boundaryDao()
    @Provides fun provideEmergencyDao(db: HunTracKDatabase): EmergencyDao = db.emergencyDao()
    @Provides fun provideCalendarDayDao(db: HunTracKDatabase): CalendarDayDao = db.calendarDayDao()
    @Provides fun provideTideCacheDao(db: HunTracKDatabase): TideCacheDao = db.tideCacheDao()
    @Provides fun provideCullDao(db: HunTracKDatabase): CullDao = db.cullDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, json: Json): OpenMeteoApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSoundManager(@ApplicationContext context: Context): SoundManager = SoundManager(context)
}
