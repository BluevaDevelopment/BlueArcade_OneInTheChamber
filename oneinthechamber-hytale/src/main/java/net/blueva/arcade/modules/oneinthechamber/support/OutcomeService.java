package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.oneinthechamber.state.PlayerKillTracker;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OutcomeService {

    private final ModuleConfigAPI moduleConfig;
    private final PlayerKillTracker killTracker;

    public OutcomeService(ModuleConfigAPI moduleConfig, PlayerKillTracker killTracker) {
        this.moduleConfig = moduleConfig;
        this.killTracker = killTracker;
    }

    public String getWinMode(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
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

    public String getScoreboardPath(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        return "scoreboard." + getWinMode(context);
    }

    public List<Player> getTopPlayersByKills(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                             Collection<Player> players,
                                             int limit) {
        Map<Player, Integer> killCounts = new HashMap<>();
        for (Player player : players) {
            if (player == null) {
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
            return a.getKey().getPlayerRef().getUsername().compareToIgnoreCase(b.getKey().getPlayerRef().getUsername());
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
