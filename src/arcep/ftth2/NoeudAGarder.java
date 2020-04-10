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

import java.io.PrintWriter;
import java.util.*;
import org.jgrapht.GraphPath;

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
    
    public void addPath(GraphPath dsp){
        pathConnu = true;
        distanceAuCentre = Parametres.arrondir(dsp.getLength(),1);
        List<Arete> liste = dsp.getEdgeList();
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
