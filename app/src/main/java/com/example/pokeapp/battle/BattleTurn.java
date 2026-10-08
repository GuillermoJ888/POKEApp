package com.example.pokeapp.battle;

import com.example.pokeapp.data.TypeColors;

/** Registro de un turno: quién atacó, con qué tipo de movimiento y cuánto daño hizo. */
public class BattleTurn {

    public final int numero;
    public final BattlePokemon atacante, defensor;
    public final String tipoMovimiento;   // null = Forcejeo (sin tipo)
    public final int danio;
    public final float efectividad;
    public final boolean critico;
    public final int hpRestante;          // HP del defensor después del golpe
    public final boolean derrotado;

    BattleTurn(int numero, BattlePokemon atacante, BattlePokemon defensor, String tipoMovimiento,
               int danio, float efectividad, boolean critico) {
        this.numero = numero;
        this.atacante = atacante;
        this.defensor = defensor;
        this.tipoMovimiento = tipoMovimiento;
        this.danio = danio;
        this.efectividad = efectividad;
        this.critico = critico;
        this.hpRestante = defensor.getHpActual();
        this.derrotado = defensor.estaDerrotado();
    }

    /**
     * Texto para la bitácora y el Historial:
     *   Turno 1
     *   Pikachu ataca (tipo Eléctrico)
     *   Causa 18 puntos de daño
     *   Charizard queda con 72 HP
     */
    public String descripcion() {
        StringBuilder sb = new StringBuilder();
        sb.append("Turno ").append(numero).append('\n');

        sb.append(atacante.nombre).append(" ataca");
        if (tipoMovimiento == null) sb.append(" (Forcejeo)");
        else sb.append(" (tipo ").append(TypeColors.traducir(tipoMovimiento)).append(")");
        sb.append('\n');

        sb.append("Causa ").append(danio).append(danio == 1 ? " punto" : " puntos").append(" de daño");
        if (critico) sb.append(" · ¡Golpe crítico!");
        if (efectividad >= 2f) sb.append(" · ¡Es súper eficaz!");
        else if (efectividad < 1f) sb.append(" · No es muy eficaz...");
        sb.append('\n');

        sb.append(defensor.nombre).append(" queda con ").append(hpRestante).append(" HP");
        if (derrotado) sb.append(" · ¡Se debilitó!");

        return sb.toString();
    }
}
