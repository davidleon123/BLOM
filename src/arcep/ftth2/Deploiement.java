package arcep.ftth2;

import java.io.*;
import java.util.*;

import com.vividsolutions.jts.geom.*;

import org.geotools.data.*;
import org.geotools.data.simple.*;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.*;
import org.geotools.feature.simple.*;

import org.opengis.feature.simple.*;
import javax.swing.*;

public class Deploiement implements Runnable{// {

    FenProgression fen;
    
    private int nbDptTraites;
    final Parametres parametres;
    final CoutsUnitaires coutsUnitaires;
    final String cheminReseau;
    final String racineResultats;
    final String dossierNRO;
    final String dossierBLO;
            
    public Deploiement(Parametres parametres, CoutsUnitaires couts, String cheminReseau, String cheminResultats, String dossierNRO, String dossierBLO) {
        this.parametres = parametres;
        this.coutsUnitaires = couts;
        this.cheminReseau = parametres.cheminReseau;
        this.racineResultats = parametres.racineResultats;
        this.dossierNRO = dossierNRO;
        this.dossierBLO = dossierBLO;
    }
    
   @Override
    public void run() {
        evaluation();
    }

    private void evaluation() {
        
        fen.afficher("Initialisation");
        long start = System.currentTimeMillis();

        boolean fichiersTest = false;
                
        System.out.println("Le set parametres.zones :");
        for (String zone : parametres.zones){
            System.out.println(zone);
        }
        
        try {
            
            fen.initBarre(parametres.getListeDpts().size());
            nbDptTraites = 0;
            
            ////////////////////////////////////////////////////////
            /// INITIALISATION  DES FICHIERS DE SORTIE GLOBAUX
            ////////////////////////////////////////////////////////
            
            fen.afficher("Initialisation des fichiers de sortie");
            
            File dir = new File(racineResultats+"/Resultats_00_national");
            dir.mkdirs();
            
            PrintWriter csvUONatDetail = new PrintWriter(dir+"/UONatDetail_"+parametres.terminaison+".csv");
            csvUONatDetail.println("Departement;Zone;"+UO.header(parametres, true, true,2));
            
            PrintWriter csvUONatCouts = new PrintWriter(dir+"/UONatCouts_"+parametres.terminaison+".csv");
           csvUONatCouts.println("Zone;"+UO.header(parametres, true, true,1));
           
           PrintWriter csvUONatparNRO = new PrintWriter(dir+"/UONatparNRO_"+parametres.terminaison+".csv");
           csvUONatparNRO.println("Departement;Zone;NRO;"+UO.header(parametres, true, true,1));
            
            PrintWriter csvCoutsNat = new PrintWriter(dir+"/CoutsNat_"+parametres.terminaison+".csv");
            csvCoutsNat.println("Departement;Zone;"+Couts.header());
            
            PrintWriter csvAnalyseGC = new PrintWriter(dir+"/AnalyseGC_"+parametres.terminaison+".csv");
            csvAnalyseGC.println("Departement;Zone;Aerien Orange;Aerien Enedis;Façade;Immeuble;Pleine terre;Caniveau;Galerie;Conduite allegee;Egout;Conduite enrobee;Conduite mixte;Routier;Creation modele");
            
            PrintWriter csvAnalyseNROPM = new PrintWriter(dir+"/AnalyseNROPM_"+parametres.terminaison+".csv");
            csvAnalyseNROPM.print("Departement;Zone;NRO;IdPM;Type;Distance NRO-PM;Lignes");
            for (int i = parametres.getCalibreMin();i<=parametres.getCalibreMax(7);i++){
                csvAnalyseNROPM.print(";Nb cables_"+parametres.calibre[i]+" entrants");
            }
            csvAnalyseNROPM.println(";Nb fibres entrantes;Longueur moyenne;Premier decile;Mediane;Dernier decile");
            
            PrintWriter csvAnalyseDemande = new PrintWriter(dir+"/AnalyseDemande_"+parametres.terminaison+".csv");
            csvAnalyseDemande.println("Departement;ZTD;AMII;RIP");
            for (String dpt : parametres.getListeDpts()) {
                csvAnalyseDemande.print(dpt);
                Map<String, Integer> demandeCible = parametres.getDemande(dpt);
                String[] zones = {"ZTD","ZMD_AMII","ZMD_RIP"};
                for (String zone : zones){
                    csvAnalyseDemande.print(";"+demandeCible.get(zone));
                }
                csvAnalyseDemande.println();
            }
            csvAnalyseDemande.close();
            
            boolean coloriage = !this.dossierBLO.contains("-GC");
            
            Map<String, UO> uoZones = new HashMap<>();
            for (String zone : parametres.zones){
                uoZones.put(zone, new UO(zone, true, true, parametres));
            }
            
            ////////////////////////////////////////////////////////
            /// BOUCLE SUR LES DEPARTEMENTS
            ////////////////////////////////////////////////////////
            
            
            for (String dpt : parametres.getListeDpts()) {
                
                fen.afficher("Démarrage -  " + parametres.getNomDepartement(dpt));

                String ligne;
                String[] donneesLigne;
                
                
                //initialisation du dossier de sortie
                //date = new Date();
                String dptResultats = dpt + "_" + parametres.terminaison; // utilisé dans les noms de fichiers aussi
                String cheminResultats = racineResultats+ "/Resultats_"+dpt;
                dir = new File(cheminResultats);
                dir.mkdirs();
                 
                fen.afficher("Lecture du fichier immeubles");
                ///////////////////////////////////////////////
                //Lecture des immeubles du département (pour les coûts de colonne montante)
                //////////////////////////////////////////////////////////////
                
                HashMap<String, int[]> listeImmeubles = new HashMap<>();
                BufferedReader ficImmeubles = new BufferedReader(new FileReader(cheminReseau + "immeubles.csv"));
                ficImmeubles.readLine(); // on passe la ligne d'en-tête
                while ((ligne = ficImmeubles.readLine()) != null) {
                    donneesLigne = ligne.split(";");
                    if (donneesLigne[0].substring(0, 2).equals(dpt)) {
                        int[] nbImmeubles = new int[100];

                        for (int i = 1; i < 99; i++) {
                            if (!donneesLigne[i].equals("#DIV/0!")) {
                                nbImmeubles[i] = Integer.parseInt(donneesLigne[i]);
                            } else {
                                nbImmeubles[i] = 0;
                            }
                        }

                        listeImmeubles.put(donneesLigne[0], nbImmeubles);
                    }
                }                

                fen.afficher("Initialisation des fichiers de sortie");
                
                ////////////////////////////////////////////////////////
                /// INITIALISATION  DES FICHIERS DE SORTIE PAR DEPARTEMENT
                ////////////////////////////////////////////////////////
                
                
                SimpleFeatureTypeBuilder builderLineaires = new SimpleFeatureTypeBuilder();
                SimpleFeatureTypeBuilder builderPM = new SimpleFeatureTypeBuilder();
                
                builderLineaires.setName("Lineaires");
                builderPM.setName("PointsMutualisation");
                
                switch(dpt){
                    case "971":
                    case "972":
                        builderLineaires.setSRS("EPSG:32620"); //UTM 20N
                        builderPM.setSRS("EPSG:32620");
                        break;
                    case "973":
                        builderLineaires.setSRS("EPSG:32622"); //UTM 22N
                        builderPM.setSRS("EPSG:32622");
                        break;
                    case "974":
                        builderLineaires.setSRS("EPSG:32740"); //UTM 40S
                        builderPM.setSRS("EPSG:32740");
                        break;
                    default:
                        builderLineaires.setSRS("EPSG:27572"); //Lambert 2 Etendu
                        builderPM.setSRS("EPSG:27572");
                }

                builderLineaires.add("Arete", LineString.class);
                builderLineaires.add("ID", Long.class);
                builderLineaires.add("ModePose", Integer.class);
                builderLineaires.length(8).add("NRO", String.class);
                //builderLineaires.length(8).add("Zone", String.class);
                //builderLineaires.add("CodePM", String.class);
                //builderLineaires.add("Transport", Integer.class);
                //builderLineaires.add("Distri", Integer.class);
                builderLineaires.add("NbLignes", Long.class);

                builderPM.add("Point", Point.class);
                builderPM.length(30).add("ID", String.class);
                builderPM.length(20).add("Type", String.class);
                builderPM.add("NbLignes", Long.class);
                
                SimpleFeatureType formatShpLineaires = builderLineaires.buildFeatureType();
                SimpleFeatureType formatShpPM = builderPM.buildFeatureType();

                SimpleFeatureCollection collectionLineaires = FeatureCollections.newCollection();
                SimpleFeatureCollection collectionPM = FeatureCollections.newCollection();
                
                SimpleFeatureBuilder featureBuilderLineaires = new SimpleFeatureBuilder(formatShpLineaires);
                SimpleFeatureBuilder featureBuilderPM = new SimpleFeatureBuilder(formatShpPM);
                
                GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 27572);

                // initialisation des fichiers de sortie UO et coûts
                PrintWriter UOparPM = null; // fichier des UO par PM (GC, câbles, boîtiers)
                if (parametres.ficUnitesOeuvre) {
                    UOparPM = new PrintWriter(new FileWriter(cheminResultats + "/UnitesOeuvreParPM_"+ dptResultats +".csv"));
                    UOparPM.println("Zone;NRO;PM;X;Y;Distance NRO-PM;"+UO.header(parametres, true, false, 0));
                }
                
                PrintWriter csvDistributionL = null; // fichier de la distribution des longueurs de lignes PM-PBO par PM
                if (parametres.ficLongueurs) {
                    csvDistributionL = new PrintWriter(new FileWriter(cheminResultats + "/LongueursParPM_"+ dptResultats +".csv"));

                    String header = "Zone;NRO;PM;Distance NRO-PM;Longueur min;Longueur max";
                    for (int i = 0; i < 2000; i++) {
                        header += ";" + i;
                    }
                    csvDistributionL.println(header);
                }

                PrintWriter csvCouts = null; // fichier des coûts par PM
                if (parametres.ficCouts) {
                    csvCouts = new PrintWriter(new FileWriter(cheminResultats + "/CoutsParNRO_"+ dptResultats +".csv"));
                    csvCouts.println("Zone;NRO;"+Couts.header()); 
                }

                // initialisation des fichiers utilisés seulement lorsque fichiersTest = true
                
                HashMap<Integer, HashMap<Integer, Double>> statsRoutes = null;            
                PrintWriter csvListePC = null;
                PrintWriter csvAnalyseRoute = null;
                PrintWriter csvAnalysePCIsoles5pc = null;
                PrintWriter csvAnalysePCIsoles20pc = null;
                PrintWriter csvAnalysePCIsoles30pc = null;
                
                if (fichiersTest) {
                    
                    statsRoutes = new HashMap<>();
                    for (int i = 0; i < 9; i++) {
                        statsRoutes.put(i, new HashMap<Integer, Double>());
                    }
                    
                    csvListePC = new PrintWriter(new FileWriter(cheminResultats + "/PCParPM_"+ dptResultats +".csv"));
                    csvListePC.println("NRO;PM;PC");

                    csvAnalyseRoute = new PrintWriter(new FileWriter(cheminResultats + "/AnalyseRoute_"+ dptResultats +".csv"));
                    String sCSV = "Dpt;ModePose;";
                    for (int i = 1; i <= 200; i++) {
                        sCSV += ";" + i;
                    }
                    csvAnalyseRoute.println(sCSV);

                    csvAnalysePCIsoles5pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles5pc_"+ dptResultats +".csv"));
                    csvAnalysePCIsoles5pc.println("Dpt;NRO;PC");

                    csvAnalysePCIsoles20pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles20pc_"+ dptResultats +".csv"));
                    csvAnalysePCIsoles20pc.println("Dpt;NRO;PC");

                    csvAnalysePCIsoles30pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles30pc_"+ dptResultats +".csv"));
                    csvAnalysePCIsoles30pc.println("Dpt;NRO;PC");
                }       

                int[] analyseGC = new int[13];
                
                int nbNRO = 0;
                File d = new File(cheminReseau + dossierBLO+"/"+dossierBLO+"_"+dpt);
                for (File f : d.listFiles()){
                    if (parametres.zones.contains(f.getName().replace(dossierBLO+"_"+dpt+"_", "")))
                        nbNRO+=(f.listFiles().length/3);
                }
                
                fen.initBarreDpt(nbNRO);
                
                Map<String, Integer> demandeDpt = parametres.getDemande(dpt);
                
                ////////////////////////////////////////////////////////
                /// BOUCLE SUR LES ZONES MACRO CHOISIES PAR L'UTILISATEUR
                ////////////////////////////////////////////////////////

                for (String zone : parametres.zones){
                
                    File ficNRO = new File(cheminReseau + dossierNRO+"/"+dossierNRO+"_"+dpt+"/"+dossierNRO+"_"+dpt+"_"+zone+"/"+dossierNRO+"_"+dpt+"_"+zone+".csv");
                    
                    if (!ficNRO.exists()){
                        System.out.println("Le département "+dpt+" et la zone "+zone+" sont d'intersection vide");
                    } else {
                        
                        // lecture du regroupement des NRA en NRO
                        HashMap<String, ArrayList<String>> NRONRA = new HashMap<>();
                        BufferedReader fichierNRO = new BufferedReader(new FileReader(ficNRO));
                        fichierNRO.readLine();
                        while ((ligne = fichierNRO.readLine()) != null) {
                            donneesLigne = ligne.split(";");
                            String nro, nra;
                            nra = donneesLigne[1];
                            if (parametres.NROauxNRA) nro = nra;
                            else nro = donneesLigne[0];
                            if (!NRONRA.containsKey(nro)) NRONRA.put(nro, new ArrayList<String>());
                            NRONRA.get(nro).add(nra);
                        }
                        Set<String> listeNRO = NRONRA.keySet();
                        UO uo = new UO(zone, true, true, parametres);
                        
                        
                        Couts couts = new Couts(); 
                        String dossier = cheminReseau+dossierBLO+"/"+dossierBLO+"_"+dpt+"/"+dossierBLO+"_"+dpt+"_"+zone;
                 
                        ////////////////////////////////////////////////////////
                        /// AJUSTEMENT DE LA DEMANDE CONSTRUITE A LA DEMANDE CIBLE
                        ////////////////////////////////////////////////////////

                        
                        Map<String,Map<Integer,Noeud>> noeudsNRO = new HashMap<>();
                        List<Noeud> noeudsDemande = new ArrayList<>();
                        int demandeConstruite = 0;
                        
                        // on remplit noeudsNRO, et noeudsDemande par effet de bord
                        int seuilPMint = parametres.seuilPM("int");
                        for (String codeNRO : listeNRO){
                            Map<Integer,Noeud> listeNoeudsNRO = Reseau.readNoeudsSelectDemande(dossier,codeNRO, noeudsDemande, seuilPMint);
                            noeudsNRO.put(codeNRO, listeNoeudsNRO);
                            for (Noeud n : listeNoeudsNRO.values()){
                                demandeConstruite+=n.demandeLocaleTotale();
                            }
                        }
                        System.out.println("Demande totale construite dans le département pour la zone "+zone+" :"+demandeConstruite);

                        this.adapteDemande(noeudsDemande, demandeConstruite, Math.max(demandeDpt.get(zone), demandeConstruite), zone, seuilPMint);
                        int newDemande = 0;
                        for (Noeud n : noeudsDemande){
                            newDemande += n.demandeLocaleTotale();
                        }
                        System.out.println("Nouvelle demande en "+zone+" : "+newDemande);
                        

                        ////////////////////////////////////////////////////////
                        /// BOUCLE SUR LES NRO 
                        ////////////////////////////////////////////////////////         
                
                        for (String codeNRO : listeNRO) {
                            fen.debutNRA("NRO " + codeNRO.replace("\"", ""));
                            
                            BLO blo = new BLO(codeNRO, Reseau.readCentre(dossier, codeNRO), noeudsNRO.get(codeNRO), Reseau.readAretes(dossier,codeNRO), zone, parametres);
                            blo.simplification(); // on fusionne les arêtes qui peuvent l'être
                            blo.posePMext();
                            blo.listingPMint();
                            blo.calculDemande(); // (on répartit entre mutu et non mutu, essentiellement)
                            if (coloriage) blo.setModesPose(dossier);
                            blo.calculerUOAretes(); // arête par arête
                            blo.calculResultatsParPM(); // là on agrège en parcourant l'arbre encore une fois
                            
                            if (parametres.ficLineaires) blo.addTreeToShape(gf, collectionLineaires, featureBuilderLineaires);
                            
                            int[] immeubles = immeubles(NRONRA.get(codeNRO), listeImmeubles);
                            UO uoNRO = blo.computeAndGetUo(immeubles);
                            csvUONatparNRO.println(dpt+";"+zone+";"+codeNRO+";"+uoNRO.print(parametres, 1));
                            uo.addUO(uoNRO);
                                                
                            blo.clearArbre(); // attention ici on suppose qu'on n'a plus besoin de l'arbre contenu dans blo et on fait blo.root = null
                            
                            ////////////////////////////////////////////////////////
                            /// IMPRESSION DANS LES FICHIERS DE SORTIE
                            ////////////////////////////////////////////////////////
                            
                            if (parametres.ficUnitesOeuvre){
                                for (String s : blo.stringsUO(0)){
                                    UOparPM.println(s);
                                }
                            }
                            
                            Couts c = blo.computeAndGetCouts(coutsUnitaires);
                            couts.add(c);
                            if(parametres.ficCouts){
                                csvCouts.println(zone+";"+codeNRO+";"+c.print());
                            }
                            
                            if (parametres.ficLongueurs){
                                for (String s : blo.stringsDistriLongueurs()){
                                    csvDistributionL.println(s);
                                }
                            }
                            if (parametres.ficPM){
                                blo.shapefilePM(gf, collectionPM, featureBuilderPM);
                            }

                            for(String s : blo.getNROPM(dpt)){
                                csvAnalyseNROPM.println(s);
                            }

                            int[] gc = blo.getAnalyseGC();
                            for (int i = 0;i<analyseGC.length;i++){
                                analyseGC[i] += gc[i];
                            }
                            
                            
                            System.out.println("Fin du NRO n°" + codeNRO);
                            fen.finNRA();
                        }
                
                        // "flush" des fichiers de résultats départementaux
                        if (parametres.ficUnitesOeuvre)
                            UOparPM.flush();

                        if (parametres.ficLongueurs) 
                            csvDistributionL.flush();               

                        if (parametres.ficCouts) 
                            csvCouts.flush();                

                        if (fichiersTest) {
                            csvListePC.flush();
                            csvAnalysePCIsoles5pc.flush();
                            csvAnalysePCIsoles20pc.flush();
                            csvAnalysePCIsoles30pc.flush();
                            csvAnalyseRoute.flush();
                        }

                        uoZones.get(zone).addUO(uo);
                        
                        csvUONatDetail.println(dpt+";"+zone+";"+uo.print(parametres, 2));
                        csvCoutsNat.println(dpt+";"+zone+";"+couts.print());
                        csvAnalyseGC.print(dpt+";"+zone);
                        for (int i : analyseGC){
                            csvAnalyseGC.print(";"+i);
                        }
                        csvAnalyseGC.println();
                    }
                }   
                
                // Fermeture des fichiers de résultats
                if (parametres.ficUnitesOeuvre)
                    UOparPM.close();
                
                if (parametres.ficLongueurs) 
                    csvDistributionL.close();                
                
                if (parametres.ficCouts) 
                    csvCouts.close();                
                
                if (fichiersTest) {
                    csvListePC.close();
                    csvAnalysePCIsoles5pc.close();
                    csvAnalysePCIsoles20pc.close();
                    csvAnalysePCIsoles30pc.close();
                    csvAnalyseRoute.close();
                }
                
                //Creation des ShapeFiles
                
                if (parametres.ficPM) {
                    File shpLineaires = null;
                    shpLineaires = new File(cheminResultats + "/PM_" + dpt + ".shp");
                    
                    ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
                    ShapefileDataStore dataStorePM = (ShapefileDataStore) dataStoreFactory.createDataStore(shpLineaires.toURI().toURL());

                    dataStorePM.createSchema(formatShpPM);

                    Transaction transaction = new DefaultTransaction("create");

                    String typeName = dataStorePM.getTypeNames()[0];
                    SimpleFeatureSource featureSourceLineaire = dataStorePM.getFeatureSource(typeName);

                    if (featureSourceLineaire instanceof SimpleFeatureStore) {
                        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSourceLineaire;

                        featureStore.setTransaction(transaction);

                        featureStore.addFeatures(collectionPM);
                        transaction.commit();
                        transaction.close();
                        
                    } else {
                        System.out.println("Le Shapefile n'est pas accessible en écriture");
                    }
                }
                
                if (parametres.ficLineaires) {
                    File shpLineaires = new File(cheminResultats + "/Lineaires_" + dpt + ".shp");

                    ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
                    ShapefileDataStore dataStoreLineaires = (ShapefileDataStore) dataStoreFactory.createDataStore(shpLineaires.toURI().toURL());

                    dataStoreLineaires.createSchema(formatShpLineaires);

                    Transaction transaction = new DefaultTransaction("create");

                    String typeName = dataStoreLineaires.getTypeNames()[0];
                    SimpleFeatureSource featureSourceLineaire = dataStoreLineaires.getFeatureSource(typeName);
                    
                    if (featureSourceLineaire instanceof SimpleFeatureStore) {
                        System.out.println("Début de l'impression du fichier des linéaires");
                        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSourceLineaire;

                        featureStore.setTransaction(transaction);

                        featureStore.addFeatures(collectionLineaires);
                        transaction.commit();
                        transaction.close();
                    } else {
                        System.out.println("Le Shapefile n'est pas accessible en écriture");
                    }
                }
                
                fen.finTraitementDpt();
                nbDptTraites++;     
                System.out.println("Fin pour le département "+dpt);
            }
            
            
            for (String zone : parametres.zones){
                csvUONatCouts.println(zone+";"+uoZones.get(zone).print(parametres, 1));
            }
            
            csvUONatCouts.close();
            
            csvUONatDetail.close();
            csvUONatparNRO.close();
            csvCoutsNat.close();
            csvAnalyseGC.close();
            csvAnalyseNROPM.close();
            
            fen.finTraitement(System.currentTimeMillis() - start);

        } catch (Exception e) {
            //throw;
            JOptionPane.showMessageDialog(fen, e.toString());
            e.printStackTrace();
        }
    }
    
    public int getAvancement() {
        List<String> dpts = parametres.getListeDpts();
        if (dpts.isEmpty())
            return 0;
        else return Math.round(100*nbDptTraites/dpts.size());
    }
    
    private void adapteDemande(List<Noeud> noeudsDemande, int demandeConstruite, int demandeVoulue, String zone, int seuilPMint){
        System.out.println("Appel de adapteDemande");
        System.out.println("Zone : "+zone);
        System.out.println("Demande existante à partir du fichier des LP : "+demandeConstruite);
        System.out.println("Demande cible : "+demandeVoulue);
        int demande = demandeConstruite;
        int nbNoeuds = noeudsDemande.size();
        while (demande > demandeVoulue & nbNoeuds > 0){
            int i = (int) Math.floor(Math.random()*nbNoeuds);
            Noeud n = noeudsDemande.get(i);
            if (n.decreaseDemande(zone)){
                noeudsDemande.remove(n);
                nbNoeuds--;
            }
            demande--;
        }
        if (nbNoeuds > 0){
            while (demande < demandeVoulue){
                int i = (int) Math.floor(Math.random()*nbNoeuds);
                Noeud n = noeudsDemande.get(i);
                n.increaseDemande(zone, seuilPMint);
                demande++;
            }
        }
    }
    
    private int[] immeubles(List<String> NRAduNRO, Map<String, int[]> listeImmeubles){
        int[] res = new int[100];
        for (String codeNRA : NRAduNRO){
            int[] immeubles=listeImmeubles.get(codeNRA);
            if (immeubles != null) {
                for (int i = 0; i < immeubles.length; i++) { 
                    res[i]+= immeubles[i];
                }
            }
            else {
                System.out.println("Problème base immeubles pour le NRA n°"+codeNRA);
            }
        }
        return res;
    }
    
}