package chess;

import chess.analysis.AccuracyCalculator;
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
    private final MoveListPanel list;

    private Side playerSide = Side.WHITE;
    private int gameId = 0;
    private boolean busy = false;

    private Evaluation lastShown;

    private static final int MIN_DEPTH = 12;
    private final List<MoveReview> reviews = new ArrayList<>();
    private int nextToReview = 0;

    public GameController(GameModel model, Bot bot, BoardPanel view, Analyzer analyzer, EvalBar bar, MoveListPanel list) {
        this.model = model;
        this.bot = bot;
        this.view = view;
        this.analyzer = analyzer;
        this.bar = bar;
        this.list = list;

        list.setOnSelect(this::goTo);

        new Timer(200, e -> refreshAnalysis()).start();
    }

    public void goTo(int idx) {
        if (busy || !model.goTo(idx)) return;

        view.resetInteraction();
        showMoveFeedback(idx - 1);

        lastShown = null;

        if(model.isLive() && model.isGameOver()) analyzer.stopAnalysis();
        else analyzer.analyze(model.fen());

        refreshMoveList();
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
        if(!model.isGameOver() || !model.isLive()) analyzer.analyze(model.fen());
    }

    private void afterMove() {
        refreshMoveList();
        showMoveFeedback(model.moveCount() - 1);

        if (model.isGameOver()) {
            analyzer.stopAnalysis();
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
                    if (!model.isLive()) model.goTo(model.moveCount());
                    if(model.applyUci(uci) != MoveResult.OK) {
                        showError("Illegal bot's move: " + uci);
                        return;
                    }
                    view.resetInteraction();
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
        refreshMoveList();
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
        if(model.isGameOver() && model.isLive()) {
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

    private void refreshMoveList() {
        list.setMoves(model.sanHistory(), new ArrayList<>(reviews));
        list.setCurrent(model.currentIndex());

        refreshAccuracy();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(view, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void reviewMoves() {
        List<String> moves = model.uciHistory();
        List<String> fens = model.fenHistory();

        boolean changed = false;

        while (nextToReview < moves.size()) {
            int i = nextToReview;
            boolean last = (i == moves.size() - 1);
            boolean whiteMove = (i % 2 == 0);
            String move = moves.get(i);

            Evaluation before = analyzer.get(fens.get(i));
            if (!deepEnough(before)) { skipMove(); changed = true; continue; }

            double loss;
            if (last && model.isGameOver()) {
                loss = MoveClassifier.lossAfterGameEnd(before, model.result() , whiteMove);
            } else {
                Evaluation after = analyzer.get(fens.get(i + 1));
                if (!deepEnough(after)) {
                    if (last) break;
                    skipMove(); changed = true; continue;
                }
                loss = MoveClassifier.loss(before, after, whiteMove);
            }

            boolean playedBest = move.equals(before.bestMove());
            if (playedBest) loss = 0;

            MoveClass cls = MoveClassifier.classify(loss, playedBest);
            reviews.add(new MoveReview(i, move, before.bestMove(), loss, cls));
            changed = true;
            nextToReview++;

            if (i == model.currentIndex() - 1) {
                view.setBadge(squareOf(move, 2), cls);
            }
        }

        if (changed) refreshMoveList();
    }

    public void first() { goTo(0); }
    public void prev() { goTo(model.currentIndex() - 1); }
    public void next() { goTo(model.currentIndex() + 1); }
    public void last() { goTo(model.moveCount()); }

    private void showMoveFeedback(int ply) {
        view.clearArrow();
        view.clearBadge();

        List<String> moves = model.uciHistory();
        if(ply < 0 || ply >= moves.size()) return;

        String played = moves.get(ply);
        MoveReview r = (ply < reviews.size()) ? reviews.get(ply) : null;
        Evaluation before = analyzer.get(model.fenHistory().get(ply));
        String best = (r != null) ? r.best() : (before != null ? before.bestMove() : null);

        if (r != null) view.setBadge(squareOf(played, 2), r.cls());
        if (best != null && !best.equals(played))
            view.setArrow(squareOf(best, 0), squareOf(best, 2));


    }

    public void takeBack() {
        if(busy || !model.isLive()) return;
        if(model.sideToMove() != playerSide && !model.isGameOver())
            return;

        do {
            if (!model.undo()) break;
        } while (model.sideToMove() != playerSide);

        while (reviews.size() > model.moveCount()) reviews.removeLast();
        nextToReview = Math.min(nextToReview, model.moveCount());

        view.resetInteraction();
        lastShown = null;
        showMoveFeedback(model.moveCount() - 1);
        refreshMoveList();

        startAnalysis();
        if (model.sideToMove() != playerSide) requestBotMove();


    }

    private static Square squareOf(String uci, int form) {
        return Square.valueOf(uci.substring(form, form + 2).toUpperCase());
    }

    private boolean deepEnough(Evaluation ev) {
        return ev != null && ev.depth() >= MIN_DEPTH;
    }

    private void skipMove() {
        reviews.add(null);
        nextToReview++;
    }

    private void refreshAccuracy() {
        List<Double> whiteLosses = new ArrayList<>();
        List<Double> blackLosses = new ArrayList<>();

        for(int i = 0; i < reviews.size(); i++) {
            MoveReview r = reviews.get(i);
            if(r == null) continue;

            if(i%2 == 0) whiteLosses.add(r.loss());
            else blackLosses.add(r.loss());
        }

        list.setAccuracy(
                whiteLosses.isEmpty() ? null : AccuracyCalculator.accuracy(whiteLosses),
                blackLosses.isEmpty() ? null : AccuracyCalculator.accuracy(blackLosses)
        );
    }
}
