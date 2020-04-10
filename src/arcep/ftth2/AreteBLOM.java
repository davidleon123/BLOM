
package arcep.ftth2;

import com.google.common.collect.HashBasedTable;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;

public class AreteBLOM extends Arete {
    
    //Compte des lignes avant mutualisation
    private int lignesZMD = 0;
    private int lignesZTD_BD = 0;
    private int lignesZTD_HD_PMint = 0;
    private int lignesZTD_HD_PMext = 0;
    
    //Compte des lignes après mutualisation
    private int lignesZTDHDNonMutu = 0;
    private int lignesZTDHDMutu = 0;
    
    private int lignesZTDBDNonMutu = 0;
    private int lignesZTDBDMutu = 0;
    
    private int lignesZMDNonMutu = 0;
    private int lignesZMDMutu = 0;
    
    private int nbLiensCollectePM_ZTDHD = 0;
    private int nbLiensCollectePM_ZTDBD = 0;
    private int nbLiensCollectePM_ZMD = 0;
    
    private Map<String,Integer> idPM = new HashMap<>();
    
    // pour la structure en arbre
    public Noeud n; // extrémité avale en étiquette
    private final List<AreteBLOM> filles = new ArrayList<>();
    
    // pour pouvoir tracer des shapefiles
    private Coordinate[] points;
    
    // UO
    private Map<String,int[]> cablesMutu;
    private Map<String,int[]> cablesNonMutu;
    private HashBasedTable<Boolean,String,Double> sections; // on utilise true et false pour mutu et non mutu, et la string pour les zones
    private double sectionTotale;
    
    private boolean parcourue = false; // champ technique pour getLongueurTronçon()
    private int niveau = 0; // champ technique pour le "coloriage"
    
    // utilisés lors de la simplification du graphe
    public AreteBLOM(AreteBLOM a, double longueur){
        this.id = a.id;
        this.n = a.n;
        this.modePose = a.modePose;
        this.longueur = longueur + a.longueur;
        this.lignesZMD = a.lignesZMD;
        this.lignesZTD_BD = a.lignesZTD_BD;
        this.lignesZTD_HD_PMext = a.lignesZTD_HD_PMext;
        this.lignesZTD_HD_PMint = a.lignesZTD_HD_PMint;
    }
    
    // utilisés par getArbre à partir de réseau
    public AreteBLOM(Noeud n){
        this.id = 999999;
        longueur = 0;
        this.n = n;
    }
    
    public AreteBLOM(Arete a, Noeud n, Noeud amont){
        this.id = a.id;
        this.modePose = a.modePose;
        this.longueur = a.longueur;
        this.n = n;
        this.lignesZMD = 0;
        this.lignesZTD_BD = 0;
        this.lignesZTD_HD_PMext = 0;
        this.lignesZTD_HD_PMint = 0;
        this.points = a.getPoints(amont,n);
    }
    
    public void add(AreteBLOM a){
        filles.add(a);
    }
    
    public List<AreteBLOM> getFilles(){
        return filles;
    }
    
    public void addNoeudLocal(Noeud n){
        for (String zone : new String[]{"ZMD", "ZTD_BD", "ZTD_HD"}){
            this.ajoutLignes(n.demandeLocaleExt(zone), zone);
        }
        this.ajoutLignes(n.demandeLocaleInt(), "ZTD_HD_PMint");
    }
    
    public void ajoutLignes(int lignes, String zone){
        switch(zone){
            case "ZMD":
                this.lignesZMD += lignes;
                break;
            case "ZTD_BD":
                this.lignesZTD_BD += lignes;
                break;
            case "ZTD_HD_int":
                this.lignesZTD_HD_PMint += lignes;
                break;
            case "ZTD_HD":
                this.lignesZTD_HD_PMext += lignes;
                break;
        }
    }
        
    public int totalLignes() {
        return lignesZMD + lignesZTD_BD + lignesZTD_HD_PMext + lignesZTD_HD_PMint;
    }
    
