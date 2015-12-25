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

			while (!end) {
				// asking for a new job
				WriteMessage w = workerSendPort.newMessage();
				w.writeString(Rubiks.READY_FOR_NEW_JOBS);
				w.finish();
				// receiving the new job
				ReadMessage r = workerReceivePort.receive();
				Object o = r.readObject();
				r.finish();
				try {
					Cube[] cubes = (Cube[]) o;
					for(Cube cube: cubes){
						solutionsFinded += Rubiks.solutions(cube, cache);
					}
				} catch (ClassCastException exc) {
					String message = (String) o;
					if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
						sendResultToMaster(solutionsFinded);

						solutionsFinded = 0;
						ReadMessage rm = workerReceivePort.receive(); // message
																		// to
																		// continue
																		// or to
																		// stop
						String msg = rm.readString();
						rm.finish();
						if (msg.equals(Rubiks.FINALIZE_MESSAGE)) {
							end = true;
						} else if (!msg.equals(Rubiks.CONTINUE_COMPUTATION)) {
							System.out.println("WEIRD MESSAGE FROM MASTER1");
						}
					} else {
						System.out.println("WEIRD MESSAGE FROM MASTER2");
					}
				}
			}
	}

	private void sendResultToMaster(int value) throws Exception {
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