package net.blueva.arcade.modules.oneinthechamber.state;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerArenaRegistry {

    private final Map<UUID, Integer> arenaByPlayer = new ConcurrentHashMap<>();

    public void registerPlayers(Collection<Player> players, int arenaId) {
        for (Player player : players) {
            arenaByPlayer.put(player.getUniqueId(), arenaId);
        }
    }

    public Integer getArenaId(Player player) {
        if (player == null) {
            return null;
        }
        return arenaByPlayer.get(player.getUniqueId());
    }

    public void removeArena(int arenaId) {
        arenaByPlayer.entrySet().removeIf(entry -> entry.getValue() == arenaId);
    }

    public void removePlayer(Player player) {
        if (player != null) {
            arenaByPlayer.remove(player.getUniqueId());
        }
    }

    public void clear() {
        arenaByPlayer.clear();
    }
}
