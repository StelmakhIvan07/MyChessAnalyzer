package chess.analysis;

/** Review of one half-move: what was played, what was best, and how bad it was. */
public record MoveReview(int index, String move, String best, double loss, MoveClass cls) {
}
