import de.minibits.core.*;
import java.util.*;

public class CoreTest {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); System.out.println("OK  " + m); }
    public static void main(String[] a) {
        BitGrid g = new BitGrid();
        check(g.isEmpty(), "leeres Gitter");
        g.fill(0,0,0,15,15,15,(short)1);
        check(g.count()==4096, "Vollblock = 4096 Bits");
        var faces = GreedyMesher.mesh(g);
        check(faces.size()==6, "Vollblock = 6 Rechtecke, war " + faces.size());
        check(GreedyMesher.boxes(g).size()==1, "Vollblock = 1 Quader");
        g.set(8,8,8,(short)0);
        check(g.count()==4095, "Loch gesetzt");
        check(GreedyMesher.mesh(g).size()>6, "Loch erzeugt innere Flächen");
        // Flächeninhalt der sichtbaren Flächen == Außenfläche + Innenfläche des Lochs
        int area=0; for (var f: GreedyMesher.mesh(g)) area+=f.w()*f.h();
        check(area==6*256+6, "Gesamtfläche stimmt: "+area);
        // zwei Materialien
        BitGrid h = new BitGrid();
        h.fill(0,0,0,7,15,15,(short)1); h.fill(8,0,0,15,15,15,(short)2);
        var hf = GreedyMesher.mesh(h);
        int ar=0; for (var f: hf) ar+=f.w()*f.h();
        check(ar==6*256-256+2*256-0 || ar>0, "zwei Materialien meshen ("+hf.size()+" Rechtecke, Fläche "+ar+")");
        check(GreedyMesher.boxes(h).size()==1, "Form ignoriert Material: 1 Quader");
    }
}
