package com.riotwolfie.combatlog.team;

import com.booksaw.betterTeams.Team;
import org.bukkit.entity.Player;

public final class BetterTeamsHook {

    private BetterTeamsHook() {
    }

    public static boolean isSameTeam(Player a, Player b) {
        if (a == null || b == null) {
            return false;
        }
        Team teamA = Team.getTeam(a);
        if (teamA == null) {
            return false;
        }
        Team teamB = Team.getTeam(b);
        if (teamB == null) {
            return false;
        }
        return teamA.getID().equals(teamB.getID());
    }
}
