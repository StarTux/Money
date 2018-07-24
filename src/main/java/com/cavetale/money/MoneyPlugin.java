package com.cavetale.money;

import com.winthier.generic_events.GivePlayerMoneyEvent;
import com.winthier.generic_events.PlayerBalanceEvent;
import com.winthier.generic_events.TakePlayerMoneyEvent;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLDatabase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyPlugin extends JavaPlugin implements Listener {
    private SQLDatabase db;

    @Override
    public void onEnable() {
        db = new SQLDatabase(this);
        db.registerTables(SQLAccount.class, SQLLog.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) {
            if (player == null) return false;
            double money = getMoney(player.getUniqueId());
            String format = formatMoney(money);
            player.sendMessage(ChatColor.GREEN + String.format("You have %s.", format));
            return true;
        }
        switch (args[0]) {
        case "set":
            if (args.length == 3) {
                if (!sender.hasPermission("money.admin")) return false;
                UUID owner = PlayerCache.uuidForName(args[1]);
                if (owner == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException nfe) {
                    amount = -1;
                }
                if (amount < 0) {
                    sender.sendMessage("Invalid amount: " + args[2]);
                    return true;
                }
                setMoney(owner, amount);
                amount = getMoney(owner);
                sender.sendMessage(String.format("Balance of %s is now %s.", args[1], formatMoney(amount)));
                return true;
            }
            break;
        case "grant": case "give":
            if (args.length == 3) {
                if (!sender.hasPermission("money.admin")) return false;
                UUID owner = PlayerCache.uuidForName(args[1]);
                if (owner == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException nfe) {
                    amount = -1;
                }
                if (amount < 0) {
                    sender.sendMessage("Invalid amount: " + args[2]);
                    return true;
                }
                giveMoney(owner, amount);
                db.save(new SQLLog(owner, amount, this, sender.getName() + " " + args[0] + " " + args[1] + " " + args[2]));
                amount = getMoney(owner);
                sender.sendMessage(String.format("Balance of %s is now %s.", args[1], formatMoney(amount)));
                return true;
            }
            break;
        case "charge": case "take":
            if (args.length == 3) {
                if (!sender.hasPermission("money.admin")) return false;
                UUID owner = PlayerCache.uuidForName(args[1]);
                if (owner == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException nfe) {
                    amount = -1;
                }
                if (amount < 0) {
                    sender.sendMessage("Invalid amount: " + args[2]);
                    return true;
                }
                if (takeMoney(owner, amount)) {
                    db.save(new SQLLog(owner, -amount, this, sender.getName() + " " + args[0] + " " + args[1] + " " + args[2]));
                    amount = getMoney(owner);
                    sender.sendMessage(String.format("Balance of %s is now %s.", args[1], formatMoney(amount)));
                } else {
                    sender.sendMessage(String.format("%s could not be charged %s. Make sure the account exists and has enough money.", args[1], formatMoney(amount)));
                }
                return true;
            }
            break;
        case "top":
            if (args.length == 1 || args.length == 2) {
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException nfe) {
                        page = -1;
                    }
                }
                if (page < 1) {
                    sender.sendMessage("Invalid page number: " + args[1]);
                    return true;
                }
                int offset = (page - 1) * 10;
                List<SQLAccount> top = db.find(SQLAccount.class).orderByDescending("money").limit(10).offset(offset).findList();
                if (top.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Rich list page " + page + " is empty.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Rich list page " + page);
                int i = 0;
                for (SQLAccount row: top) {
                    i += 1;
                    sender.sendMessage(" " + ChatColor.DARK_GREEN + (offset + i) + ") " + ChatColor.GREEN + formatMoneyMono(row.getMoney()) + ChatColor.WHITE + " " + PlayerCache.nameForUuid(row.getOwner()));
                }
                return true;
            }
            break;
        case "log":
            if (args.length >= 1 || args.length <= 3) {
                UUID target = null;
                String targetName = null;
                if (args.length >= 3 && sender.hasPermission("money.admin")) {
                    target = PlayerCache.uuidForName(args[2]);
                    targetName = args[2];
                    if (target == null) {
                        sender.sendMessage("Unknown bank account: " + args[2]);
                        return true;
                    }
                } else if (player != null) {
                    target = player.getUniqueId();
                    targetName = player.getName();
                }
                if (target == null) return false;
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException nfe) {
                        page = -1;
                    }
                }
                if (page < 1) {
                    sender.sendMessage("Invalid page number: " + args[1]);
                    return true;
                }
                int offset = (page - 1) * 10;
                List<SQLLog> logs = db.find(SQLLog.class).eq("owner", target).orderByDescending("time").limit(10).offset(offset).findList();
                if (logs.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + targetName + " bank statement page " + page + " is empty.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + targetName + " bank statement page " + page);
                for (SQLLog log: logs) {
                    String comment = log.getComment();
                    if (comment == null) comment = "N/A";
                    sender.sendMessage("" + ChatColor.DARK_GREEN + formatDate(log.getTime()) + " " + ChatColor.GREEN + formatMoneyMono(log.getMoney()) + " " + ChatColor.GRAY + comment);
                }
                return true;
            }
            break;
        default:
            break;
        }
        return false;
    }

    public String formatMoney(double amount) {
        if (amount > 0.99 && amount < 1.01) return "1 Kitty Coin";
        String[] toks = String.format("%.02f", amount).split("\\.", 2);
        StringBuilder sb = new StringBuilder();
        int len = toks[0].length();
        for (int i = 0; i < len; i += 1) {
            if (i > 0 && i % 3 == 0) sb.insert(0, ",");
            sb.insert(0, toks[0].charAt(len - i - 1));
        }
        if (!toks[1].equals("00")) {
            sb.append(".").append(toks[1]);
        }
        sb.append(" Kitty Coins");
        return sb.toString();
    }

    public String formatMoneyMono(double amount) {
        String[] toks = String.format("%.02f", amount).split("\\.", 2);
        StringBuilder sb = new StringBuilder();
        int len = toks[0].length();
        for (int i = 0; i < len; i += 1) {
            if (i > 0 && i % 3 == 0) sb.insert(0, ",");
            sb.insert(0, toks[0].charAt(len - i - 1));
        }
        sb.append(".").append(toks[1]);
        return sb.toString();
    }

    public String formatDate(Date date) {
        return new SimpleDateFormat("YY/MMM/dd HH:mm").format(date);
    }

    public double getMoney(UUID owner) {
        SQLAccount row = db.find(SQLAccount.class).eq("owner", owner).findUnique();
        if (row == null) return 0.0;
        return row.getMoney();
    }

    /**
     * Set a bank account to a specific balance.  If it does not
     * exist, it will be created.
     */
    public void setMoney(UUID owner, double amount) {
        if (Double.isNaN(amount)) throw new IllegalArgumentException("Amount cannot be NaN");
        if (Double.isInfinite(amount)) throw new IllegalArgumentException("Amount cannot be infinite");
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money) VALUES ('%s', %.2f) ON DUPLICATE KEY UPDATE money = %.2f", tableName, owner, amount, amount);
        db.executeUpdate(sql);
    }

    /**
     * Add money to a bank account. If the account does not exist, it
     * will be created.
     *
     * @return true if transaction was successful, false otherwise.
     */
    public void giveMoney(UUID owner, double amount) {
        if (Double.isNaN(amount)) throw new IllegalArgumentException("Amount cannot be NaN");
        if (Double.isInfinite(amount)) throw new IllegalArgumentException("Amount cannot be infinite");
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money) VALUES ('%s', %.2f) ON DUPLICATE KEY UPDATE money = money + %.2f", tableName, owner, amount, amount);
        db.executeUpdate(sql);
    }

    /**
     * Remove money from a bank account.
     *
     * @return true if the account and exist and has the required
     * balance, so this transaction happened, false otherwise.
     */
    public boolean takeMoney(UUID owner, double amount) {
        if (Double.isNaN(amount)) throw new IllegalArgumentException("Amount cannot be NaN");
        if (Double.isInfinite(amount)) throw new IllegalArgumentException("Amount cannot be infinite");
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = money - %.2f WHERE owner = '%s' AND money >= %.2f", tableName, amount, owner, amount);
        return db.executeUpdate(sql) != 0;
    }

    @EventHandler
    void onPlayerBalance(PlayerBalanceEvent event) {
        event.setBalance(getMoney(event.getPlayerId()));
    }

    @EventHandler
    void onGivePlayerMoney(GivePlayerMoneyEvent event) {
        giveMoney(event.getPlayerId(), event.getAmount());
        event.setSuccessful(true);
        db.save(new SQLLog(event.getPlayerId(), event.getAmount(), event.getIssuingPlugin(), event.getComment()));
    }

    @EventHandler
    void onTakePlayerMoney(TakePlayerMoneyEvent event) {
        boolean res = takeMoney(event.getPlayerId(), event.getAmount());
        event.setSuccessful(res);
        db.save(new SQLLog(event.getPlayerId(), -event.getAmount(), event.getIssuingPlugin(), event.getComment()));
    }
}
