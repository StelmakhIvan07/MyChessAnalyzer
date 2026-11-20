package chess.ui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    public MainFrame(BoardPanel board, EvalBar bar) {
        setTitle("Chess");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        add(bar, BorderLayout.WEST);
        add(board, BorderLayout.CENTER);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }
}
