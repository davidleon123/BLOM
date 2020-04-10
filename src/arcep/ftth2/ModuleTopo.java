/*
 * Copyright (c) 2017, Autorité de régulation des communications électroniques et des postes
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arcep.ftth2;

import java.io.*;
import java.util.*;

public class ModuleTopo {
    
    final private String cheminReseau;
    final private String adresseLP, adresseSR, adresseNRA, adresseCollecte;
    final private String adresseDptsLimitrophes;
    final private String adresseShapesDptsMetro, nameShapeDpts;
    final private String[] shapesDOM, fichiersZonage;

    private Map<String,List<String>> dptsLimitrophes;
    
    public ModuleTopo(String[] fichiersCuivre, String adresseDptsLimitrophes, String adresseShapesDptsMetro, String[] shapesDOM, String nameShapeDpts,
            String[] fichiersZonage){
        this.cheminReseau = Main.cheminReseau;
        this.adresseLP = fichiersCuivre[0];
        this.adresseSR = fichiersCuivre[1];
        this.adresseNRA = fichiersCuivre[2];
        this.adresseCollecte = fichiersCuivre[3];
        this.adresseDptsLimitrophes = adresseDptsLimitrophes;
        this.adresseShapesDptsMetro = adresseShapesDptsMetro;
        this.shapesDOM = shapesDOM;
        this.nameShapeDpts = nameShapeDpts;
        this.fichiersZonage = fichiersZonage;
    }
    
    public void readDptsLimitrophes(){
        String ligne;
        String[] donneesLigne;
        dptsLimitrophes = new HashMap<>();
        try{
            BufferedReader limitrophes = new BufferedReader(new FileReader(this.adresseDptsLimitrophes));
            limitrophes.readLine();
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
                Shapefiles.buffer(5, adresseShapesDptsMetro, nameShapeDpts, cheminReseau+"DptsEtendus");
                for (String shapeDOM : shapesDOM){
                    Shapefiles.buffer(5, shapeDOM, nameShapeDpts, cheminReseau+"DptsEtendus");
                }
            }
        };
        t.start();
    }

    public void pretraitementReseauCuivre(){
        Thread t = new Thread() {

            @Override
            public void run() {
                String dossierLPParDpt = filtreDpts(adresseLP);
                prepareNRA(adresseNRA, cheminReseau+"NRA");
                prepareSR(adresseSR, cheminReseau+"SR");
                createPC(dossierLPParDpt, cheminReseau+"PC1");
                Shapefiles.zonagePC(cheminReseau+"DptsEtendus-5km", fichiersZonage[2], fichiersZonage[0], fichiersZonage[1], cheminReseau+"PC1", cheminReseau+"PC2", dptsLimitrophes);
                compteLignesCuivre(cheminReseau+"PC2", cheminReseau+"acces-cuivre.csv");
                System.out.println("Fin du prétraitement des fichiers cuivre");
                // attention zonagePC est long (>3h)
            }
        };

        t.start();
    }
    
    public void pretraitementCollecte(){
        Thread t = new Thread() {

            @Override
            public void run() {
                System.out.println("Début du prétraitement de la collecte");
                separeCollecte(adresseCollecte, cheminReseau+"Collecte");
                System.out.println("Fin du prétraitement de la collecte");
            }
        };

        t.start();
    }
    
    public void createZAPCSRNRA(boolean lireSR, boolean suppression){
        Thread t = new Thread() {
            
            @Override
            public void run() {
                System.out.println("Début de la création des ZANRA");
                File f;
                if (suppression){
                    f = new File(cheminReseau+"VoronoiPC");
                    if (f.exists()) delete(f);
                }
                Shapefiles.createVoronoiPC(cheminReseau,"DptsEtendus-5km", "NRA", "SR", "PC2", "VoronoiPC");
                if (lireSR){
                    if (suppression){
                        f = new File(cheminReseau+"ZASR");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "VoronoiPC", "ZASR", 2, true, "");
                    if (suppression){
                        f = new File(cheminReseau+"ZANRA");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "ZASR", "ZANRA", 2, true, "");
                } else{
                    if (suppression){
                        f = new File(cheminReseau+"ZANRA");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "VoronoiPC", "ZANRA", 3, true, "");
                }
                System.out.println("Fin de la création des ZANRA");
            }
        };

        t.start();
    }
    
    public void regrouperNRANRO(List<String> listeDpts, int nbLignesMinNRO, double distMaxNRONRA, boolean lireSR){
        
        long start = System.currentTimeMillis();
        System.out.println("Début du regroupement des NRA en NRO");
        int dist = (int) Math.round(distMaxNRONRA);
        String nro = "NRO-"+String.valueOf(dist)+"km";
        String adresseNRO = cheminReseau+nro;
        File dirZANRO = new File(cheminReseau+"ZA"+nro);
        File dirNRO = new File(adresseNRO);
        dirNRO.mkdirs();
        File dirTestCollecte = new File(cheminReseau+"TestCollecte");
        dirTestCollecte.mkdirs();
                
        Thread t = new Thread() {

            @Override
            public void run() {
                try{
                    // fichiers d'analyse
                    PrintWriter cuivre = new PrintWriter(cheminReseau+"EtatInitialCuivre.csv");
                    cuivre.println(enteteAnalyse(true, lireSR));
                    PrintWriter cuivre3 = new PrintWriter(cheminReseau+"EtatCuivre_10km.csv");
                    cuivre3.println(enteteAnalyse(true, lireSR));
                    PrintWriter NRONRA = new PrintWriter(adresseNRO+"/Analyse"+nro+".csv");
                    NRONRA.println(enteteAnalyse(false, lireSR));
                    
                    for (String dpt : listeDpts) {
                        System.out.println("Initialisation pour le département n°"+dpt);
                        ReseauCuivre reseauC = new ReseauCuivre(cheminReseau, dpt, nbLignesMinNRO, 10000*distMaxNRONRA);
                        reseauC.loadNRA(cheminReseau+"NRA");
                        if (lireSR) reseauC.loadSR(cheminReseau+"SR");
                        reseauC.loadPC(cheminReseau+"PC2", false, null);
                        for (String s : reseauC.etat()){
                            cuivre.println(s);
                        }
                        reseauC.checkPC(10000);
                        for (String s : reseauC.etat()){
                            cuivre3.println(s);
                        }
                        reseauC.regrouperNRACollecte(adresseNRO, "Collecte");
                        for (String s : reseauC.getAnalyseNRO()){
                            NRONRA.println(dpt+";"+s);
                        }
                        File zanro = new File(dirZANRO+"/"+dirZANRO.getName()+"_"+dpt);
                        if (zanro.exists()){
                            delete(zanro);
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
        
        try{
            PrintWriter writer = new PrintWriter(dirNRO+"/parametres.txt");
            writer.println("Paramètres utilisés pour produire les résultats du présent dossier "+dirNRO.getName());
            writer.println();
            writer.print("Utilisation des sous-répartiteurs du réseau cuivre d'Orange : ");
            if (lireSR) writer.println("OUI");
            else writer.println("NON");
            writer.println("Nombre minimal théorique de lignes desservies par un NRO : "+nbLignesMinNRO+" lignes");
            writer.println("Distance maximale entre NRA regroupé et NRA regroupeur autorisant à passer sous le seuil précédant : "+distMaxNRONRA+ " km");
            writer.close();
        } catch(FileNotFoundException e){
            e.printStackTrace();
        }
        t.start();
    }
    
    private String enteteAnalyse(boolean NRA, boolean lireSR){
        String entete = "Departement;Zone";
        if (NRA)
            entete+=";NRA";
        else
            entete+=";NRO;Nombre de NRA";
        if(lireSR)
            entete+=";Nombre de SR";
        entete+=";Nombre de PC;Nombre de lignes";
        return entete;
    }
    
    public void traceReseau(boolean limitrophes, boolean gc, String nro, List<String> dptChoisis, double toleranceNoeud, double seuilToleranceGC2){
        Thread t = new Thread() {

            @Override
            public void run() {
                Shapefiles.preparerGraphes(dptChoisis, dptsLimitrophes, cheminReseau, "ZA"+nro, nro, "BLO", nro.replace("NRO", ""), limitrophes, gc, toleranceNoeud, seuilToleranceGC2);
            }
        };

        t.start();
    }
    
    // méthodes pour le module A
    
    private String filtreDpts(String nomFichier) {
        File f = new File(nomFichier);
        File dir = new File(f.getParent()+"/LP");
        dir.mkdir();
        String line, dpt;
        int n=0;
        HashMap<String,PrintWriter> writers = new HashMap<>();
        long start = System.currentTimeMillis();
        
        try {
            
            BufferedReader br = new BufferedReader(new FileReader(f));
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
        return dir.getPath();
    }
    
    
    private void createPC(String origine, String destination) {
        String line;
        long lignesExclues = 0;
        
        try {
            File dir = new File(origine);
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
                            data.put(pc, new PCBasic(fields[4]+";"+fields[5]+";"+pc+";"+fields[7]+";"+fields[10]+";"+fields[11]+";"+fields[12]));
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
    
    private class PCBasic {
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
    
    private void compteLignesCuivre(String origine, String destination){ // ajouter une annonce de fin
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

    private void prepareNRA(String origine, String destination){
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
    
    private void prepareSR(String origine, String destination){
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
    
    private String dpt(String codeNRA){
        if(codeNRA.substring(0, 2).equals("97"))
            return codeNRA.substring(0, 3);
        else return codeNRA.substring(0, 2);
    }
    
    private Map<String, List<String>> litEtSepare(String fichier){
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
    
    private void separeCollecte(String origine, String destination){
        Map<String, List<String>> res = new HashMap<>();
        String line, dpt1, dpt2, header, codeNRA1, codeNRA2;
        String[] fields;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(origine));
            header = reader.readLine();
            while ((line = reader.readLine()) != null){
                fields = line.split(";");
                codeNRA1 = fields[1];
                // attention aux codes NRA qui ont l'air de nombres en notation scientifique (vérifier aussi le fichier d'entrée sous Notepad++)
                if (codeNRA1.substring(5,7).equals("E0"))
                    codeNRA1 = codeNRA1.substring(0, 5) + "EOO";
                dpt1 = codeNRA1.substring(0,2);
                if (dpt1.equals("97")) dpt1 = codeNRA1.substring(0, 3); // outremer
                codeNRA2 = fields[4];
                if (codeNRA2.substring(5,7).equals("E0"))
                    codeNRA2 = codeNRA2.substring(0, 5) + "EOO";
                dpt2 = codeNRA2.substring(0,2);
                if (dpt2.equals("97")) dpt2 = codeNRA2.substring(0, 3); // outremer
                if (!res.containsKey(dpt1))
                    res.put(dpt1, new ArrayList<>());
                res.get(dpt1).add(line);
                if (!dpt2.equals(dpt1)){
                    if (!res.containsKey(dpt2))
                        res.put(dpt2, new ArrayList<>());
                    res.get(dpt2).add(line);
                }
            }
            for (String dpt : res.keySet()){
                File dir = new File(destination);
                dir.mkdirs();
                System.out.println("Impression du fichier des liens de collecte pour le département "+dpt);
                PrintWriter liens = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv", "utf-8");
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
    
    private void delete(File f){
       if (f.isDirectory()){
            for (File child : f.listFiles()){
                delete(child);
            }
       }
       f.delete();
    }
    
}
