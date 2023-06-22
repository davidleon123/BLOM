package arcep.utilitaires;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

import arcep.ftth2.Arete;
import arcep.ftth2.NoeudAGarder;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.Editor;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KDTree;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeySizeException;

/**
 * Cette class permet de rendre connexe un graphe (this.graphe) qui ne l'est pas.
 * Il procede en rendant connexe les ilots les plus proches de l'ilot contenant le 
 * point this.centre
 */
public class GraphConnector {
	private class Ilot {
		public Set<NoeudAGarder> noeuds;
		public KDTree<NoeudAGarder> index;
		private boolean isCentre;

		public Ilot(Set<NoeudAGarder> noeuds, Set<NoeudAGarder> centre) {
			this.noeuds = noeuds;
			this.isCentre = centre == noeuds;

			minDistance = Double.MAX_VALUE;
			minDistanceIlot = null;
			minDistanceExtremites = null;
		}

		public void buildIndex() {
			index = new KDTree<>(2);
			for (var n : noeuds) {
				try {
					index.edit(n.getCoord(), new Editor.OptionalInserter<NoeudAGarder>(n));
				} catch (KeySizeException | KeyDuplicateException e) {
					throw new RuntimeException(e);
				}
			}
		}

		List<NoeudAGarder> minDistanceExtremites;
		double minDistance;
		Ilot minDistanceIlot;

		public void trouvePont(Set<Ilot> ilots) {
			minDistance = Double.MAX_VALUE;
			minDistanceIlot = null;
			minDistanceExtremites = null;

			for (var ilot : ilots) {
				List<NoeudAGarder> extremites = trouverPont(ilot);
				var distance = extremites.get(0).distance(extremites.get(1));
				if (distance < minDistance) {
					minDistance = distance;
					minDistanceIlot = ilot;
					minDistanceExtremites = extremites;
				}
				if (distance < ilot.minDistance) {
					ilot.minDistance = distance;
					ilot.minDistanceIlot = this;
					ilot.minDistanceExtremites = extremites;
				}

			}
		}

		/*
		 * Méthode utilisée par la méthode forceConnexité pour construire le plus petit
		 * pont possible entre deux îlots connexes du réseau Le résultat est une liste
		 * contenant les deux NoeudAGarder à relier
		 */
		private List<NoeudAGarder> trouverPont(Ilot ilot) {
			NoeudAGarder n1 = null, n2 = null;
			List<NoeudAGarder> res = new ArrayList<>();
			// on fait en sorte d'indexer le grand ensemble de noeuds pour être en
			// O(nlog(m)) si n < m
			Ilot petit;
			Ilot grand;
			if (this.noeuds.size() <= ilot.noeuds.size()) {
				petit = this;
				grand = ilot;
			} else {
				petit = ilot;
				grand = this;
			}
			if (grand.index == null) {
				grand.buildIndex();
			}
			try {
				double distance, distMin = 10000000;
				for (NoeudAGarder n : petit.noeuds) {
					NoeudAGarder plusProche = grand.index.nearest(n.getCoord());
					distance = n.distance(plusProche);
					if (distance < distMin) {
						distMin = distance;
						n1 = n;
						n2 = plusProche;
					}
				}
				res.add(n1);
				res.add(n2);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return res;
		}
	}

	public class IlotGroup {
		public Set<Ilot> toGroup;
		public final Set<Ilot> done = new HashSet<>();
		private final Ilot center;

		public IlotGroup(Set<Ilot> toGroup) {
			this.toGroup = new HashSet<Ilot>(toGroup);
			center = toGroup.stream().filter(i -> i.isCentre).findAny().orElseThrow();
		}

		public List<List<NoeudAGarder>> reduce() {
			var result = new LinkedList<List<NoeudAGarder>>();
			var current = center;

			toGroup.remove(current);
			while (!toGroup.isEmpty()) {
				var minDistance = Double.MAX_VALUE;
				Ilot minDistanceIlot = null;
				List<NoeudAGarder> minDistanceExtremites = null;

				current.trouvePont(toGroup);
				minDistance = current.minDistance;
				minDistanceIlot = current.minDistanceIlot;
				minDistanceExtremites = current.minDistanceExtremites;

				for (var toGroupIlot : toGroup) {
					if (toGroupIlot.minDistance < minDistance) {
						minDistance = toGroupIlot.minDistance;
						minDistanceIlot = toGroupIlot;
						minDistanceExtremites = toGroupIlot.minDistanceExtremites;
					}
				}
				result.add(minDistanceExtremites);
				done.add(current);
				current = minDistanceIlot;
				toGroup.remove(current);
			}
			return result;
		}
	}

	private Graph<NoeudAGarder, Arete> graphe;
	private NoeudAGarder centre;

	public GraphConnector(Graph<NoeudAGarder, Arete> graphe, NoeudAGarder centre) {
		this.graphe = graphe;
		this.centre = centre;
	}

	public List<List<NoeudAGarder>> getAreteToMakeConnexe() {
		ConnectivityInspector<NoeudAGarder, Arete> ci = new ConnectivityInspector<>(graphe);
		System.out.println("Début de la complétion de " + ci.connectedSets().size() + " ilots");
		if (!ci.isConnected()) {
			List<Set<NoeudAGarder>> ilots = ci.connectedSets();
			var ilotsList = ilots.stream().map(s -> new Ilot(s, ci.connectedSetOf(centre))).toList();
			var ilotGroup = new IlotGroup(Set.copyOf(ilotsList));
			var extremitesList = ilotGroup.reduce();

			return extremitesList;
		}
		return List.of();
	}
}
