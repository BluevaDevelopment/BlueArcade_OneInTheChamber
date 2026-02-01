package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.oneinthechamber.state.PlayerKillTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class OutcomeService {

    private final ModuleConfigAPI moduleConfig;
    private final PlayerKillTracker killTracker;

    public OutcomeService(ModuleConfigAPI moduleConfig, PlayerKillTracker killTracker) {
        this.moduleConfig = moduleConfig;
        this.killTracker = killTracker;
    }

    public String getWinMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        String mode = context.getDataAccess().getGameData("basic.win_mode", String.class);
        if (mode == null) {
            return "last_standing";
        }
        mode = mode.toLowerCase(Locale.ROOT);
        if (!mode.equals("last_standing") && !mode.equals("most_kills")) {
            return "last_standing";
        }
        return mode;
    }

    public String getModeLabel(String mode) {
        if ("most_kills".equals(mode)) {
            return moduleConfig.getStringFrom("language.yml", "scoreboard.mode_labels.most_kills");
        }
        return moduleConfig.getStringFrom("language.yml", "scoreboard.mode_labels.last_standing");
    }

    public String getScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        return "scoreboard." + getWinMode(context);
    }

    public List<Player> getTopPlayersByKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                             Collection<Player> players,
                                             int limit) {
        Map<Player, Integer> killCounts = new HashMap<>();
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            killCounts.put(player, killTracker.getKills(player));
        }

        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(killCounts.entrySet());
        sorted.sort((a, b) -> {
            int compare = Integer.compare(b.getValue(), a.getValue());
            if (compare != 0) {
                return compare;
            }
            return a.getKey().getName().compareToIgnoreCase(b.getKey().getName());
        });

        List<Player> orderedPlayers = new ArrayList<>();
        for (Map.Entry<Player, Integer> entry : sorted) {
            orderedPlayers.add(entry.getKey());
            if (orderedPlayers.size() >= limit) {
                break;
            }
        }

        return orderedPlayers;
    }
}
