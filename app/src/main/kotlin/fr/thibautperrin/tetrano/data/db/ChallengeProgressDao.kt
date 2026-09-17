package fr.thibautperrin.tetrano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeProgressDao {

    @Query("SELECT * FROM challenge_progress WHERE id = ${ChallengeProgressEntity.SINGLETON_ID}")
    fun observe(): Flow<ChallengeProgressEntity?>

    @Query("SELECT * FROM challenge_progress WHERE id = ${ChallengeProgressEntity.SINGLETON_ID}")
    suspend fun get(): ChallengeProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ChallengeProgressEntity)
}
