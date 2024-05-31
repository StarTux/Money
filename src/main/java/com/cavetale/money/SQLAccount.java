package com.cavetale.money;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
@Name("accounts")
@NotNull
public final class SQLAccount implements SQLRow {
    @Id private Integer id;
    @Unique private UUID owner;
    private double money;
}
