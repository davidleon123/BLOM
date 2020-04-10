
package arcep.ftth2;

import org.jgrapht.graph.DefaultWeightedEdge;
import com.vividsolutions.jts.geom.Coordinate;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Arete extends DefaultWeightedEdge{

    long id;
    int modePose = -1;
    int nbLignes = 0;
    long idProprio;
    double longueur;
    List<NoeudInterne> intermediaires;
    private int idN1, idN2;
    private List<double[]> points;

    public Arete(){}
    
    public Arete(long id, int modePose){
        this.id = id;
        this.modePose = modePose;
    }
    
    public Arete(long id, int modePose, long idProprio, double longueur){
        this.id = id;
        this.modePose = modePose;
        this.idProprio = idProprio;
        this.longueur = longueur;
        intermediaires = new ArrayList<>();
    }
    
    public Arete(String[] fields){
        id = Long.parseLong(fields[0]);
        idN1 = Integer.parseInt(fields[1]);
        idN2 = Integer.parseInt(fields[2]);
        modePose = Integer.parseInt(fields[3]);
        longueur = Double.parseDouble(fields[4]);
        idProprio = Long.parseLong(fields[5]);
        points = new ArrayList<>();
        for (int i = 7;i<fields.length;i+=2){
            double[] coord = {Double.parseDouble(fields[i]),Double.parseDouble(fields[i+1])};
            points.add(coord);
        }
    }
    
    public Arete(Arete ar){
        this.id = ar.id;
        this.modePose = ar.modePose;
        this.nbLignes = ar.nbLignes;
        this.idProprio = ar.idProprio;
    }

    public void addIntermediaire(NoeudInterne n){
        intermediaires.add(n);
    }
    
    public void addIntermediaires(List<double[]> intermediaires, int indice, NoeudAGarder n1, NoeudAGarder n2, Reseau reseau){
        Node precedent = n1;
        double distance = 0;
        while (!intermediaires.isEmpty()){
            double[] coord = intermediaires.remove(0);
            distance+=precedent.distance(coord);
            precedent = reseau.getNoeudInterne(coord, indice, n1, n2, this, Parametres.arrondir(distance,1));
            this.intermediaires.add((NoeudInterne) precedent);
        }
    }
    
    public void changeAndAddIntermediaires(List<NoeudInterne> noeuds, NoeudAGarder n1, NoeudAGarder n2){
        Node precedent = n1;
        double distance = 0;
        while (!noeuds.isEmpty()){
            NoeudInterne n = noeuds.remove(0);
            distance+=precedent.distance(n);
            n.actualise(this, n1, n2, distance);
            intermediaires.add(n);
            precedent = n;
        }
    }
    
    public void print(PrintWriter writer, NoeudAGarder n1, NoeudAGarder n2){
        writer.print(id+";"+n1.id+";"+n2.id+";");
        writer.print(modePose+";"+longueur+";"+idProprio+";"+intermediaires.size());
        for (NoeudInterne n : intermediaires){
            writer.print(";"+n.coord[0]+";"+n.coord[1]);
        }
        writer.println();
    }
    
    public int getAutreExtremite(int id){
        if (id == idN1) return idN2;
        else if (id == idN2) return idN1;
        else {
            System.out.println("Problème dans le path NRO-PC");
            return -1;
        }
    }
    
    public void setModePose(int mode) {
        this.modePose = mode;
    }

    public Noeud autreNoeud(Noeud extremite) {
        if (super.getSource().equals(extremite)) {
            return (Noeud)super.getTarget();
        } else {
            return (Noeud)super.getSource();
        }
    }
    
    public Coordinate[] getPoints(Noeud n1, Noeud n2){
        int n = points.size()+2;
        Coordinate[] coords = new Coordinate[n];
        coords[0] = new Coordinate(n1.coord[0], n1.coord[1]);
        for (int i = 1;i<n-1;i++){
            double[] coord = points.get(i-1);
            coords[i] = new Coordinate(coord[0], coord[1]);
        }
        coords[n-1] = new Coordinate(n2.coord[0], n2.coord[1]);
        return coords;
    }

}