package chess.analysis;

import chess.engine.Evaluation;
import chess.model.GameResult;

public class MoveClassifier {
    public static MoveClass classify(double loss, boolean playedBest) {
        if (playedBest) return MoveClass.BEST;
        if (loss < 2)   return MoveClass.EXCELLENT;
        if (loss < 5)   return MoveClass.GOOD;
        if (loss < 10)  return MoveClass.INACCURACY;
        if (loss < 20)  return MoveClass.MISTAKE;
        return MoveClass.BLUNDER;
    }

    public static double loss(Evaluation before, Evaluation after, boolean whiteMove) {
        return lossOf(WinChance.whitePercent(before), WinChance.whitePercent(after), whiteMove);
    }

    public static double lossAfterGameEnd(Evaluation before, GameResult result, boolean whiteMove) {
        double afterWhite = switch (result) {
            case WHITE_WINS -> 100.0;
            case BLACK_WINS -> 0.0;
            case DRAW       -> 50.0;
            case ONGOING    -> throw new IllegalArgumentException("Game is not completed");
        };
        return lossOf(WinChance.whitePercent(before), afterWhite, whiteMove);
    }

    private static double lossOf(double beforeWhite, double afterWhite, boolean whiteMove) {
        double b = whiteMove ? beforeWhite : 100 - beforeWhite;
        double a = whiteMove ? afterWhite  : 100 - afterWhite;
        return Math.max(0, b - a);
    }
}
