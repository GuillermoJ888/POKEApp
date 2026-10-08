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
 *  3. Movimiento: del tipo del atacante que más le afecte al defensor.
 *     Si ninguno de sus tipos le afecta (×0), usa Forcejeo: sin tipo y ×1.
 *  4. Daño = 12 · (Ataque del atacante / Defensa del defensor)
 *           × 1.5  si el movimiento es de su mismo tipo (siempre, salvo Forcejeo)
 *           × efectividad de tipo (×4, ×2, ×1, ×0.5, ×0.25)
 *           × 1.5  si es golpe crítico (probabilidad = Velocidad / 512)
 *           × variación aleatoria entre 0.85 y 1.00
 *     Se redondea y el daño mínimo es 1.
 *     Ejemplo: Pikachu (Atq 55) contra Charizard (Def 78) → 12 · 0.71 · 1.5 ≈ 12 de daño.
 *  5. El HP nunca baja de 0; con 0 HP el Pokémon queda derrotado.
 */
public class BattleEngine {

    private static final float DANIO_BASE = 12f;
    private static final float BONO_MISMO_TIPO = 1.5f;
    private static final float BONO_CRITICO = 1.5f;

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

    /** Calcula el daño, lo aplica al defensor y registra el turno. */
    public BattleTurn atacar(BattlePokemon atacante, BattlePokemon defensor) {

        // Regla 3: el tipo del atacante que más le afecte al defensor
        String tipoMovimiento = null;
        float efectividad = 0f;
        for (String tipo : atacante.tipos) {
            float e = TypeChart.efectividad(tipo, defensor.tipos);
            if (tipoMovimiento == null || e > efectividad) {
                tipoMovimiento = tipo;
                efectividad = e;
            }
        }
        if (efectividad == 0f) {   // Forcejeo
            tipoMovimiento = null;
            efectividad = 1f;
        }

        // Regla 4: daño
        float danio = DANIO_BASE * atacante.ataque / (float) Math.max(1, defensor.defensa);
        if (tipoMovimiento != null) danio *= BONO_MISMO_TIPO;
        danio *= efectividad;

        boolean critico = random.nextFloat() < atacante.velocidad / 512f;
        if (critico) danio *= BONO_CRITICO;

        danio *= 0.85f + random.nextFloat() * 0.15f;

        int danioFinal = Math.max(1, Math.round(danio));
        defensor.recibirDanio(danioFinal);

        BattleTurn turno = new BattleTurn(turnos.size() + 1, atacante, defensor,
                tipoMovimiento, danioFinal, efectividad, critico);
        turnos.add(turno);
        return turno;
    }

    public List<BattleTurn> getTurnos() {
        return Collections.unmodifiableList(turnos);
    }
}
