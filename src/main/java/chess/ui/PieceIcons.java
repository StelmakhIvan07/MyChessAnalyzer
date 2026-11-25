package chess.ui;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

public class PieceIcons {
    private static final Map<Piece, SVGDocument> DOCS = new EnumMap<>(Piece.class);
    private final Map<Piece, BufferedImage> cache = new EnumMap<>(Piece.class);

    private int cachedSize = -1;


    private static String fileName(Piece p) {
        String fn;
        fn = (p.getPieceSide() == Side.WHITE) ? "w" : "b";

        switch(p.getPieceType()) {
            case BISHOP -> fn += "B";
            case KING -> fn += "K";
            case KNIGHT -> fn += "N";
            case PAWN -> fn += "P";
            case QUEEN -> fn += "Q";
            case ROOK -> fn += "R";
        }

        return fn;
    }

    static {
        SVGLoader loader = new SVGLoader();
        for (Piece p : Piece.values()) {
            if(p == Piece.NONE) continue;
            var url = PieceIcons.class.getResource("/pieces/" + fileName(p) + ".svg");

            if (url != null) {
                SVGDocument doc = loader.load(url);
                DOCS.put(p, doc);
            } else System.err.println("Piece icon not found: " + fileName(p));
        }
    }

    private static BufferedImage render(SVGDocument doc, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        doc.render(null, g, new ViewBox(0, 0, size, size));
        g.dispose();

        return img;
    }

    public BufferedImage get(Piece p, int size) {
        if(size <= 0) return null;
        if(size != cachedSize) cache.clear();
        cachedSize = size;

        return cache.computeIfAbsent(p, k -> {
            if(DOCS.containsKey(k)) return render(DOCS.get(k), size);
            else return null;
        });
    }
}
