package com.drnas.console;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded evidence gathered before the UI filter; no guest success from exit alone. */
final class WineBootEvidence {
    private boolean completed;
    private int errors;
    private final ArrayDeque<String> tail=new ArrayDeque<>();

    synchronized void observe(String line) {
        // Wineboot's main() emits this immediately before SetEvent and return 0.
        if(line.matches("^[0-9a-fA-F]+:trace:wineboot:main Operation done\\s*$"))
            completed=true;
        if(line.contains(":err:") || line.contains("Unhandled exception")) errors++;
        if(tail.size()==12)tail.removeFirst();
        tail.addLast(line.substring(0,Math.min(line.length(),480)));
    }
    synchronized boolean completed(){return completed;}
    synchronized int errors(){return errors;}
    synchronized List<String> tail(){return new ArrayList<>(tail);}

    static String classify(boolean ended,int exit,boolean complete,
                           boolean guestVerified,int registries,boolean changed) {
        if(!ended)return "WINEBOOT_TIMEOUT";
        if(exit!=0)return exit==127?"WINEBOOT_EARLY_EXIT_127":"WINEBOOT_EXIT_"+exit;
        if(!complete)return "WINEBOOT_EXIT_0_COMPLETION_UNPROVEN";
        if(!guestVerified)return "WINEBOOT_COMPLETED_CMD_UNPROVEN";
        if(registries<2)return "WINEBOOT_EXIT_0_REGISTRY_INCOMPLETE";
        // Persisted mutation and successful guest execution are separate facts.
        return changed?"WINEBOOT_PREFIX_INITIALIZED":"WINEBOOT_COMPLETED_GUEST_VERIFIED";
    }
}
