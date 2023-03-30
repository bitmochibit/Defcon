package com.enderryno.nuclearcraft.enums;

public enum DatabaseFile {
    BLOCK_DATA("block_data.db");

    private final String fileName;

    DatabaseFile(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
