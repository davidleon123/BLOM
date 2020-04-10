
package arcep.ftth2;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Parametres {
    
    ///// Paramètres globaux
    
    private final DateFormat dateFormat = new SimpleDateFormat("YYYYMMdd_HH_mm");
    Set<String> zones;
    
    public void setZones(boolean ztd, boolean amii, boolean rip){
        zones = new HashSet<>();
        if (ztd) zones.add("ZTD");
        if (amii) zones.add("ZMD_AMII");
        if (rip) zones.add("ZMD_RIP");
    }
    
    String racineResultats = "Q:/Modele FttH v2/Resultats"; // mis à jour lorsqu'on clique sur "lancement de la modélisation" dans le fenêtre principale
    String terminaison; // définie par setDossierResultats
    public final String cheminReseau;
    
    public void setDossierResultats(boolean ztd, boolean amii, boolean rip, String trace){
        File f = new File("." );
        Date date = new Date();
        terminaison = dateFormat.format(date);
        if (ztd){
            if(amii){
                if(rip) terminaison += "_global";
                else terminaison += "_prive";
            } else{
                terminaison += "_ZTD";
                if (rip) terminaison += "_RIP";
            }
        } else{
            if (amii){
                if (rip) terminaison += "_ZMD";
                else terminaison += "_AMII";
            } else{
                if (rip) terminaison += "_RIP";
                else terminaison += "_ListeCommunes";
            }
        }
        terminaison += trace.replace("BLO", "");
        racineResultats = f.getAbsolutePath() + "/Resultats/" + terminaison;
        File dir = new File(racineResultats);
        dir.mkdirs();
    }
    
    private List<String> listeDpt;
    private List<String> listeDptRoutier;
    
    public void setListesDpts(Object[] dpt, List<String> listeRoutiers){
        listeDpt = new ArrayList<>();
        listeDptRoutier = new ArrayList<>();
        String departement;
        for (Object d : dpt) {
            departement = ((String) d).split(" | ")[0]; //enlève le nom accolé au numéro
            listeDpt.add(departement);
            if (listeRoutiers.contains((String) d)) { 
                listeDptRoutier.add(departement);
            }
        }
    }
    
    public void setListesDpts(List<String> liste, List<String> routiers){
        listeDpt = liste;
        listeDptRoutier = routiers;
    }
    
    private Map<String,Set<String>> listeCommunes = new HashMap<>();
    
    public void setListeCommunes(String dossierCommunes, boolean listeSpeciale, String fichierListe, boolean ztd, boolean amii, boolean rip){
        listeCommunes = new HashMap<>();
        try {
            BufferedReader ficCommunes;
            String ligne, codeINSEE, donneesLigne[];
            if(listeSpeciale){
                String dpt;
                ficCommunes = new BufferedReader(new FileReader(cheminReseau+dossierCommunes+"/"+fichierListe));
                ficCommunes.readLine();
                while ((ligne = ficCommunes.readLine()) != null) {
                    donneesLigne = ligne.split(";");
                    codeINSEE = donneesLigne[0];
                    if (codeINSEE.length() == 4)
                        codeINSEE = "0".concat(codeINSEE);
                    dpt = codeINSEE.substring(0, 2);
                    if (listeCommunes.containsKey(dpt))
                        listeCommunes.put(dpt, new HashSet<String>());
                    listeCommunes.get(dpt).add(codeINSEE);
                }
            }
            else{
                String zone;
                for (String dpt : listeDpt){
                    listeCommunes.put(dpt, new HashSet<String>());
                    ficCommunes = new BufferedReader(new FileReader(cheminReseau+dossierCommunes+"/"+dossierCommunes +"_" + dpt + ".csv"));
                    ficCommunes.readLine();
                    while ((ligne = ficCommunes.readLine()) != null) {
                        donneesLigne = ligne.split(";");
                        codeINSEE = donneesLigne[0];
                        if (codeINSEE.length() == 4)
                            codeINSEE = "0".concat(codeINSEE);
                        zone = donneesLigne[1];
                        if ((ztd && zone.equals("ZTD")) || (amii && zone.equals("AMII")) || (rip && zone.equals("RIP"))) {
                            listeCommunes.get(dpt).add(codeINSEE);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private Map<String, String> nomDepartements;
    
    public void initNomsDepartements(){
        nomDepartements = new HashMap<>();
        nomDepartements.put("01", "Ain");
        nomDepartements.put("02", "Aisne");
        nomDepartements.put("03", "Allier");
        nomDepartements.put("04", "Alpes-de-Haute-Provence");
        nomDepartements.put("05", "Hautes-Alpes");
        nomDepartements.put("06", "Alpes-Maritimes");
        nomDepartements.put("07", "Ardèche");
        nomDepartements.put("08", "Ardennes");
        nomDepartements.put("09", "Ariège");
        nomDepartements.put("10", "Aube");
        nomDepartements.put("11", "Aude");
        nomDepartements.put("12", "Aveyron");
        nomDepartements.put("13", "Bouches-du-Rhône");
        nomDepartements.put("14", "Calvados");
        nomDepartements.put("15", "Cantal");
        nomDepartements.put("16", "Charente");
        nomDepartements.put("17", "Charente-Maritime");
        nomDepartements.put("18", "Cher");
        nomDepartements.put("19", "Corrèze");
        nomDepartements.put("2A", "Corse-du-Sud");
        nomDepartements.put("2B", "Haute-Corse");
        nomDepartements.put("21", "Côte-d'Or");
        nomDepartements.put("22", "Côtes-d'Armor");
        nomDepartements.put("23", "Creuse");
        nomDepartements.put("24", "Dordogne");
        nomDepartements.put("25", "Doubs");
        nomDepartements.put("26", "Drôme");
        nomDepartements.put("27", "Eure");
        nomDepartements.put("28", "Eure-et-Loir");
        nomDepartements.put("29", "Finistère");
        nomDepartements.put("30", "Gard");
        nomDepartements.put("31", "Haute-Garonne");
        nomDepartements.put("32", "Gers");
        nomDepartements.put("33", "Gironde");
        nomDepartements.put("34", "Hérault");
        nomDepartements.put("35", "Ille-et-Vilaine");
        nomDepartements.put("36", "Indre");
        nomDepartements.put("37", "Indre-et-Loire");
        nomDepartements.put("38", "Isère");
        nomDepartements.put("39", "Jura");
        nomDepartements.put("40", "Landes");
        nomDepartements.put("41", "Loir-et-Cher");
        nomDepartements.put("42", "Loire");
        nomDepartements.put("43", "Haute-Loire");
        nomDepartements.put("44", "Loire-Atlantique");
        nomDepartements.put("45", "Loiret");
        nomDepartements.put("46", "Lot");
        nomDepartements.put("47", "Lot-et-Garonne");
        nomDepartements.put("48", "Lozère");
        nomDepartements.put("49", "Maine-et-Loire");
        nomDepartements.put("50", "Manche");
        nomDepartements.put("51", "Marne");
        nomDepartements.put("52", "Haute-Marne");
        nomDepartements.put("53", "Mayenne");
        nomDepartements.put("54", "Meurthe-et-Moselle");
        nomDepartements.put("55", "Meuse");
        nomDepartements.put("56", "Morbihan");
        nomDepartements.put("57", "Moselle");
        nomDepartements.put("58", "Nièvre");
        nomDepartements.put("59", "Nord");
        nomDepartements.put("60", "Oise");
        nomDepartements.put("61", "Orne");
        nomDepartements.put("62", "Pas-de-Calais");
        nomDepartements.put("63", "Puy-de-Dôme");
        nomDepartements.put("64", "Pyrénées-Atlantiques");
        nomDepartements.put("65", "Hautes-Pyrénées");
        nomDepartements.put("66", "Pyrénées-Orientales");
        nomDepartements.put("67", "Bas-Rhin");
        nomDepartements.put("68", "Haut-Rhin");
        nomDepartements.put("69", "Rhône");
        nomDepartements.put("70", "Haute-Saône");
        nomDepartements.put("71", "Saône-et-Loire");
        nomDepartements.put("72", "Sarthe");
        nomDepartements.put("73", "Savoie");
        nomDepartements.put("74", "Haute-Savoie");
        nomDepartements.put("75", "Paris");
        nomDepartements.put("76", "Seine-Maritime");
        nomDepartements.put("77", "Seine-et-Marne");
        nomDepartements.put("78", "Yvelines");
        nomDepartements.put("79", "Deux-Sèvres");
        nomDepartements.put("80", "Somme");
        nomDepartements.put("81", "Tarn");
        nomDepartements.put("82", "Tarn-et-Garonne");
        nomDepartements.put("83", "Var");
        nomDepartements.put("84", "Vaucluse");
        nomDepartements.put("85", "Vendée");
        nomDepartements.put("86", "Vienne");
        nomDepartements.put("87", "Haute-Vienne");
        nomDepartements.put("88", "Vosges");
        nomDepartements.put("89", "Yonne");
        nomDepartements.put("90", "Territoire de Belfort");
        nomDepartements.put("91", "Essonne");
        nomDepartements.put("92", "Hauts-de-Seine");
        nomDepartements.put("93", "Seine-Saint-Denis");
        nomDepartements.put("94", "Val-de-Marne");
        nomDepartements.put("95", "Val-d'Oise");
        nomDepartements.put("971", "Guadeloupe");
        nomDepartements.put("972", "Martinique");
        nomDepartements.put("973", "Guyane");
        nomDepartements.put("974", "Réunion");
        nomDepartements.put("976", "Mayotte");
    }
    
    boolean ficUnitesOeuvre = false;
    boolean ficPM = false;
    boolean ficLineaires = false;
    boolean ficCouts = false;
    boolean ficLongueurs = false;
    
    public void setSorties(boolean ficUnitesOeuvre, boolean ficPM, boolean ficLineaires, boolean ficCouts, boolean ficLongueurs){
        this.ficUnitesOeuvre = ficUnitesOeuvre;
        this.ficPM = ficPM;
        this.ficLineaires = ficLineaires;
        this.ficCouts = ficCouts;
        this.ficLongueurs = ficLongueurs;
    }
    

    ///// Paramètes module topologique
    
    // pénalisation des réseaux relativement au GC conduite
    double facteurAerien = 1.5; 
    double facteurPleineTerre = 4;
    double facteurConduite = 1;
    
    double toleranceNoeud; // en mètres
    double seuilToleranceGC2; // en mètres
    
    public void setModuleTopo(double toleranceNoeud, double seuilToleranceGC2){
        this.toleranceNoeud = toleranceNoeud;
        this.seuilToleranceGC2 = seuilToleranceGC2;
    }
    
    double distMaxNRONRA = 10000;
    int nbLignesMinNRO = 1000;
    
    public void setNRO(int nbLignesMinNRO, double distMaxNRONRA){
        this.distMaxNRONRA = distMaxNRONRA;
        this.nbLignesMinNRO = nbLignesMinNRO;
    }
    
    private Map<String,List<String>> dptsLimitrophes;
    
    public void readDptsLimitrophes(){
        String ligne;
        String[] donneesLigne;
        dptsLimitrophes = new HashMap<>();
        try{
            BufferedReader limitrophes = new BufferedReader(new FileReader(cheminReseau+"liste-de-departements-limitrophes-francais.txt"));
            while ((ligne = limitrophes.readLine()) != null) {
                donneesLigne = ligne.split(";");
                List<String> voisins = new ArrayList<>();
                for (int i = 1; i < donneesLigne.length; i++) {
                    ligne = donneesLigne[i];
                    if (!ligne.equals("") && !ligne.equals("0")) {
                        voisins.add(donneesLigne[i]);
                    }
                }
                dptsLimitrophes.put(donneesLigne[0],voisins);
            }
            limitrophes.close();
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    ///// Paramètres réseau
    
    public boolean NROauxNRA = false;
    private int seuilPMint = 12;
    private int seuilPM_ZMD = 300;
    private int seuilPM_ZTD_BD = 300;
    private int seuilPM_ZTD_HD = 100;
    double maxMedianeDistPMPBO = 5000;
    double distMinPM = 50; // en mètres
    
    public void setPM(int seuilPM_ZMD, int seuilPM_ZTD_BD, double maxMedianeDistPMPBO, int seuilPM_ZTD_HD, int seuilPMint){
        this.maxMedianeDistPMPBO = maxMedianeDistPMPBO; // en mètres
        this.seuilPM_ZMD = seuilPM_ZMD;
        this.seuilPM_ZTD_BD = seuilPM_ZTD_BD;
        this.seuilPM_ZTD_HD = seuilPM_ZTD_HD;
        this.seuilPMint = seuilPMint;
    }
    
    private double tauxCouplage;
    int nbMinFibreCollectePM300;
    int nbMinFibreCollectePM100;
    
    public void setTransportOptique(double tauxCouplage, int nbMinFibreCollectePM300, int nbMinFibreCollectePM100){
        this.tauxCouplage = tauxCouplage;
        this.nbMinFibreCollectePM300 = nbMinFibreCollectePM300;
        this.nbMinFibreCollectePM100 = nbMinFibreCollectePM100;
        
    }
    
    private Map<String,Map<String,Integer>> demande;
    
    public void initDemande(){
        demande = new HashMap<>();
        String[] zonesMacro = new String[]{"ZTD", "ZMD_AMII", "ZMD_RIP"};
        for (String dpt : this.nomDepartements.keySet()){
            Map<String, Integer> demandeCible = new HashMap<>();
            for (String zone : zonesMacro){
                demandeCible.put(zone, 0);
            }
            demande.put(dpt, demandeCible);
        }
    }
    
    public void addDemande(String fichier){
        String line, dpt;
        String[] fields;
        String[] zonesMacro = new String[]{"ZTD", "ZMD_AMII", "ZMD_RIP"};
        try{
            BufferedReader reader = new BufferedReader(new FileReader(fichier));
            reader.readLine();
            while((line = reader.readLine())!=null){
                fields = line.split(";");
                dpt = fields[0];
                if (dpt.length() == 1)
                    dpt = "0"+dpt;
                if (demande.containsKey(dpt)){
                    for (int i = 0;i<3;i++){
                            demande.get(dpt).put(zonesMacro[i],demande.get(dpt).get(zonesMacro[i])+Integer.parseInt(fields[i+1]));
                    }
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    ///// Paramètres de dimensionnement des unités d'oeuvre
    
    int nbMaxLignesPBO;
    List<Integer> modesPoseOrdonnes;
    
    private int nbMaxLignesPM_ZMD;
    private int nbMaxLignesPM_ZTD_BD;
    private int nbMaxLignesPM_ZTD_HD;
    private int nbMaxLignesPM_ZTD_HD_int;
    
    public void setNbMaxLignesPM(int nbMaxLignesPM_ZMD, int nbMaxLignesPM_ZTD_BD, int nbMaxLignesPM_ZTD_HD, int nbMaxLignesPM_ZTD_HD_int){
        this.nbMaxLignesPM_ZMD = nbMaxLignesPM_ZMD;
        this.nbMaxLignesPM_ZTD_BD = nbMaxLignesPM_ZTD_BD; 
        this.nbMaxLignesPM_ZTD_HD = nbMaxLignesPM_ZTD_HD; 
        this.nbMaxLignesPM_ZTD_HD_int = nbMaxLignesPM_ZTD_HD_int;
    }
    
    public int getNbMaxLignesPM(String zone){
        switch(zone){
            case "ZMD":
                return nbMaxLignesPM_ZMD;
            case "ZTD_BD":
                return nbMaxLignesPM_ZTD_BD; 
            case "ZTD_HD":
                return nbMaxLignesPM_ZTD_HD; 
            case "ZTD_HD_int":
                return nbMaxLignesPM_ZTD_HD_int;
            default:
                return -1;
        }
    }
    
    double facteurSurcapaciteDistri;
    double facteurSurcapaciteTransport;
    double longFibreSupp;
    double seuil_boitier;
    
    public void setUO(double facteurSurcapaciteDistri, double facteurSucaocaciteTransport, double longFibreSupp, double seuil_boitier){
       this.facteurSurcapaciteDistri = facteurSurcapaciteDistri;
       this.facteurSurcapaciteTransport = facteurSucaocaciteTransport;
       this.longFibreSupp = longFibreSupp;
       this.seuil_boitier = seuil_boitier;
    }
    
    // pour l'instant on ne compte pas les coupleurs aux PM donc ces trois paramètrs ne sont pas utilisés
    private int coupleurPM300;
    private int coupleurPM100;
    private int coupleurPMint;
    
    public void setCoupleurs(int coupleurPM300, int coupleurPM100, int coupleurPMint){
        this.coupleurPM300 = coupleurPM300;
        this.coupleurPM100 = coupleurPM100;
        this.coupleurPMint = coupleurPMint;
    }
    
    public final int[] calibre = {6,12,24,48,72,96,144,288,576,720}; // tous les calibres possibles
    private final double[] diametresAerien = {6.1,6.1,8.3,9.4,10.7,11.3,11.3,14.6,18,18.5}; // en mm
    private final double[] diametresSouterrain = {6.1,6.1,8.4,8.4,10.2,12,12,13.2,18,18.5}; // en mm
    private int indiceCalibreMaxCablesSouterrain;
    private int indiceCalibreMaxCablesAerien;
    private int indiceCalibreMinHorizontal;
    
    public void setCalibresLimites(int tailleMaxSouterrain, int tailleMaxAerien, int tailleMinHorizontal){
        for (int i = 0; i<calibre.length;i++){
            if (tailleMaxSouterrain == calibre[i]){
                this.indiceCalibreMaxCablesSouterrain = i;
                System.out.println("indice max souterrain : "+i);
            }
            if (tailleMaxAerien == calibre[i]){
                this.indiceCalibreMaxCablesAerien = i;
                System.out.println("indice max aérien : "+i);
            }
            if (tailleMinHorizontal == calibre[i]){
                this.indiceCalibreMinHorizontal = i;
                System.out.println("indice min horizontal : "+i);
            }
        }
    }
    
    
    
    double partReconstrConduite;
    double partReconstrAerien;
    double partReconstrPTAerien;
    
    public void setGC(double partReconstrConduite, double partReconstrAerien, double partReconstrPTAerien){
        this.partReconstrConduite = partReconstrConduite;
        this.partReconstrAerien =  partReconstrAerien;
        this.partReconstrPTAerien = partReconstrPTAerien;
    }
    
    int nbFibresParTiroir;
    int nbTiroirsParRTO;
    double surfaceRTO ;
    double facteurSurface;
    
    public void setDimensionnementNRO(int nbFibresParTiroir, int nbTiroirsParRTO, double surfaceBaie, double facteurGlobal){
        this.nbFibresParTiroir = nbFibresParTiroir;
        this.nbTiroirsParRTO = nbTiroirsParRTO;
        this.surfaceRTO = surfaceBaie;
        this.facteurSurface = facteurGlobal;
    }
    
    public Parametres(String cheminReseau){
        this.cheminReseau = cheminReseau;
        this.modesPoseOrdonnes = new ArrayList(15);
        this.modesPoseOrdonnes.add(3);
        this.modesPoseOrdonnes.add(6);
        this.modesPoseOrdonnes.add(8);
        this.modesPoseOrdonnes.add(7);
        this.modesPoseOrdonnes.add(9);
        this.modesPoseOrdonnes.add(10);
        this.modesPoseOrdonnes.add(5);
        this.modesPoseOrdonnes.add(12);
        this.modesPoseOrdonnes.add(11);
        this.modesPoseOrdonnes.add(14);
        this.modesPoseOrdonnes.add(4);
        this.modesPoseOrdonnes.add(13);
        this.modesPoseOrdonnes.add(1);
        this.modesPoseOrdonnes.add(0);
        this.modesPoseOrdonnes.add(2);
    }
      
    ////// Fonctions d'utilisation des paramètres
    
    /// généraux
    
    public String getNomDepartement(String dpt){
        return nomDepartements.get(dpt);
    }
    
    public List<String> getListeDpts(){
        return listeDpt;
    }
    
    public boolean existeGC(String dpt){
        return !listeDptRoutier.contains(dpt);
    }
    
    public Set<String> getCommunes(String dpt){
        return listeCommunes.get(dpt);
    }

    public static double arrondir(double valeur, int nbChiffresVirgule) {
        return ((double) Math.round(valeur * Math.pow(10, nbChiffresVirgule))) / Math.pow(10, nbChiffresVirgule);
    }
    
    /// topo
    
    public double multiple (int modePose) throws Exception{
        if (modePose == -1){
            throw new Exception("Le mode de pose n'a pas été renseigné au préalable !");
        } else {
            double multiplicateur = facteurConduite; // modePose = 3,5,6,7,8,9,10
            switch(modePose){
                case 0:
                case 1:
                case 2:
                    multiplicateur = facteurAerien;
                    break;
                case 4:
                case 11:
                case 12:
                    multiplicateur = facteurPleineTerre;
                    break;
            }
            return multiplicateur;
        }
    }
    
    public List<String> getLimitrophes(String dpt){
        return dptsLimitrophes.get(dpt);
    }
    
    /// réseau
    
    public int seuilPM(String zone){
        switch(zone){
            case "int":
                return seuilPMint;
            case "ZTD_HD":
                return seuilPM_ZTD_HD;
            case "ZTD_BD":
                return seuilPM_ZTD_BD;
            default:
                return seuilPM_ZMD;
        }
    }
    
    public double tauxCouplage(String zone){
        return tauxCouplage;
    }
    
    public int nbFibresMin(String zone){
        switch(zone){
            case "ZMD":
            case "ZTD_BD":
                return nbMinFibreCollectePM300;
            default:
                return nbMinFibreCollectePM100;
        }
    }
    
    public Map<String,Integer> getDemande(String dpt){
        return demande.get(dpt);
    }
    
    public int getCalibreMax(int modePose){
        if (modePose <= 2 || modePose == 13) return this.indiceCalibreMaxCablesAerien;
        else return this.indiceCalibreMaxCablesSouterrain;
    }
    
    public int getCalibreMin(){
        return this.indiceCalibreMinHorizontal;
    }
    
    public double[] getDiametres(int modePose){
        if (modePose <= 2 || modePose == 13) return this.diametresAerien;
        else return this.diametresSouterrain;
    }
    
    public void zipShp(String nomFichier) {

        int BUFFER = 2048;

        BufferedInputStream origin;
        String nomGenerique = nomFichier.replace(".shp", "");
        String fichiers[] = new String[4];
        fichiers[0] = nomFichier;
        fichiers[1] = nomGenerique + ".dbf";
        fichiers[2] = nomGenerique + ".shx";
        fichiers[3] = nomGenerique + ".prj";

        byte data[] = new byte[BUFFER];

        try {

            FileOutputStream dest = new FileOutputStream(nomGenerique + ".zip");
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

            for (int i = 0; i < 4; i++) {

                FileInputStream fi = new FileInputStream(fichiers[i]);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry((new File(fichiers[i])).getName());
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
            out.close();

        } catch (Exception e) {
        }
    }
    
}
