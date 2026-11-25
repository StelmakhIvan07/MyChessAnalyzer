package chess.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/** Buttons « ‹ › » ↶ and the same actions on keys Home, Left, Right, End, Ctrl+Z. */
public class NavigationPanel extends JPanel {
    public NavigationPanel(Runnable onFirst, Runnable onPrev, Runnable onNext, Runnable onLast, Runnable onUndo) {
        super(new GridLayout(1, 5));
        add(button("«", onFirst));
        add(button("‹", onPrev));
        add(button("›", onNext));
        add(button("»", onLast));
        add(button("↶", onUndo));

        bind("HOME", onFirst);
        bind("LEFT", onPrev);
        bind("RIGHT", onNext);
        bind("END", onLast);
        bind("ctrl Z", onUndo);
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        b.setFocusable(false);

        return b;
    }

    /** Key works anywhere in the window (WHEN_IN_FOCUSED_WINDOW), not only when this panel has focus. */
    private void bind(String key, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
