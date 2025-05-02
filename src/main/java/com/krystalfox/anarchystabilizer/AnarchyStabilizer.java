package com.krystalfox.anarchystabilizer;

// Importar la tarea renombrada
import com.krystalfox.anarchystabilizer.tasks.EntityChunkLimitTask;
import com.krystalfox.anarchystabilizer.listeners.PluginListeners;
import com.krystalfox.anarchystabilizer.managers.ConfigManager;
import com.krystalfox.anarchystabilizer.tasks.ArmorStandRemovalTask;
import com.krystalfox.anarchystabilizer.tasks.ItemCleanupTask;
// Ya no se usa VehicleChunkLimitTask

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull; // Para @NotNull

import java.util.Objects;
import java.util.logging.Level;

/**
 * Clase principal del plugin AnarchyStabilizer.
 * Maneja la activación, desactivación, comandos y programación de tareas.
 */
public final class AnarchyStabilizer extends JavaPlugin implements CommandExecutor {

    private static AnarchyStabilizer instance; // Instancia única del plugin (Singleton)
    // Referencias a las tareas programadas para poder cancelarlas
    private BukkitTask armorStandRemovalTask;
    private BukkitTask itemCleanupTask;
    private BukkitTask entityChunkLimitTask; // RENOMBRADO (era vehicleChunkLimitTask)

    /**
     * Se ejecuta cuando el plugin es habilitado por el servidor.
     */
    @Override
    public void onEnable() {
        instance = this; // Guarda la instancia para acceso estático
        getLogger().info("AnarchyStabilizer enabling...");

        // Carga la configuración desde config.yml y la cachea en ConfigManager
        ConfigManager.loadConfig(this);
        // Registra listeners y programa las tareas basado en la config cargada
        registerFeatures();

        // Registrar el comando base '/as' y su ejecutor
        try {
            // getCommand retorna null si el comando no está en plugin.yml
            Objects.requireNonNull(getCommand("as"), "Command 'as' is not defined in plugin.yml!")
                    .setExecutor(this);
        } catch (NullPointerException e) {
            getLogger().log(Level.SEVERE, "Could not register base command 'as'! Please ensure it is correctly defined in your plugin.yml.", e);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An unexpected error occurred while registering command 'as'.", e);
        }


        getLogger().info("AnarchyStabilizer v" + this.getDescription().getVersion() + " enabled successfully!");
    }

    /**
     * Se ejecuta cuando el plugin es deshabilitado por el servidor.
     */
    @Override
    public void onDisable() {
        getLogger().info("AnarchyStabilizer disabling...");
        // Cancela todas las tareas programadas por este plugin para evitar que sigan ejecutándose
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("AnarchyStabilizer disabled.");
    }

    /**
     * Recarga la configuración del plugin desde el archivo config.yml
     * y reprograma las tareas según la nueva configuración.
     * Usado por el comando /as reload.
     */
    public void reloadPluginConfig() {
        getLogger().info("Reloading AnarchyStabilizer configuration...");

        // 1. Cancelar tareas periódicas existentes antes de recargar la config
        // Usamos las referencias guardadas para cancelarlas explícitamente.
        if (armorStandRemovalTask != null && !armorStandRemovalTask.isCancelled()) {
            armorStandRemovalTask.cancel();
        }
        if (itemCleanupTask != null && !itemCleanupTask.isCancelled()) {
            itemCleanupTask.cancel();
        }
        if (entityChunkLimitTask != null && !entityChunkLimitTask.isCancelled()) { // RENOMBRADO
            entityChunkLimitTask.cancel(); // RENOMBRADO
        }
        // Resetear las referencias a null después de cancelar
        armorStandRemovalTask = null;
        itemCleanupTask = null;
        entityChunkLimitTask = null; // RENOMBRADO

        // 2. Recargar la configuración desde el archivo
        ConfigManager.loadConfig(this); // Re-lee config.yml y actualiza los valores cacheados

        // 3. Reprogramar las tareas con los nuevos valores de configuración
        scheduleTasks(); // Llama al método que se encarga de programar

        getLogger().info("AnarchyStabilizer configuration reloaded!");
    }

