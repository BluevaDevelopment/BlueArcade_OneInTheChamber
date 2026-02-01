package net.blueva.arcade.modules.oneinthechamber.state;

import net.blueva.arcade.api.game.GameContext;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaStateRegistry {

    private final Map<Integer, ArenaState> arenas = new ConcurrentHashMap<>();

    public ArenaState register(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context) {
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

    public GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> getContext(int arenaId) {
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
