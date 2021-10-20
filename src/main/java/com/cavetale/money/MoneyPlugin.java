package com.cavetale.money;

import com.cavetale.money.vault.MoneyVault;
import com.winthier.sql.SQLDatabase;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class MoneyPlugin extends JavaPlugin {
    @Getter protected static MoneyPlugin instance;
    protected final SQLDatabase db = new SQLDatabase(this);
    protected DecimalFormat numberFormat;
    protected final MoneyCommand moneyCommand = new MoneyCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Map<UUID, Cached> cache = new HashMap<>();

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
            createAccountAsync(player);
            createCache(player);
        }
    }

    private static Component lines(ComponentLike... lines) {
        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }

    private static Component lines(Iterable<? extends ComponentLike> lines) {
        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }

    public void moneyInfo(Player player, double money) {
        String format = formatMoney(money);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Component.text("You have " + format, NamedTextColor.GREEN));
        List<Component> buttons = new ArrayList<>();
        if (player.hasPermission("money.send")) {
            buttons.add(Component.text("[Send]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/money send "))
                        .hoverEvent(HoverEvent.showText(lines(new Component[] {
                                        Component.text("/money send <player> <amount>", NamedTextColor.GREEN),
                                        Component.text("Send somebody money", NamedTextColor.GRAY),
                                    }))));
        }
        if (player.hasPermission("money.log")) {
            buttons.add(Component.text("[Log]", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/money log"))
                        .hoverEvent(HoverEvent.showText(lines(new Component[] {
                                        Component.text("/money log <page>", NamedTextColor.YELLOW),
                                        Component.text("Check your transaction", NamedTextColor.GRAY),
                                        Component.text("history.", NamedTextColor.GRAY),
                                    }))));
        }
        if (player.hasPermission("money.top")) {
            buttons.add(Component.text("[Top]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/money top"))
                        .hoverEvent(HoverEvent.showText(lines(new Component[] {
                                        Component.text("/money top" + NamedTextColor.AQUA),
                                        Component.text("Money highscore.", NamedTextColor.GRAY),
                                        Component.text("List the richest players.", NamedTextColor.GRAY),
                                    }))));
        }
        lines.add(Component.join(JoinConfiguration.separator(Component.space()), buttons));
        lines.add(Component.empty());
        player.sendMessage(lines(lines));
    }

    public void moneyInfo(Player player) {
        getMoneyAsync(player.getUniqueId(), amount -> moneyInfo(player, amount));
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

    public void getMoneyAsync(UUID owner, Consumer<Double> callback) {
        db.find(SQLAccount.class).eq("owner", owner).findUniqueAsync(row -> {
                double amount = row != null ? row.getMoney() : 0.0;
                callback.accept(amount);
            });
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
        getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                    cached.money = newMoney;
                    cached.showProgress = true;
                }));
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
        getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                    cached.money = newMoney;
                    cached.showProgress = true;
                }));
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
        final int result = db.executeUpdate(sql);
        getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                    cached.money = newMoney;
                    cached.showProgress = true;
                }));
        return result != 0;
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

    protected void createAccountAsync(Player player) {
        String tableName = db.getTable(SQLAccount.class).getTableName();
        UUID uuid = player.getUniqueId();
        final String name = player.getName();
        String sql = String.format("INSERT IGNORE INTO `%s` (`owner`, `money`)"
                                   + " VALUES ('%s', %.2f)",
                                   tableName, uuid, 0.0);
        db.executeUpdateAsync(sql, (ret) -> {
                if (ret == null || ret == 0) return;
                getLogger().info("Created account for " + name);
            });
    }

    protected void applyCache(UUID uuid, Consumer<Cached> consumer) {
        Cached cached = cache.get(uuid);
        if (cached != null) consumer.accept(cached);
    }

    protected void createCache(Player player) {
        UUID uuid = player.getUniqueId();
        getMoneyAsync(uuid, amount -> {
                if (!player.isOnline()) return;
                if (cache.containsKey(uuid)) return;
                Cached cached = new Cached();
                cached.money = amount;
                cached.displayMoney = amount;
                cache.put(uuid, cached);
            });
    }

    protected void log(final UUID owner, final double money, final Plugin plugin, final String comment) {
        SQLLog log = new SQLLog(owner, money, plugin, comment);
        db.insertAsync(log, null);
        applyCache(owner, cached -> {
                cached.logs.add(log);
                cached.showTimed = true;
                cached.showUntil = System.currentTimeMillis() + 10000L;
            });
    }
}
