package net.blueva.arcade.modules.oneinthechamber.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.oneinthechamber.state.ArenaState;
import net.blueva.arcade.modules.oneinthechamber.state.ArenaStateRegistry;
import net.blueva.arcade.modules.oneinthechamber.state.PlayerArenaRegistry;
import net.blueva.arcade.modules.oneinthechamber.state.PlayerKillTracker;
import net.blueva.arcade.modules.oneinthechamber.support.LoadoutService;
import net.blueva.arcade.modules.oneinthechamber.support.MessagingService;
import net.blueva.arcade.modules.oneinthechamber.support.OutcomeService;
import net.blueva.arcade.modules.oneinthechamber.support.StatsService;
import net.blueva.arcade.modules.oneinthechamber.support.SupplyService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class OneInTheChamberGameManager {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final StatsService statsService;
    private final ArenaStateRegistry arenaRegistry = new ArenaStateRegistry();
    private final PlayerArenaRegistry playerArenaRegistry = new PlayerArenaRegistry();
    private final PlayerKillTracker killTracker = new PlayerKillTracker();
    private final LoadoutService loadoutService;
    private final MessagingService messagingService;
    private final OutcomeService outcomeService;
    private final SupplyService supplyService;

    public OneInTheChamberGameManager(ModuleConfigAPI moduleConfig,
                                      CoreConfigAPI coreConfig,
                                      ModuleInfo moduleInfo,
                                      StatsService statsService) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.loadoutService = new LoadoutService(moduleConfig);
        this.messagingService = new MessagingService(moduleConfig, coreConfig, moduleInfo);
        this.outcomeService = new OutcomeService(moduleConfig, killTracker);
        this.supplyService = new SupplyService(moduleConfig);
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = arenaRegistry.register(context);
        state.resetSupplyTicks();
        killTracker.initializePlayers(context.getPlayers());
        playerArenaRegistry.registerPlayers(context.getPlayers(), arenaId);

        messagingService.sendDescription(context, outcomeService.getWinMode(context));
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinish(context);
    }

    public boolean freezePlayersOnCountdown() {
        return false;
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        startGameTimer(context);

        for (Player player : context.getPlayers()) {
            loadoutService.prepareForStart(player);
            context.getScoreboardAPI().showScoreboard(player, outcomeService.getScoreboardPath(context));
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        ArenaState state = arenaRegistry.get(arenaId);
        if (state == null) {
            return;
        }

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 180;
        }

        final int[] timeLeft = {gameTime};
        String taskId = "arena_" + arenaId + "_one_in_the_chamber_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            ArenaState currentState = arenaRegistry.get(arenaId);
            if (currentState == null || currentState.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context);
                return;
            }

            supplyService.tickSupplies(currentState);
            for (Player player : allPlayers) {
                String actionBarTemplate = coreConfig.getLanguage(player, "action_bar.in_game.global");
                if (!player.isOnline()) continue;

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", formatCountdownTime(timeLeft[0]));
                customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", formatCountdownTime(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                context.getScoreboardAPI().update(player, outcomeService.getScoreboardPath(context), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        ArenaState state = arenaRegistry.get(arenaId);
        if (state == null) {
            return;
        }

        if (!state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        List<Player> alivePlayers = new ArrayList<>(context.getAlivePlayers());
        String winMode = outcomeService.getWinMode(context);
        if (alivePlayers.size() == 1 && "last_standing".equals(winMode)) {
            Player winner = alivePlayers.get(0);
            context.setWinner(winner);
            awardWin(winner);
        } else if (alivePlayers.size() > 1 && "last_standing".equals(winMode)) {
            handleLastStandingTimeout(context, alivePlayers);
        }

        if ("most_kills".equals(winMode)) {
            handleMostKillsOutcome(context);
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          GameResult<Player> result) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        arenaRegistry.remove(arenaId);

        statsService.recordGamesPlayed(context.getPlayers());
        killTracker.removePlayers(context.getPlayers());
        playerArenaRegistry.removeArena(arenaId);
    }

    public void onDisable() {
        ArenaState anyState = arenaRegistry.getAny();
        if (anyState != null) {
            anyState.getContext().getSchedulerAPI().cancelModuleTasks("one_in_the_chamber");
        }

        arenaRegistry.clear();
        playerArenaRegistry.clear();
        killTracker.clear();
    }

    public void handleWin(Player player) {
        awardWin(player);
    }

    public void handleProjectileShot(Player shooter) {
        statsService.recordShot(shooter);
    }

    public void handleHit(Player attacker) {
        statsService.recordHit(attacker);
    }

    public void handleKillCredit(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Player killer) {
        if (context == null || killer == null) {
            return;
        }

        statsService.recordKill(killer);
        killTracker.incrementKill(killer);
        loadoutService.rewardKillArrow(killer);
    }

    public void handlePlayerElimination(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        Player target,
                                        Player killer) {
        if (context == null || target == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(target)) {
            return;
        }

        Location deathLocation = target.getLocation();
        playVisualEffects(target, killer, deathLocation);

        messagingService.broadcastDeathMessage(context, target, killer);

        String winMode = outcomeService.getWinMode(context);
        if ("most_kills".equals(winMode)) {
            target.setGameMode(GameMode.SPECTATOR);
            target.getInventory().clear();
            if (killer != null) {
                context.getTitlesAPI().sendRaw(target,
                        moduleConfig.getTranslation(target, "titles.you_died.title"),
                        moduleConfig.getTranslation(target, "titles.you_died.subtitle"),
                        0, 80, 20);
            }

            int respawnDelayTicks = Math.max(0, moduleConfig.getInt("respawn.most_kills_delay_ticks", 60));
            int arenaId = context.getArenaId();
            context.getSchedulerAPI().runLater(
                    "one_in_the_chamber_respawn_" + arenaId + "_" + target.getUniqueId(),
                    () -> {
                        ArenaState state = arenaRegistry.get(arenaId);
                        if (state == null || state.isEnded() || !context.isPlayerPlaying(target)) {
                            return;
                        }
                        context.respawnPlayer(target);
                        target.setGameMode(GameMode.SURVIVAL);
                        loadoutService.applyRespawnLoadout(target);
                        context.getSoundsAPI().play(target, coreConfig.getSound("sounds.in_game.respawn"));
                    },
                    respawnDelayTicks
            );
            return;
        }

        context.eliminatePlayer(target, moduleConfig.getTranslation(target, "messages.eliminated"));
        target.getInventory().clear();
        context.setPlayerSpectating(target, true);
        messagingService.sendDeathTitle(context, target, killer != null);
    }

    private void playVisualEffects(Player target, Player killer, Location deathLocation) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null) {
            return;
        }
        if (deathLocation != null) {
            visualEffectsAPI.playDeathEffect(target, deathLocation);
        } else {
            visualEffectsAPI.playDeathEffect(target);
        }
        if (killer != null) {
            visualEffectsAPI.playKillEffect(killer);
        }
    }

    public void handleRespawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Player player) {
        context.respawnPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        loadoutService.applyRespawnLoadout(player);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenaRegistry.getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        return arenaRegistry.getContext(arenaId);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            placeholders.put("kills", String.valueOf(killTracker.getKills(player)));

            String winMode = outcomeService.getWinMode(context);
            placeholders.put("mode", outcomeService.getModeLabel(player, winMode));
            if ("most_kills".equals(winMode)) {
                List<Player> topPlayers = outcomeService.getTopPlayersByKills(context, context.getPlayers(), 5);
                for (int i = 0; i < 5; i++) {
                    String placeKey = "place_" + (i + 1);
                    String killsKey = "kills_" + (i + 1);
                    if (topPlayers.size() > i) {
                        Player topPlayer = topPlayers.get(i);
                        placeholders.put(placeKey, topPlayer.getName());
                        placeholders.put(killsKey, String.valueOf(killTracker.getKills(topPlayer)));
                    } else {
                        placeholders.put(placeKey, "-");
                        placeholders.put(killsKey, "0");
                    }
                }
            }
        }

        return placeholders;
    }

    public String resolveScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        return outcomeService.getScoreboardPath(context);
    }

    public String resolveWinMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        return outcomeService.getWinMode(context);
    }

    private void handleMostKillsOutcome(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> topPlayers = outcomeService.getTopPlayersByKills(context, context.getPlayers(), 5);
        if (topPlayers.isEmpty()) {
            return;
        }

        Player winner = topPlayers.get(0);
        context.setWinner(winner);
        awardWin(winner);

        for (int i = 1; i < topPlayers.size(); i++) {
            Player player = topPlayers.get(i);
            if (!context.isPlayerPlaying(player)) {
                continue;
            }
            context.finishPlayer(player);
        }
    }

    private void handleLastStandingTimeout(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                           List<Player> alivePlayers) {
        List<Player> sortedByKills = outcomeService.getTopPlayersByKills(context, alivePlayers, alivePlayers.size());
        if (sortedByKills.isEmpty()) {
            return;
        }

        Player winner = sortedByKills.get(0);
        context.setWinner(winner);
        awardWin(winner);

        for (int i = 1; i < sortedByKills.size(); i++) {
            Player player = sortedByKills.get(i);
            if (!context.isPlayerPlaying(player)) {
                continue;
            }
            context.finishPlayer(player);
        }
    }

    private void awardWin(Player player) {
        Integer arenaId = playerArenaRegistry.getArenaId(player);
        if (arenaId == null) {
            return;
        }

        ArenaState state = arenaRegistry.get(arenaId);
        if (state == null) {
            return;
        }

        if (state.setWinnerIfAbsent(player.getUniqueId())) {
            statsService.recordWin(player);
        }
    }

    public Material resolveDeathBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return Material.valueOf(deathBlockName.toUpperCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return Material.BARRIER;
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
