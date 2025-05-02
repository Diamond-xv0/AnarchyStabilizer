package com.krystalfox.anarchystabilizer.listeners;

import com.krystalfox.anarchystabilizer.managers.ConfigManager;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.vehicle.VehicleCreateEvent; // Evento para cuando se crea un vehículo (incluye colocación por jugador)

import java.util.Set;

/**
 * Clase que contiene todos los listeners de eventos del plugin AnarchyStabilizer.
 * Escucha eventos relacionados con Redstone, colocación de Armor Stands y creación de Vehículos.
 */
public class PluginListeners implements Listener {

    // --- Lógica del Listener de Redstone ---

    /**
     * Escucha eventos de extensión de pistones.
     * @param event El evento BlockPistonExtendEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Delega el manejo a un método común para mecanismos
        handleMechanismEvent(event.getBlock(), event);
    }

    /**
     * Escucha eventos de retracción de pistones.
     * @param event El evento BlockPistonRetractEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Delega el manejo a un método común para mecanismos
        handleMechanismEvent(event.getBlock(), event);
    }

    /**
     * Escucha eventos de dispensadores/droppers al dispensar un ítem.
     * @param event El evento BlockDispenseEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        // Delega el manejo a un método común para mecanismos
        handleMechanismEvent(event.getBlock(), event);
    }

    /**
     * Escucha cambios en la corriente de redstone de un bloque.
     * Principalmente para manejar el modo 'disable_all' o 'disable_list' en bloques
     * que no se activan mediante eventos específicos como pistones (ej. repetidores, comparadores).
     * @param event El evento BlockRedstoneEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        // Si el control de redstone no está habilitado en la config, no hacer nada.
        if (!ConfigManager.isRedstoneControlEnabled()) return;

        String mode = ConfigManager.getRedstoneMode();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Modo: Deshabilitar todo
        if ("disable_all".equals(mode)) {
            // Si la nueva corriente es mayor (activación) y es un bloque relacionado con redstone
            if (event.getNewCurrent() > event.getOldCurrent() && isRedstoneRelated(blockType)) {
                // Prevenir la activación estableciendo la nueva corriente a 0
                event.setNewCurrent(0);
            }
            return; // Terminar aquí para el modo disable_all
        }

        // Modo: Deshabilitar lista específica
        if ("disable_list".equals(mode)) {
            Set<Material> disabled = ConfigManager.getDisabledMechanisms();
            // Si el bloque está en la lista de deshabilitados y se está activando
            if (disabled.contains(blockType) && event.getNewCurrent() > event.getOldCurrent()) {
                // Prevenir la activación
                event.setNewCurrent(0);
            }
        }
    }

    /**
     * Método auxiliar para manejar eventos de mecanismos que pueden ser cancelados
     * (PistonExtend, PistonRetract, Dispense).
     * Aplica las reglas de 'disable_all' o 'disable_list'.
     * @param block El bloque que origina el evento.
     * @param event El evento de bloque (debe ser Cancellable).
     */
    private void handleMechanismEvent(Block block, BlockEvent event) {
        // Salir si el control de redstone está desactivado o si el evento no se puede cancelar
        if (!ConfigManager.isRedstoneControlEnabled() || !(event instanceof Cancellable)) return;

        String mode = ConfigManager.getRedstoneMode();
        Material blockType = block.getType();
        Cancellable cancellableEvent = (Cancellable) event;

        // Modo: Deshabilitar todo
        if ("disable_all".equals(mode)) {
            cancellableEvent.setCancelled(true); // Cancela el evento (ej. el pistón no se extiende)
            return;
        }

        // Modo: Deshabilitar lista específica
        if ("disable_list".equals(mode)) {
            Set<Material> disabled = ConfigManager.getDisabledMechanisms();
            // Si el tipo de bloque está en la lista de deshabilitados
            if (disabled.contains(blockType)) {
                cancellableEvent.setCancelled(true); // Cancela el evento
            }
        }
    }

