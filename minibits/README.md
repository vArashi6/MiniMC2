# Mini Bits (Fabric, Minecraft 26.3) - ENTWURF

Nur die Mini-Blöcke aus Bits and Chisels: jeder Block besteht aus 16x16x16 Bits.

- Creative-Tab "Mini Bits": ein Bit-Item pro Vollblock.
- Rechtsklick: setzt ein Bit (auch in leere Nachbarblöcke).
- Shift + Rechtsklick: füllt einen ausgerichteten 4x4x4-Bereich.

Bauen: JDK 25, `gradle wrapper` bzw. Fabric-Template nutzen, dann `./gradlew build`.
JAR: build/libs/minibits-1.0.0.jar

## Ohne eigene Entwicklungsumgebung (GitHub Actions)
1. Kostenloses GitHub-Konto, neues Repository anlegen, den Inhalt dieses Ordners hochladen
   (Add file -> Upload files; der Ordner ".github" muss mit hoch).
2. Reiter "Actions" -> Lauf "build" öffnen. Nach 2-4 Minuten:
   - grün: unten bei "Artifacts" die Datei "minibits-jar" herunterladen (ZIP mit der JAR darin)
   - rot: den Fehlertext aus dem Schritt "gradle build" kopieren und an Claude schicken.
3. JAR in den Ordner mods (Fabric Loader 0.19.5+ und Fabric API für 26.3 nötig).
