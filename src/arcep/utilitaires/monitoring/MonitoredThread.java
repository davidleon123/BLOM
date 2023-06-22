package arcep.utilitaires.monitoring;

import arcep.utilitaires.monitoring.MonitoringService.MonitoringToken;

/**
 * Classe simplifiant le lancement d'un thread en activant le monitoring.
 * si on a le Runnable runnable qui est lancer en faisant new Thread(runnable).start(),
 * on peut le monitorer en faisant MonitoredThread.fromRunnable("Nom du monitoring", runnable).start()
 */
public abstract class MonitoredThread extends Thread {
	private String taskName;

	public MonitoredThread(String taskName) {
		this.taskName = taskName;
	}
	
	public static MonitoredThread fromRunnable(String taskName, Runnable runnable) {
		return new MonitoredThread(taskName) {
			@Override
			public void runTask() {
				runnable.run();
			}
		};
	}
	
	public void run() {
    	MonitoringToken token = MonitoringService.get().startMonitoring(taskName);
    	try {
    		runTask();
    	} finally {
    		token.stop();
    	}
	}
	
	abstract public void runTask();
}
