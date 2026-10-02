package com.virtualinvestor.core.bot;

/**
 * Slices a large parent order into child quantities executed over time, the way an institution
 * works a block to limit market impact. Deterministic: the slice sizes are fixed up front.
 */
public final class VwapExecution {

    private final long[] slices;
    private int index;

    public VwapExecution(long totalQuantity, int sliceCount) {
        if (totalQuantity <= 0L) {
            throw new IllegalArgumentException("totalQuantity must be positive");
        }
        if (sliceCount <= 0) {
            throw new IllegalArgumentException("sliceCount must be positive");
        }
        this.slices = new long[sliceCount];
        long base = totalQuantity / sliceCount;
        long remainder = totalQuantity % sliceCount;
        for (int i = 0; i < sliceCount; i++) {
            slices[i] = base + (i < remainder ? 1L : 0L);
        }
    }

    /** The next child quantity, or 0 when the parent is fully worked. */
    public long nextSliceQuantity() {
        if (index >= slices.length) {
            return 0L;
        }
        return slices[index++];
    }

    public int sliceCount() {
        return slices.length;
    }

    public boolean complete() {
        return index >= slices.length;
    }
}
