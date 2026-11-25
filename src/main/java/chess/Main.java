package chess;

import chess.engine.*;
import chess.model.GameModel;
import chess.ui.*;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Entry point: starts two Stockfish processes (bot + analysis),
 * then builds the window and connects all parts on the Swing thread.
 */
public class Main {
    private static final Path ENGINE_PATH = Path.of("stockfish", "stockfish.exe");
    private static int BOT_ELO = 1500;

    public static void main(String[] args) {
        //==================================================
        // Engines
        PipeEngine botEngine;
        PipeEngine analysisEngine;

        try {
            botEngine =      new PipeEngine(ENGINE_PATH);
            analysisEngine = new PipeEngine(ENGINE_PATH);

            // close the stockfish processes when the program exits
            Runtime.getRuntime().addShutdownHook(new Thread(botEngine::close));
            Runtime.getRuntime().addShutdownHook(new Thread(analysisEngine::close));
        } catch (
                IOException e) {
            showFatal(e.getMessage());
            return;
        }

        Bot bot = new Bot(botEngine);
        Analyzer analyzer = new Analyzer(analysisEngine);
        try {
            // wait (max 10 s) until both engines are ready
            bot.init().get(10, TimeUnit.SECONDS);
            analyzer.init().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            botEngine.close();
            analysisEngine.close();
            showFatal("Engine hasn't responded: " + e);
            return;
        }
        //==================================================

        //==================================================
        // UI (Swing components must be created on the Swing thread)
        SwingUtilities.invokeLater(() -> {
            //-------------------------------------
            // Components
            GameModel model = new GameModel();
            BoardPanel board = new BoardPanel(model);
            EvalBar bar = new EvalBar();
            MoveListPanel list = new MoveListPanel();

            GameController controller = new GameController(model, bot, board, analyzer, bar, list);
            NavigationPanel nav = new NavigationPanel(
                    controller::first,
                    controller::prev,
                    controller::next,
                    controller::last,
                    controller::takeBack);

            board.setMoveHandler(controller::onPlayerMove);

            GameOptionPanel options = new GameOptionPanel(
                    (side, vsBot, elo) -> controller.newGame(side, vsBot, elo),
                    controller::setShowAnalysis);

            MainFrame frame = new MainFrame(options, board, bar, list, nav);
            frame.setVisible(true);

            //-------------------------------------
            // Debug: print every new evaluation of the current position to the console
            final Evaluation[] lastShown = { null };   // array: a lambda can't change a local variable

            Timer timer = new Timer(200, e -> {
                Evaluation ev = analyzer.latest();
                if (ev != null && ev.fen().equals(model.fen()) && ev != lastShown[0]) {
                    lastShown[0] = ev;
                    System.out.println("depth " + ev.depth()
                            + (ev.mate() ? "  mate " : "  cp ") + ev.value()
                            + "  best " + ev.bestMove());
                }
            });
            timer.start();
        });
        //==================================================
    }

    private static void showFatal(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE));
    }
}