    /**
     * Registra los listeners necesarios y programa las tareas iniciales
     * basado en la configuración cargada.
     * Se llama en onEnable y después de recargar la config.
     */
    private void registerFeatures() {
        // Registrar PluginListeners solo si alguna de las features que controla está activa
        // Usa los getters correspondientes de ConfigManager
        if (ConfigManager.isRedstoneControlEnabled()
                || ConfigManager.isArmorStandControlEnabled() // Para onArmorStandPlace
                || ConfigManager.isEntityLimitPreventPlacement()) { // RENOMBRADO (Para onVehiclePlace)
            getServer().getPluginManager().registerEvents(new PluginListeners(), this);
            getLogger().info("Registered PluginListeners.");
        } else {
            getLogger().info("No listeners needed based on current config (Redstone, Armor Stand Placement, Vehicle Placement checks).");
        }
        // Programar o reprogramar las tareas periódicas
        scheduleTasks();
    }

    /**
     * Programa (o reprograma) las tareas periódicas del plugin
     * (ArmorStandRemoval, ItemCleanup, EntityChunkLimit)
     * basado en la configuración actual cargada en ConfigManager.
     * Cancela tareas existentes si es necesario antes de crear las nuevas.
     */
    private void scheduleTasks() {
        // Doble chequeo y cancelación por seguridad (aunque reloadPluginConfig ya lo hace)
        if (armorStandRemovalTask != null && !armorStandRemovalTask.isCancelled()) armorStandRemovalTask.cancel();
        if (itemCleanupTask != null && !itemCleanupTask.isCancelled()) itemCleanupTask.cancel();
        if (entityChunkLimitTask != null && !entityChunkLimitTask.isCancelled()) entityChunkLimitTask.cancel(); // RENOMBRADO
        armorStandRemovalTask = null;
        itemCleanupTask = null;
        entityChunkLimitTask = null; // RENOMBRADO

        long initialDelayTicks = 100L; // Pequeño retraso inicial (5 segundos) para algunas tareas
        // para dar tiempo a que el servidor se estabilice al inicio.

        // --- Programar Tarea de Eliminación Periódica de Armor Stands ---
        if (ConfigManager.isArmorStandControlEnabled() && "remove_all_periodically".equalsIgnoreCase(ConfigManager.getArmorStandMode())) {
            long intervalTicks = ConfigManager.getArmorStandPeriodicInterval() * 20L; // Convertir segundos a ticks
            if (intervalTicks > 0) {
                // runTaskTimer: Ejecuta la tarea repetidamente con un retraso inicial y un periodo fijo.
                armorStandRemovalTask = new ArmorStandRemovalTask().runTaskTimer(this, initialDelayTicks, intervalTicks);
                getLogger().info("Scheduled periodic Armor Stand removal task (Interval: " + ConfigManager.getArmorStandPeriodicInterval() + "s).");
            } else {
                getLogger().warning("Invalid interval (<= 0) for Armor Stand removal task. Task not scheduled.");
            }
        }

        // --- Programar Tarea de Limpieza de Items ---
        if (ConfigManager.isItemCleanupEnabled()) {
            long intervalTicks = ConfigManager.getItemCleanupInterval() * 20L;
            if (intervalTicks > 0) {
                itemCleanupTask = new ItemCleanupTask().runTaskTimer(this, intervalTicks, intervalTicks); // Inicia inmediatamente, luego repite
                getLogger().info("Scheduled periodic Item Cleanup task (Interval: " + ConfigManager.getItemCleanupInterval() + "s).");
            } else {
                getLogger().warning("Invalid interval (<= 0) for Item Cleanup task. Task not scheduled.");
            }
        }

        // --- Programar Tarea de Límite de Entidades por Chunk ---
        // Usar los getters renombrados para verificar si debe programarse
        if (ConfigManager.isEntityLimitControlEnabled() && "limit_per_chunk".equalsIgnoreCase(ConfigManager.getEntityLimitControlMode())) { // RENOMBRADO
            // Usar getter renombrado para el intervalo
            long intervalTicks = ConfigManager.getEntityCheckInterval() * 20L; // RENOMBRADO
            if (intervalTicks > 0) {
                // Usar la clase renombrada y la variable renombrada
                // Usamos un retraso inicial para esta tarea también
                entityChunkLimitTask = new EntityChunkLimitTask().runTaskTimer(this, initialDelayTicks, intervalTicks); // RENOMBRADO
                // Mensaje de log actualizado
                getLogger().info("Scheduled periodic Entity Chunk Limit task (Interval: " + ConfigManager.getEntityCheckInterval() + "s)."); // RENOMBRADO getter
            } else {
                getLogger().warning("Invalid interval (<= 0) for Entity Chunk Limit task. Task not scheduled."); // Mensaje actualizado
            }
        }
    }