    public void calculDemandeRoot(){
        for (AreteBLOM a : filles){
            this.lignesZMD += a.lignesZMD;
            this.lignesZTD_BD += a.lignesZTD_BD;
            this.lignesZTD_HD_PMext += a.lignesZTD_HD_PMext;
            this.lignesZTD_HD_PMint += a.lignesZTD_HD_PMint;
        }
    }
    
    // fonction récursive terminale pour la création d'un arbre simplifié à partire de l'existant
    public void fusionAretes(AreteBLOM mere, double longueur){
        if ((filles.size() == 1) && (n.demandeLocaleTotale() == 0) && (filles.get(0).modePose == modePose)) // le cas où on continue sans rien ajouter au graphe : une seule arête fille et pas de lignes au noeud
            filles.get(0).fusionAretes(mere, longueur+this.longueur);
        else{
            AreteBLOM nouvelleArete = new AreteBLOM(this, longueur);
            //System.out.println("Lignes portées par l'arête "+nouvelleArete.id+" en ZMD : "+nouvelleArete.lignesAval("ZMD"));
            mere.add(nouvelleArete);            
            // itération en profondeur
            for (AreteBLOM fille : filles){
                fille.fusionAretes(nouvelleArete,0);
            }
        }
    }
    
    // fonction récursive qui place les PM et renvoie le nb de PM actuellement existants dans son sous-arbre
    public int posePMExt(String zone, double seuilDistance, int seuilLignes){
        
        // étape 1 : appels récursifs sur les arêtes filles et identification de celles qui ont des PM a priori "regroupables" (nbLignes < 300)
        List<AreteBLOM> regroupables = new ArrayList<>();
        int nbPM = 0;
        for (AreteBLOM fille : this.filles){
            nbPM += fille.posePMExt(zone, seuilDistance, seuilLignes);
            if (fille.n.taillePM(zone) < seuilLignes)
                regroupables.add(fille);
        }
        
        // étape 2 : on caclcule la différence entre le nb de lignes dont la longueur PM-PBO serait au-dessus ou au dessous du seuil si on posait le PM là où on est
        int delta = -this.n.demandeLocaleExt(zone);
        for (AreteBLOM fille : regroupables){
            double seuil = seuilDistance - fille.longueur;
            fille.n.lignesInfSeuil = 0;
            fille.n.lignesSupSeuil = 0;
            for (Double d : fille.n.distri){
                if (d > seuil)
                    fille.n.lignesSupSeuil++;
                else
                    fille.n.lignesInfSeuil++;
            }
            delta = delta + fille.n.lignesSupSeuil - fille.n.lignesInfSeuil;
        }
        
        // étape 3 : tant qu'on est dans le mauvais cas on sort des regroupables les PM qui font le plus pencher la balance du mauvais côté
        while(delta > 0){ // pas possible si plus que de la demande locale (même = 0) --> termine bien
            AreteBLOM filleHausseMax = this; // de telle sorte qu'on puisse tester sans échec le dernier terme de la condition ci-dessous (taillePM renvoie 0 à ce stade)
            int hausseMax = 0, hausse;
            for (AreteBLOM fille : regroupables){
                hausse = fille.n.lignesSupSeuil - fille.n.lignesInfSeuil;
                if (hausse > hausseMax || (hausse == hausseMax && fille.n.taillePM(zone) > filleHausseMax.n.taillePM(zone))){
                    hausseMax = hausse;
                    filleHausseMax = fille;
                }
            }
            regroupables.remove(filleHausseMax);
            delta = delta - filleHausseMax.n.lignesSupSeuil + filleHausseMax.n.lignesInfSeuil;
        }
        
        // étape 4 : on peut désormais placer un PM respectant le critère de distance et "déclasser" ceux qui sontregroupés
        int demandePM = n.demandeLocaleExt(zone);
        this.n.distri = new ArrayList<>();
        for (int i = 0;i<demandePM;i++){
            this.n.distri.add(0.0);
        }
        for (AreteBLOM fille : regroupables){
            demandePM += fille.n.taillePM(zone);
            fille.n.setTaillePM(zone,0);
            ListIterator<Double> iterator = this.n.distri.listIterator();
            double longueurFille = Parametres.arrondir(fille.longueur,1);
            for (Double d : fille.n.distri){
                d += longueurFille;
                while (iterator.hasNext() && iterator.next()<d){}
                if (iterator.hasPrevious() && iterator.hasNext()) iterator.previous();
                iterator.add(d);
            }
            fille.n.distri = null;
        }
        this.n.setTaillePM(zone, demandePM);
        
        // étape 5 : on renvoie le bon nombre de PM
        return nbPM - regroupables.size() + 1;
    }
    
