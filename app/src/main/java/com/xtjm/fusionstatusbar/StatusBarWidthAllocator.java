package com.xtjm.fusionstatusbar;

/** Proportionally fits optional readouts into space left by native status elements. */
final class StatusBarWidthAllocator {
    private StatusBarWidthAllocator() {
    }

    static int[] allocate(int availableWidth, int[] preferredWidths) {
        int[] result = new int[preferredWidths == null ? 0 : preferredWidths.length];
        allocateInto(availableWidth, preferredWidths, result.length, result,
                new long[result.length]);
        return result;
    }

    /** Fills caller-owned buffers for layout hot paths where allocation matters. */
    static void allocateInto(int availableWidth, int[] preferredWidths, int count,
            int[] result, long[] remainders) {
        if (result == null || preferredWidths == null) {
            return;
        }
        int length = Math.min(Math.max(0, count),
                Math.min(preferredWidths.length, result.length));
        for (int i = 0; i < length; i++) {
            result[i] = 0;
            if (remainders != null && i < remainders.length) {
                remainders[i] = 0;
            }
        }
        if (availableWidth <= 0 || length == 0) {
            return;
        }
        long total = 0;
        for (int i = 0; i < length; i++) {
            total += Math.max(0, preferredWidths[i]);
        }
        if (total <= availableWidth) {
            for (int i = 0; i < length; i++) {
                result[i] = Math.max(0, preferredWidths[i]);
            }
            return;
        }

        if (remainders == null || remainders.length < length) {
            return;
        }
        int assigned = 0;
        for (int i = 0; i < length; i++) {
            long scaled = (long) Math.max(0, preferredWidths[i]) * availableWidth;
            result[i] = (int) (scaled / total);
            remainders[i] = scaled % total;
            assigned += result[i];
        }
        while (assigned < availableWidth) {
            int best = -1;
            for (int i = 0; i < length; i++) {
                if (remainders[i] > 0
                        && (best < 0 || remainders[i] > remainders[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            result[best]++;
            remainders[best] = -1;
            assigned++;
        }
        return;
    }
}
