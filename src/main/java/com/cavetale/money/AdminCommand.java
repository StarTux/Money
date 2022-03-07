package com.cavetale.money;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.text.ParseException;
import org.bukkit.command.CommandSender;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class AdminCommand extends AbstractCommand<MoneyPlugin> {
    protected AdminCommand(final MoneyPlugin plugin) {
        super(plugin, "moneyadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("get").arguments("<player>")
            .description("Get Player Balance")
            .completers(PlayerCache.NAME_COMPLETER)
            .senderCaller(this::get);
        rootNode.addChild("set").arguments("<player> <amount>")
            .description("Set Player Balance")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i >= 0))
            .senderCaller(this::set);
        rootNode.addChild("give").arguments("<player> <amount>")
            .description("Increase Player Balance")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i >= 0))
            .senderCaller(this::give);
        rootNode.addChild("take").arguments("<player> <amount>")
            .description("Reduce Player Balance")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i >= 0))
            .senderCaller(this::take);
        rootNode.addChild("log").arguments("<player> [page]")
            .description("View Player Logs")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::log);
        rootNode.addChild("transfer").arguments("<from> <to>")
            .description("Transfer a player")
            .completers(PlayerCache.NAME_COMPLETER,
                        PlayerCache.NAME_COMPLETER)
            .senderCaller(this::transfer);
        rootNode.addChild("round").denyTabCompletion()
            .description("Round Player Balances")
            .senderCaller(this::round);
    }

    private boolean get(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final PlayerCache owner = PlayerCache.forName(args[0]);
        if (owner == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        double amount = plugin.getMoney(owner.uuid);
        sender.sendMessage(text(owner.name + " has " + plugin.formatMoney(amount), YELLOW));
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        final PlayerCache owner = PlayerCache.forName(args[0]);
        if (owner == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            throw new CommandWarn("Invalid amount: " + amount);
        }
        plugin.setMoney(owner.uuid, amount);
        sender.sendMessage(text(String.format("Balance of %s is now %s", owner.name, plugin.formatMoney(amount)),
                                YELLOW));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final PlayerCache owner = PlayerCache.forName(args[0]);
        if (owner == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            throw new CommandWarn("Invalid amount: " + amount);
        }
        plugin.giveMoney(owner.uuid, amount);
        if (args.length > 2) {
            StringBuilder sb = new StringBuilder(args[2]);
            for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
            plugin.log(owner.uuid, amount, plugin, sb.toString());
        } else {
            plugin.log(owner.uuid, amount, plugin, "Admin Command");
        }
        sender.sendMessage(text(String.format("Granted %s to %s", plugin.formatMoney(amount), owner.name),
                                YELLOW));
        return true;
    }

    private boolean take(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final PlayerCache owner = PlayerCache.forName(args[0]);
        if (owner == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            throw new CommandWarn("Invalid amount: " + amount);
        }
        if (plugin.takeMoney(owner.uuid, amount)) {
            if (args.length > 2) {
                StringBuilder sb = new StringBuilder(args[2]);
                for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
                plugin.log(owner.uuid, -amount, plugin, sb.toString());
            } else {
                plugin.log(owner.uuid, -amount, plugin, "Admin Command");
            }
            amount = plugin.getMoney(owner.uuid);
            sender.sendMessage(text(String.format("Balance of %s is now %s", owner.name, plugin.formatMoney(amount)),
                                    YELLOW));
        } else {
            throw new CommandWarn(String.format("%s could not be charged %s."
                                                + " Make sure the account exists and has enough money",
                                                owner.name, plugin.formatMoney(amount)));
        }
        return true;
    }

    private boolean log(CommandSender sender, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        final PlayerCache player = PlayerCache.forName(args[0]);
        if (player == null) {
            throw new CommandWarn("Unknown bank account: " + args[0]);
        }
        final int page = args.length >= 2
            ? CommandArgCompleter.requireInt(args[1], i -> i > 0)
            : 1;
        int offset = (page - 1) * 10;
        plugin.db.find(SQLLog.class).eq("owner", player.uuid)
            .orderByDescending("time").limit(10).offset(offset).findListAsync((logs) -> {
                    if (logs.isEmpty()) {
                        sender.sendMessage(text("Page " + page + " is empty", RED));
                        return;
                    }
                    sender.sendMessage(text(player.name + " bank statement page " + page, YELLOW));
                    for (SQLLog log: logs) {
                        String comment = log.getComment();
                        if (comment == null) comment = "N/A";
                        sender.sendMessage(join(separator(space()),
                                                text(plugin.formatDate(log.getTime()), GRAY),
                                                text(plugin.numberFormat.format(log.getMoney()), YELLOW),
                                                text(comment, GRAY)));
                    }
                });
        return true;
    }

    private boolean transfer(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache from = PlayerCache.forArg(args[0]);
        if (from == null) throw new CommandWarn("Player not found: " + args[0]);
        PlayerCache to = PlayerCache.forArg(args[1]);
        if (to == null) throw new CommandWarn("Player not found: " + args[1]);
        if (from.equals(to)) throw new CommandWarn("Players are identical: " + from.getName());
        double amount = plugin.getMoney(from.uuid);
        if (amount < 0.01) throw new CommandWarn(from.name + " has no money!");
        plugin.setMoney(from.uuid, 0.0);
        plugin.giveMoney(to.uuid, amount);
        sender.sendMessage(text("Transferred " + plugin.formatMoney(amount)
                                + " from " + from.name
                                + " to " + to.name,
                                AQUA));
        return true;
    }

    private boolean round(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        String tableName = plugin.db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = ROUND(money, 2)", tableName);
        int count = plugin.db.executeUpdate(sql);
        sender.sendMessage(text("Rounded " + count + " bank accounts", AQUA));
        return true;
    }
}
