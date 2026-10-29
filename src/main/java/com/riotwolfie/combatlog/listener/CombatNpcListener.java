package com.riotwolfie.combatlog.listener;

import com.riotwolfie.combatlog.npc.CombatNpcManager;
import org.bukkit.event.Listener;

public class CombatNpcListener implements Listener {

    private final CombatNpcManager combatNpcManager;

    public CombatNpcListener(CombatNpcManager combatNpcManager) {
        this.combatNpcManager = combatNpcManager;
    }
}
