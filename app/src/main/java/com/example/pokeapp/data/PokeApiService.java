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

    /** Lista paginada de Pokémon (nombre + url). */
    @GET("pokemon")
    Call<PokemonListResponse> listarPokemon(@Query("limit") int limit, @Query("offset") int offset);
}
