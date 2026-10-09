package com.example.pokeapp.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Motor de combate compartido por Battle Emulator, Battle Versus y Torre Pokémon.
 * Cada modo decide quién pelea; aquí solo viven las reglas, para que un cambio
 * en la fórmula se refleje en los tres.
 *
 * REGLAS DEL PROYECTO (fórmula simplificada)
 *
 *  1. Se usan los stats reales de la PokéAPI (HP, Ataque, Defensa y Velocidad).
 *  2. Orden: empieza el de mayor Velocidad (empate: al azar) y luego se alternan.
 *  3. Cada Pokémon tiene hasta 4 ataques reales de la PokéAPI (tipo, potencia y precisión).
 *     El jugador elige uno; el sistema elige el que más daño haría (a veces otro, para variar).
 *  4. Precisión: si el número al azar (0–99) es mayor o igual que la precisión, el ataque falla.
 *  5. Daño = 12 · (Ataque del atacante / Defensa del defensor) · (Potencia / 60)
 *           × 1.5  si el ataque es del mismo tipo que el atacante
 *           × efectividad de tipo (×4, ×2, ×1, ×0.5, ×0.25, ×0)
 *           × 1.5  si es golpe crítico (probabilidad = Velocidad / 512)
 *           × variación aleatoria entre 0.85 y 1.00
 *     Se redondea y el daño mínimo es 1 (si el ataque le afecta).
 *     Ejemplo: Pikachu (Atq 55) usa Rayo (Pot. 90) contra Charizard (Def 78)
 *              → 12 · 0.71 · 1.5 · 1.5 ≈ 19 de daño.
 *  6. Si ninguno de sus ataques le afecta al rival (×0), usa Forcejeo (sin tipo, Pot. 50).
 *  7. El HP nunca baja de 0; con 0 HP el Pokémon queda derrotado.
 */
public class BattleEngine {

    private static final float DANIO_BASE = 12f;
    private static final float POTENCIA_REFERENCIA = 60f;
    private static final float BONO_MISMO_TIPO = 1.5f;
    private static final float BONO_CRITICO = 1.5f;
    /** Probabilidad de que el sistema use un ataque distinto al mejor (para que no sea predecible). */
    private static final float VARIEDAD_IA = 0.25f;

    private final Random random;
    private final List<BattleTurn> turnos = new ArrayList<>();

    public BattleEngine() {
        this(new Random());
    }

    public BattleEngine(Random random) {
        this.random = random;
    }

    /** Regla 2: ataca primero el más rápido; si empatan, se sortea. */
    public BattlePokemon quienEmpieza(BattlePokemon a, BattlePokemon b) {
        if (a.velocidad != b.velocidad) return a.velocidad > b.velocidad ? a : b;
        return random.nextBoolean() ? a : b;
    }

    /** Ataque con el mejor movimiento (lo usa el sistema). */
    public BattleTurn atacar(BattlePokemon atacante, BattlePokemon defensor) {
        return atacar(atacante, defensor, elegirMovimiento(atacante, defensor));
    }

    /** Usa el movimiento indicado: revisa precisión, calcula el daño, lo aplica y registra el turno. */
    public BattleTurn atacar(BattlePokemon atacante, BattlePokemon defensor, Movimiento movimiento) {
        if (movimiento == null) movimiento = elegirMovimiento(atacante, defensor);

        float efectividad = TypeChart.efectividad(movimiento.tipo, defensor.tipos);

        // Regla 6: si nada de lo que sabe le afecta, Forcejeo
        if (efectividad == 0f && !tieneAtaqueUtil(atacante, defensor)) {
            movimiento = Movimiento.FORCEJEO;
            efectividad = 1f;
        }

        // Regla 4: precisión
        boolean fallo = movimiento.precision > 0 && random.nextInt(100) >= movimiento.precision;

        int danioFinal = 0;
        boolean critico = false;

        if (!fallo && efectividad > 0f) {
            // Regla 5: daño
            float danio = DANIO_BASE * atacante.ataque / (float) Math.max(1, defensor.defensa)
                    * (movimiento.potencia / POTENCIA_REFERENCIA);
            if (movimiento.tipo != null && atacante.tipos.contains(movimiento.tipo)) danio *= BONO_MISMO_TIPO;
            danio *= efectividad;

            critico = random.nextFloat() < atacante.velocidad / 512f;
            if (critico) danio *= BONO_CRITICO;

            danio *= 0.85f + random.nextFloat() * 0.15f;
            danioFinal = Math.max(1, Math.round(danio));
            defensor.recibirDanio(danioFinal);
        }

        BattleTurn turno = new BattleTurn(turnos.size() + 1, atacante, defensor,
                movimiento, danioFinal, efectividad, critico, fallo);
        turnos.add(turno);
        return turno;
    }

    /**
     * Regla 3 (sistema): el ataque que más daño esperado haría
     * (potencia × precisión × mismo tipo × efectividad); a veces otro que sí le afecte.
     */
    public Movimiento elegirMovimiento(BattlePokemon atacante, BattlePokemon defensor) {
        List<Movimiento> opciones = atacante.getMovimientos();
        Movimiento mejor = null;
        float mejorValor = -1f;
        List<Movimiento> utiles = new ArrayList<>();

        for (Movimiento m : opciones) {
            float valor = valorEsperado(m, atacante, defensor);
            if (valor > 0) utiles.add(m);
            if (valor > mejorValor) {
                mejorValor = valor;
                mejor = m;
            }
        }

        if (utiles.size() > 1 && random.nextFloat() < VARIEDAD_IA) {
            return utiles.get(random.nextInt(utiles.size()));
        }
        return mejor != null ? mejor : Movimiento.FORCEJEO;
    }

    private float valorEsperado(Movimiento m, BattlePokemon atacante, BattlePokemon defensor) {
        float valor = m.potencia * (m.precision > 0 ? m.precision / 100f : 1f);
        if (m.tipo != null && atacante.tipos.contains(m.tipo)) valor *= BONO_MISMO_TIPO;
        return valor * TypeChart.efectividad(m.tipo, defensor.tipos);
    }

    private boolean tieneAtaqueUtil(BattlePokemon atacante, BattlePokemon defensor) {
        for (Movimiento m : atacante.getMovimientos()) {
            if (TypeChart.efectividad(m.tipo, defensor.tipos) > 0f) return true;
        }
        return false;
    }

    public List<BattleTurn> getTurnos() {
        return Collections.unmodifiableList(turnos);
    }
}
