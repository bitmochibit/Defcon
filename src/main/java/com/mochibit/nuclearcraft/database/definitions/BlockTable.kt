package com.mochibit.nuclearcraft.database.definitions

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.database.Database
import com.mochibit.nuclearcraft.enums.DatabaseFile
import java.sql.ResultSet

class BlockTable : Database() {
    init {
        connect(DatabaseFile.BLOCK_DATA)
        createTable()
    }

    public override fun createTable() {
        // Create a table which stores an id, the custom block id (including the item-id that generated this block) and the location of the block in the world
        val sql = """
                CREATE TABLE IF NOT EXISTS blocks (
                	id integer PRIMARY KEY,
                	block_id text NOT NULL,
                	item_id text NOT NULL,
                	world text NOT NULL,
                	x integer NOT NULL,
                	y integer NOT NULL,
                	z integer NOT NULL
                );
                """.trimIndent()
        try {
            connection?.createStatement().use { statement -> statement?.execute(sql) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun insert(blockId: String, itemId: String, world: String, x: Int, y: Int, z: Int): BlockTable {
        // Prepare the sql statement
        val sql = "INSERT INTO blocks(block_id, item_id, world, x, y, z) VALUES(?, ?, ?, ?, ?, ?)"
        NuclearCraft.Companion.instance!!.getLogger().info("Inserting block into database: $blockId $itemId $world $x $y $z")
        try {
            connection?.prepareStatement(sql).use { statement ->
                statement?.setString(1, blockId)
                statement?.setString(2, itemId)
                statement?.setString(3, world)
                statement?.setInt(4, x)
                statement?.setInt(5, y)
                statement?.setInt(6, z)
                statement?.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }

    operator fun get(x: Int, y: Int, z: Int, world: String?): ResultSet? {
        val sql = "SELECT * FROM blocks WHERE x = ? AND y = ? AND z = ? AND world = ?"
        try {
            connection?.prepareStatement(sql).use { statement ->
                statement?.setInt(1, x)
                statement?.setInt(2, y)
                statement?.setInt(3, z)
                statement?.setString(4, world)
                statement?.executeQuery()
                return statement?.resultSet
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun delete(x: Int, y: Int, z: Int, world: String?): BlockTable {
        val sql = "DELETE FROM blocks WHERE x = ? AND y = ? AND z = ? AND world = ?"
        // Delete the block from the database with the given coordinates and world
        try {
            connection?.prepareStatement(sql).use { statement ->
                statement?.setInt(1, x)
                statement?.setInt(2, y)
                statement?.setInt(3, z)
                statement?.setString(4, world)
                statement?.executeUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return this
    }
}
