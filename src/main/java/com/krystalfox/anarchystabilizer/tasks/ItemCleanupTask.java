package com.krystalfox.anarchystabilizer.tasks;

import com.krystalfox.anarchystabilizer.AnarchyStabilizer;
import com.krystalfox.anarchystabilizer.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ItemCleanupTask extends BukkitRunnable {

    @Override
    public void run() {
        if (!ConfigManager.isItemCleanupEnabled()) {
            this.cancel();
            return;
        }

        long intervalSeconds = ConfigManager.getItemCleanupInterval();
        List<Integer> warningTimes = ConfigManager.getItemCleanupWarningTimes();

        // 1. Programar Avisos
        if (!warningTimes.isEmpty()) {
            for (int warningTime : warningTimes) {
                if (warningTime > 0 && warningTime < intervalSeconds) {
                    long warningDelayTicks = (intervalSeconds - warningTime) * 20L;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (ConfigManager.isItemCleanupEnabled()) { // Re-chequear por si se desactivó
                                String warningMessage = ConfigManager.formatMessage(
                                        ConfigManager.getItemCleanupWarningMessage(),
                                        warningTime
                                );
                                Bukkit.broadcastMessage(warningMessage);
                            }
                        }
                    }.runTaskLater(AnarchyStabilizer.getInstance(), warningDelayTicks);
                }
            }
        }

        // 2. Ejecutar la limpieza AHORA
        int removedCount = 0;
        List<Item> itemsToRemove = new ArrayList<>();
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    // Añadir condiciones aquí si se quiere (ej. no limpiar items con nombre)
                    // if (item.customName() == null)
                    itemsToRemove.add(item);
                }
            }

            if (!itemsToRemove.isEmpty()) {
                removedCount = itemsToRemove.size();
                // Asegurar ejecución en thread principal
                if (!Bukkit.isPrimaryThread()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() { removeItems(itemsToRemove); }
                    }.runTask(AnarchyStabilizer.getInstance());
                } else {
                    removeItems(itemsToRemove);
                }
            }
        } catch (Exception e) {
            AnarchyStabilizer.getInstance().getLogger().log(Level.SEVERE, "Error during Item Cleanup task execution", e);
        }


        // 3. Enviar mensaje de limpieza
        if (removedCount > 0) {
            String cleanupMessage = ConfigManager.formatMessage(ConfigManager.getItemCleanupCleanupMessage(), removedCount);
            Bukkit.broadcastMessage(cleanupMessage);
            AnarchyStabilizer.getInstance().getLogger().info("Cleared " + removedCount + " ground items.");
        }
    }

    private void removeItems(List<Item> items) {
        for (Item item : items) {
            if (item != null && !item.isDead()) {
                item.remove();
            }
        }
    }
}