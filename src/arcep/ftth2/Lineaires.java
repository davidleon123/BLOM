
package arcep.ftth2;

import java.util.*;

public class Lineaires {
    
    private final double[] cablesFA;
    private final double[] cablesFS;
    private final double[] cablesFR;
    
    private final int[] boitiersA;
    private final int[] boitiersS;
    private final int[] boitiersR;
    
    private long nbEpissuresA;
    private long nbEpissuresS;
    private long nbEpissuresR;
    
    private final double[] longueurGC = new double[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private final double[] volumeGC = new double[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    
    private final int[] incomingCables;
    private int nbFibres;

    public Lineaires(Parametres parametres){
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        
        cablesFA = new double[maxAerien+1];
        for (int i = 0;i<=maxAerien;i++){
            cablesFA[i] = 0.0;
        }
        cablesFS = new double[maxSouterrain+1];
        for (int i = 0;i<=maxSouterrain;i++){
            cablesFS[i] = 0.0;
        }
        cablesFR = new double[maxSouterrain+1];
        for (int i = 0;i<=maxSouterrain;i++){
            cablesFR[i] = 0.0;
        }
        
        boitiersA = new int[maxAerien+1];
        boitiersS = new int[maxSouterrain+1];
        boitiersR = new int[maxSouterrain+1];

        nbEpissuresA = 0;
        nbEpissuresS = 0;
        nbEpissuresR = 0;
        
        incomingCables = new int[maxSouterrain+1-parametres.getCalibreMin()]; // que pour incomingCables
        nbFibres = 0;
    }

    public void addCablesEtBoitiers(AreteBLOM a, Parametres parametres, boolean isDistri, String zone){ // attention calcul spécifique à une zone
        
        int[] cables = a.getCables(isDistri, zone);
        
        // compta des cables 
        for (int i = 0; i<cables.length;i++){
            addToCables(a.modePose, i, cables[i]*a.longueur*parametres.longFibreSupp);
        }

        //compta des boitiers        
        List<AreteBLOM> filles = a.getFilles();
        int[] epissuresEvitables = Arrays.copyOf(cables, cables.length); // on laisse une partie du (des) cable(s) en passage autant que faire se peut
        if(filles.size()>1){//s'il existe plusieurs arètes (filles = aretes aval), alors il faut un boitier 
            for (int i=0; i<cables.length;i++){
                addToBoitiers(a.modePose,i,cables[i]);
            }
            for (AreteBLOM fille : filles){
                int[] cablesFille = fille.getCables(isDistri, zone);
                for (int i = 0;i<Math.min(epissuresEvitables.length, cablesFille.length);i++){
                    this.addEpissures(Math.max(0,cablesFille[i]-epissuresEvitables[i])*parametres.calibre[i], a.modePose);
                    epissuresEvitables[i] = Math.max(0,epissuresEvitables[i] - cablesFille[i]);
                }
            }
        }
        double longueur_arete = a.getLongueurTronçon(); // fonction récursive

        if(longueur_arete> parametres.seuil_boitier){// on rajoute un ou des boitier(s) quand c'est trop long 
            for (int i=0; i<cables.length;i++){
                int nbBoitiers = (int) Math.floor(cables[i]*longueur_arete/parametres.seuil_boitier);
                addToBoitiers(a.modePose,i,nbBoitiers);
                this.addEpissures(nbBoitiers*parametres.calibre[i], a.modePose);
            }
        }
    }
    
    private void addEpissures(int nbEpissures, int modePose){
        switch(modePose){
            case 0:
            case 1:
            case 2:
            case 13:
                this.nbEpissuresA += nbEpissures;
                break;
            case 4:
            case 11:
            case 12:
                this.nbEpissuresR += nbEpissures;
                break;
            default:
                this.nbEpissuresS += nbEpissures;
                break;
        }
    }

    public void addGC(double longueur, double section, double sectionTotale, int modePose){
        volumeGC[modePose]+= section*longueur;
        longueurGC[modePose]+= longueur*section/sectionTotale; 
    }
    
    public void addIncomingCables(int[] cables, int[] calibre, int calibreMin){
        for (int i = calibreMin;i<cables.length;i++){
            incomingCables[i-calibreMin]+=cables[i];
            nbFibres+=cables[i]*calibre[i];
        }
    }

    public void addLineaires(Lineaires lin){
        addArray(this.cablesFA, lin.cablesFA);
        addArray(this.cablesFS, lin.cablesFS);
        addArray(this.cablesFR, lin.cablesFR);
        addArray(this.boitiersA, lin.boitiersA);
        addArray(this.boitiersS, lin.boitiersS);
        addArray(this.boitiersR, lin.boitiersR);
        addArray(this.longueurGC, lin.longueurGC);
        addArray(this.volumeGC, lin.volumeGC);
        addArray(this.incomingCables, lin.incomingCables);
        this.nbFibres+=lin.nbFibres;
        this.nbEpissuresA += lin.nbEpissuresA;
        this.nbEpissuresS += lin.nbEpissuresS;
        this.nbEpissuresR += lin.nbEpissuresR;
    }
    
    public int getNbFibres(){
        return this.nbFibres;
    }

    public static String header(Parametres parametres, String prefixe, int detail){
        String header;
        switch(detail){
            case 1:
                header = prefixe+"longueur GC reconstruit aerien;";
                header += prefixe+"longueur GC reconstruit conduite remplacement conduite;";
                header += prefixe+"longueur GC reconstruit conduite remplacement pleine terre;";
                header += prefixe+"longueur GC Orange aerien;";
                header += prefixe+"longueur GC Orange souterrain;";
                header += prefixe+"volume GC Orange aerien;";
                header += prefixe+"volume GC Orange souterrain";
                break;
            case 2:
                header = prefixe+"longueur GC Aerien Orange hors facade;"
                + prefixe+"longueur GC Aerien ERDF;"
                + prefixe+"longueur GC Facade;"
                + prefixe+"longueur GC souterrain utilisable Orange;"
                + prefixe+"longueur GC pleine terre Orange;"
                + prefixe+"longueur GC routier;"
                + prefixe+"longueur GC creation algo;"
                + prefixe+"volume GC aerien Orange;"
                + prefixe+"volume GC aerien ERDF;"
                + prefixe+"volume GC souterrain utilisable Orange;"
                + prefixe+"volume GC reconstruit pleine terre;"
                + prefixe+"volume GC reconstruit routier;"
                + prefixe+"volume GC creation algo";
                break;
            default:
                header =prefixe+"longueur GC;";
                header+=prefixe+"volume GC";
        }
        int maxAerien = parametres.getCalibreMax(0);
        int maxSouterrain = parametres.getCalibreMax(7);
        int minHorizontal = parametres.getCalibreMin();
        for (int i = minHorizontal;i<=maxAerien;i++){
            header+= ";"+prefixe+"F_A_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            header+= ";"+prefixe+"F_S_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            header+= ";"+prefixe+"B_A_"+parametres.calibre[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            header+= ";"+prefixe+"B_S_"+parametres.calibre[i];
        }
        header +=";"+prefixe+"Epissures aeriennes;"+prefixe+"Epissures souterraines";
        return header;
    }
    
    public String print(Parametres parametres, int detail){
        String suite;
        switch(detail){
            case 1:
                suite = String.valueOf((longueurGC[4]+longueurGC[11]+longueurGC[12])*parametres.partReconstrPTAerien+(longueurGC[0]+longueurGC[1]+longueurGC[2])*parametres.partReconstrAerien+longueurGC[13]).replace(".", ",")+";"+
                        String.valueOf((longueurGC[5]+longueurGC[7]+longueurGC[9]+longueurGC[10]+longueurGC[3]+longueurGC[6]+longueurGC[8])*parametres.partReconstrConduite).replace(".", ",")+";"+
                        String.valueOf((longueurGC[4]+longueurGC[11]+longueurGC[12])*(1-parametres.partReconstrPTAerien)+longueurGC[14]).replace(".", ",")+";"+
                        String.valueOf((longueurGC[0]+longueurGC[1]+longueurGC[2])*(1-parametres.partReconstrAerien)).replace(".", ",")+";"+
                        String.valueOf((longueurGC[3]+longueurGC[5]+longueurGC[6]+longueurGC[7]+longueurGC[8]+longueurGC[9]+longueurGC[10])*(1-parametres.partReconstrConduite)).replace(".", ",")+";"+
                        String.valueOf(volumeGC[0]+volumeGC[1]+volumeGC[2]).replace(".", ",")+";"+
                        String.valueOf(volumeGC[3]+volumeGC[5]+volumeGC[6]+volumeGC[7]+volumeGC[8]+volumeGC[9]+volumeGC[10]).replace(".", ",");
                break;
            case 2:
                suite = String.valueOf(longueurGC[0]).replace(".", ",")+";"; //aérien Orange
                suite +=String.valueOf(longueurGC[1]).replace(".", ",")+";"; //aérien Enedis
                suite +=String.valueOf(longueurGC[2]).replace(".", ",")+";"; //aérien façade
                suite+= String.valueOf(longueurGC[5]+longueurGC[7]+longueurGC[9]+longueurGC[10]+longueurGC[3]+longueurGC[6]+longueurGC[8]).replace(".", ",")+";"; // souterrain
                suite+= String.valueOf(longueurGC[4]+longueurGC[13]+longueurGC[14]).replace(".", ",")+";"; //pleine terre
                suite+= String.valueOf(longueurGC[11]).replace(".", ",")+";"; // utilisation réseau routier
                suite+= String.valueOf(longueurGC[12]).replace(".", ",")+";"; // arêtes créées par l'algo
                suite+= String.valueOf(volumeGC[0]+volumeGC[2]).replace(".", ",")+";"
                        +String.valueOf(volumeGC[1]).replace(".", ",")+";"
                        +String.valueOf(volumeGC[3]+volumeGC[5]+volumeGC[6]+volumeGC[7]+volumeGC[8]+volumeGC[9]+volumeGC[10]).replace(".", ",")+";"
                        +String.valueOf(volumeGC[4]+volumeGC[13]+volumeGC[14]).replace(".", ",")+";"
                        +String.valueOf(volumeGC[11]).replace(".", ",")+";"
                        +String.valueOf(volumeGC[12]).replace(".", ",");
                break;
            default:
                double longueurTotale = 0, volumeTotal = 0;
                for (int i = 0;i<longueurGC.length;i++){
                    longueurTotale+=longueurGC[i];
                    volumeTotal += volumeGC[i];
                }
                suite = String.valueOf(longueurTotale).replace(".", ",")+";"+
                        String.valueOf(volumeTotal).replace(".", ",");
        }
        
        int maxAerien = parametres.getCalibreMax(2);
        int maxSouterrain = parametres.getCalibreMax(7);
        int minHorizontal = parametres.getCalibreMin();
        double[] cablesAeriens = new double[maxAerien+1];
        double[] cablesSouterrains = new double[maxSouterrain+1];
        for (int i = minHorizontal;i<=maxAerien;i++){
            double somme = cablesFA[i]+cablesFS[i];
            double cablesRA;
            if (somme > 0)
                cablesRA = cablesFR[i]*cablesFA[i]/somme;
            else cablesRA = cablesFR[i]/2;
            cablesAeriens[i] = cablesFA[i] + cablesRA;
            cablesSouterrains[i] = cablesFS[i] + cablesFR[i]-cablesRA;
        }
        for (int i = maxAerien+1;i<=maxSouterrain;i++){
            cablesSouterrains[i] = cablesFS[i] + cablesFR[i];
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            suite+=";"+String.valueOf(cablesAeriens[i]).replace(".", ",");
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            suite+=";"+String.valueOf(cablesSouterrains[i]).replace(".", ",");
        }
        int[] boitiersAeriens = new int[maxAerien+1];
        int[] boitiersSouterrains = new int[maxSouterrain+1];
        for (int i = minHorizontal;i<=maxAerien;i++){
            int boitierRA = (int) Math.round(boitiersR[i]*boitiersA[i]/(double) (boitiersA[i]+boitiersS[i]));
            boitiersAeriens[i] = boitiersA[i] + boitierRA;
            boitiersSouterrains[i] = boitiersS[i] + boitiersR[i] - boitierRA;
        }
        for (int i = maxAerien+1;i<=maxSouterrain;i++){
            boitiersSouterrains[i] = boitiersS[i] + boitiersR[i];
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            suite+=";"+boitiersAeriens[i];
        }
        for (int i = minHorizontal;i<=maxSouterrain;i++){
            suite+=";"+boitiersSouterrains[i];
        }
        long totalEpissuresGC = this.nbEpissuresA+this.nbEpissuresS;
        long epissuresRA;
        if (totalEpissuresGC > 0)
            epissuresRA = this.nbEpissuresR*this.nbEpissuresA/totalEpissuresGC;
        else epissuresRA = this.nbEpissuresR/2;
        suite+=";"+(this.nbEpissuresA + epissuresRA)+";"+(this.nbEpissuresS+this.nbEpissuresR-epissuresRA);
        return suite;
    }
    
    public String printIncomingCables(){
        String debut = "";
        for (int i = 0;i<incomingCables.length;i++){
                debut += ";"+incomingCables[i];
            }
        return debut + ";" + nbFibres;
    }
    
    public double[] coutsLineaires(CoutsUnitaires couts, Parametres parametres){
        double[] res = new double[7];
        int maxAerien = parametres.getCalibreMax(2);
        int maxSouterrain = parametres.getCalibreMax(7);
        int minHorizontal = parametres.getCalibreMin();
        double[] cablesAeriens = new double[maxAerien+1];
        double[] cablesSouterrains = new double[maxSouterrain+1];
        for(int i = 0;i<minHorizontal;i++){
            cablesAeriens[i] = 0;
            cablesSouterrains[i] = 0;
        }
        for (int i = minHorizontal;i<=maxAerien;i++){
            double somme = cablesFA[i]+cablesFS[i];
            double cablesRA;
            if (somme > 0)
                cablesRA = cablesFR[i]*cablesFA[i]/somme;
            else cablesRA = cablesFR[i]/2;
            cablesAeriens[i] = cablesFA[i] + cablesRA;
            cablesSouterrains[i] = cablesFS[i] + cablesFR[i]-cablesRA;
        }
        for (int i = maxAerien+1;i<=maxSouterrain;i++){
            cablesSouterrains[i] = cablesFS[i] + cablesFR[i];
        }
        res[0] = product(cablesAeriens,couts.CoutUnitaireCablesAeriens);
        res[1] = product(cablesSouterrains,couts.CoutUnitaireCablesSouterrains);
        int[] boitiersAeriens = new int[maxAerien+1];
        int[] boitiersSouterrains = new int[maxSouterrain+1];
        for (int i = minHorizontal;i<=maxAerien;i++){
            int boitierRA = (int) Math.round(boitiersR[i]*boitiersA[i]/(double) (boitiersA[i]+boitiersS[i]));
            boitiersAeriens[i] = boitiersA[i] + boitierRA;
            boitiersSouterrains[i] = boitiersS[i] + boitiersR[i] - boitierRA;
        }
        for (int i = maxAerien+1;i<=maxSouterrain;i++){
            boitiersSouterrains[i] = boitiersS[i] + boitiersR[i];
        }
        res[2] = product(boitiersAeriens,couts.CoutUnitaireBoitiersAeriens);
        res[3] = product(boitiersSouterrains,couts.CoutUnitaireBoitiersSouterrains);
        res[4] = ((longueurGC[4]+longueurGC[11]+longueurGC[12])*parametres.partReconstrPTAerien+(longueurGC[0]+longueurGC[1]+longueurGC[2])*parametres.partReconstrAerien+longueurGC[13])*couts.CoutUnitairePoteau;
        res[5] = (longueurGC[5]+longueurGC[7]+longueurGC[9]+longueurGC[10]+longueurGC[3]+longueurGC[6]+longueurGC[8])*parametres.partReconstrConduite*couts.CoutUnitaireConduite+((longueurGC[4]+longueurGC[11]+longueurGC[12])*(1-parametres.partReconstrPTAerien)+longueurGC[14])*couts.CoutUnitaireConduiteAllegee;
        res[6] = (longueurGC[0]+longueurGC[1]+longueurGC[2])*(1-parametres.partReconstrAerien)*couts.etudesAerien+(longueurGC[3]+longueurGC[5]+longueurGC[6]+longueurGC[7]+longueurGC[8]+longueurGC[9]+longueurGC[10])*(1-parametres.partReconstrConduite)*couts.etudesSouterrain;
        return res;
    }
    
    private void addToBoitiers(int modePose, int i, int nombre){
        switch(modePose){
            case 0:
            case 1:
            case 2:
            case 13:
                boitiersA[i]+=nombre;
                break;
            case 4:
            case 11:
            case 12:
                boitiersR[i]+=nombre;
                break;
            default:
                boitiersS[i]+=nombre;
                break;
        }
    }
    
    private void addToCables(int modePose, int i, double nombre){
        switch(modePose){
            case 0:
            case 1:
            case 2:
                cablesFA[i]+=nombre;
                break;
            case 4:
            case 11:
            case 12:
                cablesFR[i]+=nombre;
                break;
            default:
                cablesFS[i]+=nombre;
                break;
        }
    }
    
    private void addArray(double[] t1, double[] t2){
        int n = t1.length;
        if (n==t2.length){
            for (int i = 0;i<n;i++){
                t1[i]+=t2[i];
            }
        } else
            System.out.println("Les tableaux n'ont pas la même longueur !");
    }
    
    private void addArray(int[] t1, int[] t2){
        int n = t1.length;
        if (n==t2.length){
            for (int i = 0;i<n;i++){
                t1[i]+=t2[i];
            }
        } else
            System.out.println("Les tableaux n'ont pas la même longueur !");
    }
    
    private double product(double[] uo, double[] couts){
        double res = 0;
        for (int i = 0; i < uo.length;i++){
            res+= uo[i]*couts[i];
        }
        return res;
    }
    
    private double product(int[] uo, double[] couts){
        double res = 0;
        for (int i = 0; i < uo.length;i++){
            res+= uo[i]*couts[i];
        }
        return res;
    }
}
