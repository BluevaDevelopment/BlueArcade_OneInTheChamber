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

import java.util.UUID;

public class ArenaState {

    private final int arenaId;
    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private boolean ended;
    private UUID winner;
    private int supplyTicks;

    public ArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.arenaId = context.getArenaId();
        this.context = context;
    }

    public int getArenaId() {
        return arenaId;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
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
