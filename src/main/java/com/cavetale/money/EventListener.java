package com.cavetale.money;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.mytems.item.coin.Coin;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private static final Component PREFIX = text(tiny("money "), GRAY);
    private final MoneyPlugin plugin;

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
    protected void onPlayerHud(PlayerHudEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Cached cached = plugin.cache.get(uuid);
        if (cached == null) return;
        event.footer(PlayerHudPriority.LOWEST,
                     List.of(join(noSeparators(), PREFIX, Coin.format(cached.displayMoney))));
        if (!cached.showProgress && !cached.showTimed) return;
        List<Component> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (cached.showProgress) {
            Component moneyMessage = join(noSeparators(), PREFIX, Coin.format(cached.displayMoney));
            final double span = cached.max - cached.min;
            cached.progress = span >= 0.01
                ? (cached.displayMoney - cached.min) / span
                : 1.0;
            event.bossbar(PlayerHudPriority.UPDATE, moneyMessage, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS, Set.of(), (float) cached.progress);
            final double difference = cached.money - cached.displayMoney;
            if (Math.abs(difference) < 1.0) {
                cached.showProgress = false;
                cached.showTimed = true;
                cached.showUntil = now + 10000L;
                cached.min = cached.money;
                cached.max = cached.money;
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
            Component moneyMessage = join(noSeparators(), PREFIX, Coin.format(cached.money));
            event.bossbar(PlayerHudPriority.UPDATE, moneyMessage, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS, Set.of(), (float) cached.progress);
        }
        cached.logs.removeIf(log -> now > log.getTime().getTime() + 10000L);
        for (SQLLog log : cached.logs) {
            String format = plugin.numberFormat.format(log.getMoney());
            lines.add(log.getMoney() > 0
                      ? text("+" + format, GREEN)
                      : text(format, RED));
            if (log.getComment() != null) {
                for (String line : ChatPaginator.wordWrap(log.getComment(), 22)) {
                    lines.add(text(" " + ChatColor.stripColor(line), GRAY));
                }
            }
            cached.showTimed = true;
            cached.showUntil = now + 10000L;
        }
        event.sidebar(PlayerHudPriority.LOWEST, lines);
    }
}
