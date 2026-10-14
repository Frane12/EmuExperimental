package com.drnas.console;

/**
 * Monotonic, evidence-based startup phase indicator. Percentages represent
 * completed initialization milestones, NOT elapsed time or an ETA.
 * Never reports 100% before Wineboot verified successfully.
 */
final class WineBootProgress {
    private int percent;
    private String phase="Wine nije pokrenut";

    int percent(){return percent;}
    String phase(){return phase;}

    void begin(){
        percent=3;
        phase="Priprema Windowsa";
    }

    boolean advance(String message){
        String s=message.toLowerCase(java.util.Locale.ROOT);
        int next=0;
        String label=null;
        if(s.contains("ensuring embedded")) {next=5;label="Provjera komponenti";}
        else if(s.contains("[bundle] installing proton")) {next=8;label="Instalacija Protona";}
        else if(s.contains("[bundle] proton verified") || s.contains("[bundle] proton already installed")) {next=20;label="Proton spreman";}
        else if(s.contains("fex pe bridge verified")) {next=26;label="Provjera FEX-a";}
        else if(s.contains("dxvk 1.10.3 staged")) {next=35;label="Priprema DXVK-a";}
        else if(s.contains("proton 11") && s.contains("staged")) {next=50;label="Priprema Wine datoteka";}
        else if(s.contains("wine prefix staged")) {next=61;label="Priprema Windows okruženja";}
        else if(s.contains("boot plan written")) {next=70;label="Provjera izvršavanja";}
        else if(s.contains("wine --version exited 0")) {next=79;label="Wine pokrenut";}
        else if(s.contains("starting isolated wineboot")) {next=83;label="Inicijalizacija Winea";}
        else if(s.contains("gui runtime verified")) {next=87;label="Učitavanje grafičkih biblioteka";}
        else if(s.contains("wineserver preflight status")) {next=92;label="Pokretanje Wine servera";}
        else if(s.contains("wineboot completion + independent")) {next=97;label="Završna provjera";}
        if(next>percent) {
            percent=next;
            phase=label;
            return true;
        }
        return false;
    }

    void finish(boolean success){
        if(success) {
            percent=100;
            phase="Wine je spreman";
        } else {
            // An error must never make a progress indicator claim 100%.
            percent=Math.min(percent,97);
            phase="Inicijalizacija nije uspjela";
        }
    }
}
