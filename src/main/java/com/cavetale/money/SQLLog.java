package com.cavetale.money;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.plugin.Plugin;

@Data
@Name("logs")
public final class SQLLog implements SQLRow {
    private static final int MAX_COMMENT_LENGTH = 255;
    @Id private Integer id;
    @Keyed @NotNull private Date time;
    @Keyed @NotNull private UUID owner;
    @NotNull private double money;
    @VarChar(255) private String plugin;
    @VarChar(MAX_COMMENT_LENGTH) private String comment;

    public SQLLog() { }

    SQLLog(final UUID owner, final double money, final Plugin plugin, final String comment) {
        this.time = new Date();
        this.owner = owner;
        this.money = money;
        String pluginName = plugin == null ? null : plugin.getName();
        this.plugin = pluginName;
        this.comment = comment.length() <= MAX_COMMENT_LENGTH
            ? comment : comment.substring(0, MAX_COMMENT_LENGTH);
    }
}
