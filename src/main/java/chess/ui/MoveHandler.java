package chess.ui;

import chess.model.MoveResult;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;

public interface MoveHandler {
    MoveResult onMove(Square from, Square to, Piece promotion);
}
