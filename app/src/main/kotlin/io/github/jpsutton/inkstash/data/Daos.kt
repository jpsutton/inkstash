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

    @Query("SELECT * FROM Location WHERE uid = :uid")
    suspend fun byUid(uid: String): Location?

    @Query("SELECT * FROM Location WHERE id = :id AND deleted = 0")
    fun observe(id: Long): Flow<Location?>

    @Query("SELECT * FROM Location WHERE deleted = 0 ORDER BY kind = 'GENERAL' DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Location>>

    /** Places in use (not deleted). */
    @Query("SELECT * FROM Location WHERE deleted = 0")
    suspend fun all(): List<Location>

    @Query(
        "SELECT l.*, COALESCE(SUM(CASE WHEN h.quantity > 0 THEN h.quantity ELSE 0 END), 0) AS cardCount FROM Location l " +
            "LEFT JOIN Holding h ON h.locationId = l.id WHERE l.deleted = 0 GROUP BY l.id " +
            "ORDER BY l.kind = 'GENERAL' DESC, l.name COLLATE NOCASE",
    )
    fun observeSummaries(): Flow<List<LocationSummary>>
}

@Dao
interface HoldingDao {
    @Insert
    suspend fun insert(holding: Holding): Long

    @Update
    suspend fun update(holding: Holding)

    @Query("SELECT * FROM Holding WHERE id = :id")
    suspend fun byId(id: Long): Holding?

    @Query(
        "SELECT * FROM Holding WHERE cardId = :cardId AND locationId = :locationId AND foil = :foil " +
            "AND sourceLocationId = :source",
    )
    suspend fun find(cardId: String, locationId: Long, foil: Boolean, source: Long): Holding?

    @Query("SELECT * FROM Holding WHERE cardId = :cardId AND quantity > 0")
    suspend fun forCard(cardId: String): List<Holding>

    @Query("SELECT * FROM Holding WHERE locationId = :locationId AND quantity > 0")
    suspend fun inLocation(locationId: Long): List<Holding>

    /** Every row with a non-zero quantity, including ones below zero (changes from other devices not yet merged). */
    @Query("SELECT * FROM Holding WHERE locationId = :locationId AND quantity != 0")
    suspend fun nonZeroInLocation(locationId: Long): List<Holding>

    @Query("SELECT * FROM Holding WHERE sourceLocationId = :source AND quantity > 0")
    suspend fun borrowedFrom(source: Long): List<Holding>

    @Query("SELECT * FROM Holding WHERE quantity > 0")
    suspend fun all(): List<Holding>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM Holding WHERE quantity > 0")
    suspend fun totalCopies(): Int

    /** Owned cards (grouped), optionally limited to one location (`:locationId` < 0 means all). */
    @Query(
        "SELECT c.*, SUM(h.quantity) AS qty, SUM(CASE WHEN h.foil = 1 THEN h.quantity ELSE 0 END) AS foilQty " +
            "FROM Card c JOIN Holding h ON h.cardId = c.id " +
            "WHERE h.quantity > 0 AND (:locationId < 0 OR h.locationId = :locationId) GROUP BY c.id ORDER BY c.name, c.version",
    )
    fun observeOwned(locationId: Long): Flow<List<OwnedCard>>

    @Query(
        "SELECT h.*, c.id AS c_id, c.setCode AS c_setCode, c.setName AS c_setName, " +
            "c.collectorNumber AS c_collectorNumber, c.name AS c_name, c.version AS c_version, " +
            "c.ink AS c_ink, c.inkable AS c_inkable, c.cost AS c_cost, c.type AS c_type, " +
            "c.rarity AS c_rarity, c.imageSmall AS c_imageSmall, c.imageNormal AS c_imageNormal, " +
            "c.imageLarge AS c_imageLarge " +
            "FROM Holding h JOIN Card c ON c.id = h.cardId WHERE h.locationId = :locationId AND h.quantity > 0 " +
            "ORDER BY c.cost, c.name, c.version",
    )
    fun observeInLocation(locationId: Long): Flow<List<HoldingWithCard>>

    @Query("SELECT * FROM Holding WHERE cardId = :cardId AND quantity > 0 ORDER BY locationId, foil")
    fun observeForCard(cardId: String): Flow<List<Holding>>

    @Query("SELECT * FROM Holding WHERE locationId = :locationId AND quantity > 0")
    fun observeRows(locationId: Long): Flow<List<Holding>>
}

@Dao
interface SyncOpDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(op: SyncOp): Long

    @Query("UPDATE SyncOp SET applied = 1 WHERE origin = :origin AND seq = :seq")
    suspend fun markApplied(origin: String, seq: Long)

    @Query("SELECT COALESCE(MAX(seq), 0) FROM SyncOp WHERE origin = :origin")
    suspend fun maxSeq(origin: String): Long

    @Query("SELECT COALESCE(MAX(hlc), 0) FROM SyncOp")
    suspend fun maxHlc(): Long

    @Query("SELECT origin, MAX(seq) AS seq FROM SyncOp GROUP BY origin")
    suspend fun vector(): List<VectorEntry>

    @Query("SELECT * FROM SyncOp WHERE origin = :origin AND seq > :after ORDER BY seq LIMIT :limit")
    suspend fun after(origin: String, after: Long, limit: Int): List<SyncOp>

    @Query("SELECT * FROM SyncOp WHERE applied = 0 ORDER BY hlc, origin, seq")
    suspend fun pending(): List<SyncOp>

    @Query("SELECT COUNT(*) FROM SyncOp")
    suspend fun count(): Int

    @Query("SELECT * FROM SyncOp ORDER BY hlc, origin, seq")
    suspend fun all(): List<SyncOp>
}

data class VectorEntry(val origin: String, val seq: Long)

@Dao
interface MeshMemberDao {
    @Upsert
    suspend fun upsert(member: MeshMember)

    @Query("SELECT * FROM MeshMember")
    suspend fun all(): List<MeshMember>

    @Query("SELECT * FROM MeshMember")
    fun observeAll(): Flow<List<MeshMember>>

    @Query("SELECT * FROM MeshMember WHERE nodeId = :id")
    suspend fun byId(id: String): MeshMember?

    @Query("DELETE FROM MeshMember")
    suspend fun deleteAll()
}
