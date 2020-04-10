
package arcep.ftth2;

public class PC extends PointReseau {
    
    String nra;
    int lignes;
    long parcelle = 0;
    boolean PM_int;
    String changementSR;

    public PC() {}

    public PC(String identifiant, double x, double y, PointReseau pere, String nra, int lignes, String zone, boolean galerieVisitable) {
        // champs hérités de PointReseau
        this.init(identifiant, x, y, "PC", zone);
        this.pere = pere;
        this.changementNRA = "FAUX";
        
        //champs propres
        this.nra = nra;
        this.lignes = lignes;
        
        //this.parcelle = parcelle;
        this.changementSR = "FAUX";
        this.PM_int = zone.equals("ZTD_HD") && (galerieVisitable || lignes >= 12); // supprimer ligne >=12 quand ce sera pris en compte par le module Reseau

    }

    public void combinaison(PC pr) {
        int sommeLignes = this.lignes + pr.lignes;
        this.x = (this.x*this.lignes + pr.x*pr.lignes)/sommeLignes;
        this.y = (this.y*this.lignes + pr.y*pr.lignes)/sommeLignes;
        this.lignes = sommeLignes;
    }

}
