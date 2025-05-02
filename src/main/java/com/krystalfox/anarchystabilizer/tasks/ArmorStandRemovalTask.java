package com.krystalfox.anarchystabilizer.tasks;

import com.krystalfox.anarchystabilizer.AnarchyStabilizer;
import com.krystalfox.anarchystabilizer.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ArmorStandRemovalTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.isArmorStandControlEnabled() || !"remove_all_periodically".equalsIgnoreCase(ConfigManager.getArmorStandMode())) {
            this.cancel();
            return;
        }

        long intervalSeconds = ConfigManager.getArmorStandPeriodicInterval();
        long warningTimeSeconds = ConfigManager.getArmorStandWarningTime();
        boolean warn = ConfigManager.isArmorStandWarnBeforeRemoval();

        // 1. Programar Aviso (si está habilitado)
        if (warn && warningTimeSeconds > 0 && warningTimeSeconds < intervalSeconds) {
            long warningDelayTicks = (intervalSeconds - warningTimeSeconds) * 20L;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (ConfigManager.isArmorStandControlEnabled() && "remove_all_periodically".equalsIgnoreCase(ConfigManager.getArmorStandMode())) {
                        String warningMessage = ConfigManager.formatMessage(
                                ConfigManager.getArmorStandWarningMessage(),
                                warningTimeSeconds
                        );
                        Bukkit.broadcastMessage(warningMessage);
                    }
                }
            }.runTaskLater(AnarchyStabilizer.getInstance(), warningDelayTicks);
        }

        // 2. Ejecutar la eliminación AHORA
        int removedCount = 0;
        List<ArmorStand> standsToRemove = new ArrayList<>();
        try {
            for (World world : Bukkit.getWorlds()) {
                for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                    standsToRemove.add(stand);
                }
            }

            if (!standsToRemove.isEmpty()) {
                removedCount = standsToRemove.size();
                // Asegurarse que la eliminación se haga en el thread principal
                if (!Bukkit.isPrimaryThread()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() { removeStands(standsToRemove); }
                    }.runTask(AnarchyStabilizer.getInstance());
                } else {
                    removeStands(standsToRemove);
                }
            }
        } catch (Exception e) {
            AnarchyStabilizer.getInstance().getLogger().log(Level.SEVERE, "Error during Armor Stand removal task execution", e);
        }

        // 3. Enviar mensaje de eliminación (si se eliminó algo)
        if (removedCount > 0) {
            String removalMessage = ConfigManager.formatMessage(ConfigManager.getArmorStandRemovalMessage(), removedCount);
            Bukkit.broadcastMessage(removalMessage);
            AnarchyStabilizer.getInstance().getLogger().info("Removed " + removedCount + " Armor Stands.");
        }
    }

    private void removeStands(List<ArmorStand> stands) {
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }
}