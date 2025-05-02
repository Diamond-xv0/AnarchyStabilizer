package com.krystalfox.anarchystabilizer.managers;

import com.krystalfox.anarchystabilizer.AnarchyStabilizer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

public class ConfigManager {

    private static FileConfiguration config;

    // Cache de valores de configuración
    private static boolean featureRedstoneControlEnabled;
    private static boolean featureArmorStandControlEnabled;
    private static boolean featureItemCleanupEnabled;
    private static boolean featureEntityLimitControlEnabled; // RENOMBRADO

    // Redstone
    private static String redstoneMode;
    private static Set<Material> disabledMechanisms;

    // Armor Stand
    private static String armorStandMode;
    private static int armorStandPeriodicInterval;
    private static boolean armorStandWarnBeforeRemoval;
    private static int armorStandWarningTime;
    private static String armorStandWarningMessage;
    private static String armorStandRemovalMessage;

    // Item Cleanup
    private static int itemCleanupInterval;
    private static List<Integer> itemCleanupWarningTimes;
    private static String itemCleanupWarningMessage;
    private static String itemCleanupCleanupMessage;

    // Entity Limit Control (antes Vehicle Control)
    private static String entityLimitControlMode;      // RENOMBRADO
    private static int entityCheckInterval;           // RENOMBRADO (era vehicleCheckInterval)
    private static int minecartLimitPerChunk;
    private static int boatLimitPerChunk;
    private static int armorStandLimitPerChunk;       // Añadido previamente
    private static boolean logEntityRemovals;         // RENOMBRADO (era vehicleLogRemovals)

