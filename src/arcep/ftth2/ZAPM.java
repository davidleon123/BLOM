
package arcep.ftth2;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;

public class ZAPM {
    
    private final int id;
    private final String zone;
    private final UO uo;
    private final double x;
    private final double y;
    private final double distanceNRO;
    
    // connaissance des longueurs PM-PBO
    private double longueurMin;
    private double longueurMax;
    private double longueurTotale;
    private final int[] distributionLongueurs;    

    public ZAPM(Noeud n, double distance, String zone, Parametres parametres){
        this.id = n.id;
        this.zone = zone;
        uo = new UO(zone, true, false, parametres);
        uo.setNbLignes(n.taillePM(zone), parametres);

        this.x = n.coord[0];
        this.y = n.coord[1];
        this.distanceNRO = distance;
        longueurMin = Double.POSITIVE_INFINITY;
        longueurMax = 0;
        longueurTotale = 0;
        distributionLongueurs = new int[2000];
        distributionLongueurs[0] = n.demandeLocaleExt(zone);
    }
    
    public void add(AreteBLOM a, Parametres parametres, double distancePM){
        uo.addLineaires(a, parametres);
        uo.addPBO((int) Math.ceil(a.n.demandeLocaleExt(zone)/(double) parametres.nbMaxLignesPBO), a.modePose);
        if(!a.n.isPMExt(zone)){
            int demandeLocale = a.n.demandeLocaleExt(zone);
            if (demandeLocale > 0){
                if (distancePM < longueurMin) longueurMin = distancePM;
                if (distancePM > longueurMax) longueurMax = distancePM;
            }
            distributionLongueurs[Math.min(1999, (int) Math.floor(distancePM/50))] += demandeLocale;
            this.longueurTotale += demandeLocale*distancePM;
        }
    }
    
    public int getID(){
        return this.id;
    }
    
    public void addIncomingCables(AreteBLOM a, Parametres parametres){
        //System.out.println("Zone : "+zone);
        int[] cablesFille = a.getCables(true, zone);
        uo.addIncomingCables(cablesFille, true, parametres.calibre, parametres.getCalibreMin());
    }
    
    public UO getUO(){
        return uo;
    }
    
    public String getStringUO(String codePM, Parametres parametres, int detail){
        return codePM+";"+x+";"+y+";"+String.valueOf(distanceNRO).replace(".", ",")+";"+uo.print(parametres, detail);
    }
    
    public String getStringDistri(){
        String ligne = String.valueOf(distanceNRO).replace(".", ",")+";"
                +String.valueOf(longueurMin).replace(".", ",")+";"
                +String.valueOf(longueurMax).replace(".", ",");
        for (int i : distributionLongueurs){
            ligne+=";"+i;
        }
        return ligne;
    }
    
    public double percentile(double pourcent){
        int compteur = 0;
        int seuil = (int) Math.floor(this.uo.getNbLignes()*pourcent);
        for (int i = 0;i<this.distributionLongueurs.length;i++){
            compteur += this.distributionLongueurs[i];
            if (compteur >=  seuil)
                return 25+i*50;
        }
        return Double.NaN;
    }
    
    public int toShapefilePM(String CodeNRO, GeometryFactory gf, SimpleFeatureCollection collectionPM, SimpleFeatureBuilder featureBuilderPM, int i){
        featureBuilderPM.add(gf.createPoint(new Coordinate(x, y)));
        featureBuilderPM.add(CodeNRO+"_"+zone+"_"+i);
        featureBuilderPM.add("PM_ext");
        featureBuilderPM.add(uo.getNbLignes());
        collectionPM.add(featureBuilderPM.buildFeature(null));
        return i++;
    }

    public String getNROPM(Parametres parametres){
        String type = "PM300";
        if (zone.equals("ZTD_HD"))
            type = "PM100";
        return type+";"+String.valueOf(distanceNRO).replace(".", ",")+";"+uo.getNbLignes() + uo.printIncomingCables(true)+";"+String.valueOf(this.longueurTotale/this.uo.getNbLignes()).replace(".", ",");
    }
    
    public int[] getPBOGC(){
        return uo.getPBOGC();
    }
    
}
