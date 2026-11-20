package chess.engine;

import java.util.ArrayList;
import java.util.List;

public record Evaluation(String fen, int depth, boolean mate, int value, List<String> pv) {
    public String bestMove() { return pv.isEmpty() ? null : pv.get(0); }

    public static Evaluation parse(String line, String fen) {
        if (fen == null || !line.startsWith("info ")) return null;
        String[] t = line.trim().split("\\s+");

        int depth = -1, multipv = 1, value = 0;
        boolean hasScore = false, mate = false;
        List<String> pv = new ArrayList<>();

        try {
            for (int i = 1; i < t.length; i++) {
                switch (t[i]) {
                    case "string" -> { return null; }
                    case "depth"  -> depth = Integer.parseInt(t[++i]);
                    case "multipv" -> multipv = Integer.parseInt(t[++i]);
                    case "score" -> {
                        mate = t[i + 1].equals("mate");
                        value = Integer.parseInt(t[i + 2]);
                        hasScore = true;
                        i += 2;
                        if(i+ 1 < t.length
                                && (t[i+1].equals("lowerbound") || t[i+1].equals("upperbound"))) {
                            return null;
                        }
                    }
                    case "pv" -> {
                        for (int j = i + 1; j < t.length; j++) pv.add(t[j]);
                        i = t.length;
                    }
                    default -> { }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }

        if(!hasScore || pv.isEmpty() || depth < 0 || multipv != 1 || mate && value == 0) return null;
        if(fen.split(" ")[1].equals("b")) value = -value;

        return new Evaluation(fen, depth, mate, value, List.copyOf(pv));
    }
}
