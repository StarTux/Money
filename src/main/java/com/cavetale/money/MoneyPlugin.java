package com.cavetale.money;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.util.Json;
import com.cavetale.money.vault.MoneyVault;
import com.winthier.sql.SQLDatabase;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class MoneyPlugin extends JavaPlugin {
    @Getter protected static MoneyPlugin instance;
    protected final SQLDatabase db = new SQLDatabase(this);
    protected final DecimalFormat numberFormat = new DecimalFormat("#,###.00", new DecimalFormatSymbols(Locale.US));
    protected final MoneyCommand moneyCommand = new MoneyCommand(this);
    protected final AdminCommand adminCommand = new AdminCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Map<UUID, Cached> cache = new HashMap<>();
    private final CoreMoney coreMoney = new CoreMoney(this);

    @Override
    public void onLoad() {
        instance = this;
        numberFormat.setParseBigDecimal(true);
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            MoneyVault.register(this);
            getLogger().info("Vault backend registered");
        } else {
            getLogger().warning("Vault backend NOT registered!");
        }
        coreMoney.register();
    }

    @Override
    public void onEnable() {
        db.registerTables(List.of(SQLAccount.class, SQLLog.class));
        db.createAllTables();
        getServer().getPluginManager().registerEvents(eventListener, this);
        moneyCommand.enable();
        adminCommand.enable();
        for (Player player : getServer().getOnlinePlayers()) {
            createAccountAsync(player);
            createCache(player);
        }
        pruneLogs();
        new MenuListener().enable();
    }

    private void pruneLogs() {
        final Date then = new Date(Instant.now().minus(365L, ChronoUnit.DAYS).toEpochMilli());
        final int limit = 100_000;
        db.find(SQLLog.class).lt("time", then).limit(limit).deleteAsync(rt -> {
                getLogger().info("Deleted " + rt + " logs older than " + then);
                if (rt >= limit) {
                    Bukkit.getScheduler().runTaskLater(this, this::pruneLogs, 20L);
                }
            });
    }

    @Override
    public void onDisable() {
        coreMoney.unregister();
    }

    public String formatMoneyRaw(double amount) {
        String format = numberFormat.format(amount);
        if (format.endsWith(".00")) format = format.substring(0, format.length() - 3);
        if (format.isEmpty()) format = "0";
        return format;
    }

    public String formatMoney(double amount) {
        return "\u26C3" + formatMoneyRaw(amount);
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
                    cached.min = Math.min(cached.min, newMoney);
                    cached.max = Math.max(cached.max, newMoney);
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
    public boolean giveMoney(UUID owner, double amount) {
        testAmount(amount);
        if (amount < 0.01) return false;
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money)"
                                   + " VALUES ('%s', %.2f)"
                                   + " ON DUPLICATE KEY UPDATE money = ROUND(money + %.2f, 2)",
                                   tableName, owner, amount, amount);
        db.executeUpdate(sql);
        getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                    cached.min = Math.min(cached.min, newMoney);
                    cached.max = Math.max(cached.max, newMoney);
                    cached.money = newMoney;
                    cached.showProgress = true;
                }));
        return true;
    }

    public void giveMoneyAsync(UUID owner, double amount, Consumer<Boolean> callback) {
        testAmount(amount);
        if (amount < 0.01) {
            callback.accept(false);
            return;
        }
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("INSERT INTO `%s` (owner, money)"
                                   + " VALUES ('%s', %.2f)"
                                   + " ON DUPLICATE KEY UPDATE money = ROUND(money + %.2f, 2)",
                                   tableName, owner, amount, amount);
        db.executeUpdateAsync(sql, res -> {
                getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                            cached.min = Math.min(cached.min, newMoney);
                            cached.max = Math.max(cached.max, newMoney);
                            cached.money = newMoney;
                            cached.showProgress = true;
                        }));
                callback.accept(true);
            });
    }

    /**
     * Remove money from a bank account.
     *
     * @return true if the account and exist and has the required
     * balance, so this transaction happened, false otherwise.
     */
    public boolean takeMoney(UUID owner, double amount) {
        testAmount(amount);
        if (amount < 0.01) return false;
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = ROUND(money - %.2f, 2)"
                                   + " WHERE owner = '%s' AND money >= %.2f",
                                   tableName, amount, owner, amount);
        final int result = db.executeUpdate(sql);
        getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                    cached.min = Math.min(cached.min, newMoney);
                    cached.max = Math.max(cached.max, newMoney);
                    cached.money = newMoney;
                    cached.showProgress = true;
                }));
        return result != 0;
    }

    public void takeMoneyAsync(UUID owner, double amount, Consumer<Boolean> callback) {
        testAmount(amount);
        if (amount < 0.01) {
            callback.accept(false);
            return;
        }
        String tableName = db.getTable(SQLAccount.class).getTableName();
        String sql = String.format("UPDATE `%s` SET money = ROUND(money - %.2f, 2)"
                                   + " WHERE owner = '%s' AND money >= %.2f",
                                   tableName, amount, owner, amount);
        db.executeUpdateAsync(sql, result -> {
                getMoneyAsync(owner, newMoney -> applyCache(owner, cached -> {
                            cached.min = Math.min(cached.min, newMoney);
                            cached.max = Math.max(cached.max, newMoney);
                            cached.money = newMoney;
                            cached.showProgress = true;
                        }));
                callback.accept(result != 0);
            });
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
                cached.min = amount;
                cached.max = amount;
                cached.displayMoney = amount;
                cache.put(uuid, cached);
            });
    }

    protected void log(final UUID owner, final double money, final Plugin plugin, final String comment) {
        SQLLog log = new SQLLog(owner, money, plugin, comment);
        final LogPacket packet = new LogPacket(log);
        db.insertAsync(log, null);
        applyCache(owner, cached -> {
                cached.logs.add(packet);
                cached.showTimed = true;
                cached.showUntil = System.currentTimeMillis() + 10000L;
            });
        Connect.get().broadcastMessage(ServerGroup.current(), "money:log", Json.serialize(packet));
    }
}
