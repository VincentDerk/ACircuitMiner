package com.vincentderk.acircuitminer.miner.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 *
 * @version 2.0
 */
public class Misc {

    /**
     * Prints some information about the used memory.
     */
    public static void printMemory() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        pools.forEach((pool) -> {
            MemoryUsage peak = pool.getPeakUsage();
            System.out.println(String.format("Peak %s memory used: %,d", pool.getName(), peak.getUsed()));
            System.out.println(String.format("Peak %s memory reserved: %,d", pool.getName(), peak.getCommitted()));
        });
    }

}
