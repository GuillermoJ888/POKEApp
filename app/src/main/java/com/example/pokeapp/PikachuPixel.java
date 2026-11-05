package com.example.pokeapp;

import android.graphics.Bitmap;

/**
 * Pikachu en pixel art, corriendo en cuatro patas (para Pikachu Run). Cada letra es un pixel:
 * . transparente, K contorno, Y amarillo, O amarillo oscuro, R cachete, W brillo del ojo, B café.
 * Los cuadros los generó un script; se ven a la derecha, hacia donde corre.
 */
final class PikachuPixel {

    private PikachuPixel() { }

    static final String[] ESTIRADO = {
            "..KK........................",
            ".KYYKK..........KK....K.....",
            "..KYYYKKK.......KYK..KOK....",
            "...KYYYYYK.......KYKKKOK....",
            "....KYYYK.........KYYKOOK...",
            "...KYYKK...........KYYYYYK..",
            "..KYYYYK.KKKKKKKKKKYYYYYYYK.",
            "...KYYYYKYBBYBBYYYYYYYYKWYYK",
            "....KBBYYYBYYBYYYYYYYYYKKYYK",
            ".....KYYYYYYYYYYYYYYYYYYYYK.",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            ".....KOYYYYYYYYYYYYYYKKKK...",
            "....KOOYYYYYYYYYYYOYYYK.....",
            "...KOOYYYKKKKKKKKKOOYYYK....",
            "..KOOYYYK.........KOOYYYK...",
            ".KOOYYYK...........KOOYYYK..",
            "..KKKKK.............KKKKK..."
    };

    static final String[] MEDIO = {
            "..KK........................",
            ".KYYKK..........KK....K.....",
            "..KYYYKKK.......KYK..KOK....",
            "...KYYYYYK.......KYKKKOK....",
            "....KYYYK.........KYYKOOK...",
            "...KYYKK...........KYYYYYK..",
            "..KYYYYK.KKKKKKKKKKYYYYYYYK.",
            "...KYYYYKYBBYBBYYYYYYYYKWYYK",
            "....KBBYYYBYYBYYYYYYYYYKKYYK",
            ".....KYYYYYYYYYYYYYYYYYYYYK.",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            "......KYYYYYYYYYYYYYKKKKK...",
            "......KOOYYYYYYYYYYYK.......",
            ".....KOOYYYKKKKKOOYYYK......",
            ".....KOOYYK.....KOOYYK......",
            ".....KOOYYK.....KOOYYK......",
            "......KKKK.......KKKK......."
    };

    static final String[] RECOGIDO = {
            "..KK........................",
            ".KYYKK..........KK....K.....",
            "..KYYYKKK.......KYK..KOK....",
            "...KYYYYYK.......KYKKKOK....",
            "....KYYYK.........KYYKOOK...",
            "...KYYKK...........KYYYYYK..",
            "..KYYYYK.KKKKKKKKKKYYYYYYYK.",
            "...KYYYYKYBBYBBYYYYYYYYKWYYK",
            "....KBBYYYBYYBYYYYYYYYYKKYYK",
            ".....KYYYYYYYYYYYYYYYYYYYYK.",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            "......KYYYYYYYYYYYYYKKKKK...",
            ".......KOYYYYYYYYYYK........",
            ".......KOOYYYKOOYYYK........",
            "........KOOYYKOYYYK.........",
            ".........KOYYOOYYK..........",
            "..........KKKKKKK..........."
    };

    static final String[] SALTO = {
            "..KK........................",
            ".KYYKK..........KK....K.....",
            "..KYYYKKK.......KYK..KOK....",
            "...KYYYYYK.......KYKKKOK....",
            "....KYYYK.........KYYKOOK...",
            "...KYYKK...........KYYYYYK..",
            "..KYYYYK.KKKKKKKKKKYYYYYYYK.",
            "...KYYYYKYBBYBBYYYYYYYYKWYYK",
            "....KBBYYYBYYBYYYYYYYYYKKYYK",
            ".....KYYYYYYYYYYYYYYYYYYYYK.",
            ".....KYYYYYYYYYYYYYYYYRRYK..",
            "....KKYYYYYYYYYYYYYYYYRRYK..",
            "..KKOOOYYYYYYYYYYYYYYYYKK...",
            ".KOOOYYYYYYYYYYYYYOOYYYYYK..",
            "KOOOYYYYKKKKKKKKKKKKKOYYYYK.",
            ".KOKYYKK.............KKKKK..",
            "..K.KK......................",
            "............................"
    };

    static final String[] AGACHADO = {
            ".KK.......K..KK............",
            "KYYKK.....KKKYYKKKK........",
            ".KYYYKKK..KKYYYYYYYKKKKK...",
            "..KYYYYYK..KYYYYYYYYYYYYK..",
            "...KYYYK....KKYYYYYYYYYYYK.",
            "..KYYKK.......KKKYYYYYKWYYK",
            ".KYYYYK.KKKKKKKKKYYYYYKKYYK",
            "..KYYYYKYBBYBBYYYYYYYYYYYK.",
            "...KBBYYYBYYBYYYYYYYYRRYKYK",
            "....KYYYYYYYYYYYYYYYYRRYYK.",
            "....KYYYYYYYYYYYYYYYYYYKK..",
            "...KOYYYYYYYYYYYYYYYKKK....",
            "...KOOYYYYYYYYYYYYYYK......",
            "....KOOYYYYYYYYYYYYYK......"
    };

    /** Convierte un dibujo de letras en Bitmap (1 pixel por letra; se escala al dibujarlo sin suavizar). */
    static Bitmap bitmap(String[] filas) {
        int alto = filas.length, ancho = filas[0].length();
        int[] pixeles = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                pixeles[y * ancho + x] = color(filas[y].charAt(x));
            }
        }
        return Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888);
    }

    private static int color(char c) {
        switch (c) {
            case 'K': return 0xFF181818;
            case 'Y': return 0xFFFAD61D;
            case 'O': return 0xFFD6A014;
            case 'R': return 0xFFE83028;
            case 'W': return 0xFFFFFFFF;
            case 'B': return 0xFF7A4A20;
            default: return 0x00000000;
        }
    }
}
