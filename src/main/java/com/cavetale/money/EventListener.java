package com.cavetale.money;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    final MoneyPlugin plugin;

    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerJoin(PlayerJoinEvent event) {
        plugin.createAccount(event.getPlayer());
    }
}
