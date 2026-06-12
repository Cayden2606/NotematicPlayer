package me.cayde26.notematicPlayer;

import java.util.List;

public class Song {
    private final String name;
    private final double tempo;
    private final List<SongNote> notes;
    private final int maxTick;
    private double volumeMultiplier = 1.0;
    private boolean isPrivate = false;

    public Song(String name, double tempo, List<SongNote> notes) {
        this.name = name;
        this.tempo = tempo;
        this.notes = notes;
        
        int calculatedMaxTick = 0;
        for (SongNote note : notes) {
            if (note.getWhen() > calculatedMaxTick) {
                calculatedMaxTick = note.getWhen();
            }
        }
        this.maxTick = calculatedMaxTick;
    }

    public String getName() {
        return name;
    }

    public double getTempo() {
        return tempo;
    }

    public List<SongNote> getNotes() {
        return notes;
    }

    public int getMaxTick() {
        return maxTick;
    }

    public double getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = volumeMultiplier;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}