    /**
     * Carga y cachea la configuración desde el archivo config.yml.
     * Se llama al inicio y al recargar el plugin.
     * @param plugin La instancia principal del plugin.
     */
    public static void loadConfig(AnarchyStabilizer plugin) {
        plugin.saveDefaultConfig(); // Asegura que config.yml exista
        plugin.reloadConfig();      // Recarga la configuración desde el archivo
        config = plugin.getConfig(); // Obtiene el objeto de configuración

        // Cargar interruptores globales de features
        featureRedstoneControlEnabled = config.getBoolean("features.redstone_control", false);
        featureArmorStandControlEnabled = config.getBoolean("features.armor_stand_control", false);
        featureItemCleanupEnabled = config.getBoolean("features.item_cleanup", false);
        featureEntityLimitControlEnabled = config.getBoolean("features.entity_limit_control", false); // RENOMBRADO

        // Cargar configuración de Redstone Control
        if (featureRedstoneControlEnabled) {
            redstoneMode = config.getString("redstone.mode", "off").toLowerCase();
            disabledMechanisms = new HashSet<>();
            if ("disable_list".equals(redstoneMode)) {
                List<String> mechanismNames = config.getStringList("redstone.disabled_mechanisms");
                for (String name : mechanismNames) {
                    try {
                        Material mat = Material.matchMaterial(name.toUpperCase());
                        if (mat != null) {
                            disabledMechanisms.add(mat);
                        } else {
                            plugin.getLogger().warning("Invalid material name in redstone.disabled_mechanisms: " + name);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,"Error parsing material name: " + name, e);
                    }
                }
            }
        } else {
            // Valores por defecto si la feature está desactivada
            redstoneMode = "off";
            disabledMechanisms = Collections.emptySet();
        }

        // Cargar configuración de Armor Stand Control
        if (featureArmorStandControlEnabled) {
            armorStandMode = config.getString("armor_stand.mode", "off").toLowerCase();
            if ("remove_all_periodically".equals(armorStandMode)) {
                armorStandPeriodicInterval = config.getInt("armor_stand.periodic_removal.interval_seconds", 600);
                armorStandWarnBeforeRemoval = config.getBoolean("armor_stand.periodic_removal.warn_before_removal", true);
                armorStandWarningTime = config.getInt("armor_stand.periodic_removal.warning_time_seconds", 30);
                armorStandWarningMessage = colorize(config.getString("armor_stand.periodic_removal.warning_message", "&c¡ATENCIÓN! Todos los Soportes para Armaduras serán eliminados en {time} segundos."));
                armorStandRemovalMessage = colorize(config.getString("armor_stand.periodic_removal.removal_message", "&aSe han eliminado todos los Soportes para Armaduras existentes."));
            } else {
                // Resetear valores si no es el modo correcto
                armorStandPeriodicInterval = 0;
                armorStandWarnBeforeRemoval = false;
                armorStandWarningTime = 0;
                armorStandWarningMessage = "";
                armorStandRemovalMessage = "";
            }
        } else {
            // Valores por defecto si la feature está desactivada
            armorStandMode = "off";
            armorStandPeriodicInterval = 0;
            armorStandWarnBeforeRemoval = false;
            armorStandWarningTime = 0;
            armorStandWarningMessage = "";
            armorStandRemovalMessage = "";
        }

        // Cargar configuración de Item Cleanup
        if (featureItemCleanupEnabled) {
            itemCleanupInterval = config.getInt("item_cleanup.interval_seconds", 300);
            itemCleanupWarningTimes = config.getIntegerList("item_cleanup.warning_times_seconds");
            Collections.sort(itemCleanupWarningTimes, Collections.reverseOrder()); // Importante para la lógica de aviso
            itemCleanupWarningMessage = colorize(config.getString("item_cleanup.warning_message", "&eAviso: Items en el suelo se limpiarán en {time} segundos."));
            itemCleanupCleanupMessage = colorize(config.getString("item_cleanup.cleanup_message", "&aItems del suelo eliminados."));
        } else {
            // Valores por defecto si la feature está desactivada
            itemCleanupInterval = 0;
            itemCleanupWarningTimes = Collections.emptyList();
            itemCleanupWarningMessage = "";
            itemCleanupCleanupMessage = "";
        }

        // Cargar configuración de Entity Limit Control (antes Vehicle Control)
        if (featureEntityLimitControlEnabled) { // RENOMBRADO
            entityLimitControlMode = config.getString("entity_limit_control.mode", "off").toLowerCase(); // RENOMBRADO
            if ("limit_per_chunk".equals(entityLimitControlMode)) { // RENOMBRADO
                entityCheckInterval = config.getInt("entity_limit_control.limit_settings.check_interval_seconds", 20); // RENOMBRADO
                minecartLimitPerChunk = config.getInt("entity_limit_control.limit_settings.minecart_limit_per_chunk", 25); // RENOMBRADO
                boatLimitPerChunk = config.getInt("entity_limit_control.limit_settings.boat_limit_per_chunk", 15); // RENOMBRADO
                armorStandLimitPerChunk = config.getInt("entity_limit_control.limit_settings.armor_stand_limit_per_chunk", 10); // RENOMBRADO
                logEntityRemovals = config.getBoolean("entity_limit_control.limit_settings.log_removals", true); // RENOMBRADO
            } else {
                // Resetear valores si el modo no es limit_per_chunk
                entityCheckInterval = 0;
                minecartLimitPerChunk = 0;
                boatLimitPerChunk = 0;
                armorStandLimitPerChunk = 0;
                logEntityRemovals = false;
            }
            // Nota: El modo 'prevent_placement' sigue existiendo bajo entity_limit_control.mode
        } else {
            // Valores por defecto si la feature está desactivada
            entityLimitControlMode = "off";
            entityCheckInterval = 0;
            minecartLimitPerChunk = 0;
            boatLimitPerChunk = 0;
            armorStandLimitPerChunk = 0;
            logEntityRemovals = false;
        }
    }

    // --- Getters para los valores cacheados ---

    // Features globales
    public static boolean isRedstoneControlEnabled() { return featureRedstoneControlEnabled; }
    public static boolean isArmorStandControlEnabled() { return featureArmorStandControlEnabled; }
    public static boolean isItemCleanupEnabled() { return featureItemCleanupEnabled; }
    public static boolean isEntityLimitControlEnabled() { return featureEntityLimitControlEnabled; } // RENOMBRADO

