package com.virtualinvestor.narrator;

import java.util.Optional;

/**
 * Disabled narrator (newsroom-narrator 2.1): declines everything, so the wire
 * carries bare headlines exactly as before the newsroom existed.
 */
public final class DisabledNarrator implements Narrator {

    @Override
    public Optional<Draft> narrate(Request request) {
        return Optional.empty();
    }
}
