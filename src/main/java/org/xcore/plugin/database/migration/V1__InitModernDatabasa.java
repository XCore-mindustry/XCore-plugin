package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;

public class V1__InitModernDatabasa implements Migration {
    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public void up(MongoDatabase database) {

    }
}
