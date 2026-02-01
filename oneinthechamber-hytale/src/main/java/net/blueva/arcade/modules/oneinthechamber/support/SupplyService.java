package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.modules.oneinthechamber.state.ArenaState;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.List;

public class SupplyService {

    private final ModuleConfigAPI moduleConfig;

    public SupplyService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void tickSupplies(ArenaState state) {
        int interval = moduleConfig.getInt("items.timed_supplies.interval_ticks", 0);
        List<String> supplies = moduleConfig.getStringList("items.timed_supplies.items");

        if (interval <= 0 || supplies == null || supplies.isEmpty()) {
            return;
        }

        int ticks = state.incrementSupplyTicks(20);
        if (ticks % interval != 0) {
            return;
        }

        for (Player player : state.getContext().getPlayers()) {
            if (!state.getContext().isPlayerPlaying(player)) {
                continue;
            }

            for (String itemString : supplies) {
                try {
                    String[] parts = itemString.split(":");
                    if (parts.length >= 2) {
                        String itemId = parts[0];
                        int amount = Integer.parseInt(parts[1]);
                        addItem(player, new ItemStack(itemId, amount));
                    }
                } catch (Exception ignored) {
                    // Ignore malformed entries
                }
            }
        }
    }

    private void addItem(Player player, ItemStack item) {
        if (player == null || item == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        ItemContainer container = inventory.getCombinedEverything();
        if (container == null) {
            return;
        }

        container.addItemStack(item);
    }
}
