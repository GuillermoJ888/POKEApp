package com.example.pokeapp.battle;

/** Registro de un turno: quién atacó, con qué ataque y cuánto daño hizo. */
public class BattleTurn {

    public final int numero;
    public final BattlePokemon atacante, defensor;
    public final Movimiento movimiento;
    public final String tipoMovimiento;   // null = sin tipo (Forcejeo)
    public final int danio;
    public final float efectividad;
    public final boolean critico;
    public final boolean fallo;           // el ataque no acertó
    public final int hpRestante;          // HP del defensor después del golpe
    public final boolean derrotado;

    BattleTurn(int numero, BattlePokemon atacante, BattlePokemon defensor, Movimiento movimiento,
               int danio, float efectividad, boolean critico, boolean fallo) {
        this.numero = numero;
        this.atacante = atacante;
        this.defensor = defensor;
        this.movimiento = movimiento;
        this.tipoMovimiento = movimiento.tipo;
        this.danio = danio;
        this.efectividad = efectividad;
        this.critico = critico;
        this.fallo = fallo;
        this.hpRestante = defensor.getHpActual();
        this.derrotado = defensor.estaDerrotado();
    }

    /** true si el ataque no le afecta al tipo del defensor (×0). */
    public boolean sinEfecto() {
        return !fallo && efectividad == 0f;
    }

    /**
     * Texto para la bitácora y el Historial:
     *   Turno 1
     *   Pikachu ataca con Rayo (tipo Eléctrico)
     *   Causa 18 puntos de daño · ¡Es súper eficaz!
     *   Charizard queda con 72 HP
     */
    public String descripcion() {
        StringBuilder sb = new StringBuilder();
        sb.append("Turno ").append(numero).append('\n');

        sb.append(atacante.nombre).append(" ataca con ").append(movimiento.nombre);
        if (tipoMovimiento != null) sb.append(" (tipo ").append(movimiento.tipoTexto()).append(")");
        sb.append('\n');

        if (fallo) {
            sb.append("¡Pero falló!");
        } else if (sinEfecto()) {
            sb.append("No le afecta a ").append(defensor.nombre);
        } else {
            sb.append("Causa ").append(danio).append(danio == 1 ? " punto" : " puntos").append(" de daño");
            if (critico) sb.append(" · ¡Golpe crítico!");
            if (efectividad >= 2f) sb.append(" · ¡Es súper eficaz!");
            else if (efectividad < 1f) sb.append(" · No es muy eficaz...");
        }
        sb.append('\n');

        sb.append(defensor.nombre).append(" queda con ").append(hpRestante).append(" HP");
        if (derrotado) sb.append(" · ¡Se debilitó!");

        return sb.toString();
    }
}
