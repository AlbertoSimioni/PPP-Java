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
		try {
			while (!end) {
				// asking for a new job
				workerSendPort.connect(rubiks.master, "receive port");
				WriteMessage w = workerSendPort.newMessage();
				w.writeString(Rubiks.READY_FOR_NEW_JOBS);
				w.finish();
				System.out.println("cazzo");
				// receiving the new job
				ReadMessage r = workerReceivePort.receive(2000);
				try {
					String message = r.readString();
					if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
						sendResultToMaster(solutionsFinded);
						solutionsFinded = 0;
						r.finish();
					} else if (message.equals(Rubiks.FINALIZE_MESSAGE)) {
						end = true;
						r.finish();

					}
				} catch (Exception exc) {
					Cube cube = (Cube) r.readObject();
					r.finish();
					solutionsFinded += Rubiks.solutions(cube, cache);
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
