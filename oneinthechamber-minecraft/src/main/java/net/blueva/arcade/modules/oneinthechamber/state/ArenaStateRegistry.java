package net.blueva.arcade.modules.oneinthechamber.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaStateRegistry {

    private final Map<Integer, ArenaState> arenas = new ConcurrentHashMap<>();

    public ArenaState register(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = new ArenaState(context);
        arenas.put(context.getArenaId(), state);
        return state;
    }

    public ArenaState get(int arenaId) {
        return arenas.get(arenaId);
    }

    public ArenaState remove(int arenaId) {
        return arenas.remove(arenaId);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(int arenaId) {
        ArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public ArenaState getAny() {
        return arenas.values().stream().findFirst().orElse(null);
    }

    public Collection<ArenaState> values() {
        return arenas.values();
    }

    public void clear() {
        arenas.clear();
    }
}
