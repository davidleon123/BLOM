package arcep.utilitaires.monitoring;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import arcep.ftth2.configuration.Configuration;
import arcep.utilitaires.monitoring.MonitoringService.MonitoringInformation;
import arcep.utilitaires.monitoring.MonitoringService.MonitoringLeaf;
import arcep.utilitaires.monitoring.MonitoringService.MonitoringTree;

/**
 * Class permettant de creer le rapport de monitoring
 */
public class MonitoringRapport {
	static public void dumpFile(Collection<MonitoringInformation> monitoringInformations, String dumpFileName) {
		String cheminReseau = Configuration.get().cheminReseau;
		File folder = new File(cheminReseau);
		folder = new File(folder, "monitoring");
		
		if (!folder.exists()) {
			folder.mkdirs();
		}
		
		File file = new File(folder, dumpFileName);
		if (file.exists()) {
			file.delete();
		}
		PrintStream stream = null;
		try {
			stream = new PrintStream(file);
			for (MonitoringInformation information : monitoringInformations) {
				print(information, stream);
			}
		} catch (FileNotFoundException e) {
			System.out.println("Could not write file " + file.getAbsolutePath() + " : " + e.getMessage());
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
	}	
	
	public static void print(MonitoringInformation information, PrintStream stream) {
		stream.append(information.toString());
		if (information.getEndTime() == 0) {
			stream.append(String.format(" started %02d s ago", (System.currentTimeMillis() - information.getStartTime())/1000));
		} else {
			stream.append(String.format(" lasted for %02d s", (information.getEndTime() - information.getStartTime())/1000));
		}
		stream.println();

		MonitoringTree clone = information.getTree().clone();
		removeLineAndGroup(clone);
		filter(clone, getMinPercent(2));
		filter(clone, getChildrenParentRegexp("^arcep\\..*$"));
		stream.println("-----------------------------------------------------");
		stream.println("---- arcep stack + 1 without line number and > 2 %");
		stream.println("-----------------------------------------------------");
 		printTree(clone, stream, "");
 		
		clone = information.getTree().clone();
		removeLineAndGroup(clone);
		filter(clone, getChildrenParentRegexp("^arcep\\..*$"));
		stream.println("-----------------------------------------------------");
		stream.println("---- arcep stack + 1 without line number");
		stream.println("-----------------------------------------------------");
 		printTree(clone, stream, "");
 		
		clone = information.getTree().clone();
		removeLineAndGroup(clone);
		filter(clone, getMinPercent(2));
		stream.println("-----------------------------------------------------");
		stream.println("---- only > 2% without line number");
		stream.println("-----------------------------------------------------");
 		printTree(clone, stream, "");
 		
		stream.println("-----------------------------------------------------");
		stream.println("---- only arcep stack");
		stream.println("-----------------------------------------------------");
 		printTree(information.getTree(), stream, "", getChildrenRegexp("^arcep\\..*$"));
		
		stream.println("-----------------------------------------------------");
		stream.println("---- only arcep stack + 1");
		stream.println("-----------------------------------------------------");
 		printTree(information.getTree(), stream, "", getChildrenParentRegexp("^arcep\\..*$"));
		
		stream.println("-----------------------------------------------------");
		stream.println("---- 50 % dump");
		stream.println("-----------------------------------------------------");
		
		printTree(information.getTree(), stream, "", getChildrenPercentageTime(0.5));
		
		stream.println("-----------------------------------------------------");
		stream.println("---- 70% Dump");
		stream.println("-----------------------------------------------------");
		
		printTree(information.getTree(), stream, "", getChildrenPercentageTime(0.7));
		
		stream.println("-----------------------------------------------------");
		stream.println("---- Full Dump");
		stream.println("-----------------------------------------------------");
		
		printTree(information.getTree(), stream, "");
	}
	
	private static void removeLineAndGroup(MonitoringTree tree) {
		Collection<MonitoringTree> children = new ArrayList<>(tree.getChildren().values());
		tree.getChildren().clear();
		for (MonitoringTree child : children) {
			MonitoringLeaf leaf = new MonitoringLeaf(child.getSelf().getClassName(), child.getSelf().getMethod(), 0);
			MonitoringTree newchild = tree.getChildren().get(leaf);
			if (newchild == null) {
				tree.getChildren().put(leaf, newchild = new MonitoringTree(leaf, tree));
			}
			newchild.merge(child);
		}
		for (MonitoringTree child : tree.getChildren().values()) {
			removeLineAndGroup(child);
		}
	}

	private static void filter(MonitoringTree tree, Function<MonitoringTree, Collection<MonitoringTree>> filter) {
		Collection<MonitoringTree> filteredChildren = new ArrayList<>(filter.apply(tree));
		tree.getChildren().clear();
		for (MonitoringTree child : filteredChildren) {
			tree.getChildren().put(child.getSelf(), child);
			filter(child, filter);
		}
	}

	public static void printTree(MonitoringTree tree, PrintStream stream, String prefix) {
		printTree(tree, stream, prefix, t -> new TreeSet<>(t.getChildren().values()));
	}
	
	public static void printTree(MonitoringTree tree, PrintStream stream, String prefix, Function<MonitoringTree, Collection<MonitoringTree>> getChildren) {
		stream
		.append(prefix)
		.append(tree.getSelf() == null ? "root" : tree.getSelf().toString())
		.append(" ")
		.append(String.format("%.2f", tree.getNbHits()*100/tree.getRootHit()))
		.append("% (")
		.append(String.format("%.2f", tree.getNbHits()*100/tree.getParentHit()))
		.append("%) ")
		.append(Integer.toString(tree.getNbHits()))
		.append(" ")
		.append(Integer.toString(tree.getNbPersonnalHits()))
		.println();
		
		for (MonitoringTree subtree : getChildren.apply(tree) ) {
			printTree(subtree, stream, prefix + "  ", getChildren);
		}
	}
	
	public static Function<MonitoringTree, Collection<MonitoringTree>> getChildrenPercentageTime(double percent) {
		return (MonitoringTree tree) -> {
			List<MonitoringTree> list = new LinkedList<MonitoringTree>();
			double toKeep = percent * tree.getNbHits();
			for (MonitoringTree subTree : new TreeSet<MonitoringTree>(tree.getChildren().values())) {
				list.add(subTree);
				toKeep -= subTree.getNbHits();
				if (toKeep < 0) {
					break;
				}
			}
			return list;
		};
	}
	
	public static Function<MonitoringTree, Collection<MonitoringTree>> getChildrenRegexp(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		return (MonitoringTree tree) -> {
			return new TreeSet<MonitoringTree>(tree.getChildren().values()).stream()
					.filter(t -> pattern.asPredicate().test(t.getSelf().toString()) ||  t.getSelf().toString().contains("java.lang.Thread.run"))
					.collect(Collectors.toList());
		};
	}
	
	public static Function<MonitoringTree, Collection<MonitoringTree>> getChildrenParentRegexp(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		return (MonitoringTree tree) -> {
			if (tree.getSelf() == null || tree.getSelf().toString().contains("java.lang.Thread.run") || 
					pattern.asPredicate().test(tree.getSelf().toString())) {
				return new TreeSet<MonitoringTree>(tree.getChildren().values());
			}
			return Arrays.asList();
		};
	}
	
	public static Function<MonitoringTree, Collection<MonitoringTree>> getMinPercent(double percent) {
		return (MonitoringTree tree) -> {
			return new TreeSet<>(tree.getChildren().values().stream()
				.filter(t -> t.getNbHits() * 100 * 1.0 / tree.getRootHit() > percent)
				.collect(Collectors.toList()));
		};
	}
	
	public static MonitoringInformation parseMonitoringFile(File f) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
			String line = reader.readLine();
			
			Matcher m = Pattern.compile("Task\\[(.*?)\\].*for ([0-9]+) s").matcher(line);
			m.find();
			
			MonitoringInformation information = new MonitoringInformation(m.group(1), new String[] {});
			information.setEndTime(information.getStartTime() + Long.parseLong(m.group(2)) * 1000);
			
			while((line = reader.readLine()) != null) {
				if (line.equals("---- Full Dump")) break;
			}
			reader.readLine();
			
			Pattern p = Pattern.compile("(\\s+)(.*)\\.(.*)\\((.*?)\\) .*?\\% .*?\\%\\) ([0-9]+) ([0-9]+)");
			
			MonitoringTree currentTree = information.getTree();
			int currentSpace = 0;
			while((line = reader.readLine()) != null) {
				if (line.startsWith("root")) continue;
				m = p.matcher(line);
				if (!m.find()) {
					throw new RuntimeException("bad pattern");
				}
				int space = m.group(1).length() / 2;
				if (space == currentSpace + 1) {
					MonitoringLeaf leaf = new MonitoringLeaf(m.group(2), m.group(3), Integer.parseInt(m.group(4))); 
					MonitoringTree child = new MonitoringTree(leaf, currentTree);
					int nbHit = Integer.parseInt(m.group(5));
					int nbHitPersonal = Integer.parseInt(m.group(6));
					for (int i = 0; i < nbHit ; i++) child.hit();
					for (int i = 0; i < nbHitPersonal; i++) child.personnalHit();
	
					currentTree.getChildren().put(leaf, child);
					currentTree = child;
				} else if (space <= currentSpace) {
					while(space < currentSpace) {
						currentSpace--;
						currentTree = currentTree.getParent();
					}
					MonitoringLeaf leaf = new MonitoringLeaf(m.group(2), m.group(3), Integer.parseInt(m.group(4))); 
					MonitoringTree child = new MonitoringTree(leaf, currentTree.getParent());
					int nbHit = Integer.parseInt(m.group(5));
					int nbHitPersonal = Integer.parseInt(m.group(6));
					for (int i = 0; i < nbHit ; i++) child.hit();
					for (int i = 0; i < nbHitPersonal; i++) child.personnalHit();
	
					currentTree.getParent().getChildren().put(leaf, child);
					currentTree = child;
				} else {
					throw new RuntimeException("what");
				}
				currentSpace = space;
			}
			reader.close();
			return information;
		}
	}
}
