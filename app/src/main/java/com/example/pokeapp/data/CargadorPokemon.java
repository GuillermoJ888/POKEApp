package com.example.pokeapp.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Carga varios Pokémon de la PokéAPI a la vez y genera IDs al azar (normales o legendarios). */
public class CargadorPokemon {

    public static final int ID_MAXIMO = 1025;

    /** Legendarios y singulares usados por el jefe final de la Torre. */
    public static final List<Integer> LEGENDARIOS = Arrays.asList(
            144, 145, 146, 150, 151, 243, 244, 245, 249, 250, 251,
            377, 378, 379, 380, 381, 382, 383, 384, 385, 386,
            480, 481, 482, 483, 484, 485, 486, 487, 488, 491, 492, 493,
            638, 639, 640, 641, 642, 643, 644, 645, 646,
            716, 717, 718, 785, 786, 787, 788, 789, 790, 791, 792, 800,
            888, 889, 890, 891, 892, 894, 895, 896, 897, 898,
            1001, 1002, 1003, 1004, 1007, 1008, 1014, 1015, 1016, 1017, 1024);

    public interface Listo {
        /** Lista en el mismo orden que los IDs, o null si alguno falló. */
        void alTerminar(List<Pokemon> pokemon);
    }

    /** n IDs distintos al azar: solo legendarios, o solo Pokémon que NO son legendarios. */
    /**
     * Compañeros que se pueden elegir en Mi perfil: Pikachu y Raichu, y los iniciales de todas las regiones
     * con sus evoluciones (cada línea en orden: básico, 1.ª y 2.ª evolución).
     */
    public static final List<Integer> COMPANEROS = Arrays.asList(
            25, 26,
            1, 2, 3, 4, 5, 6, 7, 8, 9,
            152, 153, 154, 155, 156, 157, 158, 159, 160,
            252, 253, 254, 255, 256, 257, 258, 259, 260,
            387, 388, 389, 390, 391, 392, 393, 394, 395,
            495, 496, 497, 498, 499, 500, 501, 502, 503,
            650, 651, 652, 653, 654, 655, 656, 657, 658,
            722, 723, 724, 725, 726, 727, 728, 729, 730,
            810, 811, 812, 813, 814, 815, 816, 817, 818,
            906, 907, 908, 909, 910, 911, 912, 913, 914);

    /** Nombres de los compañeros, en el mismo orden que COMPANEROS. */
    public static final List<String> NOMBRES_COMPANEROS = Arrays.asList(
            "Pikachu", "Raichu",
            "Bulbasaur", "Ivysaur", "Venusaur", "Charmander", "Charmeleon", "Charizard", "Squirtle", "Wartortle", "Blastoise",
            "Chikorita", "Bayleef", "Meganium", "Cyndaquil", "Quilava", "Typhlosion", "Totodile", "Croconaw", "Feraligatr",
            "Treecko", "Grovyle", "Sceptile", "Torchic", "Combusken", "Blaziken", "Mudkip", "Marshtomp", "Swampert",
            "Turtwig", "Grotle", "Torterra", "Chimchar", "Monferno", "Infernape", "Piplup", "Prinplup", "Empoleon",
            "Snivy", "Servine", "Serperior", "Tepig", "Pignite", "Emboar", "Oshawott", "Dewott", "Samurott",
            "Chespin", "Quilladin", "Chesnaught", "Fennekin", "Braixen", "Delphox", "Froakie", "Frogadier", "Greninja",
            "Rowlet", "Dartrix", "Decidueye", "Litten", "Torracat", "Incineroar", "Popplio", "Brionne", "Primarina",
            "Grookey", "Thwackey", "Rillaboom", "Scorbunny", "Raboot", "Cinderace", "Sobble", "Drizzile", "Inteleon",
            "Sprigatito", "Floragato", "Meowscarada", "Fuecoco", "Crocalor", "Skeledirge", "Quaxly", "Quaxwell", "Quaquaval");

