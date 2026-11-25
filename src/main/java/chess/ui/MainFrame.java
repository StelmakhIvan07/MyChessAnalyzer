package chess.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Main window:  [options] [eval bar | board] [move list / navigation]
 */
public class MainFrame extends JFrame {
    public MainFrame(GameOptionPanel options, BoardPanel board, EvalBar bar, MoveListPanel list, NavigationPanel nav) {
        setTitle("Chess Analysis");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // BorderLayout: the bar keeps its 44 px width, the board gets the rest
        JPanel center = new JPanel(new BorderLayout());
        center.add(bar, BorderLayout.WEST);
        center.add(board, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.add(list, BorderLayout.CENTER);
        right.add(nav, BorderLayout.SOUTH);

        add(options, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }
}
