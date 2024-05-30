package com.cavetale.money;

import java.io.Serializable;
import java.util.UUID;
import lombok.Value;

@Value
public final class LogPacket implements Serializable {
    private final long time;
    private final UUID owner;
    private final double money;
    private final String plugin;
    private final String comment;

    public LogPacket(final SQLLog log) {
        this.time = log.getTime().getTime();
        this.owner = log.getOwner();
        this.money = log.getMoney();
        this.plugin = log.getPlugin();
        this.comment = log.getComment();
    }
}
