package com.cavetale.money;

import com.cavetale.money.vault.MoneyVault;
import com.winthier.sql.SQLDatabase;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class MoneyPlugin extends JavaPlugin {
    final SQLDatabase db = new SQLDatabase(this);
    DecimalFormat numberFormat;
    final MoneyCommand moneyCommand = new MoneyCommand(this);
    final EventListener eventListener = new EventListener(this);
    @Getter protected static MoneyPlugin instance;

    @Override
    public void onLoad() {
        instance = this;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            MoneyVault.register(this);
            getLogger().info("Vault backend registered");
        } else {
            getLogger().warning("Vault backend NOT registered!");
        }
    }

    @Override
    public void onEnable() {
        db.registerTables(SQLAccount.class, SQLLog.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(eventListener, this);
        numberFormat = new DecimalFormat("#,###.00", new DecimalFormatSymbols(Locale.US));
        numberFormat.setParseBigDecimal(true);
        getCommand("money").setExecutor(moneyCommand);
        for (Player player : getServer().getOnlinePlayers()) {
            createAccount(player);
        }
    }

    void moneyInfo(Player player) {
        double money = getMoney(player.getUniqueId());
        String format = formatMoney(money);
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + String.format("You have %s.", format));
        ComponentBuilder cb = new ComponentBuilder("");
        BaseComponent[] tooltip;
        tooltip = TextComponent
            .fromLegacyText(ChatColor.GREEN + "/money\n"
                            + ChatColor.WHITE + ChatColor.ITALIC + "Check your balance.");
        cb.append("[Money]").color(ChatColor.GREEN)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/money"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        if (player.hasPermission("money.log")) {
            cb.append("  ");
            tooltip = TextComponent
                .fromLegacyText(ChatColor.YELLOW + "/money log " + ChatColor.ITALIC + "PAGE\n"
                                + ChatColor.WHITE + ChatColor.ITALIC + "Check your bank statement."
                                + " This is your entire transaction history so you know where your"
                                + " money came from or where it went.");
            cb.append("[Log]").color(ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/money log"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (player.hasPermission("money.top")) {
            cb.append("  ");
            tooltip = TextComponent
                .fromLegacyText(ChatColor.AQUA + "/money top\n" + ChatColor.WHITE + ChatColor.ITALIC
                                + "Money highscore. List the richest players.");
            cb.append("[Top]").color(ChatColor.AQUA)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/money top"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (player.hasPermission("money send")) {
            cb.append("  ");
            tooltip = TextComponent
                .fromLegacyText(ChatColor.BLUE + "/money send " + ChatColor.ITALIC + "PLAYER AMOUNT\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Send money to other people. This is how business is done.");
            cb.append("[Send]").color(ChatColor.BLUE)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/money send"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        player.spigot().sendMessage(cb.create());
        if (player.hasPermission("money.admin")) {
            player.sendMessage(ChatColor.GOLD + "* * * " + ChatColor.BOLD + "Admin commands"
                               + ChatColor.RESET + ChatColor.GOLD + " * * *");
        }
        cb = null;
        if (player.hasPermission("money.round")) {
            if (cb == null) cb = new ComponentBuilder("");
            tooltip = TextComponent
                .fromLegacyText(ChatColor.GOLD + "/money round\n" + ChatColor.WHITE
                                + ChatColor.ITALIC + "Round all accounts to two decimals.");
            cb.append("<round>").color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/money round"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (player.hasPermission("money.set")) {
            if (cb == null) cb = new ComponentBuilder("");
            cb.append("  ").reset();
            tooltip = TextComponent
                .fromLegacyText(ChatColor.RED + "/money set <user> <amount>\n" + ChatColor.WHITE
                                + ChatColor.ITALIC + "Set user balance.");
            cb.append("<set>").color(ChatColor.RED)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/money set "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (player.hasPermission("money.give")) {
            if (cb == null) cb = new ComponentBuilder("");
            cb.append("  ").reset();
            tooltip = TextComponent
                .fromLegacyText(ChatColor.YELLOW + "/money give <user> <amount> [comment]\n"
                                + ChatColor.WHITE + ChatColor.ITALIC + "Grant user money.");
            cb.append("<give>").color(ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/money give "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (player.hasPermission("money.take")) {
            if (cb == null) cb = new ComponentBuilder("");
            cb.append("  ").reset();
            tooltip = TextComponent
                .fromLegacyText(ChatColor.DARK_RED + "/money take <user> <amount> [comment]\n"
                                + ChatColor.WHITE + ChatColor.ITALIC + "Remove user money.");
            cb.append("<take>").color(ChatColor.DARK_RED)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/money take "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
        }
        if (cb != null) {
            player.spigot().sendMessage(cb.create());
        }
        player.sendMessage("");
    }

    public String formatMoney(double amount) {
        return "\u26C3" + numberFormat.format(amount);
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
        testAmount(amount);
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money)"
                                   + " VALUES ('%s', %.2f)"
                                   + " ON DUPLICATE KEY UPDATE money = %.2f",
                                   tableName, owner, amount, amount);
        db.executeUpdate(sql);
    }

    /**
     * Add money to a bank account. If the account does not exist, it
     * will be created.
     *
     * @return true if transaction was successful, false otherwise.
     */
    public void giveMoney(UUID owner, double amount) {
        testAmount(amount);
        if (amount < 0.01) return;
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money)"
                                   + " VALUES ('%s', %.2f)"
                                   + " ON DUPLICATE KEY UPDATE money = ROUND(money + %.2f, 2)",
                                   tableName, owner, amount, amount);
        db.executeUpdate(sql);
    }

    /**
     * Remove money from a bank account.
     *
     * @return true if the account and exist and has the required
     * balance, so this transaction happened, false otherwise.
     */
    public boolean takeMoney(UUID owner, double amount) {
        testAmount(amount);
        if (amount < 0.01) return true;
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = ROUND(money - %.2f, 2)"
                                   + " WHERE owner = '%s' AND money >= %.2f",
                                   tableName, amount, owner, amount);
        return db.executeUpdate(sql) != 0;
    }

    private void testAmount(double amount) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    void createAccount(Player player) {
        String tableName = db.getTable(SQLAccount.class).getTableName();
        UUID uuid = player.getUniqueId();
        final String name = player.getName();
        String sql = String.format("INSERT IGNORE INTO `%s` (`owner`, `money`)"
                                   + " VALUES ('%s', %.2f)",
                                   tableName, uuid, 0.0);
        db.executeUpdateAsync(sql, (ret) -> {
                if (ret == null || ret == 0) return;
                getLogger().info("Created account for " + name + ".");
            });
    }
}
