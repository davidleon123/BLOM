
package arcep.ftth2;

import java.io.PrintWriter;
import java.util.*;
import org.jgrapht.alg.DijkstraShortestPath;

public class NoeudAGarder extends Node {
    
    boolean pathConnu;
    public double distanceAuCentre;
    private List<Long> pathToCentre;
    private Map<String,Integer> demandeParZone;
    boolean indicePMint;
    
    public NoeudAGarder(){}
    
    public NoeudAGarder(int id, double[] coord){
        this.id = id;
        this.coord = coord;
        pathConnu = false;
        demandeParZone = new HashMap<>();
        indicePMint = false;
    }
    
    public void init(){
        demandeParZone = new HashMap<>();
        pathToCentre = new ArrayList<>();
    }
    
    public void addPath(DijkstraShortestPath dsp, NoeudAGarder centre){
        pathConnu = true;
        distanceAuCentre = Parametres.arrondir(dsp.getPathLength(),1);
        List<Arete> liste = dsp.getPathEdgeList();
        pathToCentre = new ArrayList<>();
        for (Arete a : liste){
            pathToCentre.add(a.id);
        }
    }
    
    public void declarePMint(){
        this.indicePMint = true;
    }
    
    public void addDemande(String zone, int nbLignes){
        if (!demandeParZone.containsKey(zone)) demandeParZone.put(zone, nbLignes);
        else demandeParZone.put(zone,demandeParZone.get(zone)+nbLignes);
    }
    
    public boolean hasDemandeLocale(){
        return demandeParZone.size() > 0;
    }
    
    public void print(PrintWriter writer){
        writer.print(id+";"+coord[0]+";"+coord[1]+";"+demandeParZone.size());
        for (String zone : demandeParZone.keySet()){
            writer.print(";"+zone+";"+(int) demandeParZone.get(zone));
        }
        int isPMint = 0;
        if (this.indicePMint || this.getDemandeLocale("ZTD_HD") >= 12)
            isPMint = 1;
        writer.print(";"+isPMint);
        if (this.hasDemandeLocale()){
            writer.print(";"+pathToCentre.size());
            for (Long n : pathToCentre){
                writer.print(";"+n);
            }
        } else writer.print(";0");
        writer.println();
    }
    
    public void addToPath(Long id){
        pathToCentre.add(id);
    }
    
    public List<Long> getPath(){
        return pathToCentre;
    }
    
    public int getDemandeLocale(String zone){
        if (this.demandeParZone.containsKey(zone))
            return this.demandeParZone.get(zone);
        else
            return 0;
    }
    
    public int demandeLocaleTotale(){
        int demande = 0;
        for (String zone : demandeParZone.keySet()){
            demande+= demandeParZone.get(zone);
        }
        return demande;
    }
    
    public void modifieDemande(String zone, int n){
        this.demandeParZone.put(zone, this.demandeParZone.get(zone)+n);
    }
    
}
