package fr.thibautperrin.tetrano.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.thibautperrin.tetrano.data.db.ChallengeProgressDao
import fr.thibautperrin.tetrano.data.db.MIGRATION_1_2
import fr.thibautperrin.tetrano.data.db.SavedGameDao
import fr.thibautperrin.tetrano.data.db.TetranoDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTetranoDatabase(@ApplicationContext context: Context): TetranoDatabase =
        Room.databaseBuilder(context, TetranoDatabase::class.java, TetranoDatabase.FILE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideSavedGameDao(database: TetranoDatabase): SavedGameDao = database.savedGameDao()

    @Provides
    fun provideChallengeProgressDao(database: TetranoDatabase): ChallengeProgressDao =
        database.challengeProgressDao()
}
