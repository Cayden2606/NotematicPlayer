package me.cayde26.notematicPlayer;

import java.util.UUID;

public class SongPlayback {
    private final Song song;
    private double currentVirtualTick;
    private int nextNoteIndex;
    private final UUID listenerUuid; // null if global playback
    private final boolean global;
    private boolean paused;
    private final boolean showChatMessage;
    private final boolean positional;
    private final String initiator;

    public SongPlayback(Song song, UUID listenerUuid) {
        this(song, listenerUuid, true, false, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage) {
        this(song, listenerUuid, showChatMessage, false, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional) {
        this(song, listenerUuid, showChatMessage, positional, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional, String initiator) {
        this.song = song;
        this.listenerUuid = listenerUuid;
        this.global = (listenerUuid == null);
        this.paused = false;
        this.showChatMessage = showChatMessage;
        this.positional = positional;
        this.initiator = initiator != null ? initiator : "API";
        
        // Start at first note's "when" tick to avoid leading silence
        if (!song.getNotes().isEmpty()) {
            this.currentVirtualTick = song.getNotes().get(0).getWhen();
        } else {
            this.currentVirtualTick = 0;
        }
        this.nextNoteIndex = 0;
    }

    public String getInitiator() {
        return initiator;
    }

    public boolean isShowChatMessage() {
        return showChatMessage;
    }

    public boolean isPositional() {
        return positional;
    }

    public Song getSong() {
        return song;
    }

    public double getCurrentVirtualTick() {
        return currentVirtualTick;
    }

    public void incrementTick() {
        this.currentVirtualTick += song.getTempo();
    }

    public int getNextNoteIndex() {
        return nextNoteIndex;
    }

    public void setNextNoteIndex(int nextNoteIndex) {
        this.nextNoteIndex = nextNoteIndex;
    }

    public UUID getListenerUuid() {
        return listenerUuid;
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}

