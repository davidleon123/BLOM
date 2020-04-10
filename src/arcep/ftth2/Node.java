
package arcep.ftth2;

public class Node {
    
    int id;
    double[] coord;
    
    public double distanceQuad(double x, double y) {
        return Math.pow((coord[0] - x),2) + Math.pow((coord[1] - y),2);
    }

    public double distance(Node n) {
        return Math.sqrt(this.distanceQuad(n.coord[0], n.coord[1]));
    }
    
    public double distance(double[] coord){
        return Math.sqrt(this.distanceQuad(coord[0], coord[1]));
    }
    
}
