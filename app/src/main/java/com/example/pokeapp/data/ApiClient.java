package com.example.pokeapp.data;

import java.io.File;

import okhttp3.Cache;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String BASE_URL = "https://pokeapi.co/api/v2/";

    private static Retrofit retrofit;
    private static File carpetaCache;

    /**
     * Guarda en el celular las respuestas de la PokéAPI (20 MB) para no volver a descargarlas.
     * Lo llama PokeApp al abrir la app, antes de la primera consulta.
     */
    public static void activarCache(File carpeta) {
        carpetaCache = carpeta;
    }

    public static PokeApiService getService() {

        if (retrofit == null) {
            // Para elegir los ataques se piden muchos /move/{nombre} seguidos: se permiten más a la vez
            Dispatcher despachador = new Dispatcher();
            despachador.setMaxRequests(64);
            despachador.setMaxRequestsPerHost(32);

            OkHttpClient.Builder cliente = new OkHttpClient.Builder().dispatcher(despachador);
            if (carpetaCache != null) {
                cliente.cache(new Cache(new File(carpetaCache, "pokeapi"), 20L * 1024 * 1024));
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(cliente.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(PokeApiService.class);
    }
}
