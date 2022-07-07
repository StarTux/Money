package com.cavetale.money;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.coin.Coin;
import com.cavetale.mytems.item.font.Glyph;
import com.winthier.playercache.PlayerCache;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class MoneyCommand extends AbstractCommand<MoneyPlugin> {
    private static final SimpleDateFormat BRIEF_DATE_FORMAT = new SimpleDateFormat("MMM d");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYY MMM d HH:mm:ss");

    protected MoneyCommand(final MoneyPlugin plugin) {
        super(plugin, "money");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("top").denyTabCompletion()
            .permission("money.top")
            .description("Richest Player List")
            .senderCaller(this::top);
        rootNode.addChild("log").arguments("[page]")
            .permission("money.log")
            .description("View Transaction Log")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .playerCaller(this::log);
        rootNode.addChild("send").arguments("<player> <amount>")
            .permission("money.send")
            .description("Send someone Money")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.integer(i -> i > 0))
            .playerCaller(this::send);
        rootNode.playerCaller(this::money);
    }

    private boolean money(Player player, String[] args) {
        if (args.length == 1 && player.hasPermission("money.other")) {
            return plugin.adminCommand.get(player, args);
        }
        if (args.length != 0) return false;
        plugin.getMoneyAsync(player.getUniqueId(), amount -> moneyInfo(player, amount));
        PluginPlayerEvent.Name.USE_MONEY.call(plugin, player);
        return true;
    }

    private void moneyInfo(Player player, double money) {
        List<Component> lines = new ArrayList<>();
        lines.add(join(noSeparators(), Mytems.KITTY_COIN, text("Money", BLUE, BOLD)));
        lines.add(empty());
        lines.add(empty());
        lines.add(join(noSeparators(),
                       text("You have ", GRAY),
                       Mytems.COPPER_COIN,
                       text(plugin.formatMoneyRaw(money), GOLD)));
        if (player.hasPermission("money.send")) {
            lines.add(empty());
            lines.add(join(noSeparators(), Mytems.TURN_RIGHT, text(" Send", GREEN))
                      .clickEvent(runCommand("/money send"))
                      .hoverEvent(showText(join(separator(newline()),
                                                text("/money send <player> <amount>", GREEN),
                                                text("Send somebody money", GRAY)))));
        }
        if (player.hasPermission("money.log")) {
            lines.add(empty());
            lines.add(join(noSeparators(), VanillaItems.WRITABLE_BOOK, text(" Log", BLUE))
                      .clickEvent(runCommand("/money log"))
                      .hoverEvent(showText(join(separator(newline()),
                                                text("/money log [page]", BLUE),
                                                text("Check your transaction", GRAY),
                                                text("history", GRAY)))));
        }
        if (player.hasPermission("money.top")) {
            lines.add(empty());
            lines.add(join(noSeparators(), Mytems.GOLDEN_CUP, text(" Top", DARK_AQUA))
                      .clickEvent(runCommand("/money top"))
                      .hoverEvent(showText(join(separator(newline()),
                                                text("/money top", AQUA),
                                                text("List the richest players", GRAY)))));
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(meta -> {
                if (meta instanceof BookMeta bookMeta) {
                    bookMeta.author(text("Cavetale"));
                    bookMeta.title(text("Money"));
                    bookMeta.pages(List.of(join(separator(newline()), lines)));
                }
            });
        player.openBook(book);
    }

    private void top(CommandSender sender) {
        plugin.db.find(SQLAccount.class)
            .gt("money", 0.0)
            .orderByDescending("money")
            .limit(100)
            .findListAsync(rows -> topCallback(sender, rows));
    }

    private void topCallback(CommandSender sender, List<SQLAccount> rows) {
        if (rows.isEmpty()) {
            sender.sendMessage(text("No players to show", RED));
            return;
        }
        final List<Component> pages = new ArrayList<>();
        final int perPage = 7;
        int rank = 0;
        double money = -1;
        for (int i = 0; i < rows.size(); i += perPage) {
            final List<Component> page = new ArrayList<>();
            for (int j = 0; j < perPage; j += 1) {
                final int k = i + j;
                if (k >= rows.size()) break;
                SQLAccount row = rows.get(k);
                if (Math.abs(money - row.getMoney()) >= 0.01) {
                    rank += 1;
                    money = row.getMoney();
                }
                page.add(join(separator(newline()),
                              join(separator(space()), text(rank, BLUE, BOLD), text(PlayerCache.nameForUuid(row.getOwner()))),
                              join(noSeparators(), Mytems.COPPER_COIN, text(plugin.formatMoneyRaw(row.getMoney()), GOLD)))
                         .hoverEvent(showText(join(separator(newline()),
                                                   join(separator(space()), Glyph.toComponent("" + rank),
                                                        text(PlayerCache.nameForUuid(row.getOwner()), GRAY)),
                                                   Coin.format(row.getMoney())))));
            }
            pages.add(join(separator(newline()), page));
        }
        if (sender instanceof Player player) {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            book.editMeta(meta -> {
                    if (meta instanceof BookMeta bookMeta) {
                        bookMeta.author(text("Cavetale"));
                        bookMeta.title(text("Money"));
                        bookMeta.pages(pages);
                    }
                });
            player.openBook(book);
        } else {
            sender.sendMessage(join(separator(newline()), pages));
        }
    }

    private void log(Player player) {
        plugin.db.find(SQLLog.class)
            .eq("owner", player.getUniqueId())
            .orderByDescending("time")
            .limit(50 * 4)
            .findListAsync(logs -> logCallback(player, logs));
    }

    private void logCallback(Player player, List<SQLLog> logs) {
        if (logs.isEmpty()) {
            player.sendMessage(text("No logs to show", RED));
            return;
        }
        final int perPage = 5;
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < logs.size(); i += perPage) {
            List<Component> page = new ArrayList<>();
            for (int j = 0; j < perPage; j += 1) {
                final int k = i + j;
                if (k >= logs.size()) break;
                SQLLog log = logs.get(k);
                List<Component> lines = new ArrayList<>(3);
                lines.add(join(noSeparators(),
                               text(BRIEF_DATE_FORMAT.format(log.getTime()), BLUE),
                               space(),
                               Mytems.COPPER_COIN,
                               text(plugin.formatMoneyRaw(log.getMoney()), log.getMoney() >= 0.01 ? GOLD : DARK_RED)));
                if (log.getComment() != null) {
                    lines.add(text(log.getComment(), GRAY, ITALIC));
                }
                List<Component> tooltip = new ArrayList<>(3);
                tooltip.add(text(DATE_FORMAT.format(log.getTime()), BLUE));
                tooltip.add(Coin.format(log.getMoney()));
                if (log.getComment() != null) {
                    tooltip.add(text(log.getComment(), GRAY, ITALIC));
                }
                page.add(join(separator(newline()), lines).hoverEvent(showText(join(separator(newline()), tooltip))));
            }
            pages.add(join(separator(newline()), page));
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(meta -> {
                if (meta instanceof BookMeta bookMeta) {
                    bookMeta.author(text("Cavetale"));
                    bookMeta.title(text("Money"));
                    bookMeta.pages(pages);
                }
            });
        player.openBook(book);
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
