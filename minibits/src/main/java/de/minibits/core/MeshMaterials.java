package de.minibits.core;

/** Eigenschaften der Paletten-Materialien fürs Meshing. Index 1-basiert wie im BitGrid. */
public record MeshMaterials(int[] kind, int[] id) {
    /** Verdeckt angrenzende Flächen komplett (normale, undurchsichtige Blöcke). */
    public static final int OPAQUE = 0;
    /** Verdeckt nur gleiche Nachbarn (Glas, Eis, Schleim). */
    public static final int SAME_ONLY = 1;
    /** Verdeckt nichts (Gitter, Blätter, andere Ausschnitt-Texturen). */
    public static final int NONE = 2;
}
