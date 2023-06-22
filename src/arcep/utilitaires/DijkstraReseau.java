package arcep.utilitaires;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graphs;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.Pseudograph;
import org.jheaps.AddressableHeap;
import org.jheaps.tree.PairingHeap;

import arcep.ftth2.Arete;
import arcep.ftth2.NoeudAGarder;
import arcep.ftth2.Reseau;
/**
 * Classe permettant le calcul de l'algorithme Djikstra sur le graphe this.graphe
 * Il permet en plus le recalcul par la méthode recompute() sur un ensemble de noeud 
 * dont la distance au centre à peut être diminuée
 */
public class DijkstraReseau {
	private final Pseudograph<NoeudAGarder, Arete> graphe;
	private final NoeudAGarder centre;
	
    private final Map<NoeudAGarder, AddressableHeap.Handle<Double, Pair<NoeudAGarder, Arete>>> seen = new HashMap<>();
    private AddressableHeap<Double, Pair<NoeudAGarder, Arete>> heap = new PairingHeap<>();
    private Set<NoeudAGarder> inHeap = new HashSet<>();
	
	public DijkstraReseau(Pseudograph<NoeudAGarder, Arete> graphe, NoeudAGarder centre) {
		this.graphe = graphe;
		this.centre = centre;
		
		inHeap.clear();
		updateDistance(null, this.centre, null, 0d);
		processHeap();
	}
	
	public void recomputeAll() {
		for (var n : graphe.vertexSet()) {
			n.meilleurChemin.aretePrecedente = null;
			n.meilleurChemin.noeudPrecedent = null;
			n.meilleurChemin.distanceAuCentre = Double.POSITIVE_INFINITY;
		}
		inHeap.clear();
		heap.clear();
		seen.clear();
		updateDistance(null, this.centre, null, 0d);
		processHeap();
	}

    public void processHeap()
    {
        while (!heap.isEmpty()) {
	        var next = heap.deleteMin();
	        var noeud = next.getValue().getFirst();
	        double vDistance = next.getKey();
	        
	        // relax edges
	        for (Arete arete : graphe.outgoingEdgesOf(noeud)) {
	            var versNoeud = Graphs.getOppositeVertex(graphe, arete, noeud);
	            double eWeight = graphe.getEdgeWeight(arete);
	            if (eWeight < 0.0) {
	                throw new IllegalArgumentException("Negative edge weight not allowed");
	            }
	            updateDistance(noeud, versNoeud, arete, vDistance + eWeight);
	        }
        }
    }

    private void updateDistance(NoeudAGarder from, NoeudAGarder to, Arete a, double distance)
    {
    	var isBest = false;
        AddressableHeap.Handle<Double, Pair<NoeudAGarder, Arete>> node = seen.get(to);
        if (node == null) {
            node = heap.insert(distance, Pair.of(to, a));
            seen.put(to, node);
            isBest = true;
            inHeap.add(to);
        } else if (distance < node.getKey() - 0.0000001) {
        	if (inHeap.contains(to)) {
	            node.decreaseKey(distance);
	            node.setValue(Pair.of(node.getValue().getFirst(), a));
	            isBest = true;
        	} else {
                node = heap.insert(distance, Pair.of(to, a));
                seen.put(to, node);
                isBest = true;
                inHeap.add(to);
        	}
        }
        
        if (isBest) {
        	to.meilleurChemin.distanceAuCentre = distance;
        	to.meilleurChemin.noeudPrecedent = from; 
        	to.meilleurChemin.aretePrecedente = a;
        }
    }

	public void recompute(NoeudAGarder... noeuds) {
		recompute(List.of(noeuds));
	}
	
	public void recompute(Collection<NoeudAGarder> noeuds) {
		inHeap.clear();
		var noeudsPrecedent = noeuds.stream()
				.map(n -> n != centre ? n.meilleurChemin.noeudPrecedent : n)
				.filter(n -> n != null)
				.toList();
		for (var n : noeuds) {
			if (n != centre) {
				n.meilleurChemin.aretePrecedente = null;
				n.meilleurChemin.noeudPrecedent = null;
				n.meilleurChemin.distanceAuCentre = Double.POSITIVE_INFINITY;
				seen.remove(n);
			}
		}
		
		for (var n : noeudsPrecedent) {
            var node = heap.insert(n.meilleurChemin.distanceAuCentre, Pair.of(n, n.meilleurChemin.aretePrecedente));
            seen.put(n, node);
            inHeap.add(n);
		}
		
		processHeap();
		
		if (!validateNodeDistance(noeuds)) {
			recomputeAll();
		}
	}
	
	private boolean validateNodeDistance(Collection<NoeudAGarder> noeuds) {
		Set<NoeudAGarder> alreadySeen = new HashSet<>();
		for (var noeud : noeuds) {
			if (!validateNodeDistance(noeud, alreadySeen)) {
				return false;
			}
			alreadySeen.clear();
		}
		return true;
	}

	private boolean validateNodeDistance(NoeudAGarder noeud, Set<NoeudAGarder> alreadySeen) {
		if (noeud == centre) {
			return true;
		}
		if (!alreadySeen.add(noeud)) {
			return false;
		}
		return validateNodeDistance(noeud.meilleurChemin.noeudPrecedent, alreadySeen);
	}

	public Set<NoeudAGarder> ajoutFourreauxSiNecessaire(Reseau reseau, NoeudAGarder ... noeuds) {
		var res = new HashSet<NoeudAGarder>();
		for (var n : noeuds) {
			var current = n;
			while (current.meilleurChemin.noeudPrecedent != null) {
				if (current.meilleurChemin.aretePrecedente != null && graphe.containsEdge(current.meilleurChemin.aretePrecedente)) {
					try {
						var a = current.meilleurChemin.aretePrecedente;
						if (!a.dejaUtilise) {
							reseau.utiliseArete(a);
							res.add(current);
							res.add(current.meilleurChemin.noeudPrecedent);
						}
					} catch (Exception e) {
						// should not happened
						throw new RuntimeException("Should not happened", e);
					}
				}
				current = current.meilleurChemin.noeudPrecedent;
			}
		}		
    	recompute(res);
		return res;
	}
	
	public void dump(NoeudAGarder c) {
		while (c != null) {
			System.out.println("" + c.getId() + " <--> " +
					(c != centre ? ( ""  + c.meilleurChemin.noeudPrecedent.getId() + " " + c.meilleurChemin.aretePrecedente.getId() + " - " + graphe.getEdgeWeight(c.meilleurChemin.aretePrecedente)) : "root")
					+ " - " + c.meilleurChemin.distanceAuCentre
					);
			c = c.meilleurChemin.noeudPrecedent;
		}
	}

	public void dump(int id) {
		this.graphe.vertexSet().forEach(
				n -> {
					if (n.getId() == id) {
						dump(n);
					}
				}
		);
	}


}
