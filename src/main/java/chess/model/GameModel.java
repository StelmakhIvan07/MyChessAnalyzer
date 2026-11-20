package chess.model;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.*;
import java.util.*;


public class GameModel {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Board board = new Board();
    private final List<Move> moves = new ArrayList<>();
    private final List<String> positions = new ArrayList<>();
    private int currentMove = 0;

    private void rebuildBoard(int idx) {
        board.loadFromFen(START_FEN);
        for(int i = 0; i < idx; i++){
            board.doMove(moves.get(i));
        }
        currentMove = idx;
    }

    public GameModel() {
        reset();
    }

    public void reset() {
        moves.clear();
        positions.clear();

        board.loadFromFen(START_FEN);
        positions.add(board.getFen());

        currentMove = 0;
    }

    public String fen() { return board.getFen(); }
    public Side sideToMove() { return board.getSideToMove(); }
    public int moveCount() { return moves.size(); }
    public int currentIndex() { return currentMove; }
    public boolean isLive() { return currentMove == moves.size(); }
    public Piece pieceAt(Square sq) {return board.getPiece(sq); }

    public MoveResult tryMove(Square from, Square to, Piece promotion) {
        if(!isLive()) return MoveResult.NOT_LIVE;
        if (isGameOver()) return MoveResult.GAME_OVER;

        boolean promo = needsPromotion(from, to);
        if (promo && (promotion == null || promotion == Piece.NONE)) {
            return MoveResult.PROMOTION_NEEDED;
        }
        Move move = promo ? new Move(from, to, promotion) : new Move(from, to);

        if (!board.legalMoves().contains(move)) return MoveResult.ILLEGAL;

        board.doMove(move);
        moves.add(move);
        positions.add(board.getFen());
        currentMove++;
        return MoveResult.OK;
    }

    public Set<Square> legalTargets(Square from) {
        Set<Square> targets = new HashSet<>();
        if (!isLive() || isGameOver()) return targets;
        for (Move m : board.legalMoves()) {
            if (m.getFrom() == from) {
                targets.add(m.getTo());
            }
        }
        return targets;
    }

    public boolean needsPromotion(Square from, Square to) {
        Piece p = pieceAt(from);
        if (p.getPieceType() != PieceType.PAWN) return false;
        Rank lastRank = (p.getPieceSide() == Side.WHITE) ? Rank.RANK_8 : Rank.RANK_1;
        return to.getRank() == lastRank;
    }

    public MoveResult applyUci(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5))
            return MoveResult.ILLEGAL;

        Square from, to;
        try{
            from = Square.valueOf(uci.substring(0, 2).toUpperCase());
            to = Square.valueOf(uci.substring(2, 4).toUpperCase());
        } catch (IllegalArgumentException e) {
            return MoveResult.ILLEGAL;
        }

        Piece promotion = Piece.NONE;
        if(uci.length() == 5) {
            boolean white = sideToMove() == Side.WHITE;
            switch(uci.charAt(4)) {
                case 'q' -> promotion = white ? Piece.WHITE_QUEEN: Piece.BLACK_QUEEN;
                case 'b' -> promotion = white ? Piece.WHITE_BISHOP: Piece.BLACK_BISHOP;
                case 'n' -> promotion = white ? Piece.WHITE_KNIGHT: Piece.BLACK_KNIGHT;
                case 'r' -> promotion = white ? Piece.WHITE_ROOK: Piece.BLACK_ROOK;

                default  -> { return MoveResult.ILLEGAL; }
            }

        }
        return tryMove(from, to, promotion);
    }

    public boolean goTo(int idx) {
        if(idx < 0 || moves.size() < idx) return false;
        rebuildBoard(idx);
        return true;
    }

    public boolean undo() {
        if(!isLive() || moves.isEmpty()) return false;
        moves.removeLast();
        positions.removeLast();
        rebuildBoard(moves.size());
        return true;
    }

    //========================================
    // History
    public List<String> uciHistory() {
        List<String> history = new ArrayList<>();
        for(Move m: moves) {
            history.add(m.toString());
        }
        return history;
    }

    public List<String> fenHistory() {
        return List.copyOf(positions);
    }

    public List<String> sanHistory() {
        MoveList san = new MoveList(START_FEN);
        san.addAll(moves);
        return List.of(san.toSanArray());
    }
    //========================================

    public GameResult result() {
        if(board.isMated()) {
            return sideToMove() == Side.WHITE? GameResult.BLACK_WINS: GameResult.WHITE_WINS;
        }

        if(board.isDraw()) return GameResult.DRAW;
        return GameResult.ONGOING;
    }

    public boolean isGameOver()  { return result() != GameResult.ONGOING; }
    public boolean isInCheck()   { return board.isKingAttacked(); }
}