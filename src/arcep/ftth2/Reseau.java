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

import edu.wlu.cs.levy.CG.KDTree;
import edu.wlu.cs.levy.CG.KeyDuplicateException;
import java.io.*;
import java.util.*;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.Pseudograph;

public class Reseau {
   
    private final String id;
    private NoeudAGarder centre;
    private final Pseudograph<NoeudAGarder,Arete> graphe; // undirected, loops and edge multiplicity are permitted
    private int compteurArete;
    private int compteurNoeud;
    private final Set<String> zones; // ZTD_HD, ZTD_BD, ZMD_AMII ou ZMD_RIP
    //String zone; // ZTD, ZMD_AMII ou ZMD_RIP
    private final KDTree<Node> index_GC_propre = new KDTree<>(2);
    private final KDTree<Node> index_GC_voisin = new KDTree<>(2);
    private final KDTree<Node> index_routier_propre = new KDTree<>(2);
    private final KDTree<Node> index_routier_voisin = new KDTree<>(2);
    // remarque importante : les indices plus petits sont "dominants" sur les indices plus grands
    // exemple : si un Noeud peut être qualifié de GC propre et routier voisin, alors il est GC propre
    
    private final double toleranceNoeud;
    private final double seuilToleranceGC2;
    
    // ces 3 facteurs jouent un rôle en focntionnement "standard" (avec le GC d'Orange), pas en fonctionnement "dégradé" quand seul les réseaux routiers sont utilisés pour le tracé des réseaux
    private final double facteurConduite = 1.0;
    private final double facteurAerien = 1.5;
    private final double facteurPleineTerre = 4;
    
    public Reseau(String id, String zone, double toleranceNoeud, double seuilToleranceGC2){
        this.id = id;
        graphe = new Pseudograph<>(Arete.class);
        compteurArete = 0;
        compteurNoeud = 0;
        zones = new HashSet<>();
        //this.zone = zone;
        this.toleranceNoeud = toleranceNoeud;
        this.seuilToleranceGC2 = seuilToleranceGC2;
        System.out.println("Création du réseau du NRO "+id);
    }
    
    public void loadLineString(double[] coord1, double coord2[], String type, boolean voisin, int modePose, double longueur, long idProprio, String nature, List<double[]> intermediaires){
        if (type.equals("GC") || nature.startsWith("Route")){ // on évite les chemins, sentiers, pistes cyclables, bacs piétons, etc
            NoeudAGarder n1 = this.getNoeud(coord1, indice(type, voisin));
            NoeudAGarder n2 = this.getNoeud(coord2, indice(type, voisin));
            this.addArete(n1, n2, modePose, longueur, idProprio, intermediaires, type, voisin);
        }
    }
    
    public void setCentre(double x, double y){
        double[] coord = {x,y};
        centre = this.getNoeud(coord, 0);
    }

    public double[] forceConnexite(){
        double[] res = {0,0};
        ConnectivityInspector<NoeudAGarder, Arete> ci = new ConnectivityInspector(graphe);
        System.out.println("Début de la complétion de " + ci.connectedSets().size() + " ilots");
        while (!ci.isGraphConnected()) {
            Set<NoeudAGarder> noeuds = new HashSet();
            List<Set<NoeudAGarder>> ilots = ci.connectedSets();
            //System.out.println("Il reste "+ilots.size()+"ilôts"); // ça fait bien -1 à chaque fois
            Set<NoeudAGarder> ilotCentral = ci.connectedSetOf(centre);
            for (Set ilot : ilots){
                if (!ilot.equals(ilotCentral)) noeuds.addAll(ilot);
            }
            List<NoeudAGarder> extremites = this.trouverPont(ilotCentral, noeuds);
            double longueur = Parametres.arrondir(extremites.get(0).distance(extremites.get(1)),1);
            this.construitArete(extremites.get(0), extremites.get(1), longueur);
            res[0]++;
            res[1]+= longueur;
            ci.edgeAdded(null); // pour réinitialiser le ConnectivityInspector après ajout de l'arête
        }
        return res;
    }
    
