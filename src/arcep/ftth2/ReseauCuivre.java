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

import edu.wlu.cs.levy.CG.*;
import java.io.*;
import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.*;

public class ReseauCuivre {
    
    private final String cheminReseau;
    private final String dpt;
    private boolean lireSR;
    private final int nbLignesMinNRO;
    private final double distMaxNRONRA;
    
    private Map<String,Map<String,NRA>> listeNRA; // plusieurs clés peuvent pointer vers le même NRA (gestion des NRA manquants)
    private Map<String,NRA> listeNRAinitiaux;
    private KDTree<NRA> indexNRA;
   
    private Map<String,Map<String,SR>> listeSR;
    private Map<String, SR> listeSRinitiaux;
    private KDTree<SR> indexSR;
    
    private ArrayList<PC> listePC;
    private Map<String,List<NRA>> listeNRO;
    
    private final Comparateur comparateurNRA = new Comparateur();

    public ReseauCuivre(String cheminReseau, String dpt, int nbLignesMinNRO, double distMaxNRONRA){
        this.cheminReseau = cheminReseau;
        this.dpt = dpt;
        this.nbLignesMinNRO = nbLignesMinNRO;
        this.distMaxNRONRA = distMaxNRONRA;
        this.lireSR = false;
    }

    public void loadNRA(String dirNRA){
        System.out.println("Lecture des NRA du département "+dpt);
        listeNRA = new HashMap<>();
        listeNRAinitiaux = new HashMap<>();
        indexNRA = new KDTree<>(2);
        File dir = new File(dirNRA);
        String ligne, codeNRA = "";
        String[] donneesLigne;
        double[] coord = new double[2];
        NRA nra;
        try{               
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            while ((ligne = ptRes.readLine()) != null) { 
                donneesLigne = ligne.split(";");
                codeNRA = donneesLigne[0].replace("\"", ""); 
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[1].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[2].replace("\"", "").replace(",", ".")); 
                nra = new NRA(codeNRA, coord[0], coord[1], false);
                listeNRAinitiaux.put(codeNRA, nra);
                try {indexNRA.insert(coord, nra);}
                catch (KeyDuplicateException e){System.out.println(e+" - type : NRA");}                
            }
        }catch(Exception e){
            System.out.println("Exception lors de la lecture des NRA");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA);
            e.printStackTrace();
        }
        System.out.println("Nombre de NRA du réseau cuivre départemental (en lecture) : "+listeNRAinitiaux.values().size());
    }

    public void loadSR(String dirSR){
        System.out.println("Lecture des SR du département "+dpt);
        this.lireSR = true;
        listeSR = new HashMap<>();
        listeSRinitiaux = new HashMap<>();
        indexSR = new KDTree<>(2);
        File dir = new File(dirSR);
        String ligne, codeNRA = "", codeSR = "";
        String[] donneesLigne;
        double[] coord = new double[2];
        SR sr;
        try{ 
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            
            while ((ligne = ptRes.readLine()) != null) { 
                donneesLigne = ligne.split(";");
                codeSR = donneesLigne[0].replace("\"","");
                codeNRA = donneesLigne[1].replace("\"", "");
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[2].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[3].replace("\"", "").replace(",", "."));
                sr = new SR(codeSR, coord[0], coord[1], codeNRA);
                try{
                    indexSR.insert(coord, sr);
                } catch (KeyDuplicateException e){
                    sr = indexSR.search(coord);
                    System.out.println(e+" - type : SR");
                }
                listeSRinitiaux.put(codeSR, sr);              
            }
        }catch(Exception e){
            System.out.println("Exception lors de la lecture des SR");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA+"- SR "+codeSR);
            e.printStackTrace();
        }
        System.out.println("Nombre de SR du réseau cuivre départemental (en lecture) : "+listeSRinitiaux.values().size());
    }

    public void loadPC(String dirPC, boolean analyse, PrintWriter sortie){
        System.out.println("Lecture des PC du département "+dpt);
        listePC = new ArrayList<>();
        String ligne, codePC = "", codeSR = "", zone, codeNRA  = "";
        String[] donneesLigne;
        int nbAcces;
        double[] coord = new double[2];
        NRA nra;
        PC pc;
        PointReseau pt;
        
        // gestion des galeries visitables
        boolean lireCommunes = dpt.equals("13")||dpt.equals("69");
        List<String> codeCommunes = communes(dpt);
        boolean paris = dpt.equals("75");
        
        File dir = new File(dirPC);

        try{               
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            while ((ligne = ptRes.readLine()) != null) {
                donneesLigne = ligne.split(";");
                codePC = donneesLigne[2].replace("\"", "");
                codeSR = donneesLigne[1].replace("\"",""); 
                codeNRA = donneesLigne[0].replace("\"", ""); 
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[3].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[4].replace("\"", "").replace(",", "."));
                nbAcces = Integer.parseInt(donneesLigne[6]);
                zone = donneesLigne[7];
                if (nbAcces > 0){ // on exclut par précaution normalement inutile les PC qui n'ont pas au moins 1 accès
                    nra = getNRA(codeNRA, zone, coord);
                    if (lireSR){ // si on utilise les SR on rattache au SR
                        pt = getSR(codeSR, zone, nra);     
                    } else{ // sinon on rattache au NRA
                        pt = nra;
                    }
                    pc = new PC(codePC,  coord[0], coord[1], pt, codeNRA, nbAcces, zone, paris||(lireCommunes&&codeCommunes.contains(donneesLigne[8])));
                    listePC.add(pc);
                    if (!lireSR) nra.addFils(pc, indexNRA);
                    nra.ajoutPC(pc);
                }

            }
            ptRes.close();
            System.out.println("Nombre de NRA du réseau cuivre départemental (en lecture) : "+listeNRAinitiaux.values().size());
            System.out.println("Nombre par zone après attribution :");
            for (String s : listeNRA.keySet()){
                System.out.println(" - "+listeNRA.get(s).values().size()+" en zone "+s);
            }
            if (lireSR){
                System.out.println("Nombre de SR du réseau cuivre départemental (en lecture) : "+listeSRinitiaux.values().size());
                System.out.println("Nombre par zone après attribution :");
                for (String s : listeSR.keySet()){
                    System.out.println(" - "+listeSR.get(s).values().size()+" en zone "+s);
                }
            }
            System.out.println("Nombre de PC du réseau cuivre départemental : "+listePC.size());

            // analyse éventuelle des "nouveaux NRA"
            if (analyse){
                int compteurNRA = 0;
                int compteurFils = 0;
                int compteurLignes = 0;
                for (Map<String,NRA> liste : this.listeNRA.values()){
                    for (NRA nra1 : liste.values()){
                        if (nra1.nouveau){
                            compteurNRA++;
                            compteurFils += nra1.getFils().size();
                            compteurLignes += nra1.podi();
                        }
                    }
                }
                System.out.print(compteurNRA+" nouveaux NRA ont été créés, totalisant "+compteurFils);
                if (lireSR) System.out.print(" SR");
                else System.out.print(" PC");
                System.out.println(" et "+compteurLignes+" lignes.");
                sortie.println(dpt+";"+compteurNRA+";"+compteurFils+";"+compteurLignes);
                
            }
            
        } catch(Exception e){
            System.out.println("Exception lors de la lecture des PC");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA+"- SR "+codeSR+"- PC "+codePC);
            e.printStackTrace();
        }
    }
    
    private List<String> communes(String dpt){
        List<String> liste = new ArrayList<>();
        switch(dpt){
            case "13":
                liste.add("13055");
                liste.add("13201");
                liste.add("13202");
                liste.add("13203");
                liste.add("13204");
                liste.add("13205");
                liste.add("13206");
                liste.add("13207");
                liste.add("13208");
                liste.add("13209");
                liste.add("13210");
                liste.add("13211");
                liste.add("13212");
                liste.add("13213");
                liste.add("13214");
                liste.add("13215");
                liste.add("13216");
                break;
            case "69":
                liste.add("69000");
                liste.add("69123");
                liste.add("69381");
                liste.add("69382");
                liste.add("69383");
                liste.add("69384");
                liste.add("69385");
                liste.add("69386");
                liste.add("69387");
                liste.add("69388");
                liste.add("69389");
                break;
            default:
                liste = null;
        }
        return liste;
    }
    
    // gestion des NRA 
    private NRA getNRA(String codeNRA, String z, double[] coord){
        String zone = z.replace("_HD", "").replace("_BD", "");
        if (!listeNRA.containsKey(zone)){
            listeNRA.put(zone, new HashMap<String,NRA>());
        }
        NRA nra = listeNRA.get(zone).get(codeNRA);
        if (nra == null){
            nra = listeNRAinitiaux.get(codeNRA);
            if (nra != null){
                nra = new NRA(nra, zone);
                listeNRA.get(zone).put(codeNRA, nra);
            } else{
                System.out.println("Pas de NRA correspondant au code : "+codeNRA);
                try{
                    nra = indexNRA.nearest(coord); // on regarde le plus proche et on en crée un nouveau s'il est trop loin
                    if (nra.distance(coord) > 10000){
                        System.out.println("On crée un nouveau NRA");
                        nra = new NRA(codeNRA, 0, 0, true, zone);                    
                        indexNRA.insert(coord, nra);
                        listeNRA.get(zone).put(codeNRA, nra);
                    } else{
                        if (listeNRA.get(zone).containsKey(nra.identifiant)){
                            nra = listeNRA.get(zone).get(nra.identifiant);
                        } else{
                            nra = new NRA(nra,zone);
                            listeNRA.get(zone).put(codeNRA, nra);
                        }
                        nra.addNRA(codeNRA);
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        return nra;
    }
    
    // gestion des SR  (pour l'instant on rattache directement au NRA)
    private SR getSR(String codeSR, String z, NRA nra){
        String zone = z.replace("_HD", "").replace("_BD", "");
        if (!listeSR.containsKey(zone))
            listeSR.put(zone, new HashMap<String, SR>());
        SR sr = listeSR.get(zone).get(codeSR);  
        if (sr == null) {
            sr = listeSRinitiaux.get(codeSR);
            if (sr!=null){
                sr = createSR(sr, zone);
            } else{ 
                // gestion des PC rattachés directement au NRA, codage pas toujours clair dans les fichiers
                double[] coord = {nra.x, nra.y};
                try{
                    sr = indexSR.search(coord);
                    if (sr == null){
                        sr = new SR(codeSR, coord[0], coord[1], nra.identifiant);   
                        try{
                            indexSR.insert(coord, sr);
                        } catch (KeyDuplicateException e){
                            System.out.println("WARNING : on ne devrait pas avoir d'exception ici !");
                        }
                        sr = createSR(sr, zone);
                    } else{
                        if (listeSR.get(zone).containsKey(sr.identifiant)){
                            sr = listeSR.get(zone).get(sr.identifiant);
                        } else
                            sr = createSR(sr, zone);
                    }
                } catch (KeySizeException e){
                    e.printStackTrace();
                }
            }  
        }
        return sr;
    }
    
    private SR createSR(SR sr, String zone){
        SR sousrep = new SR(sr, zone);
        double[] coord = {sr.x, sr.y};
        NRA nra = getNRA(sousrep.codeNRA, sousrep.zone, coord);
        nra.addFils(sousrep, indexNRA);
        sousrep.rattachePere(nra);
        listeSR.get(zone).put(sr.identifiant, sr);
        return sousrep;
    }

    public void testPC(String dossier){
        try{
            System.out.println("Nombre de NRA dans le fichier d'entrée : "+listeNRAinitiaux.size());
            if (lireSR)
                System.out.println("Nombre de SR dans le fichier d'entrée : "+listeSRinitiaux.size());
            System.out.println("Nombre total de PC : "+listePC.size());
            System.out.println("Impression liste PC");
            File dir = new File(dossier);
            dir.mkdirs();
            PrintWriter csv = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+"_testPC.csv");
            csv.println("Code PC;Code NRA initial;Code NRA final;Zone;Nb de lignes");
            for (PC pc : listePC){
                csv.print(pc.identifiant+";"+pc.nra+";");
                if (lireSR) csv.print(pc.pere.pere.identifiant);
                else csv.print(pc.pere.identifiant);
                csv.println(";"+pc.zone+";"+pc.lignes);
            }   
            csv.close();
        } catch(FileNotFoundException e){
        e.printStackTrace();
        }
    }

    public List<String> etat(){
        List<String> res = new ArrayList<>();
        for (String zone : this.listeNRA.keySet()){
            for (NRA nra : this.listeNRA.get(zone).values()){
                String s = dpt+";"+zone+";"+nra.identifiant;
                if (lireSR) s+=";"+nra.getFils().size();
                res.add(s+";"+nra.getPC().size()+";"+nra.podi());
            }
        }
        return res;
    }

    public void checkPC(double distanceLimite){
        for (String zone : this.listeNRA.keySet()){
            for(NRA nra : this.listeNRA.get(zone).values()){
                nra.checkPC(distanceLimite);
            }
        }
    }
    
    public void regrouperNRACollecte(String sortie, String dossierCollecte){
        
        // regroupement des NRA zone par zone (ZTD/AMII/RIP)
        this.listeNRO = new HashMap<>();
        for (String zone : this.listeNRA.keySet()){
            
            System.out.println("Début du regroupement pour la zone :"+zone);
            SimpleWeightedGraph<NRA, DefaultWeightedEdge> grapheCollecte = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
            KDTree<NRA> indexCollecte = new KDTree<>(2);
            
            // ajout des NRA au graphe
            this.addNRA(grapheCollecte, indexCollecte, zone);
            
            // ajout des liens de collecte au graphe
            System.out.println("chargement du réseau de collecte");
            this.readFichierCollecte(grapheCollecte, zone, dossierCollecte);
            
            System.out.println("Ajout de liens pour les NRA isolés");
            this.ajoutNRAIsoles(grapheCollecte, indexCollecte);

            // maintenant tout le monde est dans le graphe de collecte
            for (NRA nra : grapheCollecte.vertexSet()){
                System.out.println("NRA "+nra.identifiant+" : "+nra.podi()+" lignes");
            }
            
            System.out.println("Impression du graphe");
            for (NRA nra1 : grapheCollecte.vertexSet()){
                System.out.print("NRA "+nra1.identifiant+", voisins :");
                for (DefaultWeightedEdge arete : grapheCollecte.edgesOf(nra1)){
                    NRA voisin = getVoisin(grapheCollecte, nra1, arete);
                    System.out.print(" - "+voisin.identifiant);
                }
                System.out.println();
            }
            
            // idée de l'algo :
            // but : regrouper tous les NRA en-dessous du seuil nbLignesMinNRO (a priori 1000 lignes)
            // - on aimerait avoir un arbre pour regrouper les NRA tranquillement mais tout ce qu'on a est un graphe pas du tout connexe et avec beaucoup de cycles
            // - on va donc regrouper d'abord ceux qui n'ont qu'un voisin (les "faciles")
            // - puis ceux qui ont plusieurs voisins (les "deuxiemes"), tout en revenant en arrière dès qu'on obtient des NRA à 1 voisin
            // - enfin, on regroupe ceux qui, suite aux regroupements, n'en ont plus mais sont toujours trop petits (les "isoles")
            // - à chaque fois, on supprime le NRA regroupé du graphe et de l'index

            // répartition de tous les NRA à regrouper
            PriorityQueue<NRA> faciles = new PriorityQueue<>(10, comparateurNRA);
            PriorityQueue<NRA> deuxiemes = new PriorityQueue<>(10, comparateurNRA);
            PriorityQueue<NRA> isoles = new PriorityQueue<>(10, comparateurNRA);
            
            for (NRA nra : grapheCollecte.vertexSet()){
                if (nra.podi() < nbLignesMinNRO){
                    switch(grapheCollecte.edgesOf(nra).size()){
                        case 0: // ce cas n'est pas censé arriver au départ
                            isoles.add(nra);
                            System.out.println("Regroupement collecte : NRA très éloigné de tous les autres");
                            System.out.println("Identifiant NRA : "+nra.identifiant);
                            break;
                        case 1:
                            faciles.add(nra);
                            break;
                        default:
                            deuxiemes.add(nra);
                            break;
                    }
                }
            }
            
            System.out.println("Nombre de NRA à un voisin : "+faciles.size());
            /*while (!faciles.isEmpty()){
                NRA nra = faciles.poll();
                System.out.println("Le NRA "+nra.identifiant+" a "+nra.podi()+" lignes.");
            }*/
            System.out.println("Nombre de NRA à plusieurs voisins : "+deuxiemes.size());
            /*while (!deuxiemes.isEmpty()){
                NRA nra = deuxiemes.poll();
                System.out.println("Le NRA "+nra.identifiant+" a "+nra.podi()+" lignes.");
            }*/
            System.out.println("Nombre de NRA isolés : "+isoles.size());
            
            this.regroupeMonoVoisin(grapheCollecte, indexCollecte, faciles, deuxiemes, isoles); // on regroupe dynamiquement les faciles
            // note : malgré l'appel à regroupeMonoVoisin dans la fonction suivante on doit l'appeler une première fois de manière isolée au cas où la file "deuxièmes" serait vide à l'origine
            // à ce moment il n'y a plus de nra < seuil et avec un unique voisin

            this.regroupeMultiVoisins(grapheCollecte, indexCollecte, faciles, deuxiemes, isoles);
            // cette fonction appelle regroupeMonoVoisin

            // désormais les seuls NRA de taille < seuil n'ont pas (plus) de voisins
            this.regroupeSansVoisin(grapheCollecte, indexCollecte, isoles);

            // enfin faire le tour de tous les proto-nro restant dans le graphe et les mettre dans listeNRO
            this.listeNRO.put(zone, new ArrayList<NRA>());
            for (NRA nra : grapheCollecte.vertexSet()){
                nra.nro = nra;
                this.listeNRO.get(zone).add(nra);
                System.out.print("NRA déclaré NRO : "+nra.identifiant+" ; NRA correspondants :");
                for (NRA nraReg : nra.listeNRAduNRO){
                    System.out.print(" - "+nraReg.identifiant);
                }
                System.out.println();
            }
           

            System.out.println("Nombre de NRO en zone "+zone+" : "+this.listeNRO.get(zone).size());
        }
        
        /*System.out.println("Nombre de NRO en zone AMII : "+listeNRO.get("ZMD_AMII").size());
        for (NRA nra : listeNRO.get("ZMD_AMII")){
            System.out.print("NRO "+nra.identifiant+" ; NRA correspondants :");
            for (NRA nraReg : nra.listeNRAduNRO){
                System.out.print(" - "+nraReg.identifiant);
            }
            System.out.println();
        }*/
        
        
        System.out.println("Verification inter zone");
        this.verificationInterZone();
        
        /*System.out.println("Nombre de NRO en zone AMII : "+listeNRO.get("ZMD_AMII").size());
        for (NRA nra : listeNRO.get("ZMD_AMII")){
            System.out.print("NRO "+nra.identifiant+" ; NRA correspondants :");
            for (NRA nraReg : nra.listeNRAduNRO){
                System.out.print(" - "+nraReg.identifiant);
            }
            System.out.println();
        }*/
        
        for (String zone : this.listeNRO.keySet()){
            System.out.println("Nombre de NRO en zone "+zone+" : "+this.listeNRO.get(zone).size());
            this.store(sortie, false, zone);
        }
    }
    
    private void addNRA(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, String zone){
        Iterator<NRA> iterator = this.listeNRA.get(zone).values().iterator();
        NRA nra;
        Map<String, NRA> complementListeNRA = new HashMap<>();
        while (iterator.hasNext()){
            nra = iterator.next();
            double[] coord = {nra.x, nra.y};
            try{
                indexCollecte.insert(coord, nra);
                collecte.addVertex(nra);
            } catch(KeyDuplicateException e){
                System.out.println("Problème avec le NRA "+nra.identifiant+" : deux NRA au même endroit !");
                System.out.println("On regroupe directement dans ce cas");
                try{
                    NRA nro = indexCollecte.search(coord);
                    nro.regrouper(nra);
                    iterator.remove();
                    complementListeNRA.put(nra.identifiant, nro);
                }   catch(KeySizeException e1){
                    e1.printStackTrace();
                }
            }catch(KeySizeException e){
                e.printStackTrace();
            }
        }
        this.listeNRA.get(zone).putAll(complementListeNRA);
    }
    
    private void readFichierCollecte(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, String zone, String dossierCollecte){
        try{
            BufferedReader fichierLiens = new BufferedReader(new FileReader(cheminReseau+dossierCollecte+"/"+dossierCollecte+"_"+dpt+".csv"));
            fichierLiens.readLine();
            String line;
            String[] fields;
            Map<String, NRA> listeNRAZone = this.listeNRA.get(zone);
            while ((line = fichierLiens.readLine())!=null){
                fields = line.split(";");
                String codeNRA1 = fields[1], codeNRA2 = fields[4];
                if (codeNRA1.endsWith("E00"))
                    codeNRA1 = codeNRA1.substring(0,5) + "EOO";
                if (codeNRA2.endsWith("E00"))
                    codeNRA2 = codeNRA2.substring(0,5) + "EOO";
                NRA nra1 = listeNRAZone.get(codeNRA1), nra2 = listeNRAZone.get(codeNRA2);
                if (nra1!=null && nra2!=null && !nra1.equals(nra2)){
                    double distance = Parametres.arrondir(Double.parseDouble(fields[0]),1);
                    collecte.setEdgeWeight(collecte.addEdge(nra1, nra2), distance);

                }
            }
            fichierLiens.close();
            
            PrintWriter test = new PrintWriter(cheminReseau+"TestCollecte/TestCollecte_"+dpt+".csv");
            test.println("NRA;Liens");
            for (NRA nra : collecte.vertexSet()){
                test.println(nra.identifiant+";"+collecte.degreeOf(nra));
            }
            test.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void ajoutNRAIsoles(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte){
        List<NRA> restants = new ArrayList<>();

        for (NRA nra : collecte.vertexSet()){
            if (nra.podi() < nbLignesMinNRO && collecte.degreeOf(nra) == 0)
                restants.add(nra);
        }
        
        Collections.sort(restants, comparateurNRA); // les nra sont triés par "podi" croissant
        int n = indexCollecte.size();
        for (NRA nra : restants){
            double[] coord = {nra.x, nra.y};
            NRA voisin = null;
            int k = 2;
            while (voisin == null){
                try{
                    voisin = indexCollecte.nearest(coord ,k).get(0);
                }catch(KeySizeException e){
                    System.out.println("Exception anormale");
                    e.printStackTrace();
                }
                if (collecte.containsEdge(nra, voisin) && k<n){ // la deuxième condition est utile lorsque tous les nra de indexCollecte sont des NRA "restants" : on autorise alors un lien de moins (nécessaire dans un cas en "étoile")
                    voisin = null;
                    k++;
                }
            }
            
            double distance = nra.distance(voisin);
            if (distance <= distMaxNRONRA){
                try{
                    collecte.setEdgeWeight(collecte.addEdge(nra, voisin), distance);
                } catch(IllegalArgumentException e){
                    System.out.println("Exception dans l'ajout de liens pour NRA isolés : "+e.getMessage());
                }
            }
        }        
    }
    
    private void regroupeMonoVoisin(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> faciles, PriorityQueue<NRA> deuxiemes, PriorityQueue<NRA> isoles){
        // l'utilisateur de cette fonction garantit que les NRA rpésent dans "faciles" ont un unique voisin, moins de nbLignesMinNRO lignes, et sont classés par ordre croissant du nb de lignes
        try{
            while (!faciles.isEmpty()){
                
                NRA nra = faciles.poll();
                DefaultWeightedEdge e = collecte.edgesOf(nra).iterator().next(); // un seul lien de collecte par hypothèse pour ce nra
                
                // on regroupe avec le seul voisin
                NRA voisin = getVoisin(collecte, nra, e);
                boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile + tard
                System.out.println("Voisin choisi : "+voisin.identifiant);
                if (collecte.getEdgeWeight(e) <= distMaxNRONRA){
                    System.out.println("NRA à un voisin regroupé : "+nra.identifiant);
                    voisin.regrouper(nra);
                    // on supprime "nra" du graphe et de l'index
                    collecte.removeVertex(nra);
                    double[] coord = {nra.x, nra.y};
                    indexCollecte.delete(coord);
                } else{ // il est vraiment trop loin, en fait il est tout seul
                    collecte.removeEdge(e);
                    //isoles.add(nra);
                }
                
                // gestion des files de priorité
                if (petitVoisin){ //(s'il est petit maintenant, a fortiori il l'était au départ)
                    switch(collecte.edgesOf(voisin).size()){
                        case 0: // donc 1 juste avant
                           faciles.remove(voisin);
                           if (voisin.podi() < nbLignesMinNRO)
                               isoles.add(voisin);
                            break;
                        case 1: // donc 2 juste avant
                            deuxiemes.remove(voisin);
                            if(voisin.podi() < nbLignesMinNRO)
                                faciles.add(voisin);
                            break;
                        default: // au moins 3 juste avant
                            if (voisin.podi() >= nbLignesMinNRO)
                                deuxiemes.remove(voisin);
                            break;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private void regroupeMultiVoisins(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> faciles, PriorityQueue<NRA> deuxiemes, PriorityQueue<NRA> isoles){
        while (!deuxiemes.isEmpty()){

            /*System.out.println("Taille des piles lors de l'entrée dans la boucle multivoisins");
            System.out.println("Un voisin : "+faciles.size());
            System.out.println("Plusieurs voisins : "+deuxiemes.size());
            System.out.println("Aucun voisin : "+isoles.size());
            
            System.out.println("Impression du graphe");
            for (NRA nra1 : collecte.vertexSet()){
                System.out.print("NRA "+nra1.identifiant+", voisins :");
                for (DefaultWeightedEdge arete : collecte.edgesOf(nra1)){
                    NRA voisin = getVoisin(collecte, nra1, arete);
                    System.out.print(" - "+voisin.identifiant);
                }
                System.out.println();
            }*/
            
            // on traite les "faciles" qui traînent (aucun lors du premier passage)
            regroupeMonoVoisin(collecte, indexCollecte, faciles, deuxiemes, isoles); 

            NRA nra = deuxiemes.poll();
            if (nra != null){ // si jamais l'action de regroupeMonoVoisin a vidé "deuxiemes"
                System.out.println("NRA "+nra.identifiant+" de "+nra.podi()+" lignes");
                Set<DefaultWeightedEdge> liens = collecte.edgesOf(nra); // au moins deux éléments dans cet ensemble
                System.out.println("Test : l'ensemble des arêtes du nra a "+liens.size()+" éléments.");

                // sélection du "plus proche voisin"
                Iterator<DefaultWeightedEdge> iterator = liens.iterator();
                DefaultWeightedEdge e = iterator.next(); // le premier par défaut
                double distance = collecte.getEdgeWeight(e);
                while (iterator.hasNext()){
                    DefaultWeightedEdge candidat = iterator.next();
                    double d = collecte.getEdgeWeight(candidat);
                    if (d < distance){
                        distance = d;
                        e = candidat;
                    }
                }

                // on se regroupe avec le voisin le + proche dans le réseau de collecte s'il est assez proche
                if (distance <= distMaxNRONRA){


                    NRA voisin = getVoisin(collecte, nra, e);
                    boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile plus tard
                    voisin.regrouper(nra);
                    System.out.println("Voisin choisi : "+voisin.identifiant);

                    // avant de supprimer nra du graphe, il faut transfèrer les autres voisins de nra à "voisin"; attention au cas particulier des "triangles"
                    for (DefaultWeightedEdge lien : liens){
                        if (!lien.equals(e)){
                            NRA v = getVoisin(collecte, nra, lien);
                            if (collecte.containsEdge(voisin, v)){ // cas du triangle -> possibilité de rendre des NRA "faciles"
                                if (v.podi() < nbLignesMinNRO && collecte.edgesOf(v).size() == 2){ // on n'a pas encore supprimé "nra" du graphe 
                                    faciles.add(v);
                                    deuxiemes.remove(v);
                                }
                                //System.out.println("Boolean petitVosin :"+petitVoisin);
                                if (petitVoisin){
                                    if (liens.size() == 2 && collecte.edgesOf(voisin).size() == 2){
                                        deuxiemes.remove(voisin);
                                        petitVoisin = false; // pour ne pas répéter l'opération plus bas si on l'a déjà faite
                                        if (voisin.podi() < nbLignesMinNRO)
                                            faciles.add(voisin);
                                    }
                                }
                            } else // tout va bien
                                collecte.setEdgeWeight(collecte.addEdge(voisin, v), distance + collecte.getEdgeWeight(lien));
                        }
                    }


                    // supression de nra du graphe et de l'index
                    collecte.removeVertex(nra);
                    double[] coord = {nra.x, nra.y};
                    try{
                        indexCollecte.delete(coord);
                    } catch(Exception excep){
                        excep.printStackTrace();
                    }
                    // gestion des files
                    if (petitVoisin && voisin.podi() >= nbLignesMinNRO)
                        deuxiemes.remove(voisin);
                } else{
                    // pas de problème de triangle car on supprime purement et simplement les liens, et les podi des voisins n'ont pas changé non plus
                    // par définition les voisins n'étaient ni isolés ni à un voisin puisque faciles est vide
                    Iterator<DefaultWeightedEdge> iter = (new ArrayList<>(liens)).iterator();
                    DefaultWeightedEdge lien;
                    while(iter.hasNext()){
                        lien = iter.next();
                        NRA voisin = getVoisin(collecte, nra, lien);
                        collecte.removeEdge(lien);
                        if (voisin.podi() < nbLignesMinNRO && collecte.degreeOf(voisin) == 1){
                            faciles.add(voisin);
                            deuxiemes.remove(voisin);
                        }
                    }
                }
            }
        }
    }
    
    private void regroupeSansVoisin(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> isoles){
        try{
            while (!isoles.isEmpty()){
                
                NRA nra = isoles.poll();
                
                // on supprime du graphe et de l'index
                double[] coord = {nra.x, nra.y};
                indexCollecte.delete(coord);
                
                if (indexCollecte.size() > 0){
                    NRA voisin = indexCollecte.nearest(coord);
                    double distance = nra.distance(voisin);
                    if (distance <= distMaxNRONRA){
                        // on supprime du graphe  et on regroupe avec le plus proche géographiquement
                        collecte.removeVertex(nra);
                        boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile + tard
                        voisin.regrouper(nra);

                        // gestion de la file de priorité "isolés"
                        if (petitVoisin){ // alors nécessairement faisait partie de "isolés"
                            isoles.remove(voisin);
                            if (voisin.podi() < nbLignesMinNRO)
                                isoles.add(voisin); // on le remet à la bonne place éventuellement
                        }
                    }
                }
                // si non regroupé, reste dans le graphe et sera regroupé avec lui-même à la fin
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private NRA getVoisin(Graph<NRA,DefaultWeightedEdge> graph, NRA nra, DefaultWeightedEdge edge){
        // on est dans un graphe non orienté
        NRA voisin = graph.getEdgeSource(edge);
        if (voisin.equals(nra))
            voisin = graph.getEdgeTarget(edge);
        return voisin;
    }
    
    private void store(String sortie, boolean cuivre, String zone){
        try{
            // réimpression d'un fichier de sortie
            System.out.println("Impression dans le fichier de sortie");
            File dir = new File(sortie);
            dir.mkdirs();
            String dossier = dir.getName();
            
            if (cuivre){
                PrintWriter csv = new PrintWriter(dir+"/"+dossier+"_"+dpt+"_"+zone+".csv");
                csv.println("NRA;SR;PC;X;Y;Type;Lignes;Zone;Parcelle;Isole;Changement de NRA;Changement de SR");
                for (NRA nra : listeNRA.get(zone).values()){
                    csv.println(nra.identifiant+";;;"+nra.x+";"+nra.y+";RE;;;;;;");
                }
                for (PointReseau sr : listeSR.get(zone).values()){
                    csv.println(sr.pere.identifiant+";"+sr.identifiant+";;"+sr.x+";"+sr.y+";SR;;;;;"+sr.changementNRA+";");
                }
                for (PC pc : listePC){
                    csv.println(pc.nra+";"+pc.pere.identifiant+";"+pc.identifiant+";"+pc.x+";"+pc.y+";PC;"+pc.lignes+";"+pc.zone+";"+pc.parcelle+";;"+pc.changementNRA+";"+pc.changementSR);
                }
                csv.close();
            } else{
                
                dir = new File(dir+"/"+dossier+"_"+dpt+"/"+dossier+"_"+dpt+"_"+zone);
                dir.mkdirs();
                PrintWriter csv = new PrintWriter(dir.getPath()+"/"+dir.getName()+".csv");
                csv.println("NRO;NRA;Type;X;Y");
                for (NRA nro : listeNRO.get(zone)) {
                    for (NRA nra : nro.listeNRAduNRO) {
                        csv.print(nro.identifiant + ";" + nra.identifiant);
                        if (nra.equals(nro)) {
                            csv.print(";NRO;");
                        } else {
                            csv.print(";NRA;");
                        }
                        csv.println(nra.x + ";" + nra.y);
                        for (String codeNRA : nra.getNRAComplementaires()){
                            csv.println(nro.identifiant + ";" + codeNRA+";NRA;"+nra.x+";"+nra.y);
                        }
                    }
                    PrintWriter pcNRO = new PrintWriter(dir.getPath()+"/ListePC_"+nro.identifiant+".csv");
                    pcNRO.println("Identifiant;X;Y;Lignes;Zone;PM_int;"+nro.x+";"+nro.y);
                    for (PC pc : nro.getPC()){
                        pcNRO.print(pc.identifiant+";"+pc.x+";"+pc.y+";"+pc.lignes+";"+pc.zone+";");
                        if (pc.PM_int) pcNRO.println(1);
                        else pcNRO.println(0);
                    }
                    pcNRO.close();
                }
                csv.close();
            }
        } catch(FileNotFoundException e){
            e.printStackTrace();
        }
    }

    public List<String> getAnalyseNRO(){
        List<String> res = new ArrayList<>();
        for (String zone : listeNRO.keySet()){
            for (NRA nro : listeNRO.get(zone)){
                String analyse = zone+";"+nro.identifiant+";";
                int nbNRA = 0;
                int nbNRAComplementaires = 0;
                int nbSR = 0;
                for (NRA nra : nro.listeNRAduNRO){
                    nbNRA++;
                    nbNRAComplementaires += nra.getNRAComplementaires().size();
                    nbSR+= nra.getFils().size();
                }
                analyse+=";"+(nbNRA+nbNRAComplementaires);
            if (lireSR) analyse+=";"+nbSR;
            analyse+=";"+nro.getPC().size()+";"+nro.podi();
                res.add(analyse);
            }
        }
        return res;
    }
    
    private void verificationInterZone(){
        for (String zone : this.listeNRO.keySet()){
            List<NRA> newListeNRO = new ArrayList<>(this.listeNRO.get(zone));
            ListIterator<NRA> iterator = newListeNRO.listIterator();
            NRA nro;
            while (iterator.hasNext()){
                nro = iterator.next();
                NRA nouveauNRO = null;
                if (nro.podi() < nbLignesMinNRO){
                    int podiMax = nro.podi();
                    for (String autreZone : this.listeNRA.keySet()){
                        if (!autreZone.equals(zone) && listeNRA.get(autreZone).containsKey(nro.identifiant)){
                            NRA nraEquiv = listeNRA.get(autreZone).get(nro.identifiant);
                            for (NRA nroAutre : listeNRO.get(autreZone)){
                                if (nroAutre.hasNRA(nraEquiv) && nroAutre.podi() > podiMax){
                                    podiMax = nroAutre.podi();
                                    nouveauNRO = nroAutre;
                                    System.out.println("Nouveau NRO trouvé !");
                                    break;
                                }
                            }
                        }
                    }
                    if (nouveauNRO!=null){
                        System.out.println("Le NRA "+nro.identifiant+" en zone "+zone+" n'est plus NRO et se rattache désormais au NRO "+nouveauNRO.identifiant+" de la zone "+nouveauNRO.zone);
                        iterator.remove();
                        Set<NRA> liberes = nro.libereNRAMultiples(listeNRA);
                        liberes.remove(nro);
                        for (NRA nra : liberes){
                            iterator.add(nra);
                            iterator.previous();
                        }
                        nro.setNewZone(nouveauNRO.zone);
                        nouveauNRO.addToEquivalent(nro);
                    }
                }
            }
            this.listeNRO.replace(zone, newListeNRO);
        }
    }

    public List<PC> getListePC(){
        return this.listePC;
    }

    public void filtreCommunes(Set<String> listeCommunes){
        try {
            double[] coord = new double[2];
            for (Map<String, NRA> liste : listeNRA.values()){
                for (NRA nra : liste.values()){
                    if (!listeCommunes.contains(nra.identifiant.substring(0,5))){
                        liste.remove(nra.identifiant);
                        coord[0]=nra.x;
                        coord[1]=nra.y;
                        indexNRA.delete(coord);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    class Comparateur implements Comparator<NRA> {

        @Override
        public int compare(NRA o1, NRA o2) {
            return o1.podi()-o2.podi();

        }
    }
    
}
