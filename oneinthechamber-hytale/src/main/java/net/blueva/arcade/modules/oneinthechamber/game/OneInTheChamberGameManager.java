package net.blueva.arcade.modules.oneinthechamber.game;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
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
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.MovementSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<Integer, Set<UUID>> temporarySpectators = new ConcurrentHashMap<>();

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

    public void handleStart(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = arenaRegistry.register(context);
        temporarySpectators.remove(arenaId);
        state.resetSupplyTicks();
        killTracker.initializePlayers(context.getPlayers());
        playerArenaRegistry.registerPlayers(context.getPlayers(), arenaId);

        messagingService.sendDescription(context, outcomeService.getWinMode(context));
    }

    public void handleCountdownTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                    int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        messagingService.sendCountdownFinish(context);
    }

    public boolean freezePlayersOnCountdown() {
        return false;
    }

    public void handleGameStart(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        startGameTimer(context);
        startMovementTracking(context);

        for (Player player : context.getPlayers()) {
            loadoutService.prepareForStart(player);
            context.getScoreboardAPI().showScoreboard(player, outcomeService.getScoreboardPath(context));
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
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

            String actionBarTemplate = coreConfig.getLanguage("action_bar.in_game.global");
            for (Player player : allPlayers) {
                if (player == null) {
                    continue;
                }

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", String.valueOf(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                context.getScoreboardAPI().update(player, outcomeService.getScoreboardPath(context), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
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
            Player winner = alivePlayers.getFirst();
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

    public void handleEnd(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                          GameResult<Player> result) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        clearTemporarySpectators(context);
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

        for (ArenaState state : arenaRegistry.values()) {
            clearTemporarySpectators(state.getContext());
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

    public void handleKillCredit(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                 Player killer) {
        if (context == null || killer == null) {
            return;
        }

        statsService.recordKill(killer);
        killTracker.incrementKill(killer);
        loadoutService.rewardKillArrow(killer);
    }

    public void handlePlayerElimination(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                        Player target,
                                        Player killer) {
        if (context == null || target == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(target)) {
            return;
        }

        Location deathLocation = resolvePlayerLocation(target);
        playVisualEffects(target, killer, deathLocation);

        messagingService.broadcastDeathMessage(context, target, killer);

        String winMode = outcomeService.getWinMode(context);
        if ("most_kills".equals(winMode)) {
            loadoutService.clearInventory(target);
            applyTemporarySpectatorState(context, target);
            if (killer != null) {
                context.getTitlesAPI().sendRaw(target,
                        moduleConfig.getStringFrom("language.yml", "titles.you_died.title"),
                        moduleConfig.getStringFrom("language.yml", "titles.you_died.subtitle"),
                        0, 80, 20);
            }

            int respawnDelayTicks = Math.max(0, moduleConfig.getInt("respawn.most_kills_delay_ticks", 60));
            int arenaId = context.getArenaId();
            context.getSchedulerAPI().runLater(
                    "one_in_the_chamber_respawn_" + arenaId + "_" + target.getUuid(),
                    () -> {
                        ArenaState state = arenaRegistry.get(arenaId);
                        if (state == null || state.isEnded() || !context.isPlayerPlaying(target)) {
                            return;
                        }
                        context.respawnPlayer(target);
                        clearTemporarySpectatorState(context, target);
                        restorePlayerHealth(target);
                        loadoutService.applyRespawnLoadout(target);
                        context.getSoundsAPI().play(target, coreConfig.getSound("sounds.in_game.respawn"));
                    },
                    respawnDelayTicks
            );
            return;
        }

        context.eliminatePlayer(target, moduleConfig.getStringFrom("language.yml", "messages.eliminated"));
        loadoutService.clearInventory(target);
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

    public void handleRespawn(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                              Player player) {
        context.respawnPlayer(player);
        restorePlayerHealth(player);
        loadoutService.applyRespawnLoadout(player);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenaRegistry.getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        return arenaRegistry.getContext(arenaId);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context = getGameContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            placeholders.put("kills", String.valueOf(killTracker.getKills(player)));

            String winMode = outcomeService.getWinMode(context);
            placeholders.put("mode", outcomeService.getModeLabel(winMode));
            if ("most_kills".equals(winMode)) {
                List<Player> topPlayers = outcomeService.getTopPlayersByKills(context, context.getPlayers(), 5);
                for (int i = 0; i < 5; i++) {
                    String placeKey = "place_" + (i + 1);
                    String killsKey = "kills_" + (i + 1);
                    if (topPlayers.size() > i) {
                        Player topPlayer = topPlayers.get(i);
                        placeholders.put(placeKey, topPlayer.getPlayerRef().getUsername());
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

    public String resolveScoreboardPath(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        return outcomeService.getScoreboardPath(context);
    }

    public String resolveWinMode(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        return outcomeService.getWinMode(context);
    }

    private void handleMostKillsOutcome(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        List<Player> topPlayers = outcomeService.getTopPlayersByKills(context, context.getPlayers(), 5);
        if (topPlayers.isEmpty()) {
            return;
        }

        Player winner = topPlayers.getFirst();
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

    private void handleLastStandingTimeout(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                           List<Player> alivePlayers) {
        List<Player> sortedByKills = outcomeService.getTopPlayersByKills(context, alivePlayers, alivePlayers.size());
        if (sortedByKills.isEmpty()) {
            return;
        }

        Player winner = sortedByKills.getFirst();
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

        if (state.setWinnerIfAbsent(player.getUuid())) {
            statsService.recordWin(player);
        }
    }

    public String resolveDeathBlock(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return deathBlockName.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return "hytale:barrier";
    }

    private void startMovementTracking(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_one_in_the_chamber_movement";
        Location worldLocation = context.getArenaAPI().getRandomSpawn();
        if (worldLocation == null) {
            worldLocation = context.getArenaAPI().getBoundsMin();
        }
        if (worldLocation != null) {
            context.getSchedulerAPI().runTimer(taskId, () -> handleMovementTick(context), 0L, 5L);
        } else {
            context.getSchedulerAPI().runTimer(taskId, () -> handleMovementTick(context), 0L, 5L);
        }
    }

    private void handleMovementTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (player == null) {
                continue;
            }
            handleMovementTickForPlayer(context, player);
        }
    }

    private void handleMovementTickForPlayer(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                             Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            Location current = resolvePlayerLocation(player);
            if (current == null) {
                return;
            }
            if (!context.isInsideBounds(current)) {
                handleRespawn(context, player);
                return;
            }
            if (context.getPhase() == GamePhase.PLAYING && isOnDeathBlock(context, current)) {
                handleRespawn(context, player);
            }
        });
    }

    private boolean isOnDeathBlock(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                   Location location) {
        // Death block checks are disabled in Hytale because block access requires the world thread.
        // Movement polling runs on the scheduler thread, so avoid unsafe block reads here.
        return false;
    }

    private Location resolvePlayerLocation(Player player) {
        if (player == null || player.getWorld() == null || player.getReference() == null) {
            return null;
        }
        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> store = ref.getStore();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        Vector3d position = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        return new Location(player.getWorld().getName(), position.x, position.y, position.z, rotation.pitch(), rotation.yaw(), rotation.roll());
    }

    private void applyTemporarySpectatorState(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                              Player player) {
        if (context == null || player == null) {
            return;
        }
        int arenaId = context.getArenaId();
        temporarySpectators
                .computeIfAbsent(arenaId, id -> ConcurrentHashMap.newKeySet())
                .add(player.getUuid());
        setSpectatorState(context, player, true);
    }

    private void clearTemporarySpectatorState(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                              Player player) {
        if (context == null || player == null) {
            return;
        }
        int arenaId = context.getArenaId();
        Set<UUID> spectators = temporarySpectators.get(arenaId);
        if (spectators != null) {
            spectators.remove(player.getUuid());
            if (spectators.isEmpty()) {
                temporarySpectators.remove(arenaId);
            }
        }
        setSpectatorState(context, player, false);
    }

    private void clearTemporarySpectators(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        if (context == null) {
            return;
        }
        int arenaId = context.getArenaId();
        Set<UUID> spectators = temporarySpectators.remove(arenaId);
        if (spectators == null || spectators.isEmpty()) {
            return;
        }
        for (Player player : context.getPlayers()) {
            if (player == null || !spectators.contains(player.getUuid())) {
                continue;
            }
            setSpectatorState(context, player, false);
        }
    }

    private void setSpectatorState(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                   Player player,
                                   boolean spectator) {
        if (player == null) {
            return;
        }
        updateSpectatorVisibility(context, player, spectator);
        updateSpectatorFlight(player, spectator);
    }

    private void updateSpectatorVisibility(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                           Player spectator,
                                           boolean hidden) {
        if (context == null || spectator == null) {
            return;
        }
        PlayerRef spectatorRef = spectator.getPlayerRef();
        if (spectatorRef == null) {
            return;
        }
        for (Player player : context.getPlayers()) {
            if (player == null || player.equals(spectator)) {
                continue;
            }
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) {
                continue;
            }
            HiddenPlayersManager hiddenPlayersManager = playerRef.getHiddenPlayersManager();
            if (hidden) {
                hiddenPlayersManager.hidePlayer(spectatorRef.getUuid());
            } else {
                hiddenPlayersManager.showPlayer(spectatorRef.getUuid());
            }
        }
    }

    private void updateSpectatorFlight(Player player, boolean canFly) {
        if (player == null || player.getReference() == null) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            if (player.getReference() == null) {
                return;
            }
            Store<EntityStore> store = player.getReference().getStore();
            MovementManager movementManager = store.getComponent(player.getReference(), MovementManager.getComponentType());
            if (movementManager == null) {
                return;
            }
            MovementSettings settings = movementManager.getSettings();
            if (settings == null || settings.canFly == canFly) {
                return;
            }
            settings.canFly = canFly;
            PacketHandler packetHandler = playerRef.getPacketHandler();
            if (packetHandler == null) {
                return;
            }
            movementManager.update(packetHandler);
        });
    }

    private void restorePlayerHealth(Player player) {
        if (player == null || player.getReference() == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        int healthStatId = DefaultEntityStatTypes.getHealth();
        world.execute(() -> {
            if (player.getReference() == null) {
                return;
            }
            Store<EntityStore> store = player.getReference().getStore();
            EntityStatMap statMap = store.getComponent(player.getReference(), EntityStatMap.getComponentType());
            if (statMap == null) {
                return;
            }
            statMap.maximizeStatValue(healthStatId);
        });
    }
}