    // Redstone
    public static String getRedstoneMode() { return redstoneMode; }
    public static Set<Material> getDisabledMechanisms() { return Collections.unmodifiableSet(disabledMechanisms); } // Devolver copia inmutable

    // Armor Stand
    public static String getArmorStandMode() { return armorStandMode; }
    public static int getArmorStandPeriodicInterval() { return armorStandPeriodicInterval; }
    public static boolean isArmorStandWarnBeforeRemoval() { return armorStandWarnBeforeRemoval; }
    public static int getArmorStandWarningTime() { return armorStandWarningTime; }
    public static String getArmorStandWarningMessage() { return armorStandWarningMessage; }
    public static String getArmorStandRemovalMessage() { return armorStandRemovalMessage; }

    // Item Cleanup
    public static int getItemCleanupInterval() { return itemCleanupInterval; }
    public static List<Integer> getItemCleanupWarningTimes() { return Collections.unmodifiableList(itemCleanupWarningTimes); } // Devolver copia inmutable
    public static String getItemCleanupWarningMessage() { return itemCleanupWarningMessage; }
    public static String getItemCleanupCleanupMessage() { return itemCleanupCleanupMessage; }

    // Entity Limit Control
    public static String getEntityLimitControlMode() { return entityLimitControlMode; }           // RENOMBRADO
    public static int getEntityCheckInterval() { return entityCheckInterval; }                   // RENOMBRADO
    public static int getMinecartLimitPerChunk() { return minecartLimitPerChunk; }
    public static int getBoatLimitPerChunk() { return boatLimitPerChunk; }
    public static int getArmorStandLimitPerChunk() { return armorStandLimitPerChunk; }
    public static boolean logEntityRemovals() { return logEntityRemovals; }                      // RENOMBRADO

    /**
     * Verifica si la prevención de colocación de vehículos (Minecart/Boat) está activa.
     * Depende del feature toggle y el modo 'prevent_placement'.
     * @return true si se debe prevenir la colocación de vehículos, false en caso contrario.
     */
    public static boolean isEntityLimitPreventPlacement() {                                   // RENOMBRADO
        // Comprueba si la característica general está habilitada y si el modo específico es 'prevent_placement'
        return featureEntityLimitControlEnabled && "prevent_placement".equalsIgnoreCase(entityLimitControlMode);
    }


    // --- Utilidades ---

    /**
     * Traduce códigos de color (&) en una cadena a los códigos de color de Bukkit.
     * @param message El mensaje con códigos de color (&).
     * @return El mensaje con colores aplicados. Devuelve cadena vacía si message es null.
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Formatea un mensaje reemplazando placeholders como {time} y {count}.
     * @param message El mensaje base con placeholders.
     * @param args Los argumentos para reemplazar los placeholders (ej. tiempo, contador).
     * @return El mensaje formateado. Devuelve cadena vacía si message es null.
     */
    public static String formatMessage(String message, Object... args) {
        String formattedMessage = message;
        if (formattedMessage == null) return "";

        // Reemplaza {time} si existe y hay al menos un argumento
        if (formattedMessage.contains("{time}") && args.length > 0) {
            try {
                formattedMessage = formattedMessage.replace("{time}", String.valueOf(args[0]));
            } catch (Exception e) { /* Ignorar error si el argumento no es convertible */ }
        }
        // Reemplaza {count} si existe y hay suficientes argumentos
        // (índice 1 si {time} ya fue usado, índice 0 si no)
        if (formattedMessage.contains("{count}") && args.length > (formattedMessage.contains("{time}") ? 1 : 0)) {
            try {
                int countIndex = formattedMessage.contains("{time}") ? 1 : 0;
                formattedMessage = formattedMessage.replace("{count}", String.valueOf(args[countIndex]));
            } catch (Exception e) { /* Ignorar error si el argumento no es convertible */ }
        }
        return formattedMessage;
    }
}