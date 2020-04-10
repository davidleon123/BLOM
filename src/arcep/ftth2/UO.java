
package arcep.ftth2;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class UO {

    private final String zone;
    
    private final boolean isDistri;
    private final boolean isTransport;
    
    /// UO si isDistri
    private int nbLignesExt = 0;
    private int nbLignesInt = 0;
    private final Lineaires linPMPBO;
    
    private int nbPMext = 0;
    // nb armoires de PM ext
    private int nbPM_ZMD = 0;
    private int nbPM_ZTD_BD = 0;
    private int nbPM_ZTD_HD = 0;
    
    private final int[] nbPBO;
   
    /// UO si isTransport
    private final Lineaires linNROPM;

    // PM int en ZTD_HD
    private int nbPMint = 0;
    private int nbArmoiresPMint = 0;
    
    // au niveau du NRO
    private int nbNRO = 0;
    private int nbTiroirsOptiques = 0;
    private int nbRTO = 0;
    private double surfaceNRO = 0;

    // partie verticale de la distribution calculée à la maille du NRO
    private int nbImmeubles = 0;
    private int nbPBOint = 0;
    private int nbLogementsImmeubles = 0;
    
    
    public UO(String zone, boolean isDistri, boolean isTransport, Parametres parametres){
        this.zone = zone;
        this.isDistri = isDistri;
        if (isDistri & !isTransport) this.nbPMext = 1;
        
        this.isTransport = isTransport;
        linPMPBO = new Lineaires(parametres);
        linNROPM = new Lineaires(parametres);
        nbPBO = new int[15];
    }
    
    public void setNbLignes(int nbLignes, Parametres parametres){
        // utilisé dans le cas isDistri
        this.nbLignesExt = nbLignes;
        int nbPM = (int) Math.ceil(nbLignes/ (double) parametres.getNbMaxLignesPM(zone));
        switch(zone){
            case "ZTD_HD":
                this.nbPM_ZTD_HD += nbPM;
                break;
            case "ZTD_BD":
                this.nbPM_ZTD_BD += nbPM;
                break;
            default:
                this.nbPM_ZMD += nbPM;
        }
    }
    
    public int getNbLignes(){
        return this.nbLignesExt+this.nbLignesInt;
    }
    
    public void addLineaires(AreteBLOM a, Parametres parametres){ // attention calcul spécifique à une zone
        if (isTransport){
            this.linNROPM.addCablesEtBoitiers(a, parametres, false, zone);
            this.linNROPM.addGC(a.longueur, a.getSection(false, zone), a.getSectionTotale(), a.modePose);
        }else{
            this.linPMPBO.addCablesEtBoitiers(a, parametres, true, zone);
            this.linPMPBO.addGC(a.longueur, a.getSection(true, zone), a.getSectionTotale(), a.modePose);
        }
    }
    
    public void addPBO(int nbPBO, int modePose){
        this.nbPBO[modePose] += nbPBO;
    }
    
    public void addUO(UO uo){
        this.nbLignesExt += uo.nbLignesExt;
        this.nbLignesInt += uo.nbLignesInt;
        this.linPMPBO.addLineaires(uo.linPMPBO);
        
        for (int i = 0;i<this.nbPBO.length;i++){
            this.nbPBO[i]+=uo.nbPBO[i];
        }
        
        this.nbPMext += uo.nbPMext;
        this.nbPM_ZMD += uo.nbPM_ZMD;
        this.nbPM_ZTD_BD += uo.nbPM_ZTD_BD;
        this.nbPM_ZTD_HD += uo.nbPM_ZTD_HD;
        
        this.linNROPM.addLineaires(uo.linNROPM);
        
        this.nbPMint += uo.nbPMint;
        this.nbArmoiresPMint += uo.nbArmoiresPMint;

        // au niveau du NRO
        this.nbNRO += uo.nbNRO;
        this.nbTiroirsOptiques += uo.nbTiroirsOptiques;
        this.nbRTO += uo.nbRTO;
        this.surfaceNRO += uo.surfaceNRO;

        // partie verticale de la distribution calculée à la maille du NRO
        this.nbImmeubles += uo.nbImmeubles;
        this.nbPBOint += uo.nbPBOint;
        this.nbLogementsImmeubles += uo.nbLogementsImmeubles;
        
    }
    
    public void addIncomingCables(int[] cables, boolean distri, int[] calibre, int calibreMin){
        if (distri)
            this.linPMPBO.addIncomingCables(cables, calibre, calibreMin);
        else
            this.linNROPM.addIncomingCables(cables, calibre, calibreMin);
    }
    
    public void setPMint(List<Noeud> listePMint, Parametres parametres){
        this.nbPMint = listePMint.size();
        for (Noeud pm : listePMint){
            int demandeLocale = pm.demandeLocaleInt();
            this.nbLignesInt += demandeLocale;
            this.nbArmoiresPMint += (int) Math.ceil(demandeLocale / (double) parametres.getNbMaxLignesPM("ZTD_HD_int"));
        }
    }
    
    public void setVertical(int[] immeubles, Parametres parametres){
        for (int i = 5;i<9;i++){ // NB :  i = 5 pour ne pas prendre les "immeubles" trop petit
            nbImmeubles += immeubles[i];
            nbPBOint += immeubles[i]*Math.ceil(i/ (double) parametres.nbMaxLignesPBO);
        }
        for (int i = 9;i<immeubles.length;i++){ // NB :  on compte les logements supplémentaires au-dessus de 8 
            nbImmeubles += immeubles[i];
            nbPBOint += immeubles[i]*Math.ceil(i/ (double) parametres.nbMaxLignesPBO);
            nbLogementsImmeubles += immeubles[i]*i;
        }
        int reste = this.nbPBOint;
        Queue<Integer> ordre = new ArrayBlockingQueue<>(parametres.modesPoseOrdonnes.size(),true, parametres.modesPoseOrdonnes);
        while (reste > 0 && ordre.size() > 0){
            int modePose = ordre.remove();
            int n = nbPBO[modePose];
            nbPBO[modePose] = Math.max(n - reste, 0);
            reste -= n;
        }
        if (reste > 0) System.out.println("Plus de PBOint estimés que le total de PBO trouvés !");
    }
    
    public void setNRO(Parametres parametres){
        this.nbNRO = 1;
        this.nbTiroirsOptiques = (int) Math.ceil(linNROPM.getNbFibres() / (double) parametres.nbFibresParTiroir);
        this.nbRTO = (int) Math.ceil(this.nbTiroirsOptiques / (double) parametres.nbTiroirsParRTO);
        this.surfaceNRO = nbRTO*parametres.surfaceRTO*parametres.facteurSurface;
    }
    
    public int[] getPBOGC(){
        if (!this.isDistri)
            System.out.println("Mauvais appel sur un objet UO sans la distri !");
        return this.nbPBO; 
    }
    
    static public String header(Parametres parametres, boolean distri, boolean transport, int detail){
        String header = "Nb lignes";
        if (transport){
            if (distri){
                if (detail > 0)
                    header += ";Nb de lignes ext;Nb de lignes int";
                header+= ";NRO - nb";
                if (detail > 0)
                    header+= ";NRO - tiroirs optiques;NRO - RTO et baies;NRO - surface";
            }
            header+=";"+Lineaires.header(parametres, "Transport - ", detail);
        }
        if (distri){
            if (detail == 2)
                header+=";Nb PM ext;Nb PM int";
            if (detail > 0) header+=";Armoires de rue 450;Armoires de rue 150;Armoires interieures";
            else header += ";Nb PM";
            header+=";"+Lineaires.header(parametres, "Distribution - ", detail);
            if (detail > 0){
                header += ";PBO aeriens;PBO souterrains";
                if (transport)
                    header+= ";PBO int;Vertical - adduction immeubles;Vertical - cablage logements";
            }else 
                header += ";Nb PBO";
        }
        return header;
    }
    
    public String print(Parametres parametres, int detail){
        String suite = String.valueOf(nbLignesExt+this.nbLignesInt);
        if (isTransport){
            if (isDistri){
                if (detail > 0)
                    suite += ";"+nbLignesExt+";"+this.nbLignesInt;
                suite+=";"+this.nbNRO;
                if (detail > 0)
                    suite += ";"+this.nbTiroirsOptiques+";"+(2*this.nbRTO)+";"+String.valueOf(this.surfaceNRO).replace(".", ",");
            }else {
                switch(detail){ // absence volontaire de breaks 
                    case 2:
                        suite+=";;";
                    case 1:
                        suite+=";;";
                    default:
                        suite+=";";
                }
            }
            suite += ";"+this.linNROPM.print(parametres, detail);
        }
        if (isDistri){
            if (detail == 2)
                suite += ";"+this.nbPMext+";"+this.nbPMint;
            if (detail > 0)
                suite+= ";"+(this.nbPM_ZMD+this.nbPM_ZTD_BD)+";"+this.nbPM_ZTD_HD+";"+this.nbArmoiresPMint;
            else suite += ";"+(this.nbPMext+this.nbPMint);
            suite += ";"+this.linPMPBO.print(parametres, detail);
            int totalPBOGC = nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[3]+nbPBO[5]+nbPBO[6]+nbPBO[7]+nbPBO[8]+nbPBO[9]+nbPBO[10]+nbPBO[13]+nbPBO[14];
            if (detail > 0){
                int nbPBORecAer;
                if (totalPBOGC > 0)
                    nbPBORecAer = (int) Math.round((nbPBO[4]+nbPBO[11]+nbPBO[12])*(nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[13])/ (double) totalPBOGC);
                else
                    nbPBORecAer = (nbPBO[4]+nbPBO[11]+nbPBO[12])/2;
                int nbPBOAeriens = nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[13]+nbPBORecAer;
                int nbPBOSouterrains = nbPBO[3]+nbPBO[5]+nbPBO[6]+nbPBO[7]+nbPBO[8]+nbPBO[9]+nbPBO[10]+nbPBO[14] + nbPBO[4]+nbPBO[11]+nbPBO[12]-nbPBORecAer;
                suite += ";"+nbPBOAeriens+";"+nbPBOSouterrains;
                if (isTransport)
                    suite += ";"+this.nbPBOint+";"+this.nbImmeubles+";"+this.nbLogementsImmeubles;
            }
            else{
                suite += ";"+(totalPBOGC+this.nbPBO[4]+this.nbPBO[11]+this.nbPBO[12]+this.nbPBOint);
            }
        }
        return suite;
    }
    
    public String printIncomingCables(boolean distri){
        if (distri) return linPMPBO.printIncomingCables();
        else return linNROPM.printIncomingCables();
    }
    
    public Couts calculCouts(CoutsUnitaires couts, Parametres parametres){
        double[] coutsLinD = this.linPMPBO.coutsLineaires(couts, parametres);
        double[] coutsLinT = this.linNROPM.coutsLineaires(couts, parametres);
        int totalPBOGC = nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[3]+nbPBO[5]+nbPBO[6]+nbPBO[7]+nbPBO[8]+nbPBO[9]+nbPBO[10]+nbPBO[13]+nbPBO[14];
        int nbPBORecAer;
        if (totalPBOGC > 0)
            nbPBORecAer = (int) Math.round((nbPBO[4]+nbPBO[11]+nbPBO[12])*(nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[13])/ (double) totalPBOGC);
        else
            nbPBORecAer = (nbPBO[4]+nbPBO[11]+nbPBO[12])/2;
        int nbPBOAeriens = nbPBO[0]+nbPBO[1]+nbPBO[2]+nbPBO[13]+nbPBORecAer;
        int nbPBOSouterrains = nbPBO[3]+nbPBO[5]+nbPBO[6]+nbPBO[7]+nbPBO[8]+nbPBO[9]+nbPBO[10]+nbPBO[14] + nbPBO[4]+nbPBO[11]+nbPBO[12]-nbPBORecAer;
        double coutPBOaerien = nbPBOAeriens*couts.CoutUnitairePBO_ext6;
        double coutPBOsouterrain = nbPBOSouterrains*couts.CoutUnitairePBO_ext6;
        double coutVerticalPBO = this.nbPBOint*couts.PBO_int6;
        double coutVerticalCables = this.nbImmeubles*couts.adduction + this.nbLogementsImmeubles*couts.colonneMontante;
        double coutPM_int = this.nbPM_ZTD_HD*couts.CoutUnitairePM_int;
        double coutPM_ext = (this.nbPM_ZMD+this.nbPM_ZTD_BD)*couts.CoutUnitairePM_ext300+this.nbPM_ZTD_HD*couts.CoutUnitairePM_ext100;;
        double coutsNRO = nbTiroirsOptiques*couts.coutTiroirOptique + nbRTO*couts.coutRTO + surfaceNRO*couts.coutSurfacique;
        
        return new Couts(nbLignesExt+nbLignesInt, coutsLinD[0]+coutsLinT[0], coutsLinD[1]+coutsLinT[1], coutsLinD[2]+coutsLinT[2], coutsLinD[3]+coutsLinT[3], coutPBOaerien, coutPBOsouterrain, coutsLinD[4]+coutsLinT[4], coutsLinD[5]+coutsLinT[5], coutVerticalPBO, coutVerticalCables, coutPM_int, coutPM_ext, coutsNRO, coutsLinD[6]+coutsLinT[6]);
       
    }
    
}
