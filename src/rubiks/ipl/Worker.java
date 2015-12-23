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
				ReadMessage r = workerReceivePort.receive(10000);
				// System.out.println("Ricevuto messaggio");
				
					Object o = r.readObject();
					//System.out.println("cazzo");
					r.finish();
				try {
					Cube cube = (Cube) o;
					int sol = Rubiks.solutions(cube, cache);
					solutionsFinded += sol;
				} catch (Exception exc) {
					String message = (String) o;
					if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
						sendResultToMaster(solutionsFinded);
						
						solutionsFinded = 0;
						ReadMessage rm = workerReceivePort.receive(10000); // message to continue or to stop
						String msg = rm.readString();
						rm.finish();
						if(msg.equals(Rubiks.FINALIZE_MESSAGE)){
							end = true;
							System.out.println("FINALIZE RECEIVED");
						}
						else if(!msg.equals(Rubiks.CONTINUE_COMPUTATION)){
							System.out.println("WEIRD MESSAGE FROM MASTER1");
						}
					} else {
						System.out.println("WEIRD MESSAGE FROM MASTER2");
					}
				}

			}
			System.out.println("ENDING");

		} catch (Exception exc) {
			System.out.println(exc.getMessage());
		}

	}

	private void sendResultToMaster(int value) throws Exception {
		// workerSendPort.connect(rubiks.server, "receive port");
		System.out.println("quante porche madonne? " + value);
		WriteMessage w = workerSendPort.newMessage();
		w.writeString(Integer.toString(value));
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
