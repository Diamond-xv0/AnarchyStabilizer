package com.krystalfox.anarchystabilizer.tasks;

import com.krystalfox.anarchystabilizer.AnarchyStabilizer;
import com.krystalfox.anarchystabilizer.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.*; // Necesitamos Entity, Minecart, Boat, ArmorStand
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Tarea periódica que revisa los chunks cargados y elimina entidades
 * (Minecarts, Boats, ArmorStands) que excedan los límites configurados por chunk.
 */
public class EntityChunkLimitTask extends BukkitRunnable {

    @Override
    public void run() {
        // Verificar si la funcionalidad de límite de entidades por chunk está habilitada
        if (!ConfigManager.isEntityLimitControlEnabled() || !"limit_per_chunk".equalsIgnoreCase(ConfigManager.getEntityLimitControlMode())) {
            // Si no está habilitada o el modo no es 'limit_per_chunk', cancelar esta ejecución (y la tarea si es la primera vez)
            this.cancel();
            return;
        }

        // Obtener los límites configurados desde ConfigManager
        int minecartLimit = ConfigManager.getMinecartLimitPerChunk();
        int boatLimit = ConfigManager.getBoatLimitPerChunk();
        int armorStandLimit = ConfigManager.getArmorStandLimitPerChunk();
        boolean logRemovals = ConfigManager.logEntityRemovals(); // Usar getter renombrado

        // Contadores para llevar la cuenta total de entidades eliminadas en esta ejecución
        int totalMinecartsRemoved = 0;
        int totalBoatsRemoved = 0;
        int totalArmorStandsRemoved = 0;

        // Lista única para almacenar todas las entidades que deben ser eliminadas
        List<Entity> entitiesToRemove = new ArrayList<>();

        try {
            // Iterar sobre todos los mundos actualmente cargados en el servidor
            for (World world : Bukkit.getWorlds()) {
                // Iterar sobre todos los chunks que están cargados en memoria para este mundo
                for (Chunk chunk : world.getLoadedChunks()) {
                    // Contadores y listas temporales para las entidades encontradas en ESTE chunk
                    int currentMinecarts = 0;
                    int currentBoats = 0;
                    int currentArmorStands = 0;
                    List<Minecart> minecartsInChunk = new ArrayList<>();
                    List<Boat> boatsInChunk = new ArrayList<>();
                    List<ArmorStand> armorStandsInChunk = new ArrayList<>();

                    // Revisar cada entidad dentro del chunk actual
                    for (Entity entity : chunk.getEntities()) {
                        // Clasificar la entidad y añadirla a la lista/contador correspondiente
                        if (entity instanceof Minecart minecart) { // Java 16+ pattern matching
                            currentMinecarts++;
                            minecartsInChunk.add(minecart);
                        } else if (entity instanceof Boat boat) { // Java 16+ pattern matching
                            currentBoats++;
                            boatsInChunk.add(boat);
                        } else if (entity instanceof ArmorStand stand) { // Java 16+ pattern matching
                            currentArmorStands++;
                            armorStandsInChunk.add(stand);
                        }
                        // Se podrían añadir más 'else if' aquí para otros tipos de entidades en el futuro
                    }

                    // Comprobar y marcar para eliminar Minecarts excedentes (si el límite > 0)
                    if (minecartLimit > 0 && currentMinecarts > minecartLimit) {
                        int excess = currentMinecarts - minecartLimit;
                        // Añadir los primeros 'excess' minecarts encontrados a la lista general de eliminación
                        for (int i = 0; i < excess && i < minecartsInChunk.size(); i++) {
                            entitiesToRemove.add(minecartsInChunk.get(i));
                        }
                        totalMinecartsRemoved += excess; // Incrementar contador total
                    }

                    // Comprobar y marcar para eliminar Botes excedentes (si el límite > 0)
                    if (boatLimit > 0 && currentBoats > boatLimit) {
                        int excess = currentBoats - boatLimit;
                        for (int i = 0; i < excess && i < boatsInChunk.size(); i++) {
                            entitiesToRemove.add(boatsInChunk.get(i));
                        }
                        totalBoatsRemoved += excess;
                    }

                    // Comprobar y marcar para eliminar Armor Stands excedentes (si el límite > 0)
                    if (armorStandLimit > 0 && currentArmorStands > armorStandLimit) {
                        int excess = currentArmorStands - armorStandLimit;
                        for (int i = 0; i < excess && i < armorStandsInChunk.size(); i++) {
                            entitiesToRemove.add(armorStandsInChunk.get(i));
                        }
                        totalArmorStandsRemoved += excess;
                    }
                    // Fin del procesamiento para este chunk
                } // Fin del bucle de chunks
            } // Fin del bucle de mundos

            // Si hemos marcado alguna entidad para eliminar
            if (!entitiesToRemove.isEmpty()) {
                // Es crucial realizar la eliminación en el hilo principal del servidor
                if (!Bukkit.isPrimaryThread()) {
                    // Si estamos en un hilo asíncrono (lo cual es común para tareas periódicas),
                    // programamos la eliminación para que se ejecute en el siguiente tick del servidor.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            removeMarkedEntities(entitiesToRemove); // Llama al método de eliminación
                        }
                    }.runTask(AnarchyStabilizer.getInstance()); // Programa la tarea usando la instancia del plugin
                } else {
                    // Si ya estamos en el hilo principal (menos probable para runTaskTimer), eliminamos directamente.
                    removeMarkedEntities(entitiesToRemove);
                }

                // Registrar en consola si la opción está activada y se eliminó al menos una entidad
                if (logRemovals && (totalMinecartsRemoved > 0 || totalBoatsRemoved > 0 || totalArmorStandsRemoved > 0)) {
                    AnarchyStabilizer.getInstance().getLogger().info(
                            String.format("Culled %d excess Minecarts, %d excess Boats, and %d excess Armor Stands from loaded chunks.",
                                    totalMinecartsRemoved, totalBoatsRemoved, totalArmorStandsRemoved)
                    );
                }
            } // Fin de la eliminación

        } catch (Exception e) {
            // Capturar cualquier error inesperado durante la ejecución de la tarea
            AnarchyStabilizer.getInstance().getLogger().log(Level.SEVERE, "Error occurred during EntityChunkLimitTask", e);
            this.cancel(); // Cancelar la tarea para prevenir ejecuciones futuras con error
        }
    }

    /**
     * Elimina de forma segura una lista de entidades.
     * Verifica si la entidad todavía es válida antes de intentar eliminarla.
     * @param entities La lista de entidades a eliminar.
     */
    private void removeMarkedEntities(List<Entity> entities) {
        int actuallyRemoved = 0;
        // Iterar sobre la lista de entidades marcadas
        for (Entity entity : entities) {
            // Comprobación adicional: ¿la entidad todavía existe y no está ya muerta/inválida?
            if (entity != null && !entity.isDead() && entity.isValid()) {
                entity.remove(); // Elimina la entidad del mundo
                actuallyRemoved++;
            }
        }
        // Opcional: Registrar cuántas entidades se eliminaron realmente (podría ser menor que el conteo inicial si algunas se volvieron inválidas entre el chequeo y la eliminación)
        // if (actuallyRemoved > 0) {
        //    AnarchyStabilizer.getInstance().getLogger().fine("EntityChunkLimitTask actually removed " + actuallyRemoved + " entities.");
        // }
    }
}