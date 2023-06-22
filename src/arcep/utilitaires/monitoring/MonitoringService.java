package arcep.utilitaires.monitoring;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import arcep.ftth2.configuration.Configuration;

/**
 * Class permettant d'ajouter à intervalle donnée des informations sur les Threads monitorées.
 * Ces informations permettront la constitution du rapport de monitoring. 
 */
public class MonitoringService {
	public static class MonitoringToken {
		private final MonitoringService monitoringService;
		
		public MonitoringToken(MonitoringService monitoringService) {
			this.monitoringService = monitoringService;
		}
		
		public MonitoringInformation stop() {
			return this.monitoringService.stop(this);
		}
	}
	
	public static class WatchToken {
		private final MonitoringWatchInformation monitoringWatchInformation;
		
		public WatchToken(MonitoringWatchInformation monitoringWatchInformation) {
			this.monitoringWatchInformation = monitoringWatchInformation;
		}
		
		public void stop() {
			this.monitoringWatchInformation.stop();
		}
	}
	
	public static class MonitoringLeaf {
		final private String className;
		final private String method;
		final private int lineNumber;
		public MonitoringLeaf(String className, String method, int lineNumber) {
			this.className = className;
			this.method = method;
			this.lineNumber = lineNumber;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof MonitoringLeaf)) {
				return false;
			}
			MonitoringLeaf o = (MonitoringLeaf) obj;
			return className.equals(o.className) &&
					method.equals(o.method) &&
					lineNumber == o.lineNumber;
		}
		
		public String getClassName() {
			return className;
		}

		public String getMethod() {
			return method;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		@Override
		public int hashCode() {
			return className.hashCode() * method.hashCode() * lineNumber;
		}
		
		@Override
		public String toString() {
			return className + "." + method + "(" + lineNumber + ")";
		}
	}
	
	public static class MonitoringTree implements Comparable<MonitoringTree>  {
		private final MonitoringLeaf self;
		private final MonitoringTree parent;
		
		private int nbHits;
		private int nbPersonnalHits;
		private final Map<MonitoringLeaf, MonitoringTree> children = new HashMap<>();
		
		public MonitoringTree(MonitoringLeaf self, MonitoringTree parent) {
			this.self = self;
			this.parent = parent;
		}
		
		public Map<MonitoringLeaf, MonitoringTree> getChildren() {
			return children;
		}
		public MonitoringLeaf getSelf() {
			return self;
		}
		public MonitoringTree getParent() {
			return parent;
		}

		void hit() {
			nbHits++;
		}
		void personnalHit() {
			nbPersonnalHits++;
		}
		public void merge(MonitoringTree tree) {
			nbHits += tree.nbHits;
			nbPersonnalHits += tree.nbPersonnalHits;
			for (Entry<MonitoringLeaf, MonitoringTree> child : tree.children.entrySet()) {
				MonitoringTree mergeIntoChild = children.get(child.getKey());
				if (mergeIntoChild != null) {
					mergeIntoChild.merge(child.getValue());
				} else {
					children.put(child.getKey(), child.getValue());
				}
			}
		}
		public int getNbHits() {
			return nbHits;
		}
		public int getNbPersonnalHits() {
			return nbPersonnalHits;
		}
		
		@Override
		public int compareTo(MonitoringTree o) {
			return nbHits < o.nbHits ? 1 :
				nbHits > o.nbHits ? -1 :
				nbPersonnalHits < o.nbPersonnalHits ? 1 :
				nbPersonnalHits > o.nbPersonnalHits ? -1 :
				Integer.compare(hashCode(), o.hashCode());
		}

		public double getParentHit() {
			if (parent == null || parent.getParent() == null)
				return nbHits == 0 ? 0.000001 : nbHits;
			return parent.getNbHits();
		}
		public double getRootHit() {
			if (parent == null || parent.getParent() == null)
				return nbHits == 0 ? 0.000001 : nbHits;
			return parent.getRootHit();
		}
		
		public MonitoringTree clone() {
			return clone(null, null);
		}
		
		private MonitoringTree clone(MonitoringLeaf self, MonitoringTree parent) {
			MonitoringTree tree = new MonitoringTree(self, parent);
			tree.nbHits = this.nbHits; 
			tree.nbPersonnalHits = this.nbPersonnalHits;
			for (Entry<MonitoringLeaf, MonitoringTree> entry : this.children.entrySet()) {
				tree.children.put(entry.getKey(), entry.getValue().clone(entry.getKey(), tree));
			}
			return tree;
		}
	}
	
	public static class MonitoringWatchInformation {
		private final String name;
		private final Thread thread;

		private int nbHits;
		private long currentStartTime;
		private long totalTime;
		
		public MonitoringWatchInformation(String name) {
			this.name = name;
			this.thread = Thread.currentThread();
		}
		
		public void start() {
			nbHits++;
			currentStartTime = System.nanoTime();
		}
		public void stop() {
			totalTime += System.nanoTime() - currentStartTime;
		}
		
		public int getNbHits() {
			return nbHits;
		}
		public long getTotalTime() {
			return totalTime;
		}
		
		@Override
		public int hashCode() {
			return name.hashCode() + thread.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MonitoringWatchInformation)
				return name.equals(((MonitoringWatchInformation)obj).name) &&
					thread.equals(((MonitoringWatchInformation)obj).thread);
			
			return false;
		}
	}
	
	public static class MonitoringInformation {
		private final String taskName;
		private final String[] filters;
		private final Thread thread = Thread.currentThread();
		private final long startTime = System.currentTimeMillis();
		private long endTime;
		private final MonitoringTree tree = new MonitoringTree(null, null);
		private final Map<MonitoringWatchInformation, MonitoringWatchInformation> watched = new HashMap<>();
		
		public MonitoringInformation(String taskName, String[] filters) {
			this.taskName = taskName;
			this.filters = filters;
		}
		public String getTaskName() {
			return taskName;
		}
		public Thread getThread() {
			return thread;
		}
		public long getStartTime() {
			return startTime;
		}
		public String[] getFilters() {
			return filters;
		}
		public MonitoringTree getTree() {
			return tree;
		}
		public MonitoringInformation stop() {
			this.endTime = System.currentTimeMillis();
			return this;
		}
		public void setEndTime(long endTime) {
			this.endTime = endTime;
		}
		public long getEndTime() {
			return endTime;
		}
		@Override
		public String toString() {
			return "Task[" + taskName + "]";
		}
		
		public WatchToken startWatch(String name) {
			MonitoringWatchInformation monitoringWatchInformation = new MonitoringWatchInformation(name);
			synchronized (watched) {
				MonitoringWatchInformation existingMonitoringWatchInformation = watched.get(monitoringWatchInformation);
				if (existingMonitoringWatchInformation == null) {
					existingMonitoringWatchInformation = monitoringWatchInformation;
				}
				WatchToken token = new WatchToken(monitoringWatchInformation);
				monitoringWatchInformation.start();
				return token;
			}
		}
	}
	
	private final Map<MonitoringToken, MonitoringInformation> monitored = new HashMap<>();
	private final Map<Thread, MonitoringInformation> monitoredByThread = new ConcurrentHashMap<>();
	private final Map<Thread, Set<Thread>> subThreads = new ConcurrentHashMap<>();
	
	private Thread t;
	
	private static MonitoringService instance;
	static public MonitoringService get() {
		if (instance != null) {
			return instance;
		}
		synchronized (MonitoringService.class) {
			return instance == null ? instance = new MonitoringService() : instance;
		}
	}
	
	private MonitoringService() {
	}
	
	public MonitoringToken startMonitoring(String taskName, String...filter) {
		synchronized (monitored) {
			MonitoringToken token = new MonitoringToken(this);
			MonitoringInformation information = new MonitoringInformation(taskName, filter);
			this.monitored.put(token, information);
			this.monitoredByThread.put(Thread.currentThread(), information);
			startMonitoringThreadIfNeeded();
			return token;
		}
	}
	
	public boolean isMonitored() {
		return monitoredByThread.containsKey(Thread.currentThread());
	}
	
	public <T> T watch(String name, Supplier<T> toExecute) {
		MonitoringInformation info = this.monitoredByThread.get(Thread.currentThread());
		WatchToken token = info.startWatch(name);
		T t = toExecute.get();
		token.stop();
		return t;
	}
	
	private void processMonitored() {
		Collection<MonitoringInformation> monitoringInformations;
		synchronized (monitored) {
			monitoringInformations = new ArrayList<>(monitored.values());
		}
		for (MonitoringInformation monitoringInformation : monitoringInformations) {
			processStack(monitoringInformation, monitoringInformation.thread);
			var set = subThreads.getOrDefault(monitoringInformation.thread, Set.of());
			synchronized (set) {
				set = new HashSet<>(set);
			}
			for (Thread thread : set) {
				processStack(monitoringInformation, thread);
			}
		}
	}

	private void processStack(MonitoringInformation monitoringInformation, Thread thread) {
		StackTraceElement[] elements = thread.getStackTrace();
		MonitoringTree tree = monitoringInformation.getTree();
		
		for (int i = elements.length - 1; i >=0; i--) {
			StackTraceElement element = elements[i];
			tree = addElement(element, monitoringInformation, tree);
		}
		tree.personnalHit();
	}
	
	private MonitoringTree addElement(StackTraceElement element, MonitoringInformation monitoringInformation, MonitoringTree tree) {
		if (monitoringInformation.getFilters().length > 0) {
			boolean found = false;
			for (String filter : monitoringInformation.getFilters()) {
				if (element.getClassName().matches(filter)) {
					found = true;
				}
			}
			if (!found) {
				return tree;
			}
		}
		String clazz = element.getClassName().replaceAll("\\$.*$", "");
		
		String method = element.getMethodName();
		int line = element.getLineNumber();
		
		MonitoringLeaf leaf = new MonitoringLeaf(clazz, method, line);
		MonitoringTree sub = tree.getChildren().get(leaf);
		if (sub == null) {
			tree.getChildren().put(leaf, sub = new MonitoringTree(leaf, tree));
		}
		sub.hit();
		return sub;
	}

	private void startMonitoringThreadIfNeeded() {
		if (!Configuration.get().activerMonitoring)
			return;
		synchronized (monitored) {
			if (t != null) {
				return;
			}
			t = new Thread(new Runnable() {
				@Override
				public void run() {
					int i = 0;
					while(true) {
						i = (i + 1) % 60;
						synchronized (monitored) {
							if (monitored.isEmpty()) {
								t = null;
								return;
							}
						}
						try {
							Thread.sleep(1000);
							processMonitored();
							if (i == 0 && Configuration.get().monitoringPeriodicDump) {
								Collection<MonitoringInformation> monitoringInformations;
								synchronized (monitored) {
									monitoringInformations = new ArrayList<>(monitored.values());
								}
								MonitoringRapport.dumpFile(monitoringInformations, "current-dump.log");
							}
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			});
			t.start();
		}
	}

	private MonitoringInformation stop(MonitoringToken monitoringToken) {
		MonitoringInformation information;
		synchronized (monitored) {
			information = monitored.remove(monitoringToken).stop();
			monitoredByThread.remove(information.getThread());
		}
		if (Configuration.get().activerMonitoring) {
			dumpFile(information);
		}
		return information;
	}

	static DateFormat Format = new SimpleDateFormat("yyyyMMddHHmm"); 
	static private void dumpFile(MonitoringInformation information) {
		String dumpFileName = 
				Format.format(new Date()) +
				information.getTaskName()
				.replaceAll("[éèê]", "e")
				.replaceAll("[âà]", "a")
				.replaceAll("[^a-zA-Z0-9]+", "_") 
				+ ".log";
		
		MonitoringRapport.dumpFile(Arrays.asList(information), dumpFileName);
	}

	public void addSubThread(Thread mainThread, Thread thread) {
		if (!monitoredByThread.containsKey(mainThread)) return;
		
		var subs = subThreads.get(mainThread);
		if (subs == null) {
			synchronized (subThreads) {
				subs = subThreads.get(mainThread);
				if (subs == null) {
					subThreads.put(mainThread, subs = new HashSet<>());
				}
			}
		}
		synchronized (subs) {
			subs.add(thread);
		}
	}

	public void removeSubThread(Thread mainThread,Thread thread) {
		if (!monitoredByThread.containsKey(mainThread)) return;

		var subs = subThreads.get(mainThread);
		if (subs != null) {
			synchronized (subs) {
				subs.remove(thread);
			}
		}
	}
}
