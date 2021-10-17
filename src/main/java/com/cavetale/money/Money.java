package com.cavetale.money;

import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class Money {
    private Money() { }

    public static boolean has(UUID uuid, double amount) {
        return get(uuid) >= amount;
    }

    public static double get(UUID uuid) {
        return MoneyPlugin.instance.getInstance().getMoney(uuid);
    }

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

    public static String format(double amount) {
        return MoneyPlugin.instance.formatMoney(amount);
    }
}
