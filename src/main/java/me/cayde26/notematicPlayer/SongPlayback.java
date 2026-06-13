package me.cayde26.notematicPlayer;

import org.bukkit.Location;
import java.util.UUID;

public class SongPlayback {
    private final Song song;
    private double currentVirtualTick;
    private int nextNoteIndex;
    private final UUID listenerUuid; // null if global/location playback
    private final boolean global;
    private boolean paused;
    private final boolean showChatMessage;
    private final boolean positional;
    private final String initiator;
    
    private int id;
    private final Location location; // null if bound to a player
    private final double radius;
    private final boolean looping;
    private double volumeMultiplier = 1.0;

    public SongPlayback(Song song, UUID listenerUuid) {
        this(song, listenerUuid, true, false, "API", false);
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage) {
        this(song, listenerUuid, showChatMessage, false, "API", false);
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional) {
        this(song, listenerUuid, showChatMessage, positional, "API", false);
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional, String initiator) {
        this(song, listenerUuid, showChatMessage, positional, initiator, false);
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional, String initiator, boolean looping) {
        this.song = song;
        this.listenerUuid = listenerUuid;
        this.global = (listenerUuid == null);
        this.location = null;
        this.radius = 0;
        this.looping = looping;
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

    public SongPlayback(Song song, Location location, double radius, boolean looping, boolean showChatMessage, String initiator) {
        this.song = song;
        this.listenerUuid = null;
        this.global = true;
        this.location = location;
        this.radius = radius;
        this.looping = looping;
        this.paused = false;
        this.showChatMessage = showChatMessage;
        this.positional = true; // Location playbacks are inherently 3D
        this.initiator = initiator != null ? initiator : "API";
        
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

    /**
     * Seeks the playback based on a seek value string (e.g. "+10s", "-5t", "100t", "5s").
     *
     * @param seekValueStr The seek value string
     * @return true if seek was successful, false if invalid format
     */
    public boolean seek(String seekValueStr) {
        if (seekValueStr == null || seekValueStr.isEmpty()) {
            return false;
        }

        boolean relative = false;
        boolean negative = false;
        String numberPart = seekValueStr;

        if (seekValueStr.startsWith("+")) {
            relative = true;
            numberPart = seekValueStr.substring(1);
        } else if (seekValueStr.startsWith("-")) {
            relative = true;
            negative = true;
            numberPart = seekValueStr.substring(1);
        }

        boolean isSeconds = false;
        if (numberPart.endsWith("s") || numberPart.endsWith("sec") || numberPart.endsWith("seconds")) {
            isSeconds = true;
            if (numberPart.endsWith("seconds")) {
                numberPart = numberPart.substring(0, numberPart.length() - 7);
            } else if (numberPart.endsWith("sec")) {
                numberPart = numberPart.substring(0, numberPart.length() - 3);
            } else {
                numberPart = numberPart.substring(0, numberPart.length() - 1);
            }
        } else if (numberPart.endsWith("t") || numberPart.endsWith("tick") || numberPart.endsWith("ticks")) {
            if (numberPart.endsWith("ticks")) {
                numberPart = numberPart.substring(0, numberPart.length() - 5);
            } else if (numberPart.endsWith("tick")) {
                numberPart = numberPart.substring(0, numberPart.length() - 4);
            } else {
                numberPart = numberPart.substring(0, numberPart.length() - 1);
            }
        }

        double numericValue;
        try {
            numericValue = Double.parseDouble(numberPart);
        } catch (NumberFormatException e) {
            return false;
        }

        double virtualTicksChange;
        if (isSeconds) {
            // 20 game ticks per second, each game tick is song.getTempo() virtual ticks
            virtualTicksChange = numericValue * 20.0 * song.getTempo();
        } else {
            virtualTicksChange = numericValue;
        }

        double targetTick;
        if (relative) {
            if (negative) {
                targetTick = this.currentVirtualTick - virtualTicksChange;
            } else {
                targetTick = this.currentVirtualTick + virtualTicksChange;
            }
        } else {
            targetTick = virtualTicksChange;
        }

        seekToTick(targetTick);
        return true;
    }

    public void seekToTick(double targetTick) {
        double maxTick = song.getMaxTick();
        if (targetTick < 0) {
            targetTick = 0;
        } else if (targetTick > maxTick) {
            if (looping && maxTick > 0) {
                targetTick = targetTick % maxTick;
            } else {
                targetTick = maxTick;
            }
        }
        this.currentVirtualTick = targetTick;

        // Recalculate nextNoteIndex
        java.util.List<SongNote> notes = song.getNotes();
        int index = 0;
        while (index < notes.size()) {
            if (notes.get(index).getWhen() >= targetTick) {
                break;
            }
            index++;
        }
        this.nextNoteIndex = index;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public double getRadius() {
        return radius;
    }

    public boolean isLooping() {
        return looping;
    }

    public double getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = volumeMultiplier;
    }
}

