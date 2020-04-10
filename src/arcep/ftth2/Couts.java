
package arcep.ftth2;

public class Couts {
    
    private int nbLignes;
    private final double[] couts;
    
    public Couts(){
        nbLignes = 0;
        couts = new double[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    }
    
    public Couts(int nbLignes, double cables_a, double cables_s, double boitiers_a, double boitiers_s, double pbo_aerien, double pbo_souterrain, double GCReconstruit_a, double GCReconstruit_s, double coutVertical_PBO, double coutVertical_cables, double coutPM_int, double coutPM_ext, double coutNRO, double etudes){
        this.nbLignes = nbLignes;
        couts = new double[14];
        couts[0] = cables_a;
        couts[1] = cables_s;
        couts[2] = boitiers_a;
        couts[3] = boitiers_s;
        couts[4] = pbo_aerien;
        couts[5] = pbo_souterrain;
        couts[6] = GCReconstruit_a;
        couts[7] = GCReconstruit_s;
        couts[8] = coutVertical_PBO;
        couts[9] = coutVertical_cables;
        couts[10] = coutPM_int;
        couts[11] = coutPM_ext;
        couts[12] = coutNRO;
        couts[13] = etudes;
    }
    
    public void add(Couts c){
        this.nbLignes+=c.nbLignes;
        for (int i = 0;i<this.couts.length;i++){
            this.couts[i]+=c.couts[i];
        }
    }
    
    public static String header(){
        return "Lignes;Cout Cables aeriens;Cout Cables souterrains;Cout Boitiers aeriens;Cout Boitiers souterrains;Cout PBO ext aeriens;Cout PBO ext souterrains;Cout GC reconstruit aerien;Cout GC reconstruit souterrain;Cout Vertical PBint;Cout Vertical cables;Cout PM interieur;Cout PM exterieur;Cout NRO;Cout etudes avant pose";
    }
    
    public String print(){
        String s = String.valueOf(nbLignes);
        for (double c : couts){
            s+=";"+String.valueOf(c).replace(".", ",");
        }
        return s;
    }

}
