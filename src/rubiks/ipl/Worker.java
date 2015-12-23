package rubiks.ipl;

import java.util.ArrayList;

import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Worker {

	private SendPort sendPortJobs = null;

	private SendPort sendPortControl = null;

	private ReceivePort workerReceivePort = null;

	private Rubiks rubiks = null;

	private boolean zeroCubesReceived = false;

	private boolean roundEnded = false;

	private CubeCache cache = null;

	public void workerComputation() throws Exception {
		boolean end = false;
		int solutionsFinded = 0;
		sendPortJobs.connect(rubiks.master, "jobs port");
		sendPortControl.connect(rubiks.master, "control port");
		ArrayList<Cube> cubes = null;
		while (!end) {
			System.out.println("READY_JOBS");
			// asking for a new job
			if(!zeroCubesReceived){
				WriteMessage w = sendPortJobs.newMessage();
				w.writeString(Rubiks.READY_FOR_NEW_JOBS);
				w.finish();
			}
			// receiving the new job
			ReadMessage r = workerReceivePort.receive();
			Object o = r.readObject();
			r.finish();
			try {
				cubes = (ArrayList<Cube>) o;
				if (cubes.isEmpty()) {
					System.out.println("EMPTY_CUBE");
					zeroCubesReceived = true;
					if (roundEnded == true) {
						end = endRoundComputations(solutionsFinded);
						roundEnded = false;
						zeroCubesReceived = false;
					}
				} else {
					System.out.println("CALCULATING_CUBES");
					for (Cube c : cubes) {
						solutionsFinded += Rubiks.solutions(c, cache);
					}
				}
			} catch (ClassCastException exc) {
				String message = (String) o;
				
				if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
					System.out.println("PAUSE_WORKER");
					roundEnded = true;
					if (zeroCubesReceived == true) {
						end = endRoundComputations(solutionsFinded);
						roundEnded = false;
						zeroCubesReceived = false;
					}

				} else {
					System.out.println("WEIRD MESSAGE FROM MASTER2");
				}
			}
		}
	}

	private boolean endRoundComputations(int solutionsFinded) throws Exception {
		boolean end = false;
		sendResultToMaster(solutionsFinded);

		solutionsFinded = 0;
		ReadMessage rm = workerReceivePort.receive(); 
		String msg = rm.readString();
		rm.finish();
		if (msg.equals(Rubiks.FINALIZE_MESSAGE)) {
			end = true;
		} else if (!msg.equals(Rubiks.CONTINUE_COMPUTATION)) {
			System.out.println("WEIRD MESSAGE FROM MASTER1");
		}
		return end;
	}

	private void sendResultToMaster(int value) throws Exception {
		WriteMessage w = sendPortControl.newMessage();
		w.writeString(Integer.toString(value));
		w.finish();
	}

	public Worker(int cubeSize, Rubiks rubiks) throws Exception {
		cache = new CubeCache(cubeSize);
		this.rubiks = rubiks;
		sendPortJobs = rubiks.myIbis
				.createSendPort(Rubiks.portWorkerToMasterJobs);
		sendPortControl = rubiks.myIbis
				.createSendPort(Rubiks.portWorkerToMasterControl);
		workerReceivePort = rubiks.myIbis.createReceivePort(
				Rubiks.portMasterToWorker, "receive port");
		workerReceivePort.enableConnections();
	}
}
