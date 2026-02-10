package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.mindrot.jbcrypt.BCrypt;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class AdminData extends ModelData {
    @Builder.Default public String uuid = "";
    @Builder.Default public String password = "";
    @Builder.Default public boolean adminConfirmed = false;

    public AdminData(String uuid) {
        this.uuid = uuid;
    }

    public void hashPassword(String password) {
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public boolean verifyPassword(String password) {
        return BCrypt.checkpw(password, this.password);
    }
}