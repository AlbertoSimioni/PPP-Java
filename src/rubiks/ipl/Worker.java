package rubiks.ipl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
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
			try {
				Cube cube = (Cube) r.readObject();
				r.finish();
				solutionsFinded += Rubiks.solutions(cube, cache);
			} catch (ClassNotFoundException exc) {
				String message = r.readString();
				if(message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)){
					sendResultToMaster(solutionsFinded);
					solutionsFinded = 0;
				}
				else if (message.equals(Rubiks.FINALIZE_MESSAGE)){
					end = true;
				}
				else{
					System.out.println("UNKNOWN MESSAGE");
				}
			} catch (ClassCastException exc) {
				System.out.println("ClassCastException");
			}

		}

	}

	private void sendResultToMaster(int value) throws Exception {
		//workerSendPort.connect(rubiks.server, "receive port");
		WriteMessage w = workerSendPort.newMessage();
		w.writeInt(value);
		w.finish();
	}

	public Worker(int cubeSize, Rubiks rubiks) throws Exception {

		cache = new CubeCache(cubeSize);
		this.rubiks = rubiks;
		workerSendPort = rubiks.myIbis.createSendPort(Rubiks.portWorkerToMaster);
		workerReceivePort = rubiks.myIbis.createReceivePort(Rubiks.portMasterToWorker,
				"receive port");
		workerReceivePort.enableConnections();
	}
}
