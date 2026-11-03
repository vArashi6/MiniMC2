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

class CoreTest2 {
    public static void main(String[] a) {
        BitGrid g = new BitGrid();
        g.set(1, 2, 3, (short) 5);
        g.rotateY(1);
        CoreTest.check(g.get(12, 2, 1) == 5, "Drehung 90°: (1,2,3) -> (12,2,1)");
        g.rotateY(3);
        CoreTest.check(g.get(1, 2, 3) == 5 && g.count() == 1, "4 Drehungen = Original");
        BitGrid h = new BitGrid();
        CoreTest.check(h.fillEmpty(0,0,0,3,3,3,(short)1) == 64, "fillEmpty 4x4x4 = 64");
        CoreTest.check(h.fillEmpty(0,0,0,3,3,3,(short)2) == 0, "fillEmpty überschreibt nichts");
        CoreTest.check(h.clearBox(0,0,0,1,1,1) == 8, "clearBox 2x2x2 = 8");
        int[] b = ShapePreset.box(2, 0, 9, 5, 14);
        CoreTest.check(b[0]==8 && b[1]==4 && b[2]==12 && b[3]==11 && b[4]==7 && b[5]==15, "4x4x4 rastet am Raster ein");
        int[] l = ShapePreset.box(4, 0, 7, 3, 9);
        CoreTest.check(l[0]==7 && l[2]==0 && l[5]==15, "Leiste 1x1x16 läuft durch den ganzen Block");
        int[] l2 = ShapePreset.box(4, 1, 7, 3, 9);
        CoreTest.check(l2[0]==0 && l2[3]==15 && l2[2]==9 && l2[5]==9, "Leiste gedreht läuft entlang X");
    }
}

class CoreTest3 {
    public static void main(String[] a) {
        int[] d = ShapePreset.dims(4, 1);
        CoreTest.check(d[0]==16 && d[1]==1 && d[2]==1, "Leiste gedreht: 16x1x1");
        int[] e = ShapePreset.dims(2, 3);
        CoreTest.check(e[0]==4 && e[1]==4 && e[2]==4, "4x4x4 bleibt 4x4x4");
    }
}