    /**
     * Helper para el modo 'disable_all' en BlockRedstoneEvent.
     * Determina si un material es considerado parte del sistema de redstone básico.
     * @param material El material del bloque.
     * @return true si es un componente de redstone común, false en caso contrario.
     */
    private boolean isRedstoneRelated(Material material) {
        // Usamos un switch expression (Java 14+) para más claridad
        return switch (material) {
            // Lista de componentes comunes de redstone
            case PISTON, STICKY_PISTON, OBSERVER, DISPENSER, DROPPER,
                 COMPARATOR, REPEATER, REDSTONE_WIRE, LEVER, REDSTONE_LAMP,
                 STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON, JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON, BAMBOO_BUTTON, // Botones de madera
                 CRIMSON_BUTTON, WARPED_BUTTON, POLISHED_BLACKSTONE_BUTTON, // Otros botones
                 REDSTONE_TORCH, REDSTONE_WALL_TORCH, TRIPWIRE_HOOK,
                 DAYLIGHT_DETECTOR, TARGET, NOTE_BLOCK, LECTERN, // Otros bloques que interactúan con redstone
                 POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL // Raíles
                    -> true; // Si coincide con alguno de los de arriba, devuelve true
            // Si no coincide con los de arriba, verificamos si está explícitamente en la lista de deshabilitados (por si acaso)
            default -> ConfigManager.getDisabledMechanisms().contains(material);
        };
    }

    // --- Lógica del Listener de Armor Stands ---

    /**
     * Escucha cuando una entidad es colocada en el mundo por un jugador.
     * Se usa para controlar la colocación de Armor Stands.
     * @param event El evento EntityPlaceEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandPlace(EntityPlaceEvent event) {
        // Salir si el control de Armor Stands está desactivado
        if (!ConfigManager.isArmorStandControlEnabled()) return;
        // Salir si la entidad colocada no es un Armor Stand
        if (event.getEntityType() != EntityType.ARMOR_STAND) return;

        String mode = ConfigManager.getArmorStandMode();
        // Si el modo es prevenir o remover al colocar
        if ("prevent_placement".equals(mode) || "remove_on_placement".equals(mode)) {
            event.setCancelled(true); // Cancela la colocación del Armor Stand
            // Opcional: Enviar un mensaje al jugador que intentó colocarlo
            // if (event.getPlayer() != null) {
            //     event.getPlayer().sendMessage(ConfigManager.colorize("&cLa colocación de Soportes para Armadura está desactivada."));
            // }
        }
        // El modo 'remove_all_periodically' es manejado por la tarea ArmorStandRemovalTask, no aquí.
    }

    // --- Lógica del Listener de Vehículos (para prevent_placement) ---

    /**
     * Escucha cuando se crea un vehículo en el mundo.
     * Esto incluye cuando un jugador coloca un Minecart o un Bote.
     * Se usa para implementar el modo 'prevent_placement' de entity_limit_control.
     * @param event El evento VehicleCreateEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehiclePlace(VehicleCreateEvent event) {
        // Usar el getter renombrado que verifica si la feature y el modo 'prevent_placement' están activos
        if (ConfigManager.isEntityLimitPreventPlacement()) { // RENOMBRADO
            EntityType type = event.getVehicle().getType(); // Obtener el tipo de vehículo creado

            // Lista de tipos de vehículos cuya colocación se previene en este modo.
            // Podría hacerse configurable en el futuro si se desea más flexibilidad.
            if (type == EntityType.MINECART || type == EntityType.MINECART_CHEST ||
                    type == EntityType.MINECART_FURNACE || type == EntityType.MINECART_TNT ||
                    type == EntityType.MINECART_HOPPER || type == EntityType.MINECART_COMMAND ||
                    type == EntityType.MINECART_MOB_SPAWNER ||
                    type == EntityType.BOAT || type == EntityType.CHEST_BOAT) {
                event.setCancelled(true); // Cancela la creación/colocación del vehículo
            }
        }
    }
}