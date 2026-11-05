package com.example.pokeapp.battle;

import com.example.pokeapp.data.Idioma;

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

    /**
     * Batalla entre dispositivos (invitado): turno que ya calculó el anfitrión.
     * El HP del defensor debe estar ya actualizado (BattlePokemon.fijarHp).
     */
    public static BattleTurn remoto(int numero, BattlePokemon atacante, BattlePokemon defensor,
                                    Movimiento movimiento, int danio, float efectividad,
                                    boolean critico, boolean fallo) {
        return new BattleTurn(numero, atacante, defensor, movimiento, danio, efectividad, critico, fallo);
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
        sb.append(Idioma.t("Turno ", "Turn ")).append(numero).append('\n');

        sb.append(atacante.nombre).append(Idioma.t(" ataca con ", " attacks with ")).append(movimiento.nombre);
        if (tipoMovimiento != null) sb.append(Idioma.t(" (tipo ", " (")).append(movimiento.tipoTexto()).append(")");
        sb.append('\n');

        if (fallo) {
            sb.append(Idioma.t("¡Pero falló!", "But it missed!"));
        } else if (sinEfecto()) {
            sb.append(Idioma.t("No le afecta a ", "It doesn't affect ")).append(defensor.nombre);
        } else {
            sb.append(Idioma.t("Causa ", "Deals ")).append(danio).append(danio == 1 ? Idioma.t(" punto", " point") : Idioma.t(" puntos", " points")).append(Idioma.t(" de daño", " of damage"));
            if (critico) sb.append(Idioma.t(" · ¡Golpe crítico!", " · Critical hit!"));
            if (efectividad >= 2f) sb.append(Idioma.t(" · ¡Es súper eficaz!", " · It's super effective!"));
            else if (efectividad < 1f) sb.append(Idioma.t(" · No es muy eficaz...", " · It's not very effective..."));
        }
        sb.append('\n');

        sb.append(defensor.nombre).append(Idioma.t(" queda con ", " is left with ")).append(hpRestante).append(" HP");
        if (derrotado) sb.append(Idioma.t(" · ¡Se debilitó!", " · It fainted!"));

        return sb.toString();
    }
}
