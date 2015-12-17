package rubiks.ipl;

import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Worker {

	private SendPort workerSendPort = null;

	private ReceivePort workerReceivePort = null;

	private Rubiks rubiks = null;

	private CubeCache cache = null;

	public void workerComputation() throws Exception {
		boolean end = false;
		int solutionsFinded = 0;
		workerSendPort.connect(rubiks.master, "receive port");
		try {
			while (!end) {
				// asking for a new job
				WriteMessage w = workerSendPort.newMessage();
				w.writeString(Rubiks.READY_FOR_NEW_JOBS);
				w.finish();
				// receiving the new job
				ReadMessage r = workerReceivePort.receive(2000);
				//System.out.println("Ricevuto messaggio");
				try{
					Object o = r.readObject();
					if (o instanceof Cube) {
						//System.out.println("cazzo");
						r.finish();
						Cube cube = (Cube) o;
						solutionsFinded += Rubiks.solutions(cube, cache);
					} else {
						String message = r.readString();
						r.finish();
						//System.out.println("cazzo1.5");
						if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
							sendResultToMaster(solutionsFinded);
							solutionsFinded = 0;
							System.out.println("cazzo2");
						} else if (message.equals(Rubiks.FINALIZE_MESSAGE)) {
							end = true;
							System.out.println("cazzo3");
						} else {
							System.out.println("WEIRD MESSAGE FROM MASTER");
						}
						
					}
				} catch(Exception exc){
					System.out.println(exc.getMessage());
				}
				

			}

		} catch (Exception exc) {
			System.out.println(exc.getMessage());
		}

	}

	private void sendResultToMaster(int value) throws Exception {
		// workerSendPort.connect(rubiks.server, "receive port");
		System.out.println("quante porche madonne? " + value);
		WriteMessage w = workerSendPort.newMessage();
		w.writeInt(value);
		w.finish();
	}

	public Worker(int cubeSize, Rubiks rubiks) throws Exception {

		cache = new CubeCache(cubeSize);
		this.rubiks = rubiks;
		workerSendPort = rubiks.myIbis
				.createSendPort(Rubiks.portWorkerToMaster);
		workerReceivePort = rubiks.myIbis.createReceivePort(
				Rubiks.portMasterToWorker, "receive port");
		workerReceivePort.enableConnections();
	}
}
