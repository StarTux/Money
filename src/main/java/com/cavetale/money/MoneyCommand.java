package com.cavetale.money;

import com.winthier.generic_events.GenericEvents;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class MoneyCommand implements TabExecutor {
    private final MoneyPlugin plugin;

    @Override
    public boolean onCommand(final CommandSender sender, Command command,
                             String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) {
            if (player == null) return false;
            plugin.moneyInfo(player);
            return true;
        }
        String[] argl = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
        case "round": return roundCommand(sender, argl);
        case "set": return setCommand(sender, argl);
        case "grant": case "give": return giveCommand(sender, args[0], argl);
        case "charge": case "take": return takeCommand(sender, args[0], argl);
        case "top": return topCommand(sender, argl);
        case "log": return logCommand(sender, player, argl);
        case "send":
            if (player == null) {
                sender.sendMessage("[money] player expected");
                return true;
            }
            return sendCommand(player, argl);
        case "help": case "?":
            if (!sender.hasPermission("money.help")) return noPerm(sender);
            if (player == null) {
                sender.sendMessage("[money] player expected");
                return true;
            }
            if (args.length == 1) {
                plugin.moneyInfo(player);
                return true;
            }
            break;
        default:
            if (args.length == 1) {
                if (!sender.hasPermission("money.other")) return noPerm(sender);
                UUID target = GenericEvents.cachedPlayerUuid(args[0]);
                if (target == null) return false;
                double money = plugin.getMoney(target);
                String format = plugin.formatMoney(money);
                sender.sendMessage(String.format("%s has %s.", args[0], format));
                return true;
            }
            break;
        }
        if (player != null) {
            plugin.moneyInfo(player);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = args.length == 0 ? "" : args[0];
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            Stream<String> commands = Stream.of("top", "log", "send", "help", "set", "give", "take", "round")
                .filter(c -> sender.hasPermission("money." + c));
            Stream<String> players = plugin.getServer().getOnlinePlayers().stream().map(Player::getName);
            return Stream.concat(commands, players)
                .filter(i -> i.startsWith(arg))
                .collect(Collectors.toList());
        } else if (args.length == 3 && cmd.equals("send")) {
            if (arg.isEmpty()) return Arrays.asList("0.00");
            try {
                double amount = plugin.numberFormat.parse(arg).doubleValue();
                return Arrays.asList(plugin.numberFormat.format(amount));
            } catch (ParseException nfe) {
                return Collections.emptyList();
            }
        } else if (args.length == 2 && cmd.equals("log")) {
                return Collections.emptyList();
        }
        return null;
    }

    private static boolean noPerm(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission!");
        return true;
    }

    private boolean roundCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("money.round")) return noPerm(sender);
        if (args.length != 0) return false;
        String tableName = plugin.db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = ROUND(money, 2)", tableName);
        int count = plugin.db.executeUpdate(sql);
        sender.sendMessage("Rounded " + count + " bank accounts.");
        return true;
    }

    private boolean setCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("money.set")) return noPerm(sender);
        if (args.length != 2) return false;
        UUID owner = GenericEvents.cachedPlayerUuid(args[0]);
        if (owner == null) {
            sender.sendMessage("Player not found: " + args[0]);
            return true;
        }
        String name = GenericEvents.cachedPlayerName(owner);
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            sender.sendMessage("Invalid amount: " + args[1]);
            return true;
        }
        plugin.setMoney(owner, amount);
        sender.sendMessage(String.format("Balance of %s is now %s.", name,
                                         plugin.formatMoney(amount)));
        return true;
    }

    private boolean giveCommand(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("money.give")) return noPerm(sender);
        if (args.length < 2) return false;
        UUID owner = GenericEvents.cachedPlayerUuid(args[0]);
        if (owner == null) {
            sender.sendMessage("Player not found: " + args[0]);
            return true;
        }
        String name = GenericEvents.cachedPlayerName(owner);
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            sender.sendMessage("Invalid amount: " + args[1]);
            return true;
        }
        plugin.giveMoney(owner, amount);
        if (args.length > 2) {
            StringBuilder sb = new StringBuilder(args[2]);
            for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
            plugin.db.insertAsync(new SQLLog(owner, amount, plugin, sb.toString()), null);
        } else {
            plugin.db.insertAsync(new SQLLog(owner, amount, plugin, sender.getName() + ": /" + alias
                                      + " " + args[0] + " " + args[1]), null);
        }
        sender.sendMessage(String.format("Granted %s %s.", name, plugin.formatMoney(amount)));
        return true;
    }

    private boolean takeCommand(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("money.take")) return noPerm(sender);
        if (args.length < 2) return false;
        UUID owner = GenericEvents.cachedPlayerUuid(args[0]);
        if (owner == null) {
            sender.sendMessage("Player not found: " + args[0]);
            return true;
        }
        String name = GenericEvents.cachedPlayerName(owner);
        double amount;
        try {
            amount = plugin.numberFormat.parse(args[1]).doubleValue();
        } catch (ParseException pe) {
            amount = -1;
        }
        if (amount < 0.0) {
            sender.sendMessage("Invalid amount: " + args[1]);
            return true;
        }
        if (plugin.takeMoney(owner, amount)) {
            if (args.length > 2) {
                StringBuilder sb = new StringBuilder(args[2]);
                for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
                plugin.db.insertAsync(new SQLLog(owner, -amount, plugin, sb.toString()), null);
            } else {
                plugin.db.insertAsync(new SQLLog(owner, -amount, plugin, sender.getName() + ": /"
                                                 + alias + " " + args[0] + " " + args[1]), null);
            }
            amount = plugin.getMoney(owner);
            sender.sendMessage(String.format("Balance of %s is now %s.", name,
                                             plugin.formatMoney(amount)));
        } else {
            String msg = String.format("%s could not be charged %s."
                                       + " Make sure the account exists and has enough money.",
                                       name, plugin.formatMoney(amount));
            sender.sendMessage(msg);
        }
        return true;
    }

    private boolean topCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("money.top")) return noPerm(sender);
        if (args.length > 1) return false;
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                page = -1;
            }
        }
        if (page < 1) {
            sender.sendMessage("Invalid page number: " + args[0]);
            return true;
        }
        int pageLen = 10;
        int offset = (page - 1) * pageLen;
        final int finalPage = page;
        plugin.db.find(SQLAccount.class).orderByDescending("money")
            .limit(pageLen).offset(offset).findListAsync((top) -> {
                    if (top.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Rich list page "
                                       + finalPage + " is empty.");
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "Rich list page " + finalPage);
                int i = 0;
                for (SQLAccount row: top) {
                    i += 1;
                    String name = GenericEvents.cachedPlayerName(row.getOwner());
                    sender.sendMessage(" " + ChatColor.DARK_GREEN + (offset + i) + ") "
                                       + ChatColor.GREEN
                                       + plugin.numberFormat.format(row.getMoney())
                                       + ChatColor.WHITE + " " + name);
                }
            });
        return true;
    }

    private boolean logCommand(CommandSender sender, Player player, String[] args) {
        if (!sender.hasPermission("money.log")) return noPerm(sender);
        if (args.length > 2) return false;
        UUID target = null;
        String targetName = null;
        if (args.length >= 2) {
            if (!sender.hasPermission("money.log.other")) return noPerm(sender);
            target = GenericEvents.cachedPlayerUuid(args[1]);
            targetName = args[1];
            if (target == null) {
                sender.sendMessage("Unknown bank account: " + args[1]);
                return true;
            }
        } else if (player != null) {
            target = player.getUniqueId();
            targetName = player.getName();
        }
        if (target == null) return false;
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                page = -1;
            }
        }
        if (page < 1) {
            sender.sendMessage("Invalid page number: " + args[0]);
            return true;
        }
        int offset = (page - 1) * 10;
        final String finalTargetName = targetName;
        final int finalPage = page;
        plugin.db.find(SQLLog.class).eq("owner", target)
            .orderByDescending("time").limit(10).offset(offset).findListAsync((logs) -> {
                    if (logs.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + finalTargetName
                                           + " bank statement page " + finalPage + " is empty.");
                        return;
                    }
                    sender.sendMessage(ChatColor.GREEN + finalTargetName
                                       + " bank statement page " + finalPage);
                    for (SQLLog log: logs) {
                        String comment = log.getComment();
                        if (comment == null) comment = "N/A";
                        sender.sendMessage("" + ChatColor.DARK_GREEN
                                           + plugin.formatDate(log.getTime())
                                           + " " + ChatColor.GREEN
                                           + plugin.numberFormat.format(log.getMoney())
                                           + " " + ChatColor.GRAY + comment);
                    }
                });
        return true;
    }

    private boolean sendCommand(Player player, String[] args) {
        if (!player.hasPermission("money.send")) return noPerm(player);
        if (args.length != 2) return false;
        String argTarget = args[0];
        String argAmount = args[1];
        UUID target = GenericEvents.cachedPlayerUuid(argTarget);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + argTarget);
            return true;
        }
        String targetName = GenericEvents.cachedPlayerName(target);
        double amount;
        try {
            amount = plugin.numberFormat.parse(argAmount).doubleValue();
        } catch (ParseException pe) {
            amount = -1.0;
        }
        if (amount < 0.01) {
            player.sendMessage(ChatColor.RED + "Invalid amount: " + argAmount);
            return true;
        }
        if (!plugin.takeMoney(player.getUniqueId(), amount)) {
            player.sendMessage(ChatColor.RED + "You cannot afford "
                               + plugin.formatMoney(amount) + "!");
            return true;
        }
        plugin.giveMoney(target, amount);
        plugin.db.insertAsync(new SQLLog(player.getUniqueId(), -amount, plugin,
                                         "Sent to " + targetName), null);
        plugin.db.insertAsync(new SQLLog(target, amount, plugin, "Sent by " + player.getName()),
                              null);
        player.sendMessage(ChatColor.GREEN + "Sent " + plugin.formatMoney(amount)
                           + " to " + targetName);
        Player targetPlayer = plugin.getServer().getPlayer(target);
        if (targetPlayer != null) {
            targetPlayer.sendMessage("" + ChatColor.GREEN + player.getName()
                                     + " sent you " + plugin.formatMoney(amount) + ".");
        }
        return true;
    }
}
