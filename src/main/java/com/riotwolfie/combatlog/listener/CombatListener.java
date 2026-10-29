package com.riotwolfie.combatlog.listener;

import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.team.BetterTeamsHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;

public class CombatListener implements Listener {

    private final CombatLogPlugin plugin;
    private final CombatManager combatManager;

    public CombatListener(CombatLogPlugin plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        if (plugin.isBetterTeamsAvailable()) {
            if (BetterTeamsHook.isSameTeam(attacker, victim)) {
                return;
            }
        } else {
            if (plugin.getTrustManager().isTrusted(attacker.getUniqueId(), victim.getUniqueId())) {
                return;
            }
        }

        combatManager.tagPlayer(victim, attacker);
        combatManager.tagPlayer(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        combatManager.endCombatForVictimAndOpponents(victim.getUniqueId());
    }

    @Nullable
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }
}
