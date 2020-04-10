
package arcep.ftth2;

import java.io.*;
import java.util.*;
import javax.swing.JList;

public class ModuleTopo {
    
    final String cheminReseau;
    private Map<String,List<String>> dptsLimitrophes;
    
    public ModuleTopo(String cheminReseau){
        this.cheminReseau = cheminReseau;
    }
    
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
    
    public List<String> getLimitrophes(String dpt){
        return dptsLimitrophes.get(dpt);
    }
    
    public void pretraitementShapes(){
        Thread t = new Thread() {
            @Override
            public void run() {
                Shapefiles.conversion("Q:/Donnees limites administratives IGN/GEOFLA/FranceMetro",cheminReseau+"Dpts/FranceMetro","DEPARTEMENT","27572");
                Shapefiles.creerShpDptEtendus(5, cheminReseau, "Dpts");
                Shapefiles.conversion("U:/3-donnees mises a disposition - pour tout le monde/32-donnees metiers/323-BLF/ZTD", cheminReseau+"ZTD", "PBD_PHD_aggr", "27572");
                Shapefiles.conversion("U:/3-donnees mises a disposition - pour tout le monde/32-donnees metiers/323-BLF/AMII", cheminReseau+"AMII", "AMII", "27572");
            }
        };
        t.start();
    }
    
    public void pretraitementReseauCuivre(){
        Thread t = new Thread() {

            @Override
            public void run() {
                // Décompressez le zip des LP sur votre C:/Inputs/LP avant de lancer cette étape
                filtreDpts("C:/Inputs/LP", "Lignes principales en ND banalises.csv");
                prepareNRA(cheminReseau+"listeNRA.csv", cheminReseau+"NRA");
                prepareSR(cheminReseau+"listeSR.txt", cheminReseau+"SR");
                createPC("C:/Inputs/LP", cheminReseau+"PC1");
                Shapefiles.zonagePC(cheminReseau+"DptEtendus-5km", cheminReseau+"Communes", cheminReseau+"ZTD/PBD_PHD_aggr.shp", cheminReseau+"AMII/AMII.shp", cheminReseau+"PC1", cheminReseau+"PC2", dptsLimitrophes);
                compteLignesCuivre("Q:/Modele FttH v2/files/PC2", "Q:/Modele FttH v2/files/acces-cuivre.csv");
                // attention zonagePC est long (>3h)
            }
        };

        t.start();
    }
    
    public void pretraitementCollecte(){
        Thread t = new Thread() {

            @Override
            public void run() {
                separeCollecte(cheminReseau+"Collecte/", "liens_collecte_fibre.csv", "liens_optiques_");
            }
        };

        t.start();
    }
    
    public void createZAPCSRNRA(){
        Thread t = new Thread() {

            @Override
            public void run() {
                Shapefiles.createVoronoiPC(cheminReseau,"DptEtendus-5km", "NRA", "SR", "PC2", "VoronoiPC");
                Shapefiles.fusionShapes(cheminReseau, "VoronoiPC", "ZASR", 2, true, "");
                Shapefiles.fusionShapes(cheminReseau, "ZASR", "ZANRA", 2, true, ""); // supprimer les dossiers des shapefiles qu'on veut recréer avant
            }
        };

        t.start();
    }
    
    public void regrouperNRANRO(final Parametres parametres, final double distMax){
        Thread t = new Thread() {

            @Override
            public void run() {

                long start = System.currentTimeMillis();
                System.out.println("Début du regroupement des NRA en NRO");
                int dist = (int) Math.round(distMax);
                String nro = "NRO-"+String.valueOf(dist)+"km";
                String adresseNRO = cheminReseau+nro;
                File dirZANRO = new File(cheminReseau+"ZA"+nro);
                
                try{
                    File dirNRO = new File(adresseNRO);
                    dirNRO.mkdirs();
                    
                    PrintWriter cuivre = new PrintWriter(cheminReseau+"EtatInitialCuivre.csv");
                    cuivre.println("Departement;Zone;NRA;Nombre de SR;Nombre de PC;Nombre de lignes");

                    PrintWriter cuivre3 = new PrintWriter(cheminReseau+"EtatCuivre_10km.csv");
                    cuivre3.println("Departement;Zone;NRA;Nombre de SR;Nombre de PC;Nombre de lignes");
                    PrintWriter NRONRA = new PrintWriter(adresseNRO+"/Analyse"+nro+".csv");
                    NRONRA.println("Departement;Zone;NRO;Nombre de NRA;Nombre de SR;Nombre de PC;Nombre de lignes");
                    for (String dpt : parametres.getListeDpts()) {
                        System.out.println("Initialisation pour le département n°"+dpt);
                        ReseauCuivre reseauC = new ReseauCuivre(cheminReseau, dpt);
                        reseauC.loadNRA(cheminReseau+"NRA");
                        reseauC.loadSR(cheminReseau+"SR");
                        reseauC.loadPC(cheminReseau+"PC2", false, null);
                        for (String s : reseauC.etat()){
                            cuivre.println(s);
                        }
                        reseauC.checkPC(10000);
                        for (String s : reseauC.etat()){
                            cuivre3.println(s);
                        }
                        reseauC.regrouperNRACollecte(parametres, adresseNRO);
                        for (String s : reseauC.getAnalyseNRO()){
                            NRONRA.println(dpt+";"+s);
                        }
                        File zanro = new File(dirZANRO+"/"+dirZANRO.getName()+"_"+dpt);
                        if (zanro.exists()){
                            Shapefiles.delete(zanro);
                        }
                    }
                    NRONRA.close();
                    cuivre.close();
                    cuivre3.close();
                } catch (Exception e){
                    e.printStackTrace();
                }
                System.out.println("Fin du regroupement - calcul réalisé en " + Math.round((System.currentTimeMillis() - start) / 1000) + " secondes");
                Shapefiles.fusionShapes(cheminReseau, "ZANRA", dirZANRO.getName(), 1, false, nro);
            }
        };

        t.start();
    }
    
