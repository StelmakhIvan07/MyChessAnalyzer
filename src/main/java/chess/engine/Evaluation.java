package chess.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * One engine evaluation of a position.
 * value: centipawns (or moves to mate if mate == true), always from White's point of view.
 * pv: the best line, pv[0] is the best move.
 */
public record Evaluation(String fen, int depth, boolean mate, int value, List<String> pv) {
    public String bestMove() { return pv.isEmpty() ? null : pv.get(0); }

    /**
     * Parses a line like
     * "info depth 20 multipv 1 score cp 35 nodes ... pv e2e4 e7e5 ...".
     * Returns null for lines that are not a full, exact evaluation.
     */
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
                        // "score cp 35" or "score mate -3"
                        mate = t[i + 1].equals("mate");
                        value = Integer.parseInt(t[i + 2]);
                        hasScore = true;
                        i += 2;
                        // bound = not an exact score, skip it
                        if(i+ 1 < t.length
                                && (t[i+1].equals("lowerbound") || t[i+1].equals("upperbound"))) {
                            return null;
                        }
                    }
                    case "pv" -> {
                        // pv is always last: the rest of the line is moves
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
        // Stockfish scores for the side to move; flip to White's point of view
        if(fen.split(" ")[1].equals("b")) value = -value;

        return new Evaluation(fen, depth, mate, value, List.copyOf(pv));
    }
}
