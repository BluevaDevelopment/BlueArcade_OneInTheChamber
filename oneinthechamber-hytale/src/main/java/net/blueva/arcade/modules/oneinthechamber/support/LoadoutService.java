package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.List;

public class LoadoutService {

    private static final String DEFAULT_ARROW_ID = "Weapon_Arrow_Crude";

    private final ModuleConfigAPI moduleConfig;

    public LoadoutService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void prepareForStart(Player player) {
        clearInventory(player);
        giveStartingItems(player);
        ensureMinimumArrows(player);
        applyStartingEffects(player);
        applyRespawnEffects(player);
    }

    public void applyRespawnLoadout(Player player) {
        clearInventory(player);
        giveStartingItems(player);
        ensureMinimumArrows(player);
        applyStartingEffects(player);
        applyRespawnEffects(player);
    }

    public void rewardKillArrow(Player killer) {
        int reward = Math.max(1, moduleConfig.getInt("items.kill_reward_arrows", 1));
        addItem(killer, new ItemStack(resolveArrowId(), reward));
    }

    public void ensureMinimumArrows(Player player) {
        int minimum = Math.max(0, moduleConfig.getInt("items.minimum_arrows", 1));
        if (minimum == 0) {
            return;
        }

        int current = countItems(player, resolveArrowId());

        if (current < minimum) {
            addItem(player, new ItemStack(resolveArrowId(), minimum - current));
        }
    }

    public void clearInventory(Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        clearContainer(inventory.getArmor());
        clearContainer(inventory.getHotbar());
        clearContainer(inventory.getStorage());
        clearContainer(inventory.getUtility());
        clearContainer(inventory.getTools());
        clearContainer(inventory.getBackpack());
    }

    private void giveStartingItems(Player player) {
        List<String> startingItems = moduleConfig.getStringList("items.starting_items");

        if (startingItems == null || startingItems.isEmpty()) {
            return;
        }

        for (String itemString : startingItems) {
            try {
                String[] parts = itemString.split(":");
                if (parts.length >= 2) {
                    String itemId = parts[0];
                    int amount = Integer.parseInt(parts[1]);
                    int slot = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;

                    addItem(player, new ItemStack(itemId, amount), slot);
                }
            } catch (Exception ignored) {
                // Ignore malformed entries
            }
        }
    }

    private void applyStartingEffects(Player player) {
        applyEffects(player, moduleConfig.getStringList("effects.starting_effects"));
    }

    private void applyRespawnEffects(Player player) {
        applyEffects(player, moduleConfig.getStringList("effects.respawn_effects"));
    }

    private void applyEffects(Player player, List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        // Status effect application is not yet available in the Hytale runtime.
    }

    private void addItem(Player player, ItemStack item) {
        addItem(player, item, -1);
    }

    private void addItem(Player player, ItemStack item, int slot) {
        if (player == null || item == null || player.getInventory() == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (slot >= 0) {
            ItemContainer hotbar = inventory.getHotbar();
            // Slots 0-8 go to hotbar
            if (slot < 9 && hotbar != null) {
                try {
                    hotbar.addItemStackToSlot((short) slot, item);
                } catch (Exception e) {
                    // Fallback: add to any available slot
                    hotbar.addItemStack(item);
                }
                return;
            }

            // Slots 9+ go to storage/inventory
            ItemContainer storage = inventory.getStorage();
            if (storage != null) {
                short storageSlot = (short) Math.max(0, slot - 9);
                try {
                    if (storageSlot < storage.getCapacity()) {
                        storage.addItemStackToSlot(storageSlot, item);
                    } else {
                        storage.addItemStack(item);
                    }
                } catch (Exception e) {
                    // Fallback: add to any available slot
                    storage.addItemStack(item);
                }
            }
            return;
        }

        // No specific slot: add to storage
        ItemContainer storage = inventory.getStorage();
        if (storage != null) {
            storage.addItemStack(item);
        }
    }

    private int countItems(Player player, String itemId) {
        if (player == null || player.getInventory() == null || itemId == null) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        return countItemsInContainer(inventory.getHotbar(), itemId)
                + countItemsInContainer(inventory.getStorage(), itemId)
                + countItemsInContainer(inventory.getUtility(), itemId)
                + countItemsInContainer(inventory.getTools(), itemId)
                + countItemsInContainer(inventory.getBackpack(), itemId);
    }

    private void clearContainer(ItemContainer container) {
        if (container != null) {
            container.clear();
        }
    }

    private int countItemsInContainer(ItemContainer container, String itemId) {
        if (container == null || itemId == null) {
            return 0;
        }

        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (itemId.equalsIgnoreCase(stack.getItemId())) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    private String resolveArrowId() {
        String configValue = moduleConfig.getString("items.arrow_id");
        if (configValue == null || configValue.isBlank()) {
            return DEFAULT_ARROW_ID;
        }
        return configValue;
    }
}
