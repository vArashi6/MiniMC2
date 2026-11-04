import de.minibits.core.*;
import java.io.*;

public class IconTest {
    public static void main(String[] a) throws Exception {
        BitGrid g = new BitGrid();
        // kleine Figur: Sockel, Körper, Kopf, Arm
        g.fill(2,0,2,13,3,13,(short)1);
        g.fill(5,4,5,10,10,10,(short)2);
        g.fill(6,11,6,9,14,9,(short)3);
        g.fill(11,6,6,15,7,9,(short)2);
        int[] pal = {0, 0x7A7A7A, 0xA0522D, 0xE8C27A};
        int[] px = IconRenderer.render(g, pal);
        try (PrintWriter w = new PrintWriter("/tmp/icon.txt")) {
            for (int v : px) w.println(Integer.toHexString(v));
        }
        BitGrid full = new BitGrid(); full.fill(0,0,0,15,15,15,(short)1);
        int[] px2 = IconRenderer.render(full, pal);
        try (PrintWriter w = new PrintWriter("/tmp/icon2.txt")) { for (int v : px2) w.println(Integer.toHexString(v)); }
        System.out.println("ok");
    }
}
