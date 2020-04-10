
package arcep.ftth2;

public class SR extends PointReseau {
    
    String codeNRA; // champ provisoire avant de trouvet l'objet NRA auquel rattacher le SR
    
    public SR(String identifiant, double x, double y, String codeNRA){
        this.init(identifiant, x, y, "SR", "");
        this.codeNRA = codeNRA;
    }
    
    public SR(SR sr, String zone){
        this.init(sr.identifiant, sr.x, sr.y, "SR", zone);
        this.codeNRA = sr.codeNRA;
    }
    
    public void rattachePere(NRA nra){
        this.pere = nra;
    }
    
}