    /**
     * Pokémon fuertes que NO son legendarios: los pseudolegendarios (Dragonite, Tyranitar, Salamence,
     * Metagross, Garchomp, Hydreigon, Goodra, Kommo-o, Dragapult, Baxcalibur) y otros de stats altos.
     */
    public static final List<Integer> FUERTES = Arrays.asList(
            149, 248, 373, 376, 445, 635, 706, 784, 887, 998,
            6, 59, 65, 68, 94, 130, 131, 143, 212, 230, 257, 282, 306, 330, 350,
            448, 464, 466, 467, 468, 473, 530, 612, 637, 658, 681, 823);

    /**
     * n Pokémon al azar, de los que al menos "fuertes" salen de la lista FUERTES. Cada uno de los
     * demás tiene una probabilidad pequeña (0 a 100) de ser legendario; si no, es uno común al azar.
     * Nunca salen los que se pueden elegir como compañero (COMPANEROS).
     */
    public static List<Integer> idsConFuertes(int n, int fuertes, int probabilidadLegendario, Random random) {
        Set<Integer> elegidos = new HashSet<>();
        while (elegidos.size() < Math.min(fuertes, n)) {
            int id = FUERTES.get(random.nextInt(FUERTES.size()));
            if (!COMPANEROS.contains(id)) elegidos.add(id);
        }
        Set<Integer> legendariosSet = new HashSet<>(LEGENDARIOS);
        while (elegidos.size() < n) {
            if (random.nextInt(100) < probabilidadLegendario) {
                elegidos.add(LEGENDARIOS.get(random.nextInt(LEGENDARIOS.size())));
            } else {
                int id = 1 + random.nextInt(ID_MAXIMO);
                if (!legendariosSet.contains(id) && !COMPANEROS.contains(id)) elegidos.add(id);
            }
        }
        List<Integer> resultado = new ArrayList<>(elegidos);
        Collections.shuffle(resultado, random);   // que los fuertes no salgan siempre en el mismo lugar
        return resultado;
    }

    public static List<Integer> idsAleatorios(int n, boolean legendarios, Random random) {
        Set<Integer> elegidos = new HashSet<>();
        List<Integer> resultado = new ArrayList<>();
        Set<Integer> legendariosSet = new HashSet<>(LEGENDARIOS);

        while (resultado.size() < n) {
            int id = legendarios
                    ? LEGENDARIOS.get(random.nextInt(LEGENDARIOS.size()))
                    : 1 + random.nextInt(ID_MAXIMO);
            if (!legendarios && legendariosSet.contains(id)) continue;
            if (elegidos.add(id)) resultado.add(id);
        }
        return resultado;
    }

    /** Pide todos los Pokémon en paralelo y avisa una sola vez cuando terminan. */
    public static void cargar(List<Integer> ids, Listo listo) {
        Pokemon[] resultado = new Pokemon[ids.size()];
        int[] pendientes = {ids.size()};
        boolean[] fallo = {false};

        if (ids.isEmpty()) {
            listo.alTerminar(new ArrayList<>());
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            final int posicion = i;
            ApiClient.getService().getPokemon(String.valueOf(ids.get(i))).enqueue(new Callback<Pokemon>() {
                @Override
                public void onResponse(Call<Pokemon> call, Response<Pokemon> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        resultado[posicion] = response.body();
                    } else {
                        fallo[0] = true;
                    }
                    terminarUno();
                }

                @Override
                public void onFailure(Call<Pokemon> call, Throwable t) {
                    fallo[0] = true;
                    terminarUno();
                }

                private void terminarUno() {
                    pendientes[0]--;
                    if (pendientes[0] == 0) {
                        listo.alTerminar(fallo[0] ? null : new ArrayList<>(Arrays.asList(resultado)));
                    }
                }
            });
        }
    }
}
