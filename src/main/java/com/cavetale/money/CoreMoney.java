package com.cavetale.money;

import com.cavetale.core.money.Money;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public final class CoreMoney implements Money {
    private final MoneyPlugin plugin;

    @Override
    public MoneyPlugin getPlugin() {
        return plugin;
    }

    /**
     * Check if a player has enough balance.
     * @param uuid the player uuid
     * @param the required balance
     * @return true if they have enough, false otherwise
     */
    @Override
    public boolean has(UUID uuid, double amount) {
        return get(uuid) >= amount;
    }

    /**
     * Get a player balance.
     * @param uuid the player uuid
     * @return the balance
     */
    @Override
    public double get(UUID uuid) {
        return plugin.getMoney(uuid);
    }

    /**
     * Get a player balance.
     * @param uuid the player uuid
     * @return the balance
     */
    @Override
    public void get(UUID uuid, Consumer<Double> callback) {
        plugin.getMoneyAsync(uuid, callback);
    }

    /**
     * Add money to a player's bank account.  This will be logged.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param plugin the issuing plugin
     * @param comment the comment
     * @return whether the transaction was successful
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public boolean give(UUID uuid, double amount, Plugin issuingPlugin, String comment) {
        if (plugin.giveMoney(uuid, amount)) {
            plugin.log(uuid, amount, issuingPlugin, comment);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add money to a player's bank account without logging.
     * @param uuid the player uuid
     * @param amount the money amount
     * @return whether the transaction was successful
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public boolean give(UUID uuid, double amount) {
        return plugin.giveMoney(uuid, amount);
    }

    /**
     * Add money asynchronously to a player's bank account and log it.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param issuingPlugin the issuing plugin
     * @param comment the comment
     * @param callback a consumer accepting the result
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public void give(UUID uuid, double amount, Plugin issuingPlugin, String comment, Consumer<Boolean> callback) {
        plugin.giveMoneyAsync(uuid, amount, result -> {
                if (result) {
                    plugin.log(uuid, amount, issuingPlugin, comment);
                    callback.accept(true);
                } else {
                    callback.accept(false);
                }
            });
    }

    /**
     * Add money to a player's bank account asynchronously without
     * logging.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param callback a consumer accepting the result
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public void give(UUID uuid, double amount, Consumer<Boolean> callback) {
        plugin.giveMoneyAsync(uuid, amount, callback);
    }

    /**
     * Subtract money from a player's bank account.  This will be
     * logged.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param issuingPlugin the issuing plugin
     * @param comment the comment
     * @return true if successful, false if the player balance is
     *   insufficient
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public boolean take(UUID uuid, double amount, Plugin issuingPlugin, String comment) {
        if (!plugin.takeMoney(uuid, amount)) return false;
        plugin.log(uuid, -amount, issuingPlugin, comment);
        return true;
    }

    /**
     * Silently subtract money from a player's bank account without
     * logging.
     * @param uuid the player uuid
     * @param amount the money amount
     * @return true if successful, false if the player balance is
     *   insufficient
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public boolean take(UUID uuid, double amount) {
        return plugin.takeMoney(uuid, amount);
    }

    /**
     * Subtract money from a player's bank account and log it.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param issuingPlugin the issuing plugin
     * @param comment the comment
     * @param callback a consumer accepting the result
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public void take(UUID uuid, double amount, Plugin issuingPlugin, String comment, Consumer<Boolean> callback) {
        plugin.takeMoneyAsync(uuid, amount, result -> {
                if (result) {
                    plugin.log(uuid, -amount, issuingPlugin, comment);
                    callback.accept(true);
                } else {
                    callback.accept(false);
                }
            });
    }

    /**
     * Subtract money from a player's bank account asynchronously
     * without logging.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param callback a consumer accepting the result
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    @Override
    public void take(UUID uuid, double amount, Consumer<Boolean> callback) {
        plugin.takeMoneyAsync(uuid, amount, callback);
    }

    /**
     * Format a money amount.
     * @param amount the amount
     * @return the formatted amount
     */
    @Override
    public String format(double amount) {
        return plugin.formatMoney(amount);
    }

    @Override
    public Component toComponent(double amount) {
        return Component.text(format(amount));
    }

    /**
     * Log a transaction that never happened.  This is done
     * automatically by give() and take().  Other than those two, this
     * function accepts negative input.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param issuingPlugin the issuing plugin
     * @param comment the comment
     */
    @Override
    public void log(UUID uuid, double amount, Plugin issuingPlugin, String comment) {
        plugin.log(uuid, amount, issuingPlugin, comment);
    }
}
