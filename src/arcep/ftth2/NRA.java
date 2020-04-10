
package arcep.ftth2;

import java.util.*;
import edu.wlu.cs.levy.CG.*;

public class NRA extends PointReseau {
    
    private int PODI = 0; // nombre de lignes rattachées au NRA
    private final ArrayList<PC> listePC = new ArrayList<>();; // maintenue triée dans l'ordre croissant des distances NROPC (possible que lorsque les PC ont été rattachés au réseau)
    private final ArrayList<PointReseau> fils = new ArrayList<>();;
    boolean nouveau;
    private final List<String> nraComplementaires = new ArrayList<>();;
        
    HashSet<NRA> listeNRAduNRO = new HashSet<>();
    boolean traite = false;
    NRA nro;
    
    public NRA(String identifiant, double x, double y, boolean nouveau){
        this.init(identifiant, x, y, nouveau, "");
    }
    
    public NRA(String identifiant, double x, double y, boolean nouveau, String zone) {
        this.init(identifiant, x, y, nouveau, zone);
    }
    
    public NRA(NRA nra, String zone){
        this.init(nra.identifiant, nra.x, nra.y, nra.nouveau, zone);
    }
    
    private void init(String identifiant, double x, double y, boolean nouveau, String zone) {
        // champs hérités de PointReseau - attention, "pere" et "changementNRA" ne sont pas initialisés
        this.init(identifiant, x, y, "NRA", zone);
        
        // champs propres
        listeNRAduNRO.add(this);
        this.nouveau = nouveau;
    }
    
    public void addNRA(String codeNRA){
        nraComplementaires.add(codeNRA);
    }
    
    public List<String> getNRAComplementaires(){
        return this.nraComplementaires;
    }
    
    public int podi(){
        return this.PODI;
    }
    
    public void ajoutPC(PC pc){
        listePC.add(pc);
        PODI+= pc.lignes;
    }
    
    public void clearListePC(){
        listePC.clear();
        PODI = 0;
    }
    
    public List<PC> getPC(){
        return listePC;
    }

    public void checkPC(double distanceLimite){
        ListIterator<PC> iterator = this.listePC.listIterator();
        PC pc;
        double[] coord = new double[]{this.x, this.y};
        while (iterator.hasNext()){
            pc = iterator.next();
            if (pc.distance(coord) > distanceLimite){
                iterator.remove();
                this.PODI -= pc.lignes;
            }
        }
    }
    
    public void regrouper(NRA nra) {
        this.ajoutPC(nra.listePC);
        this.traite = true;
        this.nro = this;
        nra.traite = true;
        for (NRA nra1 : nra.listeNRAduNRO){
            nra1.nro = this;
            listeNRAduNRO.add(nra1);
        }
    }
    
    public Set<NRA> libereNRAMultiples(Map<String,Map<String,NRA>> listeNRA){
        Set<NRA> res = new HashSet<>();
        Iterator<NRA> iterator = this.listeNRAduNRO.iterator();
        NRA nra;
        while(iterator.hasNext()){
            nra = iterator.next();
            if (!nra.equals(this)){
                boolean multiple = false;
                for (String s : listeNRA.keySet()){
                    if (!s.equals(nra.zone) && listeNRA.get(s).containsKey(nra.identifiant)){
                        multiple = true;
                        break;
                    }
                }
                if (multiple){
                    res.addAll(nra.libereNRAMultiples(listeNRA));
                    this.listePC.removeAll(nra.listePC);
                    this.PODI -= nra.PODI;
                    iterator.remove();
                }
            }
        }
        this.nro = this;
        res.add(this);
        return res;
    }
    
    public boolean hasNRA(NRA nra){
        return this.listeNRAduNRO.contains(nra);
    }
    
    public void addToEquivalent(NRA nra){
        for (NRA equiv : this.listeNRAduNRO){
            if (equiv.identifiant.equals(nra.identifiant)){
                equiv.absorbe(nra);
                this.ajoutPC(nra.listePC);
                break;
            }
        }
    }
    
    public void absorbe(NRA nra){
        String newZone = this.zone;
        if (this.zone.equals("ZTD"))
            newZone = "ZTD_BD";
        for (PC pc : nra.listePC){
            pc.zone = newZone;
        }
        this.ajoutPC(nra.listePC);
        for (PointReseau pt : nra.fils){
            pt.zone = newZone;
            pt.pere = this;
            this.fils.add(pt);
        }
    }
    
    public void setNewZone(String zone){
        this.zone = zone;
        for (PC pc : this.listePC){
            if (zone.equals("ZTD"))
                pc.zone = "ZTD_BD";
            else pc.zone = zone;
        }
    }
    
    private void ajoutPC(List<PC> liste) {
        listePC.addAll(liste);
        for (PC PC : liste) {
            PODI += PC.lignes;
        }
    }
    
    public void addFils(PointReseau pt, KDTree<NRA> indexNRA){
        if (nouveau) {
            double[] coord = new double[2];
            coord[0] = this.x;
            coord[1] = this.y;
            try{
                indexNRA.delete(coord);
                double n = (double) this.fils.size();
                this.x = (coord[0]*n+pt.x)/(n+1);
                this.y = (coord[1]*n+pt.y)/(n+1);
                coord[0] = this.x;
                coord[1] = this.y;
                indexNRA.insert(coord, this);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        this.fils.add(pt);
    }
    
    public List<PointReseau> getFils(){
        return this.fils;
    }
    
    public double distance(NRA nra){
        return Math.sqrt(Math.pow(this.x - nra.x, 2)+Math.pow(this.y - nra.y, 2));
    }

}
