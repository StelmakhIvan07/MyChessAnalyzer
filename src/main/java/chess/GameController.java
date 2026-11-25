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

/**
 * Connects the model (GameModel), the engines (Bot, Analyzer) and the UI.
 * All public methods are called from the Swing thread (buttons, mouse, timer).
 */
public class GameController {
    //==================================================
    // Fields

    //-------------------------------------
    // Dependencies
    private final GameModel model;
    private final Bot bot;
    private final BoardPanel view;
    private final Analyzer analyzer;
    private final EvalBar bar;
    private final MoveListPanel list;

    //-------------------------------------
    // Game state
    private Side playerSide = Side.WHITE;
    private boolean vsBot = true;          // false -> solo game (one person plays both sides)
    private boolean showAnalysis = true;   // show eval, arrows, badges and accuracy
    private int gameId = 0;                // increases with every new game; old async answers are ignored
    private boolean busy = false;          // true while the bot engine is being reset
    private boolean started = false;       // false until the first "Start game" click

    //-------------------------------------
    // Analysis state
    private Evaluation lastShown;          // last evaluation drawn on the eval bar

    private static final int MIN_DEPTH = 12;   // shallower evaluations are not trusted for reviews
    private final List<MoveReview> reviews = new ArrayList<>();   // index = ply, null = skipped
    private int nextToReview = 0;
    //==================================================

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

    //==================================================
    // Game flow

    /**
     * Starts a new game.
     * @param side  the side of the human player (in a solo game: the side at the bottom)
     * @param vsBot true - play against the bot, false - solo game
     * @param elo   bot strength
     */
    public void newGame(Side side, boolean vsBot, int elo) {
        started = true;
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
        this.vsBot = vsBot;

        model.reset();
        refreshMoveList();
        view.setPlayerSide(side);
        view.setControlsBothSides(!vsBot);

        // The bot engine resets asynchronously; the rest runs when it answers "readyok"
        bot.newGame()
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (id != gameId) return;
                    bot.setElo(elo);
                    busy = false;
                    if (vsBot && side == Side.BLACK) requestBotMove();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (id == gameId) showError("Failed to start new game: " + ex);
                    });
                    return null;
                });

        startAnalysis();
    }

    /** Called by BoardPanel when the human drops a piece. */
    public MoveResult onPlayerMove(Square from, Square to, Piece promotion) {
        if (!started) return null;
        if(busy || model.isGameOver()) return null;
        if(vsBot && model.sideToMove() != playerSide) return null;   // it is the bot's turn

        MoveResult r = model.tryMove(from, to, promotion);

        if(r == MoveResult.OK) {
            view.repaint();
            afterMove();
            return r;
        }

        return MoveResult.ILLEGAL;
    }

    /** Common work after any move (human or bot): UI refresh, game end check, next turn. */
    private void afterMove() {
        refreshMoveList();
        showMoveFeedback(model.moveCount() - 1);

        if (model.isGameOver()) {
            analyzer.stopAnalysis();
            GameResult r = model.result();
            SwingUtilities.invokeLater(() -> view.showResult(r));
            return;
        }
        if (vsBot && model.sideToMove() != playerSide) requestBotMove();

        startAnalysis();
    }

    /**
     * Asks the bot for a move in the background.
     * The answer comes later on another thread, so it is moved back to the Swing thread,
     * and ignored if a new game has started meanwhile (id != gameId).
     */
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

    /** Undo: against the bot - back to the player's turn; solo - one move back. */
    public void takeBack() {
        if(busy || !model.isLive()) return;

        if (vsBot) {
            if(model.sideToMove() != playerSide && !model.isGameOver())
                return;
            // undo the bot's move and ours until it is our turn again
            do {
                if (!model.undo()) break;
            } while (model.sideToMove() != playerSide);
        } else {
            model.undo();   // solo game: just one move back
        }

        // forget reviews of moves that no longer exist
        while (reviews.size() > model.moveCount()) reviews.removeLast();
        nextToReview = Math.min(nextToReview, model.moveCount());

        view.resetInteraction();
        lastShown = null;
        showMoveFeedback(model.moveCount() - 1);
        refreshMoveList();

        startAnalysis();
        if (vsBot && model.sideToMove() != playerSide) requestBotMove();
    }
    //==================================================

    //==================================================
    // Navigation through the move history

    /** Shows the position after idx half-moves (0 = start position). */
    public void goTo(int idx) {
        if (busy || !model.goTo(idx)) return;

        view.resetInteraction();
        showMoveFeedback(idx - 1);

        lastShown = null;

        if(model.isLive() && model.isGameOver()) analyzer.stopAnalysis();
        else analyzer.analyze(model.fen());

        refreshMoveList();
    }

    public void first() { goTo(0); }
    public void prev() { goTo(model.currentIndex() - 1); }
    public void next() { goTo(model.currentIndex() + 1); }
    public void last() { goTo(model.moveCount()); }
    //==================================================

    //==================================================
    // Analysis

    private void startAnalysis() {
        if(!model.isGameOver() || !model.isLive()) analyzer.analyze(model.fen());
    }

    /**
     * Turns analysis display on/off. The engine keeps analysing anyway,
     * so everything appears at once when it is turned back on.
     */
    public void setShowAnalysis(boolean show) {
        showAnalysis = show;
        bar.setShowEvaluation(show);
        showMoveFeedback(model.currentIndex() - 1);   // arrow + badge, or clear them
        refreshMoveList();                             // move colors + accuracy
    }

    /** Called by the timer every 200 ms: reviews new moves and updates the eval bar. */
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

    //-------------------------------------
    // Move review

    /**
     * Classifies played moves in order, starting from nextToReview.
     * A move needs a deep enough evaluation of the position before it and after it.
     * Not deep enough -> the move is skipped (null), except the last move,
     * which waits because the engine is probably still analysing it.
     */
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
                // no position to analyse after mate/draw: compare with the result
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

            if (showAnalysis && i == model.currentIndex() - 1) {
                view.setBadge(squareOf(move, 2), cls);
            }
        }

        if (changed) refreshMoveList();
    }

    private boolean deepEnough(Evaluation ev) {
        return ev != null && ev.depth() >= MIN_DEPTH;
    }

    private void skipMove() {
        reviews.add(null);
        nextToReview++;
    }
    //==================================================

    //==================================================
    // UI updates

    /** Draws the badge of the given ply and an arrow with the best move (if it differs). */
    private void showMoveFeedback(int ply) {
        view.clearArrow();
        view.clearBadge();
        if (!showAnalysis) return;

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

    private void refreshMoveList() {
        // Without analysis an empty review list is passed -> moves have no colors
        List<MoveReview> shown = showAnalysis ? new ArrayList<>(reviews) : List.of();
        list.setMoves(model.sanHistory(), shown);
        list.setCurrent(model.currentIndex());

        refreshAccuracy();
    }

    /** Average accuracy per side; even plies are White's moves, odd are Black's. */
    private void refreshAccuracy() {
        if (!showAnalysis) {
            list.setAccuracy(null, null);
            return;
        }

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

    private void showError(String msg) {
        JOptionPane.showMessageDialog(view, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
    //==================================================

    //==================================================
    // Helpers

    /** "e2e4" with form=0 -> E2, with form=2 -> E4. */
    private static Square squareOf(String uci, int form) {
        return Square.valueOf(uci.substring(form, form + 2).toUpperCase());
    }
    //==================================================
}
