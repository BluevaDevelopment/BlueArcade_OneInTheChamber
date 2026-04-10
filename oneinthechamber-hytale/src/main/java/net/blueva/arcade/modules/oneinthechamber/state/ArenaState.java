package net.blueva.arcade.modules.oneinthechamber.state;

import net.blueva.arcade.api.game.GameContext;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.UUID;

public class ArenaState {

    private final int arenaId;
    private final GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context;
    private boolean ended;
    private UUID winner;
    private int supplyTicks;

    public ArenaState(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        this.arenaId = context.getArenaId();
        this.context = context;
    }

    public int getArenaId() {
        return arenaId;
    }

    public GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> getContext() {
        return context;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean markEnded() {
        boolean previouslyEnded = this.ended;
        this.ended = true;
        return !previouslyEnded;
    }

    public UUID getWinner() {
        return winner;
    }

    public boolean setWinnerIfAbsent(UUID winner) {
        if (this.winner != null) {
            return false;
        }
        this.winner = winner;
        return true;
    }

    public int incrementSupplyTicks(int amount) {
        supplyTicks += amount;
        return supplyTicks;
    }

    public void resetSupplyTicks() {
        supplyTicks = 0;
    }
}
