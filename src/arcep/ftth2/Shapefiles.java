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

import java.io.*;
import java.util.*;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.valid.IsValidOp;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;
import com.vividsolutions.jts.triangulate.VoronoiDiagramBuilder;

import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.*;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.*;
import org.geotools.feature.simple.*;
import org.geotools.geometry.jts.JTSFactoryFinder;

import org.opengis.feature.simple.*;
import org.opengis.feature.type.*;
import org.opengis.filter.*;

public class Shapefiles {
    
    private static void computePourcentagesGC(String codeNRO, String dossierNRO, File fichierGC, String dossierZANRO, String pourcentages){
        int n = 11;
        double[] res = new double[n];
        File modesPose = new File(dossierNRO+"/"+pourcentages+"_"+codeNRO+".csv");
        try{
            PrintWriter writer = new PrintWriter(modesPose, "utf-8");
            writer.println("NRO;Aerien Orange;Aerien Enedis;Façade;Immeuble;Pleine terre;Caniveau;Galerie;Coduite allegee;Egout;Conduite enrobee;Conduite mixte");
            File dirZANRO = new File(dossierZANRO);
            FileDataStore store = FileDataStoreFinder.getDataStore(new File(dirZANRO+"/"+dirZANRO.getName()+".shp"));
            FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
            SimpleFeatureType schema = store.getSchema();
            System.out.println("CRS du shapefile : "+schema.getCoordinateReferenceSystem().getName().getCode());
            while (reader.hasNext()){
                SimpleFeature feature = reader.next();
                String nro = (String) feature.getAttribute("Code_ZANRO");
                if (nro.equals(codeNRO)){
                    res = analyseGC((Geometry) feature.getDefaultGeometry(), fichierGC, n);
                    break;
                }
            }
            reader.close();
            store.dispose();
            writer.print(codeNRO);
            for (int i = 0;i<n;i++){
                writer.print(";"+String.valueOf(Parametres.arrondir(res[i],4)).replace(".", ","));
            }
            writer.println();
            writer.close();
        }catch(IOException e){}
    }
       
    private static double[] analyseGC(Geometry enveloppe, File fichierGC, int n){
        long start = System.currentTimeMillis();
        double[] longueursGC = new double[n];
        try{   
            // accès au shapefile
            FileDataStore store = FileDataStoreFinder.getDataStore(fichierGC);           
            SimpleFeatureType type = store.getSchema();
            
            // description des champs 
            /*List<AttributeDescriptor> descriptors = type.getAttributeDescriptors();
            for (AttributeDescriptor descriptor : descriptors){
                System.out.println("Champ "+descriptor.getLocalName()+" de type "+descriptor.getType().getName().toString());
                i++;
            }*/
            
            // filtre sur le périmètre de la ZANRO
            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
            Filter filtreNRA = ff.intersects(ff.property(type.getGeometryDescriptor().getLocalName()), ff.literal(enveloppe.buffer(100)));
            FeatureIterator<SimpleFeature> iterator = store.getFeatureSource().getFeatures(filtreNRA).features();
            
            // lecture        
            while (iterator.hasNext()){
                SimpleFeature feature = iterator.next();
                MultiLineString geom =  (MultiLineString) feature.getAttribute("the_geom");
                int modePose;
                try{
                    modePose = Integer.parseInt((String) feature.getAttribute("MODE_POSE"));
                    longueursGC[modePose]+=(double) geom.getLength();
                }catch(NumberFormatException e){
                    System.out.println("ModePose mal indiqué pour une arête de GC");
                    e.printStackTrace();
                }  
            }
            iterator.close();
            store.dispose();
            
            // calcul pourcentages
            double longueurTotale = 0;
            for (int i = 0;i<n;i++){
                longueurTotale += longueursGC[i];
            }
            for (int i = 0;i<n;i++){
                longueursGC[i] = longueursGC[i]/longueurTotale;
            }
            
        } catch (Exception e){
            e.printStackTrace();                        
        }
        System.out.println("Analyse du GC terminée - temps de calcul : "+Math.round((System.currentTimeMillis()-start)/ (double) 1000)+" secondes.");
        return longueursGC;

    }

