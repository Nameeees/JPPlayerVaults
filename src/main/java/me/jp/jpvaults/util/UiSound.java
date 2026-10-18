package me.jp.jpvaults.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sonidos de interfaz resueltos por nombre: cada version renombro los enum de
 * Sound, asi que se prueban los candidatos y se usa el primero que exista en
 * la version que corre el servidor.
 */
public enum UiSound {

    /** Ender chest: la metafora perfecta para un vault. */
    OPEN("BLOCK_ENDER_CHEST_OPEN", "BLOCK_ENDERCHEST_OPEN", "CHEST_OPEN"),
    SUCCESS("ENTITY_PLAYER_LEVELUP", "LEVEL_UP"),
    DENY("ENTITY_VILLAGER_NO", "VILLAGER_NO");

    private final String[] candidates;

    UiSound(String... candidates) {
        this.candidates = candidates;
    }

    /** Reproduce el sonido en la ubicacion del jugador. */
    public void play(Player player, float volume, float pitch) {
        for (String candidate : candidates) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(candidate), volume, pitch);
                return;
            } catch (IllegalArgumentException otherVersion) {
                // Nombre de otra version de Minecraft: probamos el siguiente.
            }
        }
    }
}
