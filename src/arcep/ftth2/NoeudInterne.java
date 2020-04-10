
package arcep.ftth2;

public class NoeudInterne extends Node{
    
    Arete a;
    NoeudAGarder[] voisins = new NoeudAGarder[2];
    double distance;
    int indice;
    
    public NoeudInterne(int id, double[] coord, NoeudAGarder n1, NoeudAGarder n2, Arete a, double distance, int indice){
        this.id = id;
        this.coord = coord;
        this.a = a;
        voisins[0] = n1;
        voisins[1] = n2;
        this.distance = distance;
        this.indice = indice;
    }
    
    public void actualise(Arete a, NoeudAGarder n1, NoeudAGarder n2, double distance){
        this.a = a;
        this.voisins[0] = n1;
        this.voisins[1] = n2;
        this.distance = distance;
    }
}
