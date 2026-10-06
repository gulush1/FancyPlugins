package de.oliver.fancynpcs.api.events;

import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Is fired after an NPC has been removed from the NpcManager, regardless of the cause (command or API). Not cancellable.
 */
public class NpcRemovedEvent extends Event {

    private static final HandlerList handlerList = new HandlerList();
    @NotNull
    private final Npc npc;

    public NpcRemovedEvent(@NotNull Npc npc) {
        super(!Bukkit.isPrimaryThread());
        this.npc = npc;
    }

    public static HandlerList getHandlerList() {
        return handlerList;
    }

    /**
     * @return the removed npc
     */
    public @NotNull Npc getNpc() {
        return npc;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }
}
