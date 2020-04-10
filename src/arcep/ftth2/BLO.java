
package arcep.ftth2;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import java.io.*;
import java.util.*;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;

public class BLO {
    
    private final String codeNRO;
    private AreteBLOM root; // arête virtuelle racine de l'arbre représentant le réseau
    private final String zoneMacro; // ZTD ou ZMD_AMII ou ZMD_RIP
    private final Set<String> zones; // ZTD_HD, ZTD_BD ou ZMD
    private int nbLignes;
    private final Parametres parametres;
    
    private List<Noeud> listePMint;
    private Map<String,List<ZAPM>> ResParPM; // par zone, une liste de classe où sont stockés UO et autres infos, par PM
    private Map<String,UO> transports;
    private UO uo;
    private Couts c;
    
    public BLO(String codeNRO, int centre, Map<Integer,Noeud> noeuds, Map<Long,Arete> aretes,  String zone, Parametres parametres) {
        System.out.println("Zone : "+zone);
        System.out.println("Nombre de noeuds : "+ noeuds.size());
        System.out.println("Nombre d'arêtes : "+ aretes.size());
        this.codeNRO = codeNRO;
        
        // passage des zones "vision macro" (ZTD/AMII/RIP) au zones "architecture réseau" (ZTD_HD, ZTD_BD, ZMD)
        zoneMacro = zone;
        zones = new HashSet<>(); 
        if (zone.equals("ZTD")){
            zones.add("ZTD_HD");
            zones.add("ZTD_BD");
        }
        else zones.add("ZMD");
        
        this.parametres = parametres;
        root = new AreteBLOM(noeuds.get(centre));
        buildTree(noeuds,aretes);
        System.out.println("Le NRO "+codeNRO+" correspond au noeud réseau : " + root.n.id);
    }
    
