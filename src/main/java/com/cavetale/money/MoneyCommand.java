package com.cavetale.money;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.winthier.playercache.PlayerCache;
import java.text.ParseException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class MoneyCommand extends AbstractCommand<MoneyPlugin> {
    protected MoneyCommand(final MoneyPlugin plugin) {
        super(plugin, "money");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("top").arguments("[page]")
            .description("Richest Player List")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::top);
        rootNode.addChild("log").arguments("[page]")
            .description("View Transaction Log")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .playerCaller(this::log);
        rootNode.addChild("send").arguments("<player> <amount>")
            .description("Send someone Money")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.integer(i -> i > 0))
            .playerCaller(this::send);
        rootNode.playerCaller(this::money);
    }

    private boolean money(Player player, String[] args) {
        if (args.length != 0) return false;
        plugin.moneyInfo(player);
        PluginPlayerEvent.Name.USE_MONEY.call(plugin, player);
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        final int page;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                throw new CommandWarn("Invalid page number: " + args[0]);
            }
            if (page < 1) {
                throw new CommandWarn("Invalid page number: " + args[0]);
            }
        } else {
            page = 1;
        }
        int pageLen = 10;
        int offset = (page - 1) * pageLen;
        plugin.db.find(SQLAccount.class).orderByDescending("money")
            .limit(pageLen).offset(offset).findListAsync((top) -> {
                    if (top.isEmpty()) {
                        sender.sendMessage(text("Page " + page + " is empty.", RED));
                        return;
                    }
                    sender.sendMessage(text("Rich list page " + page, GREEN));
                    int i = 0;
                    for (SQLAccount row: top) {
                        i += 1;
                        String name = PlayerCache.nameForUuid(row.getOwner());
                        sender.sendMessage(join(noSeparators(),
                                                text(" " + (offset + i) + ") ", DARK_GREEN),
                                                text(plugin.numberFormat.format(row.getMoney()), GREEN),
                                                text(" " + name, WHITE)));
                    }
                });
        return true;
    }

    private boolean log(Player player, String[] args) {
        if (args.length > 1) return false;
        final int page;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                throw new CommandWarn("Invalid page number: " + args[0]);
            }
            if (page < 1) {
                throw new CommandWarn("Invalid page number: " + args[0]);
            }
        } else {
            page = 1;
        }
        int offset = (page - 1) * 10;
        plugin.db.find(SQLLog.class).eq("owner", player.getUniqueId())
            .orderByDescending("time").limit(10).offset(offset).findListAsync((logs) -> {
                    if (logs.isEmpty()) {
                        player.sendMessage(text("Page " + page + " is empty", RED));
                        return;
                    }
                    player.sendMessage(text("Bank Statement Page " + page, GREEN));
                    for (SQLLog log: logs) {
                        String comment = log.getComment();
                        if (comment == null) comment = "N/A";
                        player.sendMessage(join(noSeparators(),
                                                text(plugin.formatDate(log.getTime()), DARK_GREEN),
                                                text(" " + plugin.numberFormat.format(log.getMoney()), GREEN),
                                                text(" " + comment, GRAY)));
                    }
                });
        return true;
    }

    private boolean send(Player player, String[] args) {
        if (args.length != 2) return false;
        String argTarget = args[0];
        String argAmount = args[1];
        final PlayerCache target = PlayerCache.forName(argTarget);
        if (target == null) {
            throw new CommandWarn("Player not found: " + argTarget);
        }
        double amount;
        try {
            amount = plugin.numberFormat.parse(argAmount).doubleValue();
        } catch (ParseException pe) {
            amount = -1.0;
        }
        if (amount < 1) {
            throw new CommandWarn("Invalid amount: " + argAmount);
        }
        if (!plugin.takeMoney(player.getUniqueId(), amount)) {
            throw new CommandWarn("You cannot afford " + plugin.formatMoney(amount) + "!");
        }
        plugin.giveMoney(target.uuid, amount);
        plugin.log(player.getUniqueId(), -amount, plugin, "Sent to " + target.name);
        plugin.log(target.uuid, amount, plugin, "Sent by " + player.getName());
        player.sendMessage(text("Sent " + plugin.formatMoney(amount) + " to " + target.name, GREEN));
        Player targetPlayer = plugin.getServer().getPlayer(target.uuid);
        if (targetPlayer != null) {
            targetPlayer.sendMessage(text(player.getName() + " sent you " + plugin.formatMoney(amount), GREEN));
        }
        return true;
    }
}
