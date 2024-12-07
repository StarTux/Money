package com.cavetale.money;

import com.cavetale.core.chat.Chat;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static com.cavetale.core.font.DefaultFont.bookmarked;
import static com.cavetale.core.font.Unicode.subscript;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class MoneyCommand extends AbstractCommand<MoneyPlugin> {
    private static final DateTimeFormatter BRIEF_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d YYYY");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("YYYY MMM d HH:mm:ss");
    private static final TextColor BOOKMARK = color(0x333333);
    private static final ZoneId ZONE_ID = ZoneId.of("UTC-11");

    protected MoneyCommand(final MoneyPlugin plugin) {
        super(plugin, "money");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("top").denyTabCompletion()
            .permission("money.top")
            .description("Richest Player List")
            .senderCaller(this::top);
        rootNode.addChild("log").denyTabCompletion()
            .permission("money.log")
            .description("View Transaction Log")
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
        lines.add(textOfChildren(Mytems.KITTY_COIN, text("Money", BLUE, BOLD))
                  .hoverEvent(showText(text("Back to /menu", GRAY)))
                  .clickEvent(runCommand("/menu")));
        lines.add(textOfChildren(text("You have", GRAY), newline(), bookmarked(BOOKMARK, Coin.format(money))));
        if (player.hasPermission("money.send")) {
            lines.add(empty());
            lines.add(textOfChildren(Mytems.TURN_RIGHT, text(" Send", GREEN))
                      .clickEvent(runCommand("/money send"))
                      .hoverEvent(showText(join(separator(newline()),
                                                text("/money send <player> <amount>", GREEN),
                                                text("Send somebody money", GRAY)))));
        }
        if (player.hasPermission("money.log")) {
            lines.add(empty());
            lines.add(textOfChildren(VanillaItems.WRITABLE_BOOK, text(" Log", BLUE))
                      .clickEvent(runCommand("/money log"))
                      .hoverEvent(showText(join(separator(newline()),
                                                text("/money log", BLUE),
                                                text("Check your transaction", GRAY),
                                                text("history", GRAY)))));
        }
        if (player.hasPermission("money.top")) {
            lines.add(empty());
            lines.add(textOfChildren(Mytems.GOLDEN_CUP, text(" Top", DARK_AQUA))
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
        player.closeInventory();
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
                              textOfChildren(text(subscript("" + rank), DARK_GRAY), text(PlayerCache.nameForUuid(row.getOwner()))),
                              bookmarked(BOOKMARK, Coin.format(row.getMoney())))
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
            player.closeInventory();
            player.openBook(book);
        } else {
            sender.sendMessage(join(separator(newline()), pages));
        }
    }

    private void log(Player player) {
        plugin.db.find(SQLLog.class)
            .eq("owner", player.getUniqueId())
            .orderByDescending("time")
            .findListAsync(logs -> logCallback(player, logs));
    }

    private void logCallback(Player player, List<SQLLog> logs) {
        if (logs.isEmpty()) {
            player.sendMessage(text("No logs to show", RED));
            return;
        }
        // Sort by date
        final var dateMap = new HashMap<LocalDate, List<SQLLog>>();
        for (SQLLog log : logs) {
            final List<SQLLog> list = dateMap.computeIfAbsent(LocalDate.ofInstant(log.getTime().toInstant(), ZONE_ID), _date -> new ArrayList<>());
            boolean didMerge = false;
            for (SQLLog oldLog : list) {
                if (!Objects.equals(log.getComment(), oldLog.getComment()) || !Objects.equals(log.getPlugin(), oldLog.getPlugin())) {
                    continue;
                }
                oldLog.setMoney(log.getMoney() + oldLog.getMoney());
                didMerge = true;
                break;
            }
            if (didMerge) continue;
            list.add(log);
        }
        final List<LocalDate> dateList = new ArrayList<>(dateMap.keySet());
        dateList.sort(Comparator.<LocalDate>naturalOrder().reversed());
        // Build pages
        final long now = System.currentTimeMillis();
        final int perPage = 5;
        List<Component> pages = new ArrayList<>();
        for (LocalDate date : dateList) {
            final List<SQLLog> dailyLogs = dateMap.get(date);
            List<Component> page = new ArrayList<>();
            page.add(text(BRIEF_DATE_FORMAT.format(date), DARK_GRAY));
            page.add(empty());
            for (SQLLog log : dailyLogs) {
                if (page.size() >= 10) {
                    if (pages.size() >= 50) break;
                    pages.add(join(separator(newline()), page));
                    page.clear();
                }
                List<Component> lines = new ArrayList<>(2);
                lines.add(bookmarked(BOOKMARK, Coin.format(log.getMoney())));
                if (log.getComment() != null) {
                    String comment = log.getComment();
                    final int max = 18;
                    if (comment.length() > max) {
                        comment = comment.substring(0, max) + "...";
                    }
                    lines.add(text(tiny(comment), log.getMoney() >= 0.0 ? DARK_AQUA : DARK_RED));
                }
                // Build the tooltip
                List<Component> tooltip = new ArrayList<>(4);
                // Add the exact date
                tooltip.add(text(DATE_TIME_FORMAT.format(LocalDateTime.ofInstant(log.getTime().toInstant(), ZONE_ID)), BLUE));
                // Print how long ago
                List<Component> ago = new ArrayList<>();
                final long millis = now - log.getTime().getTime();
                final long seconds = millis / 1000L;
                final long minutes = seconds / 60L;
                final long hours = minutes / 60L;
                final long days = hours / 24L;
                if (days > 0) {
                    ago.add(text(days, GRAY));
                    ago.add(text("d ", DARK_GRAY));
                }
                if (hours > 0) {
                    ago.add(text(hours % 24L, GRAY));
                    ago.add(text("h ", DARK_GRAY));
                }
                if (days == 0) {
                    if (minutes > 0) {
                        ago.add(text(minutes % 60L, GRAY));
                        ago.add(text("m ", DARK_GRAY));
                    }
                    if (hours == 0) {
                        ago.add(text(seconds % 60L, GRAY));
                        ago.add(text("s ", DARK_GRAY));
                    }
                }
                ago.add(text("ago", GRAY, ITALIC));
                tooltip.add(join(noSeparators(), ago));
                // Add money and comment
                tooltip.add(Coin.format(log.getMoney()));
                if (log.getComment() != null) {
                    tooltip.add(text(log.getComment(), GRAY, ITALIC));
                }
                page.add(join(separator(newline()), lines).hoverEvent(showText(join(separator(newline()), tooltip))));
            }
            pages.add(join(separator(newline()), page));
            if (pages.size() >= 50) break;
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(meta -> {
                if (meta instanceof BookMeta bookMeta) {
                    bookMeta.author(text("Cavetale"));
                    bookMeta.title(text("Money"));
                    bookMeta.pages(pages);
                }
            });
        player.closeInventory();
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
        if (Chat.doesIgnore(target.uuid, player.getUniqueId())) {
            throw new CommandWarn("You cannot send money to that player");
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
