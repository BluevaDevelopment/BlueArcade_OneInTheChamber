package net.blueva.arcade.modules.oneinthechamber.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.oneinthechamber.game.OneInTheChamberGameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class OneInTheChamberListener implements Listener {

    private final OneInTheChamberGameManager gameManager;

    public OneInTheChamberListener(OneInTheChamberGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() == GamePhase.COUNTDOWN && gameManager.freezePlayersOnCountdown()) {
            player.teleport(event.getFrom());
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            if (!context.isInsideBounds(event.getTo())) {
                gameManager.handleRespawn(context, player);
            }
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            gameManager.handleRespawn(context, player);
            return;
        }

        Material deathBlock = gameManager.resolveDeathBlock(context);
        Material blockBelowType = event.getTo().clone().subtract(0, 1, 0).getBlock().getType();
        if (blockBelowType == deathBlock) {
            gameManager.handleRespawn(context, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(shooter);
        if (context == null || context.getPhase() != GamePhase.PLAYING || !context.isPlayerPlaying(shooter)) {
            return;
        }

        if (projectile instanceof AbstractArrow) {
            gameManager.handleProjectileShot(shooter);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !context.isPlayerPlaying(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (attacker.equals(target)) {
            event.setCancelled(true);
            gameManager.handlePlayerElimination(context, target, null);
            return;
        }

        gameManager.handleHit(attacker);
        gameManager.recordHit(context, target, attacker);

        boolean projectileHit = event.getDamager() instanceof AbstractArrow;
        double finalHealth = projectileHit ? -1 : target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        gameManager.handleKillCredit(context, attacker);
        gameManager.handlePlayerElimination(context, target, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        double finalHealth = target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        gameManager.handleNonCombatDeath(context, target);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }
}
