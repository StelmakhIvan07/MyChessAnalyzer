package chess.analysis;

public record MoveReview(int index, String move, String best, double loss, MoveClass cls) {
}