    public void addPC(double x, double y, String zone, int nbLignes, boolean isPMint){
        double[] coord = {x, y};
        Object[] gc = this.distanceAuCentre(coord, "GC");
        Node n = (Node) gc[1];
        NoeudAGarder noeud;
        double distGC = n.distance(coord);
        if (distGC < toleranceNoeud){
            if (n instanceof NoeudInterne) noeud = this.keep((NoeudInterne) n,(Integer) gc[2]);
            else noeud = (NoeudAGarder) n;
        } else{
            Object[] routier = this.distanceAuCentre(coord, "routier");
            if (distGC < this.seuilToleranceGC2 && (Double) gc[0] < (Double) routier[0]){
                noeud = this.getNoeud(coord, (Integer) gc[2]);
                if (n instanceof NoeudInterne) n = keep((NoeudInterne) n,(Integer) gc[2]);
                this.construitArete(noeud, (NoeudAGarder) n, distGC);
                this.distanceDijkstra(noeud); // pour s'assurer que le pathToCentre existe
            } else{
                n = (Node) routier[1];
                if (n instanceof NoeudInterne) n = keep((NoeudInterne) n,(Integer) routier[2]);
                double distRoutier = n.distance(coord);
                if(distRoutier < toleranceNoeud) noeud = (NoeudAGarder) n;
                else{
                    noeud = this.getNoeud(coord, (Integer) routier[2]);
                    this.construitArete(noeud, (NoeudAGarder) n, distRoutier);
                    this.distanceDijkstra(noeud); // pour s'assurer que le pathToCentre existe
                }
            }
        }
        if (zone.equals("ZTD_HD") && isPMint) noeud.declarePMint();
        noeud.addDemande(zone, nbLignes);
        zones.add(zone);
    }
    
    public void test(){
        System.out.println("Nombre de noeuds dans le graphe : " + graphe.vertexSet().size());
        System.out.println("Nombre d'arêtes dans le graphe : " + graphe.edgeSet().size());
        for (int i = 0;i<=3;i++){
            System.out.println("Taille de l'index "+type(i)+" : "+index(i).size());
        }
    }
    
