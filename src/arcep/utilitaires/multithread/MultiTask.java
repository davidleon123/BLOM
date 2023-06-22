package arcep.utilitaires.multithread;

import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import arcep.ftth2.configuration.Configuration;
import arcep.utilitaires.monitoring.MonitoringService;

public class MultiTask {
	final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
	final int nbTaskSlot;
	final AtomicInteger nbThread = new AtomicInteger();
	final Thread launchingThread;
	
	static final boolean activate = Configuration.get().activerMultithreading;
	
	private MultiTask(int nbTask) {
		nbTaskSlot = nbTask;
		launchingThread = Thread.currentThread();
	}
	
	private MultiTask() {
		this(Runtime.getRuntime().availableProcessors());
	}
	
	public static <T> void forEach(Collection<T> input, Consumer<T> task) {
		new MultiTask().forEachInternal(input, task);
	}
	
	public static <T> void forEach(T[] input, Consumer<T> task) {
		new MultiTask().forEachInternal(Arrays.asList(input), task);
	}
	
	private <T> void forEachInternal(Collection<T> input, Consumer<T> task) {
		if (activate) {
			for (T t : input) {
				add(() -> {
					task.accept(t);
				});
			}
			startAndWait();
		}
		else {
			for (T t : input) {
				task.accept(t);
			}
		}
	}
	
	private void add(Runnable r) {
		tasks.add(r);
	}
	
	private void start() {
		for (int i = 0; i < nbTaskSlot; i ++) {
			nbThread.incrementAndGet();
			var mainThread = Thread.currentThread();
			var thread = new Thread(new Runnable() {
				@Override
				public void run() {
					MonitoringService.get().addSubThread(mainThread, Thread.currentThread());
					while(true) {					
						Runnable task = tasks.poll();
						if (task == null) {
							break;
						}		
						try {
							task.run();
						} catch(Exception e) {
							
						}
					}
					int remaining = nbThread.decrementAndGet();
					MonitoringService.get().removeSubThread(mainThread, Thread.currentThread());
					if (remaining == 0) {
						synchronized (MultiTask.this) {
							MultiTask.this.notifyAll();
						}
					}
				}
			});
			thread.start();
		}
	}
	
	private void startAndWait() {
		synchronized (this) {
			this.start();
			try {
				this.wait();
			} catch (InterruptedException e) {
			}
		}
	}
}
