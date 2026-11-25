package chess.analysis;

import chess.engine.Evaluation;

import static java.lang.Math.exp;

/** Converts an engine evaluation to White's winning chance in percent (0..100). */
public class WinChance {
    public static double whitePercent(Evaluation ev) {
        if(ev.mate()) {
            return (ev.value() > 0) ? 100.0 : 0.0;
        }

        // logistic curve: 0 cp -> 50%, about +300 cp -> 75%
        return 100.0 / (1 + exp(-0.00368208 * ev.value()));
    }
}
