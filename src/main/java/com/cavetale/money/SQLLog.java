package com.cavetale.money;

import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.plugin.Plugin;

@Data @Table(name = "logs")
public final class SQLLog {
    @Id private Integer id;
    private Date time;
    private UUID owner;
    private Double money;
    private String plugin;
    private String comment;

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
