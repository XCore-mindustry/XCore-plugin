package org.xcore.plugin.utils.models;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;

import static org.xcore.plugin.PluginVars.database;

@NoArgsConstructor
@AllArgsConstructor
public class AdminData {
    public String uuid = "";
    public String password = "";

    public boolean adminConfirmed = false;

    public AdminData(String uuid) {
        this.uuid = uuid;
    }

    public void hashPassword(String password) {
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public boolean verifyPassword(String password) {
        return BCrypt.checkpw(password, this.password);
    }

    public void save() {
        database.adminDatas.save(this);
    }
}
