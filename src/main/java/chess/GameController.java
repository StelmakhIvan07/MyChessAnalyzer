package chess;

import chess.analysis.MoveClass;
import chess.analysis.MoveClassifier;
import chess.analysis.MoveReview;
import chess.engine.Analyzer;
import chess.engine.Bot;
import chess.engine.Evaluation;
import chess.model.GameModel;
import chess.model.GameResult;
import chess.model.MoveResult;
import chess.ui.*;
import com.github.bhlangonijr.chesslib.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GameController {
    private final GameModel model;
    private final Bot bot;
    private final BoardPanel view;
    private final Analyzer analyzer;
    private final EvalBar bar;

    private Side playerSide = Side.WHITE;
    private int gameId = 0;
    private boolean busy = false;

    private Evaluation lastShown;

    private static final int MIN_DEPTH = 12;
    private final List<MoveReview> reviews = new ArrayList<>();
    private int nextToReview = 0;

    public GameController(GameModel model, Bot bot, BoardPanel view, Analyzer analyzer, EvalBar bar) {
        this.model = model;
        this.bot = bot;
        this.view = view;
        this.analyzer = analyzer;
        this.bar = bar;

        new Timer(200, e -> refreshAnalysis()).start();
    }

    public MoveResult onPlayerMove(Square from, Square to, Piece promotion) {
        if(busy || model.isGameOver() || model.sideToMove() != playerSide) return null;

        MoveResult r = model.tryMove(from, to, promotion);

        if(r == MoveResult.OK) {
            view.repaint();
            afterMove();
            return r;
        }

        return MoveResult.ILLEGAL;
    }

    private void startAnalysis() {
        if(!model.isGameOver()) analyzer.analyze(model.fen());
    }

    private void afterMove() {
        updateArrow();
        view.clearBadge();

        if (model.isGameOver()) {
            GameResult r = model.result();
            SwingUtilities.invokeLater(() -> view.showResult(r));
            return;
        }
        if (model.sideToMove() != playerSide) requestBotMove();

        startAnalysis();
    }

    private void requestBotMove() {
        int id = gameId;
        bot.chooseMove(model.uciHistory())
                .thenAccept(uci -> SwingUtilities.invokeLater(() -> {
                    if (id != gameId || uci == null) return;
                    if(model.applyUci(uci) != MoveResult.OK) {
                        showError("Illegal bot's move: " + uci);
                        return;
                    }
                    view.repaint();
                    afterMove();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (id == gameId) showError("Engine has not responded: " + ex);
                    });
                    return null;
                });
    }

    public void newGame(Side side, int elo) {
        analyzer.clear();
        bar.reset();
        bar.setFlipped(side == Side.BLACK);
        view.clearArrow();
        lastShown = null;

        reviews.clear();
        nextToReview = 0;
        view.clearBadge();

        int id = ++gameId;
        busy = true;
        playerSide = side;

        model.reset();
        view.setPlayerSide(side);

        bot.newGame()
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (id != gameId) return;
                    bot.setElo(elo);
                    busy = false;
                    if (side == Side.BLACK) requestBotMove();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (id == gameId) showError("Failed to start new game: " + ex);
                    });
                    return null;
                });

        startAnalysis();
    }

    public void refreshAnalysis() {
        reviewMoves();
        if(model.isGameOver()) {
            bar.setResult(model.result());
            return;
        }

        Evaluation ev = analyzer.latest();
        if( ev == null || !ev.fen().equals(model.fen())) {
            return;
        }
        if(ev == lastShown) return;

        lastShown = ev; bar.setEvaluation(ev);
    }

    private void updateArrow() {
        List<String> played = model.uciHistory();
        List<String> fens = model.fenHistory();
        if (played.isEmpty()) { view.clearArrow(); return; }

        String lastMove = played.getLast();
        Evaluation before = analyzer.get(fens.get(fens.size() - 2));   // FEN before move
        String best = (before == null) ? null : before.bestMove();

        if (best == null || best.equals(lastMove)) { view.clearArrow(); return; }

        Square from = Square.valueOf(best.substring(0, 2).toUpperCase());
        Square to   = Square.valueOf(best.substring(2, 4).toUpperCase());
        view.setArrow(from, to);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(view, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void reviewMoves() {
        List<String> moves = model.uciHistory();
        List<String> fens = model.fenHistory();

        while (nextToReview < moves.size()) {
            int i = nextToReview;
            boolean last = (i == moves.size() - 1);
            boolean whiteMove = (i % 2 == 0);
            String move = moves.get(i);

            Evaluation before = analyzer.get(fens.get(i));
            if (!deepEnough(before)) { skipMove(); continue; }

            double loss;
            if (last && model.isGameOver()) {
                loss = MoveClassifier.lossAfterGameEnd(before, model.result() , whiteMove);
            } else {
                Evaluation after = analyzer.get(fens.get(i + 1));
                if (!deepEnough(after)) {
                    if (last) break;
                    skipMove(); continue;
                }
                loss = MoveClassifier.loss(before, after, whiteMove);
            }

            boolean playedBest = move.equals(before.bestMove());
            if (playedBest) loss = 0;

            MoveClass cls = MoveClassifier.classify(loss, playedBest);
            reviews.add(new MoveReview(i, move, before.bestMove(), loss, cls));
            nextToReview++;

            if (last) {
                Square dest = Square.valueOf(move.substring(2, 4).toUpperCase());
                view.setBadge(dest, cls);
            }
        }
    }

    private boolean deepEnough(Evaluation ev) {
        return ev != null && ev.depth() >= MIN_DEPTH;
    }

    private void skipMove() {
        reviews.add(null);
        nextToReview++;
    }
}