    public void traceReseau(final Parametres parametres, final boolean limitrophes, final boolean gc, final String nro, final JList jList1, final List<String> listeDptRoutier){
        Thread t = new Thread() {

            @Override
            public void run() {
                Object[] dptChoisis = jList1.getSelectedValues();
                parametres.setListesDpts(dptChoisis, listeDptRoutier);
                Shapefiles.preparerGraphes(parametres, cheminReseau, "ZA"+nro, nro, "BLO", nro.replace("NRO", ""), limitrophes, gc);
            }
        };

        t.start();
    }
    
    // méthodes pour le module A
    
    static void filtreDpts(String chemin, String nomFichier) {
        String line, dpt;
        int n=0;
        HashMap<String,PrintWriter> writers = new HashMap<>();
        long start = System.currentTimeMillis();
        
        try {
            File dir = new File(chemin+"/LP");
            dir.mkdir();
            BufferedReader br = new BufferedReader(new FileReader(chemin+"/"+nomFichier));
            String entete = br.readLine();
            
            while ((line = br.readLine()) != null) {
                dpt = dpt(line.replace("\"","").split(";")[6]);
                if (!writers.containsKey(dpt)){
                    PrintWriter writer = new PrintWriter(dir+"/LP_"+dpt+".csv");
                    writer.println(entete);
                    writer.println(line);
                    writers.put(dpt,writer);
                    System.out.println(writers.size()+" départements différents trouvés");
                } else writers.get(dpt).println(line);
                n++;
                if(n%10000 == 0){
                    System.out.println(n);
                }
            }
            br.close();
            for (PrintWriter writer : writers.values()){
                writer.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Temps pour filtrer par département : "+(System.currentTimeMillis()-start)+" milisecondes");
    }
    
    
    static void createPC(String chemin, String destination) {
        String line;
        long lignesExclues = 0;
        
        try {
            File dir = new File(chemin+"/LP");
            File newDir = new File(destination);
            newDir.mkdirs();
            String pc;
            String fields[];
            for (File file : dir.listFiles()){
                //lecture
                long start = System.currentTimeMillis();
                int n=0;
                BufferedReader br = new BufferedReader(new FileReader(file));
                HashMap<String,PCBasic> data = new HashMap<>();
                System.out.println(br.readLine());

                // itération
                while ((line = br.readLine()) != null) {
                    fields = line.replace("\"","").split(";");
                    if (fields.length >= 13){ 
                        pc = fields[6];
                        if (!data.containsKey(pc)){
                            data.put(pc, new PCBasic(pc+";"+fields[5]+";"+pc+";"+fields[7]+";"+fields[10]+";"+fields[11]+";"+fields[12]));
                        } else data.get(pc).addLigne();
                    } else{
                        lignesExclues++;
                    }
                    n++;
                    if(n%10000 == 0){
                        System.out.println(n);
                    }
                }
                br.close();

                // écriture
                String dpt = file.getName().replace("LP", "");
                PrintWriter writer = new PrintWriter(newDir+"/"+newDir.getName()+dpt);
                writer.println("NRA;SR;PC;Commune;X;Y;Referentiel;Lignes");
                for (PCBasic value : data.values()){
                    writer.println(value.print());
                }
                writer.close();
                System.out.println("Fini pour le département "+dpt.replace(".csv", "").replace("_", "")+" ; temps nécessaire : "+(System.currentTimeMillis()-start)+" milisecondes");
            }
            System.out.println("Nombres de lignes exclues : "+lignesExclues);
        }
        catch (Exception e) {
            e.printStackTrace();
        } 
    }
    
    static class PCBasic {
        private final String infos;
        private int nbLignes;

        PCBasic(String infos){
            this.infos = infos;
            nbLignes = 1;
        }

        void addLigne(){
            nbLignes++;
        }
        
        String print(){
            return infos+";"+nbLignes;
        }

    }
    
    static void compteLignesCuivre(String origine, String destination){
        try{
            PrintWriter writer = new PrintWriter(destination);
            writer.println("Departement;ZTD;ZMD_AMII;ZMD_RIP");
            String line;
            String[] fields;
            int compteurZTD, compteurAMII, compteurRIP, nbLignes;
            File dir = new File(origine);
            for (File f : dir.listFiles()){
                String dpt = f.getName().replace(dir.getName()+"_", "").replace(".csv", "");
                System.out.println("Comptage des lignes principales du réseau cuivre pour le département "+dpt);
                BufferedReader reader = new BufferedReader(new FileReader(f));
                reader.readLine();
                compteurZTD = 0;
                compteurAMII = 0;
                compteurRIP = 0;
                while ((line = reader.readLine())!=null){
                    fields = line.split(";");
                    nbLignes = Integer.parseInt(fields[6]);
                    switch(fields[7]){
                        case "ZMD_RIP":
                            compteurRIP+=nbLignes;
                            break;
                        case "ZMD_AMII":
                            compteurAMII+=nbLignes;
                            break;
                        default:
                            compteurZTD += nbLignes;
                            break;
                    }
                }
                reader.close();
                writer.println(dpt+";"+compteurZTD+";"+compteurAMII+";"+compteurRIP);
            }
            writer.close();
        } catch(FileNotFoundException e){
            System.out.println("Problème avec "+origine+" ou "+destination);
            e.printStackTrace();
        } catch(IOException e){
            e.printStackTrace();
        }
        
    }

    static void prepareNRA(String origine, String destination){
        Map<String, List<String>> dptNRA = litEtSepare(origine);
        File dir = new File(destination);
        dir.mkdirs();
        String nra;
        String[] fields;
        for (String dpt : dptNRA.keySet()){
            try{
                PrintWriter writer = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv");
                writer.println("NRA;X;Y;Referentiel");
                for (String line : dptNRA.get(dpt)){
                    fields = line.split(";");
                    nra = fields[0];
                    if (nra.endsWith("E00"))
                        nra = nra.substring(0, 5) + "EOO";
                    writer.println(nra+";"+fields[8]+";"+fields[9]+";"+fields[10]);
                }
                writer.close();
            } catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
    }
    
    static void prepareSR(String origine, String destination){
        Map<String, List<String>> dptSR = litEtSepare(origine);
        File dir = new File(destination);
        dir.mkdirs();
        String nra;
        String[] fields;
        for (String dpt : dptSR.keySet()){
            try{
                PrintWriter writer = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv");
                writer.println("SR;NRA;X;Y;Referentiel");
                for (String line : dptSR.get(dpt)){
                    fields = line.split(";");
                    nra = fields[1];
                    if (nra.endsWith("E00"))
                        nra = nra.substring(0, 5) + "EOO";
                    writer.println(fields[0]+";"+nra+";"+fields[6]+";"+fields[7]+";"+fields[8]);
                }
                writer.close();
            } catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
    }
    
    private static String dpt(String codeNRA){
        if(codeNRA.substring(0, 2).equals("97"))
            return codeNRA.substring(0, 3);
        else return codeNRA.substring(0, 2);
    }
    
    private static Map<String, List<String>> litEtSepare(String fichier){
        Map<String, List<String>> res = new HashMap<>();
        String line, dpt;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(fichier));
            reader.readLine();
            while ((line = reader.readLine()) != null){
                dpt = line.substring(0, 2);
                if (dpt.equals("97")) dpt = line.substring(0, 3); // outremer
                if (!res.containsKey(dpt))
                    res.put(dpt, new ArrayList<String>());
                res.get(dpt).add(line);
            }
        } catch (IOException e){
            System.out.println("Problème avec le fichier "+fichier);
            e.printStackTrace();
        }
        return res;
    }
    
    static void separeCollecte(String collecte, String origine, String destination){
        Map<String, List<String>> res = new HashMap<>();
        String line, dpt1, dpt2, header;
        String[] fields;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(collecte+origine));
            header = reader.readLine();
            while ((line = reader.readLine()) != null){
                fields = line.split(";");
                dpt1 = fields[1].substring(0,2);
                if (dpt1.equals("97")) dpt1 = fields[1].substring(0, 3); // outremer
                dpt2 = fields[4].substring(0,2);
                if (dpt2.equals("97")) dpt2 = fields[4].substring(0, 3); // outremer
                if (!res.containsKey(dpt1))
                    res.put(dpt1, new ArrayList<String>());
                res.get(dpt1).add(line);
                if (!dpt2.equals(dpt1)){
                    if (!res.containsKey(dpt2))
                        res.put(dpt2, new ArrayList<String>());
                    res.get(dpt2).add(line);
                }
            }
            for (String dpt : res.keySet()){
                File dir = new File(collecte+dpt);
                dir.mkdir();
                PrintWriter liens = new PrintWriter(dir+"/"+destination+dpt+".csv", "utf-8");
                liens.println(header);
                for (String s : res.get(dpt)){
                    liens.println(s);
                }
                liens.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    
}
