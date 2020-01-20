package com.cavetale.money;

import com.winthier.generic_events.FormatMoneyEvent;
import com.winthier.generic_events.GivePlayerMoneyEvent;
import com.winthier.generic_events.PlayerBalanceEvent;
import com.winthier.generic_events.TakePlayerMoneyEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    final MoneyPlugin plugin;

    @EventHandler
    void onPlayerBalance(PlayerBalanceEvent event) {
        event.setBalance(plugin.getMoney(event.getPlayerId()));
    }

    @EventHandler
    void onGivePlayerMoney(GivePlayerMoneyEvent event) {
        plugin.giveMoney(event.getPlayerId(), event.getAmount());
        event.setSuccessful(true);
        plugin.db.insertAsync(new SQLLog(event.getPlayerId(), event.getAmount(),
                                         event.getIssuingPlugin(), event.getComment()), null);
    }

    @EventHandler
    void onTakePlayerMoney(TakePlayerMoneyEvent event) {
        boolean res = plugin.takeMoney(event.getPlayerId(), event.getAmount());
        event.setSuccessful(res);
        if (res) {
            plugin.db.insertAsync(new SQLLog(event.getPlayerId(), -event.getAmount(),
                                             event.getIssuingPlugin(), event.getComment()),
                                  null);
        }
    }

    @EventHandler
    void onFormatMoney(FormatMoneyEvent event) {
        event.setFormat(plugin.formatMoney(event.getMoney()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPlayerJoin(PlayerJoinEvent event) {
        plugin.createAccount(event.getPlayer());
    }
}
