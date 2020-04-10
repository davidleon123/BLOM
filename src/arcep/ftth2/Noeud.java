package arcep.ftth2;

import java.util.List;

public class Noeud extends NoeudAGarder {
        
    // en tant que PMint
    boolean isPMint;
    
    // champs techniques pour l'algo de placement des PM ext
    int lignesInfSeuil;
    int lignesSupSeuil;
    List<Double> distri;
    
    // en tant que PM ext 
    private int taillePM_ZMD = 0;
    private int taillePM_ZTD_BD = 0;
    private int taillePM_ZTD_HD = 0;
    

    public Noeud(String[] fields, int seuilPMint){
        this.id = Integer.parseInt(fields[0]);
        this.coord = new double[]{Double.parseDouble(fields[1]), Double.parseDouble(fields[2])};
        this.init();

        int placeIsPMint = 4+2*Integer.parseInt(fields[3]);
        for (int i = 4;i<placeIsPMint;i+=2){
            String zoneLue = fields[i];
            String zone = zoneLue.replace("_AMII","").replace("_RIP", "");
            int nbLignes = Integer.parseInt(fields[i+1]);
            this.addDemande(zone, nbLignes);
        }
        if (fields[placeIsPMint].equals("1") || this.getDemandeLocale("ZTD_HD") >= seuilPMint){
            this.isPMint = true;
        } else this.isPMint = false;
        if (this.hasDemandeLocale()){
            for (int i = placeIsPMint+2;i<fields.length;i++){
                this.addToPath(Long.parseLong(fields[i]));
            }
        }
    }
        
    public boolean decreaseDemande(String zone){
        if (zone.equals("ZTD")){
            int i = (int) Math.ceil(Math.random()*this.demandeLocaleTotale());
            if (i<=this.getDemandeLocale("ZTD_BD")){
                this.modifieDemande("ZTD_BD", -1);
            } else {
                this.modifieDemande("ZTD_HD", -1);
            }
        }
        else{
            this.modifieDemande("ZMD", -1);
        }
        return this.demandeLocaleTotale() == 0;
    }
    
    public void increaseDemande(String zone, int seuilPMint){
        if (zone.equals("ZTD")){
            int i = (int) Math.ceil(Math.random()*demandeLocaleTotale());
            if (i<=this.getDemandeLocale("ZTD_BD")){
                this.modifieDemande("ZTD_BD", 1);
            } else {
                this.modifieDemande("ZTD_HD", 1);
            } 
            if (!this.isPMint && this.getDemandeLocale("ZTD_HD") >= seuilPMint)
                this.isPMint = true;
        }
        else{
            this.modifieDemande("ZMD", 1);
        }
    }
    

    public int demandeLocaleExt(String zone){
        switch(zone){
            case "ZTD_HD":
                if (this.isPMint) return 0;
            default:
                return this.getDemandeLocale(zone);
        }
    }
    
    public boolean isPMint(){
        return this.isPMint;
    }
    
    public int demandeLocaleInt(){
        if (this.isPMint) return this.getDemandeLocale("ZTD_HD");
        else return 0;
    }

    // méthodes "de PM"
    
    public boolean isPMExt(String zone) {
        return taillePM(zone) > 0;
    }
    
    public int taillePM(String zone) {
        switch (zone) {
            case "ZMD":
                return taillePM_ZMD;
            case "ZTD_BD":
                return taillePM_ZTD_BD;
            default:
                return taillePM_ZTD_HD;
        }
    }

    public void setTaillePM(String zone, int n) {
        switch (zone) {
            case "ZMD":
                taillePM_ZMD = n;
                break;
            case "ZTD_BD":
                taillePM_ZTD_BD = n;
                break;
            case "ZTD_HD":
                taillePM_ZTD_HD = n;
                break;
        }
    }
    
}