    public void listingPMint(List listePMint, double distance){
        double d = distance+this.longueur;
        this.n.distanceAuCentre = d;
        if (this.n.isPMint()){
            listePMint.add(n);
        }
        for (AreteBLOM fille : this.filles){
            fille.listingPMint(listePMint, d);
        }
    }
    
    // fonction récursive
    public int calculNiveau(){
        int niv = 0;
        for (AreteBLOM a : this.filles){
            int nivA = a.calculNiveau();
            if (nivA > niv)
                niv = nivA;
        }
        this.niveau = niv + 1;
        return this.niveau;
    }
    
    // fonction récursive
    public double longueurTotale(){
        double longueurTot = this.longueur;
        for (AreteBLOM a : this.filles){
            longueurTot+=a.longueurTotale();
        }
        return longueurTot;
    }
    
    // fonction récursive
    public double setModesPose(int niveau, double seuilAerien, double seuilPleineTerreAerien, double seuilPleineTerreSouterrain, double longueurParcourue){
        double longueurP = longueurParcourue;
        if (niveau == this.niveau){// && this.modePose != 12){
            if(longueurParcourue < seuilAerien){
                this.modePose = 0; 
            } else if(longueurParcourue < seuilPleineTerreAerien){
                this.modePose = 13;
            } else if(longueurParcourue < seuilPleineTerreSouterrain){
                this.modePose = 14;
            }else{
                this.modePose = 7; 
            }
            longueurP += this.longueur;
        } else if (niveau < this.niveau){
            Queue<AreteBLOM> file = new ArrayBlockingQueue(filles.size(), true, filles);
            while(!file.isEmpty()){
                longueurP = file.remove().setModesPose(niveau, seuilAerien, seuilPleineTerreAerien, seuilPleineTerreSouterrain, longueurP);
            }
        }
        return longueurP;
    }
    
    // fonction récursive
    public List<ZAPM> agregeResultats(String zone, double distance, Parametres parametres){
        List<ZAPM> res = new ArrayList<>();
        if (this.n.isPMExt(zone)){
            ZAPM zapm = new ZAPM(this.n, distance + this.longueur, zone, parametres);
            for (AreteBLOM fille : this.filles){
                fille.calculZAPM(zone, zapm, 0, parametres);
                zapm.addIncomingCables(fille, parametres);
            }
            res.add(zapm);
        }
        
        for (AreteBLOM fille : this.filles){
            res.addAll(fille.agregeResultats(zone, distance + this.longueur, parametres));
        }
        return res;
    }
    
    // fonction récursive
    public void calculZAPM(String zone, ZAPM zapm, double distancePM, Parametres parametres){
        zapm.add(this, parametres, distancePM);
        this.idPM.put(zone, zapm.getID());
        if (!this.n.isPMExt(zone)){
            for (AreteBLOM fille : this.filles){
                fille.calculZAPM(zone, zapm, distancePM+this.longueur, parametres);
            }
        }
    }
    
    // fonction récursive
    public void calculTransport (UO transport, Parametres parametres){
        transport.addLineaires(this, parametres);
        for (AreteBLOM fille : this.filles){
            fille.calculTransport(transport, parametres);
        }
    }
    
    // fonction récursive
    public List<Noeud> zoneArrierePM(String zone) { // à changer si on fusionne plus les arêtes
        List<Noeud> resultat = new ArrayList<>();
        resultat.add(n);
        for (AreteBLOM a : filles) {
            if (a.n.taillePM(zone) == 0) resultat.addAll(a.zoneArrierePM(zone));
        }
        return resultat;
    }
    
