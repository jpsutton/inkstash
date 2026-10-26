package io.github.jpsutton.inkstash.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Upsert
    suspend fun upsertAll(cards: List<Card>)

    @Query("SELECT COUNT(*) FROM Card")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM Card")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT setCode) FROM Card")
    fun setCountFlow(): Flow<Int>

    @Query("SELECT * FROM Card WHERE id = :id")
    suspend fun byId(id: String): Card?

    @Query("SELECT * FROM Card WHERE id = :id")
    fun observe(id: String): Flow<Card?>

    @Query("SELECT * FROM Card WHERE setCode = :setCode COLLATE NOCASE AND collectorNumber = :number")
    suspend fun bySetAndNumber(setCode: String, number: String): Card?

    @Query("SELECT * FROM Card WHERE collectorNumber = :number")
    suspend fun byNumber(number: String): List<Card>

    @Query("SELECT * FROM Card WHERE setCode = :setCode COLLATE NOCASE")
    suspend fun bySet(setCode: String): List<Card>

    @Query("SELECT * FROM Card")
    suspend fun all(): List<Card>

    @Query(
        "SELECT * FROM Card WHERE name LIKE '%' || :q || '%' OR version LIKE '%' || :q || '%' " +
            "ORDER BY name, version LIMIT :limit",
    )
    suspend fun search(q: String, limit: Int = 40): List<Card>

    @Query("SELECT DISTINCT setCode, setName FROM Card ORDER BY setName")
    fun sets(): Flow<List<SetRow>>
}

data class SetRow(val setCode: String, val setName: String)

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: Location): Long

    @Update
    suspend fun update(location: Location)

    @Query("SELECT * FROM Location WHERE id = :id")
    suspend fun byId(id: Long): Location?

    @Query("SELECT * FROM Location WHERE id = :id")
    fun observe(id: Long): Flow<Location?>

    @Query("SELECT * FROM Location ORDER BY kind = 'GENERAL' DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Location>>

    @Query("SELECT * FROM Location")
    suspend fun all(): List<Location>

    @Query(
        "SELECT l.*, COALESCE(SUM(h.quantity), 0) AS cardCount FROM Location l " +
            "LEFT JOIN Holding h ON h.locationId = l.id GROUP BY l.id " +
            "ORDER BY l.kind = 'GENERAL' DESC, l.name COLLATE NOCASE",
    )
    fun observeSummaries(): Flow<List<LocationSummary>>

    @Query("DELETE FROM Location WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Location WHERE id != $GENERAL_ID")
    suspend fun deleteAllButGeneral()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(location: Location)
}

@Dao
interface HoldingDao {
    @Insert
    suspend fun insert(holding: Holding): Long

    @Update
    suspend fun update(holding: Holding)

    @Query("DELETE FROM Holding WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Holding")
    suspend fun deleteAll()

    @Query("SELECT * FROM Holding WHERE id = :id")
    suspend fun byId(id: Long): Holding?

    @Query(
        "SELECT * FROM Holding WHERE cardId = :cardId AND locationId = :locationId AND foil = :foil " +
            "AND sourceLocationId = :source",
    )
    suspend fun find(cardId: String, locationId: Long, foil: Boolean, source: Long): Holding?

    @Query("SELECT * FROM Holding WHERE cardId = :cardId")
    suspend fun forCard(cardId: String): List<Holding>

    @Query("SELECT * FROM Holding WHERE locationId = :locationId")
    suspend fun inLocation(locationId: Long): List<Holding>

    @Query("SELECT * FROM Holding WHERE sourceLocationId = :source")
    suspend fun borrowedFrom(source: Long): List<Holding>

    @Query("SELECT * FROM Holding")
    suspend fun all(): List<Holding>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM Holding")
    suspend fun totalCopies(): Int

    /** Owned cards (grouped), optionally limited to one location (`:locationId` < 0 means all). */
    @Query(
        "SELECT c.*, SUM(h.quantity) AS qty, SUM(CASE WHEN h.foil = 1 THEN h.quantity ELSE 0 END) AS foilQty " +
            "FROM Card c JOIN Holding h ON h.cardId = c.id " +
            "WHERE :locationId < 0 OR h.locationId = :locationId GROUP BY c.id ORDER BY c.name, c.version",
    )
    fun observeOwned(locationId: Long): Flow<List<OwnedCard>>

    @Query(
        "SELECT h.*, c.id AS c_id, c.setCode AS c_setCode, c.setName AS c_setName, " +
            "c.collectorNumber AS c_collectorNumber, c.name AS c_name, c.version AS c_version, " +
            "c.ink AS c_ink, c.inkable AS c_inkable, c.cost AS c_cost, c.type AS c_type, " +
            "c.rarity AS c_rarity, c.imageSmall AS c_imageSmall, c.imageNormal AS c_imageNormal, " +
            "c.imageLarge AS c_imageLarge " +
            "FROM Holding h JOIN Card c ON c.id = h.cardId WHERE h.locationId = :locationId " +
            "ORDER BY c.cost, c.name, c.version",
    )
    fun observeInLocation(locationId: Long): Flow<List<HoldingWithCard>>

    @Query("SELECT * FROM Holding WHERE cardId = :cardId ORDER BY locationId, foil")
    fun observeForCard(cardId: String): Flow<List<Holding>>

    @Query("SELECT * FROM Holding WHERE locationId = :locationId")
    fun observeRows(locationId: Long): Flow<List<Holding>>
}
