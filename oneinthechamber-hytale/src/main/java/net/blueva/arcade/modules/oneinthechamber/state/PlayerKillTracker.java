package net.blueva.arcade.modules.oneinthechamber.state;

import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerKillTracker {

    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();

    public void initializePlayers(Collection<Player> players) {
        for (Player player : players) {
            kills.put(player.getUuid(), 0);
        }
    }

    public void incrementKill(Player player) {
        if (player == null) {
            return;
        }
        kills.merge(player.getUuid(), 1, Integer::sum);
    }

    public int getKills(Player player) {
        if (player == null) {
            return 0;
        }
        return kills.getOrDefault(player.getUuid(), 0);
    }

    public void removePlayers(Collection<Player> players) {
        for (Player player : players) {
            kills.remove(player.getUuid());
        }
    }

    public void clear() {
        kills.clear();
    }
}