    // fonction récursive
    public void calculUO(Set<String> zones, Parametres parametres){
        for (AreteBLOM fille : filles){
            fille.calculUO(zones, parametres);
        }
        this.calculCables(zones, parametres);
        this.calculSectionGC(zones, parametres);
    }
    
    public int lignesAval(String zone){
        switch(zone){
            case "ZMD":
                return lignesZMD;
            case "ZTD_BD":
                return lignesZTD_BD;
            case "ZTD_HD_PMint":
                return lignesZTD_HD_PMint;
            default:
                return lignesZTD_HD_PMext;
        }
    }
    
    // fonction récursive pour calculer la demande (en distinguant mutualisé et non mutualisé)
    public void calculDemande(Set<String> zones, Parametres parametres){
        // itération sur les arêtes filles
        for (AreteBLOM fille : filles){
            fille.calculDemande(zones, parametres);
        }
        // calcule de la demande pour l'arête mère
        for (String zone : zones){
            int lignesNonMutu = 0, lignesMutu = 0, collecte = 0;
            for (AreteBLOM fille : filles){
                lignesMutu+=fille.lignesMutu(zone);
                lignesNonMutu += fille.lignesNonMutu(zone);
                collecte += fille.nbPMCollectes(zone);
            }
            this.setDemande(zone, lignesMutu, lignesNonMutu, collecte, parametres);
        }
    }
    
    private int lignesMutu(String zone) {
        int resultat = 0;
        switch(zone) {
            case "ZMD":
                    resultat = lignesZMDMutu;
                break;
            case "ZTD_BD": 
                    resultat = lignesZTDBDMutu;
                break; 
            case "ZTD_HD": 
                    resultat = lignesZTDHDMutu;
                break;
        }
        return resultat;
    }
    
    private int lignesNonMutu(String zone) {
        int resultat = 0;
        switch(zone) {
            case "ZMD":
                resultat = lignesZMDNonMutu;
                break;
            case "ZTD_BD": 
                resultat = lignesZTDBDNonMutu;
                break; 
            case "ZTD_HD": 
                resultat = lignesZTDHDNonMutu;
                break;
        }
        return resultat;
    }
    
    private int nbPMCollectes(String zone) {
        int resultat = 0;
        switch(zone) {
            case "ZMD":
                    resultat = nbLiensCollectePM_ZMD;
                break;
            case "ZTD_BD": 
                    resultat = nbLiensCollectePM_ZTDBD;
                break; 
            case "ZTD_HD": 
                    resultat = nbLiensCollectePM_ZTDHD;
                break;
        }
        return resultat;
    }
    
    private void setDemande(String zone, int mutu, int nonMutu, int collecte, Parametres parametres){
        if (n.isPMExt(zone)){
            mutu = 0;
            nonMutu += (int) Math.ceil(Math.max(n.taillePM(zone)*parametres.tauxCouplage(zone),parametres.nbFibresMin(zone)));
            collecte++;
        } else{
            mutu += n.demandeLocaleExt(zone);
        }
        if (zone.equals("ZTD_HD")){
            collecte+= 1;
            nonMutu+=n.demandeLocaleInt();
        }
        switch(zone) {
            case "ZMD":
                    lignesZMDMutu = mutu;
                    lignesZMDNonMutu = nonMutu;
                    nbLiensCollectePM_ZMD = collecte;
                break;
            case "ZTD_BD": 
                    lignesZTDBDMutu = mutu;
                    lignesZTDBDNonMutu = nonMutu;
                    nbLiensCollectePM_ZTDBD = collecte;
                break; 
            case "ZTD_HD": 
                    lignesZTDHDMutu = mutu;
                    lignesZTDHDNonMutu = nonMutu;
                    nbLiensCollectePM_ZTDHD = collecte;
                break;
        }
    }
    
    private void calculCables(Set<String> zones, Parametres parametres) {
            cablesMutu = new HashMap<>();
            cablesNonMutu = new HashMap<>();
            for (String zone : zones){
                cablesMutu.put(zone, cables(this.lignesMutu(zone)*parametres.facteurSurcapaciteDistri, parametres));
                cablesNonMutu.put(zone, cables(this.lignesNonMutu(zone)*parametres.facteurSurcapaciteTransport, parametres));
            }
    }
    
