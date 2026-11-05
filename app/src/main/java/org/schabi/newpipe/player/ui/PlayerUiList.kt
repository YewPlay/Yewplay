package org.schabi.newpipe.player.ui

import java.util.Optional
import java.util.function.Consumer

class PlayerUiList(vararg initialPlayerUis: PlayerUi) {
    private val playerUis: MutableList<PlayerUi> = mutableListOf(*initialPlayerUis)

    /**
     * Adds the provided player ui to the list and calls on it the initialization functions that
     * apply based on the current player state. The preparation step needs to be done since when
     * UIs are removed and re-added, the player will not call e.g. initPlayer again since the
     * exoplayer is already initialized, but we need to notify the newly built UI that the player
     * is ready nonetheless.
     * @param playerUi the player ui to prepare and add to the list; its [PlayerUi.player] will be
     * used to query information about the player state
     */
    fun addAndPrepare(playerUi: PlayerUi) {
        if (playerUi.player.fragmentListener.isPresent) {
            // make sure UIs know whether a service is connected or not
            playerUi.onFragmentListenerSet()
        }

        if (!playerUi.player.exoPlayerIsNull()) {
            playerUi.initPlayer()
            if (playerUi.player.playQueue != null) {
                playerUi.initPlayback()
            }
        }

        playerUis.add(playerUi)
    }

    /**
     * Destroys all matching player UIs and removes them from the list.
     * @param playerUiType the class of the player UI to destroy; [Class.isInstance] will be used,
     * so even subclasses will be destroyed and removed
     */
    fun <T> destroyAll(playerUiType: Class<T>) {
        playerUis.filter(playerUiType::isInstance).forEach { playerUi ->
            playerUi.destroyPlayer()
            playerUi.destroy()
        }
        playerUis.removeIf(playerUiType::isInstance)
    }

    /**
     * @param playerUiType the class of the player UI to return; [Class.isInstance] will be used,
     * so even subclasses could be returned
     * @return the first player UI of the required type found in the list, or an empty [Optional]
     */
    fun <T> get(playerUiType: Class<T>): Optional<T> {
        return playerUis.stream()
            .filter(playerUiType::isInstance)
            .map(playerUiType::cast)
            .findFirst()
    }

    /** Calls the provided consumer on all player UIs in the list, in order of addition. */
    fun call(consumer: Consumer<PlayerUi>) {
        playerUis.forEach { consumer.accept(it) }
    }
}
