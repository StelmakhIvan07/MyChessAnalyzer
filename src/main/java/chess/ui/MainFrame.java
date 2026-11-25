package chess.ui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    public MainFrame(BoardPanel board, EvalBar bar, MoveListPanel list, NavigationPanel nav) {
        setTitle("Chess Analysis");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        add(bar, BorderLayout.WEST);
        add(board, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.add(list, BorderLayout.CENTER);
        right.add(nav, BorderLayout.SOUTH);
        add(right, BorderLayout.EAST);

        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }
}
