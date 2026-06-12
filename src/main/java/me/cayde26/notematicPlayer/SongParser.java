package me.cayde26.notematicPlayer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SongParser {
    private static final Gson gson = new Gson();

    public static Song parseSong(File file) throws IOException {
        String fileName = file.getName();
        if (!fileName.contains(".")) {
            throw new IOException("File has no extension: " + fileName);
        }
        String songName = fileName.substring(0, fileName.lastIndexOf('.'));
        
        String content = readFileContent(file);
        
        if (fileName.endsWith(".mcfunction")) {
            content = extractJsonFromMcFunction(content);
            if (content == null) {
                throw new IOException("Failed to extract JSON song data from .mcfunction: " + file.getName());
            }
        }
        
        return parseFromJson(songName, content);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String extractJsonFromMcFunction(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) continue;
            
            // Check for storage command or storage sheet set value
            if (line.contains("data modify storage") || line.contains("sheet set value")) {
                int openBraceIdx = line.indexOf('{');
                int closeBraceIdx = line.lastIndexOf('}');
                if (openBraceIdx != -1 && closeBraceIdx != -1 && closeBraceIdx > openBraceIdx) {
                    return line.substring(openBraceIdx, closeBraceIdx + 1);
                }
            }
        }
        return null;
    }

    private static Song parseFromJson(String name, String jsonStr) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            double tempo = 1.0;
            if (root.has("tempo")) {
                tempo = root.get("tempo").getAsDouble();
            }
            
            List<SongNote> notes = new ArrayList<>();
            if (root.has("notes")) {
                JsonArray notesArray = root.getAsJsonArray("notes");
                for (JsonElement noteElem : notesArray) {
                    JsonObject noteObj = noteElem.getAsJsonObject();
                    String instrument = noteObj.has("instrument") ? noteObj.get("instrument").getAsString() : "harp";
                    double note = noteObj.has("note") ? noteObj.get("note").getAsDouble() : 1.0;
                    double volume = noteObj.has("volume") ? noteObj.get("volume").getAsDouble() : 1.0;
                    int when = noteObj.has("when") ? noteObj.get("when").getAsInt() : 0;
                    
                    notes.add(new SongNote(instrument, note, volume, when));
                }
            }
            
            // Ensure notes are sorted by their trigger time
            notes.sort(Comparator.comparingInt(SongNote::getWhen));
            
            return new Song(name, tempo, notes);
        } catch (Exception e) {
            throw new IOException("Failed to parse song JSON structure: " + e.getMessage(), e);
        }
    }

    public static Song parseMimicSequence(File file) throws IOException {
        String fileName = file.getName();
        String songName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String content = readFileContent(file);
        
        try {
            JsonArray rootArray = JsonParser.parseString(content).getAsJsonArray();
            List<SongNote> notes = new ArrayList<>();
            
            for (JsonElement groupElem : rootArray) {
                JsonObject groupObj = groupElem.getAsJsonObject();
                int startTick = groupObj.has("startTick") ? groupObj.get("startTick").getAsInt() : 0;
                
                if (groupObj.has("sounds")) {
                    JsonArray soundsArray = groupObj.getAsJsonArray("sounds");
                    for (JsonElement soundElem : soundsArray) {
                        JsonObject soundObj = soundElem.getAsJsonObject();
                        String sound = soundObj.has("sound") ? soundObj.get("sound").getAsString() : "minecraft:block.note_block.harp";
                        double volume = soundObj.has("volume") ? soundObj.get("volume").getAsDouble() : 1.0;
                        double pitch = soundObj.has("pitch") ? soundObj.get("pitch").getAsDouble() : 1.0;
                        
                        notes.add(new SongNote(sound, pitch, volume, startTick));
                    }
                }
            }
            
            // Ensure notes are sorted by their trigger time
            notes.sort(Comparator.comparingInt(SongNote::getWhen));
            
            return new Song(songName, 1.0, notes);
        } catch (Exception e) {
            throw new IOException("Failed to parse mimic sequence JSON structure: " + e.getMessage(), e);
        }
    }
}

