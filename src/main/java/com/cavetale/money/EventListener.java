package com.cavetale.money;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.ChatPaginator;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    final MoneyPlugin plugin;

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.createAccountAsync(player);
        plugin.createCache(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.cache.remove(uuid);
    }

    @EventHandler
    protected void onPlayerSidebar(PlayerSidebarEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Cached cached = plugin.cache.get(uuid);
        if (cached == null) return;
        if (!cached.showProgress && !cached.showTimed) return;
        List<Component> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (cached.showProgress) {
            Component moneyMessage = Component.text("/money ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.formatMoney(cached.displayMoney), NamedTextColor.GOLD));
            lines.add(moneyMessage);
            player.sendActionBar(moneyMessage);
            double difference = cached.money - cached.displayMoney;
            if (Math.abs(difference) < 1.0) {
                cached.showProgress = false;
                cached.showTimed = true;
                cached.showUntil = now + 10000L;
            } else {
                final double distance;
                if ((difference > 0) != (cached.distance > 0) || Math.abs(difference) > Math.abs(cached.distance)) {
                    distance = difference;
                    cached.distance = difference;
                } else {
                    distance = cached.distance;
                }
                double addition = distance > 0
                    ? Math.min(difference, Math.max(1.0, Math.ceil(distance * 0.01)))
                    : Math.max(difference, Math.min(-1.0, Math.ceil(distance * 0.01)));
                cached.displayMoney += addition;
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.MASTER,
                                 0.25f, (addition > 0 ? 2.0f : 0.5f));
            }
        } else if (cached.showTimed) {
            if (now > cached.showUntil) {
                cached.showTimed = false;
                return;
            }
            Component moneyMessage = Component.text("/money ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.formatMoney(cached.money), NamedTextColor.GOLD));
            lines.add(moneyMessage);
        }
        cached.logs.removeIf(log -> now > log.getTime().getTime() + 10000L);
        for (SQLLog log : cached.logs) {
            String format = plugin.numberFormat.format(log.getMoney());
            lines.add(log.getMoney() > 0
                      ? Component.text("+" + format, NamedTextColor.GREEN)
                      : Component.text(format, NamedTextColor.RED));
            if (log.getComment() != null) {
                for (String line : ChatPaginator.wordWrap(log.getComment(), 22)) {
                    lines.add(Component.text(" " + ChatColor.stripColor(line), NamedTextColor.GRAY));
                }
            }
            cached.showTimed = true;
            cached.showUntil = now + 10000L;
        }
        event.add(plugin, Priority.LOWEST, lines);
    }
}
