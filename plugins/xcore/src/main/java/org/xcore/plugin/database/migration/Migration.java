package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;

public interface Migration {
    int getVersion();
    String getDescription();
    void up(MongoDatabase database);
}