    private void calculSectionGC(Set<String> zones, Parametres parametres){
        double[] diametres = parametres.getDiametres(this.modePose);
        sections = HashBasedTable.create();
        sectionTotale = 0;
        for (String zone : zones){
            sections.put(Boolean.TRUE, zone, section(zone, true, diametres));
            sections.put(Boolean.FALSE, zone, section(zone, false, diametres));
            sectionTotale+=sections.get(Boolean.TRUE, zone);
            sectionTotale+=sections.get(Boolean.FALSE, zone);
        }
    }
    
    private double section(String zone, boolean isMutu, double[] diametres){
        double section = 0;
        int[] cables;
        if (isMutu) cables = cablesMutu.get(zone);
        else cables = cablesNonMutu.get(zone);
        for (int i = 0;i<cables.length; i++){
            section+=cables[i]*Math.PI*Math.pow(diametres[i]/2000, 2); // là on est en m^2
        }
        return section;
    }
    
    private int[] cables(double demande, Parametres parametres){
        int calibreMax = parametres.getCalibreMax(modePose);
        int calibreMin = parametres.getCalibreMin();
        int[] cables = new int[calibreMax+1];
        if (demande > 0) {
            for (int i = calibreMin;i<=calibreMax;i++){
                if (demande <= parametres.calibre[i]){
                    cables[i]++;
                    break;
                }   
            }
            if (demande > parametres.calibre[calibreMax]){
                cables[calibreMax] = (int) Math.ceil(demande / (double) parametres.calibre[calibreMax]);
            }
        }
        return cables;
    }
    
    public int[] getCables(boolean isDistri, String zone){
        //System.out.println("Appel de getCables pour l'arête : "+this.id);
        //System.out.println("isDistri : "+ isDistri+" - zone : "+zone);
        if(isDistri) return cablesMutu.get(zone);
        else return cablesNonMutu.get(zone);
    }
    
    public double getSection(boolean isDistri, String zone){
        return sections.get(isDistri, zone);
    }
    
    public double getSectionTotale(){
        return sectionTotale;
    }
    
    public int getNbFibres(boolean isDistri, String zone, int[] calibre, int calibreMin){
        int[] cables = this.getCables(isDistri, zone);
        int n = Math.min(calibre.length, cables.length);
        if (calibre.length < cables.length)
            System.out.println("Problème avec les câbles ! Le tableau est trop grand");
        int res = 0;
        for (int i =0;i<n;i++){
            res+=calibre[calibreMin+i]*cables[i];
        }
        return res;
    }
            
    public void addTreeToShape(String CodeNRO, String zone, GeometryFactory gf, SimpleFeatureCollection collection,SimpleFeatureBuilder featureBuilderLineaires){
        for (AreteBLOM a : this.filles){
            int lignesDistri = a.lignesMutu(zone);
            int lignesTransport = a.lignesNonMutu(zone);
            if (lignesDistri + lignesTransport > 0){
                featureBuilderLineaires.add(gf.createLineString(a.points));
                featureBuilderLineaires.add(a.id);
                featureBuilderLineaires.add(a.modePose);
                featureBuilderLineaires.add(CodeNRO);
                //featureBuilderLineaires.add(zone);
                //featureBuilderLineaires.add(String.valueOf(a.idPM.get(zone)));
                //featureBuilderLineaires.add(lignesTransport);
                //featureBuilderLineaires.add(lignesDistri);
                featureBuilderLineaires.add(lignesDistri+lignesTransport);
                collection.add(featureBuilderLineaires.buildFeature(null));
                a.addTreeToShape(CodeNRO, zone, gf, collection, featureBuilderLineaires);
            }
        }
    }
    
    // utilisé par Lineaires pour le calcul des UO de boîtiers (tant que la fusion des arêtes conserve les noeuds avec seulement de la demande locale)
    public double getLongueurTronçon(){
        double res = 0;
        if (!parcourue){
            this.parcourue = true;
            res += this.longueur;
            if(this.filles.size()==1){
                res+= this.filles.get(0).getLongueurTronçon();
            }
        }
        return res;
    }
     
}