    public void store(File dossier){
        try{
            PrintWriter writer = new PrintWriter(dossier+"/Aretes_"+id+".csv");
            writer.println("Identifiant;Noeud1;Noeud2;Mode de pose;Longueur;Proprietaire;Points intermediaires");
            for (Arete a : graphe.edgeSet()){
                a.print(writer, graphe.getEdgeSource(a), graphe.getEdgeTarget(a));
            }
            writer.close();
            writer = new PrintWriter(dossier+"/Noeuds_"+id+".csv");
            writer.print("Id du centre;"+centre.id+";Zones:;"+zones.size());
            for (String zone : zones){
                writer.print(";"+zone);
            }
            writer.println();
            writer.println("Identifiant;X;Y;Nombre de zone de demande locale;Demande locale par zone;Indice PM;Nombre d'arêtes dans le path;Identifiants des arêtes");
            DijkstraShortestPath dsp = new DijkstraShortestPath(graphe);
            for (NoeudAGarder n : graphe.vertexSet()){
                if (n.hasDemandeLocale()) n.addPath(dsp.getPath(centre, n));
                n.print(writer);
            }
            writer.close();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static Map<Integer,Noeud> readNoeudsSelectDemande(String dossier, String codeNRO, List<Noeud> demande, int seuilPMint){
        Map<Integer,Noeud> noeuds = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            reader.readLine();
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String fields[] = line.split(";");
                Noeud n = new Noeud(line.split(";"), seuilPMint);
                noeuds.put(Integer.parseInt(fields[0]),n);
                if (n.demandeLocaleTotale()>0){
                    demande.add(n);
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return noeuds;
    }
    
    public static Map<Integer,Noeud> readNoeuds(String dossier, String codeNRO, int seuilPMint){
        Map<Integer,Noeud> map = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            reader.readLine();
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String fields[] = line.split(";");
                map.put(Integer.parseInt(fields[0]), new Noeud(fields, seuilPMint));
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return map;
    }
    
    public static int readCentre(String dossier, String codeNRO){
        int i = -1;
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            String line = reader.readLine();
            i = Integer.parseInt(line.split(";")[1]);
        } catch (IOException e){
            e.printStackTrace();
        }
        return i;
    }

    public static Map<Long,Arete> readAretes(String dossier, String codeNRO){
        Map<Long,Arete> map = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Aretes_"+codeNRO+".csv"));
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String[] fields = line.split(";");
                map.put(Long.parseLong(fields[0]), new Arete(fields));
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return map;
    }
    
    private KDTree<Node> index(int indice){
        switch(indice){
            case 0:
                return index_GC_propre;
            case 1:
                return index_GC_voisin;
            case 2:
                return index_routier_propre;
            case 3:
                return index_routier_voisin;
            default:
                return null;
        }
    } 
    
    private int indice(String type, boolean voisin){
        int i = -1;
        switch(type){
            case "GC":
                if (voisin) i = 1;
                else i = 0;
                break;
            case "routier":
                if (voisin) i = 3;
                else i = 2;
                break;
        }
        return i;
    }
    
    private String type(int indice){
        switch(indice){
            case 0:
            case 1:
                return "GC";
            case 2:
            case 3:
                return "routier";
            default:
                return "ERROR";
        }
    }                
                        
    private NoeudAGarder getNoeud(double[] coord, int iMax){
        Node n = null;
        try{
            // on cherche si un noeud existe déjà à cet emplacement dans les index jusqu'au "sien" (le remplissage se faisant dans cet ordre)
            for (int i = 0; i <= iMax ;i++){
                n = index(i).search(coord);
                if (n != null && n instanceof NoeudAGarder) return (NoeudAGarder) n;
            }
            try{
                // on cherche un noeud proche dans les index
                for (int i = 0; i <= iMax;i++){
                    if (index(i).size() > 0) n = index(i).nearest(coord);
                    else n = null;
                    if (n != null && n instanceof NoeudAGarder && n.distance(coord) < toleranceNoeud){
                        index(iMax).insert(coord, n); // plusieurs coordonnées peuvent bien pointer sur le même noeud, même parmi différents index
                        return (NoeudAGarder) n;
                    }
                }
                // si on n'a vraiment trouvé aucun noeud correspondant déjà existant
                compteurNoeud++;
                n = new NoeudAGarder(compteurNoeud, coord);
                graphe.addVertex((NoeudAGarder) n);
                index(iMax).insert(coord, n);
                return (NoeudAGarder) n;
            } catch(KeyDuplicateException e){
                // si jamis il y avait un NoeudInterne au mauvais endroit, on l'ôte de l'index en question pour mettre un vrai Noeud
                //System.out.println("Utilisation de KeyDuplicateException dans getNoeud"); // ça arrive au moins qqs fois à chaque NRO, et parfois  souvent
                index(iMax).delete(coord);
                index(iMax).insert(coord, n);
                return (NoeudAGarder) n;
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("Utilisation du return qui ne devrait pas l'être lors de la création d'un noeud !");
        return (NoeudAGarder) n;
    }

    public NoeudInterne getNoeudInterne(double[] coord, int i, NoeudAGarder n1, NoeudAGarder n2, Arete a, double distanceADroite){
        compteurNoeud++;
        NoeudInterne n = new NoeudInterne(compteurNoeud, coord, n1, n2, a, distanceADroite, i);
        try{
            if (index(i).search(coord) == null){
               index(i).insert(coord, n);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        return n;
    }
    
    private void addArete(NoeudAGarder n1, NoeudAGarder n2, int modePose, double longueur, long idProprio, List<double[]> intermediaires, String type, boolean voisin){
        try{
            double longueurPonderee = longueur*multiple(modePose);
            // si on utilise un graphe qui n'autorise pas des plusieurs arêtes différentes entre deux points
            /*if (a!=null && graphe.getEdgeWeight(a) > longueurPonderee){ // on supprime l'arête du graphe et ses noeuds internes des index
                graphe.removeEdge(a);
                for (NoeudInterne n : a.intermediaires){
                    if (index(n.indice).search(n.coord).equals(n))                        
                        index(n.indice).delete(n.coord);
                }
                a = null;
            }
            if (a==null){*/
            compteurArete++;
            Arete a = new Arete(compteurArete, modePose, idProprio, longueur);
            a.addIntermediaires(intermediaires, indice(type,voisin), n1, n2,this);                
            graphe.addEdge(n1, n2, a);
            graphe.setEdgeWeight(a, longueurPonderee);
            //}
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void addArete(NoeudAGarder n1, NoeudAGarder n2, int modePose, double longueur, long idProprio, List<NoeudInterne> intermediaires){
        compteurArete++;
        Arete a = new Arete(compteurArete, modePose, idProprio, longueur);
        a.changeAndAddIntermediaires(intermediaires,n1,n2);
        graphe.addEdge(n1, n2, a);
        try{
            graphe.setEdgeWeight(a, longueur*multiple(modePose));
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void construitArete(NoeudAGarder n1, NoeudAGarder n2, double longueur){
        addArete(n1, n2, 12, longueur, -1, new ArrayList<NoeudInterne>());
    }
    
    private List<NoeudAGarder> trouverPont(Set<NoeudAGarder> s1, Set<NoeudAGarder> s2){
        NoeudAGarder n1 = null,n2 = null;
        List<NoeudAGarder> res = new ArrayList<>();
        // on fait en sorte d'indexer le grand ensemble de noeuds pour être en O(nlog(m)) si n < m
        Set<NoeudAGarder> petit;
        Set<NoeudAGarder> grand;
        if (s1.size() <= s2.size()){
            petit = s1;
            grand = s2;
        } else{
            petit = s2;
            grand = s1;
        }
        KDTree<NoeudAGarder> index = new KDTree<>(2);
        try{
            for (NoeudAGarder n : grand){
                index.insert(n.coord, n);
            }
            double distance, distMin = 10000000;
            for (NoeudAGarder n : petit){
                NoeudAGarder plusProche = index.nearest(n.coord);
                distance = n.distance(plusProche);
                if (distance < distMin){
                    distMin = distance;
                    n1 = n;
                    n2 = plusProche;
                }
            }
            res.add(n1);
            res.add(n2);
        } catch(Exception e){
            e.printStackTrace();
        }
        return res;
    }
    
    
    private double distanceDijkstra(NoeudAGarder noeud){
        if (!noeud.pathConnu){
            noeud.addPath((new DijkstraShortestPath(graphe)).getPath(centre, noeud));
        }
        return noeud.distanceAuCentre;
    }
    
    private double distanceDijkstra(Node n){
        if (n instanceof NoeudAGarder){
            return distanceDijkstra((NoeudAGarder) n);
        } else{
            NoeudInterne ni = (NoeudInterne) n;
            try{
            double d0 = ni.distance*multiple(ni.a.modePose) + distanceDijkstra(ni.voisins[0]);
            double d1 = (graphe.getEdgeWeight(ni.a) - ni.distance)*multiple(ni.a.modePose) + distanceDijkstra(ni.voisins[1]);
            if (d0 <= d1) return d0;
            else return d1;
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        return 0;
    }
    
    private Object[] distanceAuCentre(double[] coord, String type){
        Object[] result = new Object[3];
        try{
            Node n1 = index(indice(type,false)).nearest(coord);
            double d1 = n1.distance(coord)*multiple(11)+ distanceDijkstra(n1);
            result[0] = d1;
            result[1] = n1;
            result[2] = indice(type,false);
            if (index(indice(type,true)).size() > 0){
                Node n2 = index(indice(type,true)).nearest(coord);
                double d2 = n2.distance(coord)*multiple(11)+ distanceDijkstra(n2);
                if (d2<d1){
                    result[0] = d2;
                    result[1] = n2;
                    result[2] = indice(type,true);
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return result;
    }
    
    // fonction pour modifier le graphe quand un noeud interne devient finalement à garder
    private NoeudAGarder keep(NoeudInterne n, int i){
        NoeudAGarder nouveau = getNoeud(n.coord, i);
        graphe.removeEdge(n.a);
        Iterator<NoeudInterne> iterator = n.a.intermediaires.iterator();
        Node precedent;
        NoeudInterne suivant = iterator.next(); // au moins 1 élément, n !
        double longueur = n.voisins[0].distance(suivant);
        List<NoeudInterne> intermediaires1 = new ArrayList<>();
        while (!suivant.equals(n)){
            intermediaires1.add(suivant);
            precedent = suivant;
            suivant = iterator.next();
            longueur += precedent.distance(suivant);
        }
        addArete(n.voisins[0],nouveau,n.a.modePose,Parametres.arrondir(longueur,1),n.a.idProprio,intermediaires1);
        longueur = 0;
        List<NoeudInterne> intermediaires2 = new ArrayList<>();
        precedent = n;
        while (iterator.hasNext()){
            suivant = iterator.next();
            longueur += precedent.distance(suivant);
            intermediaires2.add(suivant);
            precedent = suivant;
        }
        longueur += precedent.distance(n.voisins[1]);
        addArete(nouveau,n.voisins[1],n.a.modePose, Parametres.arrondir(longueur,1), n.a.idProprio, intermediaires2);
        distanceDijkstra(nouveau); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
        return nouveau;
    }
    
    private double multiple (int modePose) throws Exception{
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
    
}