    /**
     * Maneja la ejecución de los comandos definidos en plugin.yml (actualmente solo /as).
     * @param sender La entidad que ejecutó el comando (Jugador o Consola).
     * @param command El comando que se ejecutó.
     * @param label El alias del comando que se usó.
     * @param args Los argumentos proporcionados después del comando.
     * @return true si el comando fue manejado por este plugin, false en caso contrario.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Asegurarse de que el comando es 'as' (definido en plugin.yml)
        if (!command.getName().equalsIgnoreCase("as")) {
            return false; // No es nuestro comando, dejar que otro plugin lo maneje si existe
        }

        // Si no se proporcionan argumentos, mostrar ayuda
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        // Obtener el subcomando (primer argumento) en minúsculas
        String subCommand = args[0].toLowerCase();

        // Procesar los subcomandos conocidos
        switch (subCommand) {
            case "reload":
                // Verificar permiso antes de ejecutar
                if (!sender.hasPermission("anarchystabilizer.command.reload")) {
                    sender.sendMessage(ConfigManager.colorize("&cNo tienes permiso para ejecutar este subcomando. (anarchystabilizer.command.reload)"));
                    return true;
                }
                // Llamar al método de recarga
                reloadPluginConfig();
                sender.sendMessage(ConfigManager.colorize("&aConfiguración de AnarchyStabilizer recargada correctamente."));
                break; // Importante salir del switch

            case "help":
                // Podría tener permiso base: anarchystabilizer.command.base
                // if (!sender.hasPermission("anarchystabilizer.command.base")) { ... }
                sendHelpMessage(sender);
                break;

            // --- Futuros subcomandos irían aquí ---
            // case "status":
            //    if (!sender.hasPermission("anarchystabilizer.command.status")) { ... }
            //    sendStatusMessage(sender); // Otra función para mostrar estado actual
            //    break;

            default:
                // Si el subcomando no es reconocido
                sender.sendMessage(ConfigManager.colorize("&cSubcomando desconocido. Usa /as help para ver los comandos disponibles."));
                break;
        }
        return true; // Indicamos que hemos manejado el comando (incluso si fue desconocido)
    }

    /**
     * Envía un mensaje de ayuda básico al CommandSender.
     * @param sender El jugador o consola que recibirá el mensaje.
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ConfigManager.colorize("&e--- AnarchyStabilizer Ayuda ---"));
        // Usar los permisos para mostrar solo los comandos que el sender puede usar sería una mejora
        sender.sendMessage(ConfigManager.colorize("&a/as reload &7- Recarga la configuración del plugin."));
        sender.sendMessage(ConfigManager.colorize("&a/as help &7- Muestra este mensaje de ayuda."));
        // Añadir líneas para futuros comandos aquí (ej. /as status)
        sender.sendMessage(ConfigManager.colorize("&ePlugin v" + this.getDescription().getVersion() + " por KrystalFox")); // Muestra versión
    }

    /**
     * Obtiene la instancia única del plugin (Singleton pattern).
     * @return La instancia del plugin AnarchyStabilizer.
     */
    public static AnarchyStabilizer getInstance() {
        return instance;
    }
}