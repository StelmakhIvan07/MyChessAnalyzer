package chess.ui;

import chess.analysis.MoveClass;
import com.github.bhlangonijr.chesslib.Square;

public class MoveBadge {
    private Square square;
    private BadgePhase phase;
    private MoveClass moveClass;
    private double spinnerAngle;
    private float scale;

    public void startLoading(Square target) {
        phase = BadgePhase.LOADING;
        moveClass = null;
        spinnerAngle = 0;
    }

    public void setResult(MoveClass result) {
        moveClass = result;
        scale = 0.5f;
    }

    public boolean tick() {
        switch (phase) {
            case HIDDEN, SHOWN: return false;
            case LOADING: spinnerAngle +=18;
            case REVEALING: scale = 1.0f;
        }
        return true;
    }

    public void clear() {
        phase = BadgePhase.HIDDEN; square = null;
    }
}
