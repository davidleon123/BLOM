
package arcep.ftth2;

public class PointReseau {
        
    String identifiant;
    public double x,y;
    String type;
    PointReseau pere;
    String zone;
    Noeud n;
    
    String changementNRA;
    
    PointReseau() {}
    
    PointReseau(String identifiant, double x, double y, String type, PointReseau pere){
        this.init(identifiant, x, y, type, "");
        this.pere = pere;        
        this.changementNRA = "FAUX";
    }
    
    public void init(String identifiant, double x, double y, String type, String zone){
        this.identifiant = identifiant;
        this.x = x;
        this.y = y;
        this.type = type;
        this.zone = zone;
    }
    
    public double distance(double[] coord){
        return Math.sqrt(Math.pow(x-coord[0],2)+Math.pow(y-coord[1],2));
    }
}
