
package arcep.ftth2;

import javax.swing.UIManager;

public class Main {
    
    public static String cheminReseau = "Q:/Modele FttH v2/files/";

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        String shapesGC = "C:/Inputs/GC";
        String shapesRoutes = "C:/Inputs/Routes";
        String fileDpts = "liste-departements.csv";
        FenPrincipale fenPrincipale = new FenPrincipale(cheminReseau, shapesGC, shapesRoutes, fileDpts);
    }
    
}
