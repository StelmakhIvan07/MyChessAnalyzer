package chess.analysis;

import chess.engine.Evaluation;

import static java.lang.Math.exp;

public class WinChance {
    public static double whitePercent(Evaluation ev) {
        if(ev.mate()) {
            return (ev.value() > 0) ? 100.0 : 0.0;
        }

        return 100.0 / (1 + exp(-0.00368208 * ev.value()));
    }
}
