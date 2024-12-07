package com.cavetale.money;

import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class MenuListener implements Listener {
    public static final String MENU_KEY = "money:money";

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, MoneyPlugin.getInstance());
    }

    @EventHandler
    private void onMenuItem(MenuItemEvent event) {
        event.addItem(builder -> builder
                      .key(MONEY_KEY)
                      .command("money")
                      .icon(Mytems.GOLDEN_COIN.createIcon(List.of(text("Money", GREEN)))));
    }
}
