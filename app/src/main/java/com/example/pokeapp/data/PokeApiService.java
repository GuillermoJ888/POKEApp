package com.example.pokeapp.data;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface PokeApiService {

    @GET("pokemon/{query}")
    Call<Pokemon> getPokemon(@Path("query") String query);

    @GET("pokemon-species/{query}")
    Call<PokemonSpecies> getPokemonSpecies(@Path("query") String query);

    @GET("evolution-chain/{id}")
    Call<EvolutionChainResponse> getEvolutionChain(@Path("id") String id);

    /** Detalle de un ataque: tipo, potencia, precisión y nombres en otros idiomas. */
    @GET("move/{nombre}")
    Call<MoveDetalle> getMove(@Path("nombre") String nombre);

    /** Lista paginada de Pokémon (nombre + url). */
    @GET("pokemon")
    Call<PokemonListResponse> listarPokemon(@Query("limit") int limit, @Query("offset") int offset);
}
