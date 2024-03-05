package com.mochibit.defcon.database

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.enums.DatabaseFile
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * This class is responsible for handling all database related tasks.
 * Useful for storing data such as player data, block data, etc.
 */
abstract class Database {
    var connection: Connection? = null
        private set
    private var databaseFile: DatabaseFile? = null
    fun connect(databaseFile: DatabaseFile) {
        this.databaseFile = databaseFile
        try {
            if (connection != null && connection!!.isClosed) return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Get data folder path of plugin
        val dataFolder: String = Defcon.Companion.instance!!.getDataFolder().getAbsolutePath()
        // Create the db directory if it doesn't exist
        val dbDirectory = "$dataFolder/db"
        val dbDir = File(dbDirectory)
        if (!dbDir.exists()) {
            if (!dbDir.mkdir()) {
                Defcon.Companion.instance!!.getLogger().warning("Failed to create database directory!")
            }
        }
        val url = "jdbc:sqlite:" + dbDirectory + "/" + databaseFile.fileName
        try {
            connection = DriverManager.getConnection(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            if (connection != null) {
                connection!!.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected abstract fun createTable()

    companion object {
        var openedDatabases = HashMap<DatabaseFile, Database>()
        fun disconnectAll() {
            for (database in openedDatabases.values) {
                try {
                    database?.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
