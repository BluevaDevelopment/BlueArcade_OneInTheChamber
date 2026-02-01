package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.Collection;

public class StatsService {

    private final StatsAPI<Player> statsAPI;
    private final ModuleInfo moduleInfo;

    public StatsService(StatsAPI<Player> statsAPI, ModuleInfo moduleInfo) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", "Wins", "One in the Chamber wins", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", "Games Played", "One in the Chamber games played", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("kills", "Kills", "Opponents defeated in One in the Chamber", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("arrows_shot", "Arrows shot", "Arrows fired in One in the Chamber", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("hits_landed", "Hits landed", "Successful hits in One in the Chamber", StatScope.MODULE));
    }

    public void recordWin(Player player) {
        if (statsAPI == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
    }

    public void recordGamesPlayed(Collection<Player> players) {
        if (statsAPI == null) {
            return;
        }
        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }

    public void recordKill(Player player) {
        if (statsAPI == null || player == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "kills", 1);
    }

    public void recordShot(Player player) {
        if (statsAPI == null || player == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "arrows_shot", 1);
    }

    public void recordHit(Player player) {
        if (statsAPI == null || player == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "hits_landed", 1);
    }
}
