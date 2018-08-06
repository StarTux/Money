package com.cavetale.money;

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
public final class SQLLog {
    @Id private Integer id;
    @Column(nullable = false) private Date time;
    @Column(nullable = false) private UUID owner;
    @Column(nullable = false) private Double money;
    @Column(nullable = true, length = 255) private String plugin;
    @Column(nullable = true, length = 255) private String comment;

    public SQLLog() { }

    SQLLog(UUID owner, double money, Plugin plugin, String comment) {
        this.time = new Date();
        this.owner = owner;
        this.money = money;
        String pluginName = plugin == null ? null : plugin.getName();
        this.plugin = pluginName;
        this.comment = comment;
    }
}