    private void buildTree(Map<Integer,Noeud> noeuds, Map<Long,Arete> aretes){
        System.out.println("Construction de l'arbre en AreteBLOM");
        for (Noeud noeud : noeuds.values()){
            if (noeud.demandeLocaleTotale() > 0){
                List<Long> path = noeud.getPath();
                if (path == null) {
                    System.out.println("Path null pour le noeud " + noeud.id);
                } else {
                    //System.out.println("On accroche à l'arbre le noeud n°" + noeud.id);
                    AreteBLOM mere = root;
                    for (Long id : path){
                        boolean ajout = true;
                        for (AreteBLOM fille : mere.getFilles()){
                            if (fille.id == id){
                                ajout = false;
                                fille.addNoeudLocal(noeud);
                                //System.out.println("L'arête "+fille.id+" a "+fille.lignesAval(zone)+" lignes.");
                                mere = fille;
                                break;
                            }
                        }
                        if (ajout){
                            //System.out.println("Id de l'arête à ajouter : "+id);
                            Arete a = aretes.get(id);
                            //System.out.println("Id de l'arête mère : "+mere.id);
                            //System.out.println("Id du noeud commun : "+mere.n.id);
                            Noeud n = noeuds.get(a.getAutreExtremite(mere.n.id));
                            //System.out.println("Id du noeud aval : "+n.id);
                            try{
                                AreteBLOM fille = new AreteBLOM(a, n, mere.n);
                                fille.addNoeudLocal(n);
                                //System.out.println("L'arête "+fille.id+" a "+fille.lignesAval(zone)+" lignes.");
                                mere.add(fille);
                                mere = fille;
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        root.calculDemandeRoot();
    }
    
    public void addTreeToShape(GeometryFactory gf, SimpleFeatureCollection collection,SimpleFeatureBuilder featureBuilderLineaires){
        for (String zone : zones){
            root.addTreeToShape(codeNRO, zone, gf, collection, featureBuilderLineaires);
        }
    }
    
    public void simplification(){
        //Construction de l'arbre orienté simplifié
        AreteBLOM newRoot = new AreteBLOM(root, 0);
        System.out.println("Demande locale au NRO : "+newRoot.n.demandeLocaleTotale());
        for (AreteBLOM ar : root.getFilles()){
            ar.fusionAretes(newRoot,0);
        }
        this.root = newRoot;
        nbLignes = this.root.totalLignes();
        System.out.println("Nb de lignes au NRO : "+nbLignes);
    }
    
    public void posePMext(){
        for (String zone : zones){
            int nbPM = root.posePMExt(zone, parametres.maxMedianeDistPMPBO, parametres.seuilPM(zone));
            System.out.println("Nombre de PM en zone "+zone+" : "+nbPM);
        }
    }
    
    public void listingPMint(){
        listePMint = new  ArrayList<>();
        root.listingPMint(listePMint, 0);
    }
    
    public void calculDemande(){
        root.calculDemande(zones, parametres); // fonction récursive
    }
    
    public void setModesPose(String dossier){
        
        double[] pourcentages = this.getPourcentagesGC(codeNRO, dossier, "ModesPose");
        double pourcentageAerien = pourcentages[0]+pourcentages[1]+pourcentages[2];
        double pourcentagePT = pourcentages[3];
        
        int niveauRoot = root.calculNiveau();
        double longueurTotale = root.longueurTotale();
        
        double seuilAerien = longueurTotale*pourcentageAerien;
        double longueurPT = longueurTotale*pourcentagePT;
        double seuilPleineTerreAerien = seuilAerien + longueurPT*parametres.partReconstrPTAerien;
        double seuilPleineTerreSouterrain = seuilAerien + longueurPT;
        
        int niveau = 1;
        double longueurParcourue = 0.0;
        while (niveau < niveauRoot){
            longueurParcourue = root.setModesPose(niveau, seuilAerien, seuilPleineTerreAerien, seuilPleineTerreSouterrain, longueurParcourue);
            niveau++;
        }
        
        System.out.println("Longueur totale : "+longueurTotale);
        System.out.println("LongueurP finale : "+longueurParcourue);
        System.out.println("Niveau de la racine : "+niveauRoot);
    }
    
    public void calculerUOAretes(){
        for (AreteBLOM a : root.getFilles()){
            a.calculUO(zones, parametres); // fonction récursive
        }
    }
    
    public void calculResultatsParPM(){
        ResParPM = new HashMap<>();
        transports = new HashMap<>();
        for (String zone : zones){
            // distribution
            List<ZAPM> res;// = new ArrayList<>();
            res = root.agregeResultats(zone, 0, parametres);
            ResParPM.put(zone, res);
            System.out.println("Nombre de ZAPM en zone "+zone+" : "+(res.size()));
            
            // transport
            UO transport = new UO(zone, false, true, parametres);
            for (AreteBLOM a : root.getFilles()){
                a.calculTransport(transport, parametres);
                transport.addIncomingCables(a.getCables(false, zone), false, parametres.calibre, parametres.getCalibreMin());
            }
            transports.put(zone, transport);   
        }
    }
    
    public UO computeAndGetUo(int[] immeubles){
        this.uo = new UO(zoneMacro, true, true, parametres);
        for (String zone : zones){
            for (ZAPM zapm : ResParPM.get(zone)){
                this.uo.addUO(zapm.getUO());
            }
            this.uo.addUO(transports.get(zone));
        }
        if (zoneMacro.equals("ZTD"))
            this.uo.setPMint(listePMint, parametres);
        this.uo.setVertical(immeubles, parametres);
        this.uo.setNRO(parametres);
        return uo;
    }
    
    public List<String> stringsUO(int detail){
        List<String> liste = new ArrayList<>();
        for (String zone : zones){
            int i = 1;
            for (ZAPM zapm : this.ResParPM.get(zone)){
                liste.add(zoneMacro+";"+codeNRO+";"+zapm.getStringUO(codeNRO+"_"+zone+"_"+i, parametres, detail));
                i++;
            }
            liste.add(zoneMacro+";"+codeNRO+";Transport_"+zone+";"+";"+";"+";"+transports.get(zone).print(parametres, detail));
        }
        return liste;
    }
    
    public List<String> stringsDistriLongueurs(){
        List<String> liste = new ArrayList<>();
        for (String zone : zones){
            int i = 1;
            for (ZAPM zapm : this.ResParPM.get(zone)){
                //if (zapm.isDistri){
                    liste.add(zoneMacro+";"+codeNRO+";"+codeNRO+"_"+zone+"_"+i+";"+zapm.getStringDistri());
                    i++;
                //}
            }
        }
        return liste;
    }
    
    public void shapefilePM(GeometryFactory gf, SimpleFeatureCollection collectionPM, SimpleFeatureBuilder featureBuilderPM){
        for(String zone : zones){
            int i = 1;
            for (ZAPM zapm : ResParPM.get(zone)){
                i = zapm.toShapefilePM(codeNRO, gf, collectionPM, featureBuilderPM, i);
            }
            if (zone.equals("ZTD_HD")){
                for (Noeud pm : listePMint){
                    featureBuilderPM.add(gf.createPoint(new Coordinate(pm.coord[0], pm.coord[1])));
                    featureBuilderPM.add(codeNRO+"_"+zone+"_"+i);
                    featureBuilderPM.add("PM_int");
                    featureBuilderPM.add(pm.demandeLocaleInt());
                    collectionPM.add(featureBuilderPM.buildFeature(null));
                    i++;
                }
            }
        }
    }
    
    public Couts computeAndGetCouts(CoutsUnitaires couts){
        c = uo.calculCouts(couts, parametres);
        return c;
    }
    
    public int[] getAnalyseGC(){
        int[] result = new int[13];
        for (String zone : this.zones){
            if (zones.contains(zone)){
                for (ZAPM zapm : ResParPM.get(zone)){
                    int[] boitiers = zapm.getPBOGC();
                    for (int i = 0;i<13;i++){
                        result[i] += boitiers[i];
                    }
                }
            }
        }
        return result;
    }
    
    public void clearArbre(){
        root = null; // on libère de la place en mémoire car on n'a plus besoin de l'arbre
    }
    
    public List<String> getNROPM(String dpt){
        List<String> res = new ArrayList<>();
        String debut = dpt+";"+zoneMacro+";"+codeNRO;
        for(String zone : zones){
            for (ZAPM zapm : ResParPM.get(zone)){
                res.add(debut+";"+(res.size()+1)+";"+zapm.getNROPM(parametres)+";"+String.valueOf(zapm.percentile(0.10)).replace(".", ",")+";"+String.valueOf(zapm.percentile(0.50)).replace(".", ",")+";"+String.valueOf(zapm.percentile(0.90)).replace(".", ","));
            }
            if (zone.equals("ZTD_HD")){
                for (Noeud pm : listePMint){
                    res.add(debut+";"+(res.size()+1)+";PMint;"+String.valueOf(pm.distanceAuCentre).replace(".", ",")+";"+pm.demandeLocaleInt()); 
                }
            }
            res.add(debut+";0;NRO;;"+transports.get(zone).printIncomingCables(false));
        }
        return res;
    }
    
    private double[] getPourcentagesGC(String codeNRO, String dossierNRO, String pourcentages){
        int n = 11;
        double[] res = new double[n];
        String line;
        String[] fields;
        File modesPose = new File(dossierNRO+"/"+pourcentages+"_"+codeNRO+".csv");
        try{
            BufferedReader reader = new BufferedReader(new FileReader(modesPose));
            reader.readLine();
            line = reader.readLine();
            fields = line.split(";");
            if (fields[0].equals(codeNRO)){
                for (int i = 0;i<n;i++){
                    res[i] = Double.parseDouble(fields[i+1].replace(",","."));
                }
            }
        }catch(IOException e){}
        return res;
    }
    
}
