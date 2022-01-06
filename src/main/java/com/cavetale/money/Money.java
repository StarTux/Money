package com.cavetale.money;

import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class Money {
    private Money() { }

    /**
     * Check if a player has enough balance.
     * @param uuid the player uuid
     * @param the required balance
     * @return true if they have enough, false otherwise
     */
    public static boolean has(UUID uuid, double amount) {
        return get(uuid) >= amount;
    }

    /**
     * Get a player balance.
     * @param uuid the player uuid
     * @return the balance
     */
    public static double get(UUID uuid) {
        return MoneyPlugin.instance.getInstance().getMoney(uuid);
    }

    /**
     * Add money to a player's bank account.  This will be logged.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param plugin the issuing plugin
     * @param comment the comment
     * @return always true
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    public static boolean give(UUID uuid, double amount, Plugin plugin, String comment) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        MoneyPlugin.instance.giveMoney(uuid, amount);
        MoneyPlugin.instance.log(uuid, amount, plugin, comment);
        return true;
    }

    /**
     * Add money to a player's bank account, without logging.  Despite
     * the name, the player will still notice the effect, especially
     * the sidebar and action bar.
     * @param uuid the player uuid
     * @param amount the money amount
     * @return always true
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    public static boolean giveSilent(UUID uuid, double amount) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        MoneyPlugin.instance.giveMoney(uuid, amount);
        return true;
    }

    /**
     * Subtract money from a player's bank account.  This will be
     * logged.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param plugin the issuing plugin
     * @param comment the comment
     * @return true if successful, false if the player balance is
     *   insufficient
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    public static boolean take(UUID uuid, double amount, Plugin plugin, String comment) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (!MoneyPlugin.instance.takeMoney(uuid, amount)) return false;
        MoneyPlugin.instance.log(uuid, -amount, plugin, comment);
        return true;
    }

    /**
     * Silently subtract money from a player's bank account without
     * logging it.  Despite the name, the player will still notice the
     * effects of the transaction, especially the sidebar and action
     * bar.
     * @param uuid the player uuid
     * @param amount the money amount
     * @return true if successful, false if the player balance is
     *   insufficient
     * @throws IllegalArgumentException if amount is not a number,
     *   infinite, or negative.
     */
    public static boolean takeSilent(UUID uuid, double amount) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        return MoneyPlugin.instance.takeMoney(uuid, amount);
    }

    /**
     * Format a money amount.
     * @param amount the amount
     * @return the formatted amount
     */
    public static String format(double amount) {
        return MoneyPlugin.instance.formatMoney(amount);
    }

    /**
     * Log a transaction that never happened.  This is done
     * automatically by give() and take().  Other than those two, this
     * function accepts negative input.
     * @param uuid the player uuid
     * @param amount the money amount
     * @param plugin the issuing plugin
     * @param comment the comment
     */
    public static void log(UUID uuid, double amount, Plugin plugin, String comment) {
        if (Double.isNaN(amount)) {
            throw new IllegalArgumentException("Amount cannot be NaN");
        }
        if (Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Amount cannot be infinite");
        }
        MoneyPlugin.instance.log(uuid, amount, plugin, comment);
    }
}
