package chess;

import chess.engine.*;
import chess.model.GameModel;
import chess.ui.*;
import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Path ENGINE_PATH = Path.of("stockfish", "stockfish.exe");

    public static void main(String[] args) {
        PipeEngine botEngine;
        PipeEngine analysisEngine;

        try {
            botEngine =      new PipeEngine(ENGINE_PATH);
            analysisEngine = new PipeEngine(ENGINE_PATH);

            Runtime.getRuntime().addShutdownHook(new Thread(botEngine::close));
            Runtime.getRuntime().addShutdownHook(new Thread(analysisEngine::close));
        } catch (
                IOException e) {
            showFatal(e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(botEngine::close));

        Bot bot = new Bot(botEngine);
        Analyzer analyzer = new Analyzer(analysisEngine);
        try {
            bot.init().get(10, TimeUnit.SECONDS);
            analyzer.init().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            botEngine.close();
            analysisEngine.close();
            showFatal("Engine hasn't responded: " + e);
            return;
        }

        SwingUtilities.invokeLater(() -> {
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

            controller.newGame(Side.BLACK, 1500);

            MainFrame frame = new MainFrame(board, bar, list, nav);
            frame.setVisible(true);

            final Evaluation[] lastShown = { null };

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
    }

    private static void showFatal(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE));
    }
}
