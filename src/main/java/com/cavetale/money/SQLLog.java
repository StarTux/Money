package com.cavetale.money;

import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.plugin.Plugin;

@Data @Table(name = "logs",
             indexes = @Index(columnList = "owner"))
public final class SQLLog implements SQLRow {
    private static final int MAX_COMMENT_LENGTH = 255;
    @Id private Integer id;
    @Column(nullable = false) private Date time;
    @Column(nullable = false) private UUID owner;
    @Column(nullable = false) private double money;
    @Column(nullable = true, length = 255) private String plugin;
    @Column(nullable = true, length = MAX_COMMENT_LENGTH) private String comment;

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