    // contrôle des PC, attribution des zones et préformatage
    public static void zonagePC(String shpDpts, String dossierCommunes, String shpZTD, String shpAMII, String origine, String destination, Map<String, List<String>> dptLimitrophes){
        long start = System.currentTimeMillis();
        System.out.println("Début de l'attribution des zones aux PC");
        try{
            
        // lecture et stockage de ZTD_HD et ZTD_BD
        FileDataStore store = FileDataStoreFinder.getDataStore(new File(shpZTD));
        FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
        SimpleFeatureType type = store.getSchema();
        System.out.println("CRS du shapefile : "+type.getCoordinateReferenceSystem().getName().getCode());
        List<AttributeDescriptor> descriptors = type.getAttributeDescriptors();
        String[] attributes = new String[descriptors.size()];
        int i = 0;
        for (AttributeDescriptor descriptor : descriptors){
            System.out.println(attributes[i] = descriptor.getLocalName());
            i++;
        }
        int srid = 0;
        
        PrecisionModel pm = new PrecisionModel(10);
        GeometryPrecisionReducer gpr = new GeometryPrecisionReducer(pm);
        
        Map<String, MultiPolygon> poches = new HashMap<>();
        while(reader.hasNext()){           
            SimpleFeature feature = reader.next();
            MultiPolygon geom = (MultiPolygon) gpr.reduce((MultiPolygon) feature.getDefaultGeometry());
            String code = (String) feature.getAttribute(1);
            System.out.println(code);
            poches.put(code, geom);
            srid = geom.getSRID();
            System.out.println(srid);
        }
        reader.close();
        store.dispose();
        
        GeometryFactory gf = new GeometryFactory(pm);
        
        GeometryCollection col = new GeometryCollection(poches.values().toArray(new MultiPolygon[poches.size()]),gf);
        Geometry ztd = col.union();
        
        Map<String,Geometry> amii = getAMII(shpAMII, gf);
        
        File dirPC1 = new File(origine);
        File dirCommunes = new File(dossierCommunes);
        File dirPC2 = new File(destination);
        dirPC2.mkdirs();
        for (File f : dirPC1.listFiles()){
            String ligne, codeINSEE, dpt = f.getName().replace(dirPC1.getName()+"_", "").replace(".csv", "");
            String[] donneesLigne;
            
            // on récupère le "buffer 5km" du département
            store = FileDataStoreFinder.getDataStore(new File(shpDpts+"/"+dpt+"/"+dpt+".shp"));
            reader = store.getFeatureReader();
            type = store.getSchema();
            System.out.println("CRS du shapefile : "+type.getCoordinateReferenceSystem().getName().getCode());
            SimpleFeature feature = reader.next();
            MultiPolygon shapeDpt = (MultiPolygon) feature.getDefaultGeometry();
            String code = (String) feature.getAttribute(1);
            System.out.println("Test département : "+code+" = "+dpt);
            reader.close();
            store.dispose();
            
            // on récupère les zones des communes
            Map<String, String> communesToZones = new HashMap<>();
            BufferedReader ficCommunes = new BufferedReader(new FileReader(dirCommunes+"/"+dirCommunes.getName()+"_"+dpt+".csv"));
            ficCommunes.readLine();
            while ((ligne = ficCommunes.readLine()) != null) {
                donneesLigne = ligne.split(";");
                codeINSEE = donneesLigne[0];
                if (codeINSEE.length() == 4)
                    codeINSEE = "0".concat(codeINSEE);
                communesToZones.put(codeINSEE, donneesLigne[1]);
            }
            ficCommunes.close();
            
            // y compris pour les départements voisins
            for (String voisin : dptLimitrophes.get(dpt)){
                ficCommunes = new BufferedReader(new FileReader(dirCommunes+"/"+dirCommunes.getName()+"_"+voisin+".csv"));
                ficCommunes.readLine();
                while ((ligne = ficCommunes.readLine()) != null) {
                    donneesLigne = ligne.split(";");
                    codeINSEE = donneesLigne[0];
                    if (codeINSEE.length() == 4)
                        codeINSEE = "0".concat(codeINSEE);
                    communesToZones.put(codeINSEE, donneesLigne[1]);
                }
                ficCommunes.close();
            }
            
            // création du fichier de sortie
            PrintWriter csv = new PrintWriter(dirPC2+"/"+dirPC2.getName()+"_"+dpt+".csv");
            csv.println("NRA;SR;PC;X;Y;Type;Lignes;Zone;Commune;Referentiel");
            
            // parcours du fichier des pc
            BufferedReader pc = new BufferedReader(new FileReader(f));
            System.out.println(pc.readLine()); // en-tête
            int pcHorsDpt = 0;
            int pcCommuneSansZone = 0;
            int pcProblemeHD = 0;
            int pcProblemeBD =0;
            while ((ligne = pc.readLine()) != null) {
                donneesLigne = ligne.split(";");
                Coordinate coordinate = new Coordinate(Double.parseDouble(donneesLigne[4]), Double.parseDouble(donneesLigne[5]));
                Point pt = gf.createPoint(coordinate);
                
                // on ne garde le point que si sa localisation est raisonnable
                
                if (shapeDpt.covers(pt)){
                    codeINSEE = donneesLigne[3];
                    if (codeINSEE.length() == 4)
                        codeINSEE = "0".concat(codeINSEE);
                    String zone = communesToZones.get(codeINSEE);
                    if (zone == null){
                        pcCommuneSansZone++;
                        if (ztd.contains(pt)) zone = "ZTD";
                        else if (amii.containsKey(dpt) && amii.get(dpt).contains(pt)) zone = "AMII";
                        else zone = "RIP";
                    }
                    switch(zone){
                        case "RIP":
                        case "AMII":
                            zone = "ZMD_"+zone;
                            break;
                        case "ZTD":
                            if (poches.get("PHD").covers(pt)) zone = "ZTD_HD";
                            else if (poches.get("PBD").covers(pt)) zone = "ZTD_BD";
                            else {
                                System.out.println("Problème avec le PC "+donneesLigne[2]);
                                double distHD = pt.distance(poches.get("PHD"));
                                double distBD = pt.distance(poches.get("PBD"));
                                if (distHD <= distBD){
                                    zone = "ZTD_HD";
                                    pcProblemeHD++;
                                } else{
                                    zone = "ZTD_BD";
                                    pcProblemeBD++;
                                }
                            }
                            break;    
                    }
                    csv.println(donneesLigne[0]+";"+donneesLigne[1]+";"+donneesLigne[2]+";"+donneesLigne[4]+";"+donneesLigne[5]
                            +";PC;"+donneesLigne[7]+";"+zone+";"+codeINSEE+";"+donneesLigne[6]);
                } else pcHorsDpt++;
                
            }
            csv.close();
            System.out.println("Nombre de PC exclus car trop loin des limites du département : "+pcHorsDpt);
            System.out.println("Nombre de PC dont le code commune a posé problème : "+pcCommuneSansZone);
            System.out.println("Nombre de PC en ZTD_HD pas exactement dans le shpfile ZTD_HD : "+pcProblemeHD);
            System.out.println("Nombre de PC en ZTD_BD pas exactement dans le shpfile ZTD_BD : "+pcProblemeBD);  
        }
        
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("Fin de zonagePC ! Temps écoulé : "+(System.currentTimeMillis() - start)/1000+" secondes.");
    }
    
    private static Map<String,Geometry> getAMII(String shpAMII, GeometryFactory gf){
        try{
            // lecture shp AMII
            FileDataStore store = FileDataStoreFinder.getDataStore(new File(shpAMII));
            FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
            SimpleFeatureType type = store.getSchema();
            System.out.println("CRS du shapefile : "+type.getCoordinateReferenceSystem().getName().getCode());
            List<AttributeDescriptor>descriptors = type.getAttributeDescriptors();
            String[] attributes = new String[descriptors.size()];
            int i = 0;
            for (AttributeDescriptor descriptor : descriptors){
                System.out.println(attributes[i] = descriptor.getLocalName());
                i++;
            }
            Map<String,List<MultiPolygon>> DptToGeos = new HashMap<>();
            while(reader.hasNext()){           
                SimpleFeature feature = reader.next();
                MultiPolygon geom = (MultiPolygon) feature.getDefaultGeometry();
                String dpt = ((String) feature.getAttribute(1)).substring(0, 2);
                if (!DptToGeos.containsKey(dpt))
                    DptToGeos.put(dpt, new ArrayList<>());
                DptToGeos.get(dpt).add(geom);
            }
            reader.close();  
            store.dispose();

            // on fusionne tous les features pour avoir une zone AMII par département
            Map<String,Geometry> amii = new HashMap<>();
            for (String d : DptToGeos.keySet()){
                List<MultiPolygon> geos = DptToGeos.get(d);
                GeometryCollection col = new GeometryCollection(geos.toArray(new MultiPolygon[geos.size()]),gf);
                amii.put(d, col.union());
            }
            return amii;
        } catch(IOException e){
            System.out.println("Problème avec la zone AMII");
            e.printStackTrace();
            return null;
        }
    }
    
    public static void buffer(int distance, String dossierShape, String nameShape, String destination){
        System.out.println("Début du buffer des départements - fichier "+dossierShape);
        String dpt;
        int i;
        SimpleFeature feature;
        File dirShape = new File(dossierShape);
        try{
            FileDataStore store = FileDataStoreFinder.getDataStore(new File(dirShape+"/"+nameShape+".shp"));
            SimpleFeatureType type = store.getSchema();
            // description
            List<AttributeDescriptor> descriptors = type.getAttributeDescriptors();
            String[] attributes = new String[descriptors.size()];
            i = 0;
            for (AttributeDescriptor descriptor : descriptors){
                attributes[i] = descriptor.getLocalName();
                System.out.println(attributes[i]+", type : "+descriptor.getType().getName().toString());
                i++;
            }
            System.out.println("La géométrie est de type "+type.getGeometryDescriptor().getType().getName().toString()+" et il y a "+(descriptors.size()-1)+" champ(s) supplémentaire(s).");
            
            //création d'un "builder" et d'un "type" pour les features modifiés
            GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName("DptEtendu");
            builder.setCRS(type.getCoordinateReferenceSystem()); 
            builder.add("the_geom", MultiPolygon.class);
            builder.length(3).add("CODE_DPT", String.class);
            builder.length(30).add("NOM_DPT", String.class);
            SimpleFeatureType format = builder.buildFeatureType();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(format);            
            
            // lecture du fichier et transformation
            FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
            while (reader.hasNext()){
                feature = reader.next();
                if (dirShape.getName().equals("FranceMetro")) dpt = (String) feature.getAttribute(2);
                else dpt = dirShape.getName().replace("Dpt", "");
                System.out.println("Département : "+dpt);
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                
                // création du nouveau feature
                Geometry union = geom.buffer((double) distance*1000);
                if (union instanceof Polygon){
                    featureBuilder.add(gf.createMultiPolygon(new Polygon[]{(Polygon) union}));
                } else if (union instanceof MultiPolygon){
                    featureBuilder.add((MultiPolygon) union);
                } else System.out.println("Problème lors du buffer pour le département "+dpt);
                featureBuilder.add(dpt);
                featureBuilder.add((String) feature.getAttribute(3));
                List<SimpleFeature> featuresList = new ArrayList<>();
                featuresList.add(featureBuilder.buildFeature(null));
                
                // création du shapefile à un feature
                File dir = new File(destination+"-"+distance+"km/"+dpt);
                dir.mkdirs();
                printShapefile(dir.getAbsolutePath()+"/"+dpt, format, featuresList);
            }
            reader.close();
            store.dispose();

            System.out.println("Terminé !");

        } catch (Exception e){
            e.printStackTrace();
        }     
    }
    
    public static void createVoronoiPC(String cheminReseau, String origine, String adresseNRA, String adresseSR, String adressePC, String destination){
        long start = System.currentTimeMillis();
        System.out.println("Création des polygones de Vooronoï des PC à partir du répertoire : "+cheminReseau+origine);
        File dir = new File(cheminReseau+origine);
        for (File f : dir.listFiles()){
            String dpt = f.getName();
            System.out.println("Déput de la création des voronoï de PC pour le département "+dpt);
            File dossier = new File(cheminReseau+destination+"/"+destination+"_"+dpt);
            if (!dossier.exists()){
                dossier.mkdirs();
                try {
                    // accès au shapefile du dpt "étendu"
                    FileDataStore store = FileDataStoreFinder.getDataStore(new File(f.getPath()+"/"+dpt+".shp"));
                    FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
                    SimpleFeatureType type = store.getSchema();
                    System.out.println("CRS du shapefile : "+type.getCoordinateReferenceSystem().getName().getCode());
                    SimpleFeature feature = reader.next(); // ce sont des shapefiles "monofeature"

                    // checks sur geometry
                    Geometry geometry = (MultiPolygon) feature.getDefaultGeometry();
                    if (!geometry.isValid()) {
                        IsValidOp vo = new IsValidOp(geometry);
                        if (vo.getValidationError().getErrorType() == 9) {
                            System.out.println("Geometry non valide : " + vo.getValidationError().getMessage());
                            System.out.println(vo.getValidationError().getCoordinate());
                        } else {
                            geometry = geometry.buffer(0);
                        }
                    }

                    // fermeture
                    reader.close();
                    store.dispose();

                    // on récupère les PC au bon format
                    GeometryFactory gf = new GeometryFactory(new PrecisionModel());
                    //ReseauCuivre reseauCuivre = new ReseauCuivre(cheminReseau+adresseCuivre, dpt, true, false, null);
                    ReseauCuivre reseauCuivre = new ReseauCuivre(cheminReseau, dpt, -1, -1); // les deux derniers arguments ne sont pas utiles ici
                    reseauCuivre.loadNRA(cheminReseau+"/"+adresseNRA);
                    //reseauCuivre.loadSR(cheminReseau+"/"+adresseSR);
                    reseauCuivre.loadPC(cheminReseau+"/"+adressePC, false, null);
                    Map<Coordinate, String[]> listeNoeuds = new HashMap<>();
                    System.out.println("Nombre de PC en entrée de Voronoï : "+reseauCuivre.getListePC().size());
                    for (PC pc : reseauCuivre.getListePC()){
                        //if (pc.x!=0||pc.y!=0){
                            Coordinate coordPC = new Coordinate(pc.x, pc.y);
                            if (geometry.contains(gf.createPoint(coordPC))){ 
                                String[] codes = {pc.identifiant,pc.pere.identifiant,pc.nra};
                                listeNoeuds.put(coordPC, codes);
                            }
                        //}
                    }

                    System.out.println("Nombre de PC : "+listeNoeuds.size());

                    // création des type, builder et collection pour les features ZAPC
                    SimpleFeatureTypeBuilder builderZAPC = new SimpleFeatureTypeBuilder();
                    builderZAPC.setName("ZAPC");
                    builderZAPC.setCRS(type.getCoordinateReferenceSystem()); //Lambert 2 Etendu ou autre en outremer
                    builderZAPC.add("the_geom", MultiPolygon.class);
                    builderZAPC.length(50).add("CodePC", String.class);
                    builderZAPC.length(20).add("CodeSR", String.class);
                    builderZAPC.length(8).add("CodeNRA", String.class);
                    SimpleFeatureType formatShpZAPC = builderZAPC.buildFeatureType();
                    SimpleFeatureBuilder featureBuilderZAPC = new SimpleFeatureBuilder(formatShpZAPC);
                    List<SimpleFeature> listeZAPC = new ArrayList<>();
                    
                    // calcul Voronoï
                    VoronoiDiagramBuilder vdb = new VoronoiDiagramBuilder();
                    vdb.setClipEnvelope(geometry.getEnvelopeInternal()); // on envoie le plus petit rectangle contenant geometry (c'est plus simple ?)
                    vdb.setSites(listeNoeuds.keySet());
                    Geometry listePolygones = vdb.getDiagram(new GeometryFactory(new PrecisionModel()));
                    System.out.println("Nb Polygones " + listePolygones.getNumGeometries());

                    //Listing des PC
                    for (int i = 0; i < listePolygones.getNumGeometries(); i++) {
                        Geometry PC = listePolygones.getGeometryN(i);
                        Geometry PC_env = PC.intersection(geometry); // on recoupe avec la vraie forme
                        String[] codes = listeNoeuds.get((Coordinate) PC.getUserData());
                        featureBuilderZAPC.add(PC_env);
                        featureBuilderZAPC.add(codes[0]);
                        featureBuilderZAPC.add(codes[1]);
                        featureBuilderZAPC.add(codes[2]);
                        listeZAPC.add(featureBuilderZAPC.buildFeature(null));
                    }

                    // création du fichier
                    printShapefile(dossier.getPath()+"/"+dossier.getName(), formatShpZAPC, listeZAPC);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else System.out.println("Le fichier existe déjà !");
        }
        System.out.println("Fin de createVoronoiPC ! Temps écoulé : "+(System.currentTimeMillis() - start)/1000+" secondes.");
    }

    public static void fusionShapes(String commun, String or, String destination, int index, boolean codeInFile, String adresseTableNRONRA){
        String origine = commun+or;
        String adresseTable = commun+adresseTableNRONRA;
        System.out.println("Début de fusionShapes pour les fichiers du dossier "+origine);        
        
        // création du dossier de destination
        File dossier = new File(commun+destination);
        dossier.mkdirs();
        File dir = new File(origine);
        List<String> dpts = new ArrayList<>();
        if (codeInFile){
            for (File f : dir.listFiles()){
                dpts.add(f.getName().replace(or+"_",""));
            }
        } else{
            File d = new File(adresseTable);
            for (File f : d.listFiles()){
                if (f.getName().startsWith(adresseTableNRONRA)) // attention aux fichiers d'analyse potentiels
                    dpts.add(f.getName().replace(adresseTableNRONRA+"_", ""));
            }
        }
        for (String dpt : dpts){
            
            System.out.println("Début pour le département : "+dpt);
            
            File shpDir = new File(dossier.getPath()+"/"+destination+"_"+dpt);
            if (!shpDir.exists()){
                shpDir.mkdirs();
                File dirTable = null;
                String[] zones ={""};
                if (!codeInFile){
                    dirTable = new File(adresseTable+"/"+adresseTableNRONRA+"_"+dpt);
                    File[] dirZones = dirTable.listFiles();
                    zones = new String[dirZones.length];
                    for (int i = 0;i<dirZones.length;i++){
                        zones[i] = dirZones[i].getName().replace(dpt+"_", "").replace(adresseTableNRONRA+"_", "");
                    }
                }
                for (String zone : zones){

                    // on récupère un table de correspondance si besoin (et on change le dossier d'arrivée...)
                    Map<String,String> table = null;
                    if (!codeInFile){
                        
                        System.out.println("Fusion des NRA pour la zone "+zone);
                        shpDir = new File(dossier.getPath()+"/"+destination+"_"+dpt+"/"+destination+"_"+dpt+"_"+zone);
                        shpDir.mkdir();
                        
                        table = new HashMap<>();
                        try{
                            BufferedReader reader = new BufferedReader(new FileReader(dirTable.getPath()+"/"+dirTable.getName()+"_"+zone+"/"+dirTable.getName()+"_"+zone+".csv"));
                            reader.readLine();
                            String line;
                            int compteur = 0;
                            while ((line = reader.readLine()) != null){
                                compteur++;
                                String[] fields = line.split(";");
                                table.put(fields[1], fields[0]);
                            }
                            reader.close();
                            System.out.println("Nombre de lignes lues dans le fichier NRO-NRA : "+compteur);
                            System.out.println("Nombre de NRA dans la table de correspondance : "+table.size());
                        } catch(IOException e){
                            System.out.println("Pas de fichier NRO-NRA ?");
                            e.printStackTrace();
                        }
                    }

                    try{
                        // accès au shapefile
                        FileDataStore store = FileDataStoreFinder.getDataStore(new File(dir+"/"+or+"_"+dpt+"/"+or+"_"+dpt+".shp"));
                        FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
                        SimpleFeatureType type = store.getSchema();
                        int n = type.getAttributeCount();
                        System.out.println("CRS du shapefile : "+type.getCoordinateReferenceSystem().getName().getCode());

                        // lecture et stockage
                        Map<String,ArrayList<MultiPolygon>> geoToMerge = new HashMap<>();
                        Map<String,List<Object>> attributes = new HashMap<>();
                        int compteur = 0;
                        while(reader.hasNext()){
                            compteur++;
                            SimpleFeature feature = reader.next();
                            MultiPolygon geom = (MultiPolygon) feature.getDefaultGeometry();
                            
                            String code = (String) feature.getAttribute(index);
                            if (!codeInFile) code = table.get((String) feature.getAttribute(index));
                            if (code != null){ // en général, un NRA ne figure pas dans toutes les zones dont le cas arrive fréquemment         
                                if (!geoToMerge.containsKey(code)){
                                    geoToMerge.put(code, new ArrayList<MultiPolygon>());
                                    attributes.put(code, feature.getAttributes()); // on supposera que tous les champs autres sont identiques
                                }
                                geoToMerge.get(code).add(geom);
                            }
                        }
                        reader.close();
                        System.out.println("Nombre de feature lus dans le fichier d'origine : "+compteur);
                        System.out.println("Nombre de "+destination+" à créer : "+geoToMerge.size());

                        // création des type, featureBuilder et collection
                        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                        builder.setName("ZA");
                        builder.setCRS(type.getCoordinateReferenceSystem()); 
                        builder.add("the_geom", MultiPolygon.class);
                        if (codeInFile)
                            builder.length(20).add("Code_"+destination, String.class);
                        else 
                            builder.length(20).add("Code_ZANRO", String.class);
                        for (int i = 2;i<n;i++){ // on enlève la geometry et le code propre au feature
                            if (i!=index) builder.add(type.getDescriptor(i));
                        }
                        SimpleFeatureType format = builder.buildFeatureType();
                        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(format);
                        //SimpleFeatureCollection collection = FeatureCollections.newCollection();
                        store.dispose();
                        
                        List<SimpleFeature> newFeatures = new ArrayList<>();

                        // fusion et création des nouveaux features
                        GeometryFactory gf = new GeometryFactory();
                        for(String code : geoToMerge.keySet()){
                            List<MultiPolygon> geos = geoToMerge.get(code);
                            GeometryCollection col = new GeometryCollection(geos.toArray(new MultiPolygon[geos.size()]),gf);
                            featureBuilder.add(col.union());
                            featureBuilder.add(code);
                            for (int i = 2;i<n;i++){
                                if (i!=index) featureBuilder.add(attributes.get(code).get(i));
                            }
                            newFeatures.add(featureBuilder.buildFeature(null));
                        }

                        //création du fichier
                        printShapefile(shpDir.getPath()+"/"+shpDir.getName(), format, newFeatures);

                    } catch (IOException e){
                        e.printStackTrace();
                    }
                } 
            } else System.out.println("Fusion des shapes déjà réalisée !");
        }
        System.out.println("Fin de fusionShapes !");
    }
    
    private static void readShpReseau(String dpt, String type, Geometry enveloppe, Reseau reseau, boolean voisin){
        System.out.print("Début lecture du réseau "+type+" du département "+dpt+" en tant que réseau ");
        if (voisin) System.out.println("voisin");
        else System.out.println("propre.");
        try{
            FileDataStore store = FileDataStoreFinder.getDataStore(new File(Main.getShapefileReseau(type, dpt)));
            SimpleFeatureType schema = store.getSchema();
            System.out.println("CRS du shapefile : "+schema.getCoordinateReferenceSystem().getName().getCode());
            
            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
            Filter filtreNRA = ff.intersects(ff.property(schema.getGeometryDescriptor().getLocalName()), ff.literal(enveloppe.buffer(100)));
            
            FeatureIterator<SimpleFeature> iterator = store.getFeatureSource().getFeatures(filtreNRA).features();
            int compteur = 0;
            while (iterator.hasNext()){
                SimpleFeature feature = iterator.next();
                MultiLineString routes = (MultiLineString) feature.getDefaultGeometry();
                LineString arete = (LineString) routes.getGeometryN(0);
                compteur++;
                
                List<double[]> intermediaires = new ArrayList<>();
                for (Coordinate point : arete.getCoordinates()){
                    double[] coord = {Parametres.arrondir(point.x,1), Parametres.arrondir(point.y,1)};
                    intermediaires.add(coord);
                }
                double[] coord1 = intermediaires.remove(0);
                double[] coord2 = intermediaires.remove(intermediaires.size()-1);
                
                int modePose = 11;
                double longueur = 0;
                String nature = "";
                
                switch(type){
                    case "GC":
                        try{modePose = Integer.parseInt((String) feature.getAttribute("MODE_POSE"));}
                        catch(NumberFormatException e){System.out.println("Mode pose non ou mal indiqué dans un fichier GC");}
                        longueur = Parametres.arrondir(arete.getLength(),1);
                        break;
                    case "routier":
                        longueur = Parametres.arrondir(arete.getLength(),1);
                        if (dpt.length() == 2)
                            nature = (String) feature.getAttribute("Nature");
                        else nature = (String) feature.getAttribute("NATURE");
                        break;
                }
                
                reseau.loadLineString(coord1, coord2, type, voisin, modePose, longueur, 1, nature, intermediaires);
            }
            iterator.close();
            System.out.println(compteur+" LineString parcourues");
            store.dispose();
        } catch(IOException e){
            e.printStackTrace();
        }
    }
    
    public static void preparerGraphes(List<String> dptChoisis, Map<String,List<String>> dptsLimitrophes, String racine, String dossierContours, String dossierNRO, String dossierBLO, String options, boolean limitrophes, boolean gc, double toleranceNoeud, double seuilToleranceGC2){
        long start = System.currentTimeMillis();
        System.out.println("Début du module topologique !");
        String line;
        String[] types = {"routier"};
        if (gc)
            types = new String[]{"GC", "routier"};
        String destination = dossierBLO;
        for (String s : types){
            destination+="-"+s;
        }
        destination += options;
        File dir = new File(racine+destination);
        dir.mkdir();
        
        try{
            PrintWriter writer = new PrintWriter(dir+"/parametres.txt");
            writer.println("Paramètres utilisés pour produire les résultats du présent dossier "+dir.getName());
            writer.println();
            BufferedReader paramNRO = new BufferedReader(new FileReader(racine+dossierNRO+"/parametres.txt"));
            paramNRO.readLine();
            paramNRO.readLine();
            while ((line = paramNRO.readLine()) != null){
                writer.println(line);
            }
            writer.println();
            writer.print("Utilisation du (des) réseau(x) du (des) département(s) limitrophe(s) : ");
            if (limitrophes) writer.println("OUI");
            else writer.println("NON");
            writer.print("Utilisation directe du tracé du réseau de génie civil d'Orange : ");
            if (gc) writer.println("OUI");
            else writer.println("NON");
            writer.println("Longueur maximale pour identifier point du réseau cuivre et noeud de réseau physique : "+toleranceNoeud+" m");
            writer.println("Longueur maxiamle de préférence pour le CG : "+seuilToleranceGC2+ " m");
            writer.close();

            PrintWriter aretesAjoutees = new PrintWriter(dir+"/aretes_ajoutees.csv");
            aretesAjoutees.println("Departement;Zone;Nombre;Longueur moyenne");
            for (String dpt : dptChoisis){ // ceux qui sont sélectionnés donc
                double nbArAjoutees = 0, longArAjoutees = 0;
                System.out.println("Début pour le département "+dpt);
                          
                List<String> dptVoisins = null;
                if (limitrophes) dptVoisins = dptsLimitrophes.get(dpt);
                File dirZANRO = new File(racine+dossierContours+"/"+dossierContours+"_"+dpt);
                File[] dirZones = dirZANRO.listFiles();
                String[] zones = new String[dirZones.length];
                for (int i = 0;i<dirZones.length;i++){
                    zones[i] = dirZones[i].getName().replace(dpt+"_", "").replace(dossierContours+"_", "");
                }
                for (String zone : zones){
                    System.out.println("Début pour la zone "+zone);
                    // dossier de destination
                    File dossier = new File(dir.getPath()+"/"+destination+"_"+dpt+"/"+destination+"_"+dpt+"_"+zone);
                    dossier.mkdirs();  
                    FileDataStore store = FileDataStoreFinder.getDataStore(new File(dirZANRO.getPath()+"/"+dirZANRO.getName()+"_"+zone+"/"+dirZANRO.getName()+"_"+zone+".shp"));
                    FeatureReader<SimpleFeatureType, SimpleFeature> reader = store.getFeatureReader();
                    SimpleFeatureType schema = store.getSchema();
                    System.out.println("CRS du shapefile : "+schema.getCoordinateReferenceSystem().getName().getCode());
                    while (reader.hasNext()){
                        SimpleFeature feature = reader.next();
                        String nro = (String) feature.getAttribute("Code_ZANRO");
                        System.out.println("Début pour le NRO "+nro);
                        Geometry geo = (Geometry) feature.getDefaultGeometry();
                        Reseau reseau = new Reseau(nro, zone, toleranceNoeud, seuilToleranceGC2);
                        for (String type : types){
                            readShpReseau(dpt, type, geo, reseau, false);
                            if (limitrophes){
                                for (String voisin : dptVoisins){
                                    readShpReseau(voisin, type, geo, reseau, true);
                                }
                            }
                        }
                        System.out.println("Fin de la première étape : tests");
                        reseau.test();

                        BufferedReader rcNRO = new BufferedReader(new FileReader(racine+dossierNRO+"/"+dossierNRO+"_"+dpt+"/"+dossierNRO+"_"+dpt+"_"+zone+"/ListePC_"+nro+".csv"));
                        line = rcNRO.readLine();
                        String[] fields = line.split(";");
                        reseau.setCentre(Double.parseDouble(fields[6]), Double.parseDouble(fields[7]));
                        double[] res  = reseau.forceConnexite();
                        nbArAjoutees+=res[0];
                        longArAjoutees+=res[1];
                        System.out.println("On rajoute les PC");
                        while ((line = rcNRO.readLine()) != null){
                            fields = line.split(";");
                            reseau.addPC(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]), fields[4], Integer.parseInt(fields[3]), Integer.parseInt(fields[5]) == 1);
                        }
                        reseau.store(dossier);
                        computePourcentagesGC(nro, dossier.getPath(), new File(Main.getShapefileReseau("GC", dpt)), dirZANRO.getPath()+"/"+dirZANRO.getName()+"_"+zone, "ModesPose");
                    }
                    reader.close();
                    store.dispose();
                    aretesAjoutees.println(dpt+";"+zone+";"+nbArAjoutees+";"+String.valueOf(longArAjoutees/nbArAjoutees).replace(".",","));
                }
            }
            aretesAjoutees.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("Fin de preparerGraphes ! Temps écoulé : "+(System.currentTimeMillis() - start)/1000+" secondes.");
    }
    
    public static void printShapefile(String filePath, SimpleFeatureType type, List<SimpleFeature> featuresList){
        try{
            File shp = new File(filePath+".shp");
            System.out.println("Début de l'impression du shapefile "+shp);
            
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            ShapefileDataStore dataStorePM = (ShapefileDataStore) dataStoreFactory.createDataStore(shp.toURI().toURL());

            dataStorePM.createSchema(type);

            Transaction transaction = new DefaultTransaction("create");

            String typeName = dataStorePM.getTypeNames()[0];
            SimpleFeatureSource featureSourcePoints = dataStorePM.getFeatureSource(typeName);
            SimpleFeatureType shape_type = featureSourcePoints.getSchema();
            System.out.println("Format du shape : "+shape_type);

            if (featureSourcePoints instanceof SimpleFeatureStore) {
                System.out.println("Début de l'impression du fichier "+shp);
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSourcePoints;
                SimpleFeatureCollection collectionPoints = new ListFeatureCollection(type,featuresList);
                featureStore.setTransaction(transaction);
                try{
                    featureStore.addFeatures(collectionPoints);
                    transaction.commit();
                } catch (Exception problem) {
                    problem.printStackTrace();
                    transaction.rollback();
                } finally {
                    transaction.close();
                    System.out.println("Impression réussie pour "+shp);
                }
            } else {
                System.out.println("Le shapefile n'est pas accessible en écriture");
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

}
