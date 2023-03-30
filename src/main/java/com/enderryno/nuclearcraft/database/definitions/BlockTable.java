package com.enderryno.nuclearcraft.database.definitions;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.database.Database;
import com.enderryno.nuclearcraft.enums.DatabaseFile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class BlockTable extends Database {
    public BlockTable() {
        this.connect(DatabaseFile.BLOCK_DATA);
        this.createTable();
    }
    public void createTable() {
        // Create a table which stores an id, the custom block id (including the item-id that generated this block) and the location of the block in the world
        String sql = """
                CREATE TABLE IF NOT EXISTS blocks (
                	id integer PRIMARY KEY,
                	block_id text NOT NULL,
                	item_id text NOT NULL,
                	world text NOT NULL,
                	x integer NOT NULL,
                	y integer NOT NULL,
                	z integer NOT NULL
                );""";
        try (Statement statement = this.getConnection().createStatement()){
            statement.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public BlockTable insert(String blockId, String itemId, String world, int x, int y, int z) {
        // Prepare the sql statement
        String sql = "INSERT INTO blocks(block_id, item_id, world, x, y, z) VALUES(?, ?, ?, ?, ?, ?)";

        NuclearCraft.instance.getLogger().info("Inserting block into database: " + blockId + " " + itemId + " " + world + " " + x + " " + y + " " + z);

        try (PreparedStatement statement = this.getConnection().prepareStatement(sql)){
            statement.setString(1, blockId);
            statement.setString(2, itemId);
            statement.setString(3, world);
            statement.setInt(4, x);
            statement.setInt(5, y);
            statement.setInt(6, z);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }
    public ResultSet get(int x, int y, int z, String world) {
        String sql = "SELECT * FROM blocks WHERE x = ? AND y = ? AND z = ? AND world = ?";
        try (PreparedStatement statement = this.getConnection().prepareStatement(sql)) {
            statement.setInt(1, x);
            statement.setInt(2, y);
            statement.setInt(3, z);
            statement.setString(4, world);
            statement.executeQuery();
            return statement.getResultSet();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public BlockTable delete(int x, int y, int z, String world) {
        String sql = "DELETE FROM blocks WHERE x = ? AND y = ? AND z = ? AND world = ?";
        // Delete the block from the database with the given coordinates and world
        try (PreparedStatement statement = this.getConnection().prepareStatement(sql)) {
            statement.setInt(1, x);
            statement.setInt(2, y);
            statement.setInt(3, z);
            statement.setString(4, world);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }





}
