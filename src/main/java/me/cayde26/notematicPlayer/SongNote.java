package me.cayde26.notematicPlayer;

public class SongNote {
    private final String instrument;
    private final double note; // pitch multiplier
    private final double volume;
    private final int when; // tick offset

    public SongNote(String instrument, double note, double volume, int when) {
        this.instrument = instrument;
        this.note = note;
        this.volume = volume;
        this.when = when;
    }

    public String getInstrument() {
        return instrument;
    }

    public double getNote() {
        return note;
    }

    public double getVolume() {
        return volume;
    }

    public int getWhen() {
        return when;
    }
}

