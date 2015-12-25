package rubiks.ipl;

import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Worker {

	/**
	 * Send port of the worker
	 */
	private SendPort workerSendPort = null;

	/**
	 * Receive port of the worker
	 */
	private ReceivePort workerReceivePort = null;

	/**
	 * Instance of the Rubiks class
	 */
	private Rubiks rubiks = null;

	/**
	 * Cache of the Worker
	 */
	private CubeCache cache = null;

	/**
	 * Function that performs the entire computation of a Worker, it's composed
	 * of a loop that ends when the master informs the Worker that the
	 * computation is ended. When the Master informs the Worker that the all the
	 * cubes for the current bound are sent, the Worker replies with the number
	 * of solutions found for the current bound
	 */
	public void workerComputation() throws Exception {
		boolean endLoop = false;
		int solutionsFound = 0;
		while (!endLoop) {

			// asking for a new job
			WriteMessage w = workerSendPort.newMessage();
			w.writeString(Rubiks.READY_FOR_NEW_JOBS);
			w.finish();

			// receiving the new job or a pause message
			ReadMessage r = workerReceivePort.receive();
			Object o = r.readObject();
			r.finish();
			
			try {
				//trying to see if the message contains some jobs
				Cube[] cubes = (Cube[]) o;
				
				//Computing the received cubes
				for (Cube cube : cubes) {
					solutionsFound += Rubiks.solutions(cube, cache);
				}
				
			} catch (ClassCastException exc) {
				//Pause message received from the Master
				//If the function returns true the computation will terminate
				endLoop = handleControlMessages((String) o, solutionsFound);
				solutionsFound = 0;
			}
		}
	}

	/**
	 * Function that handles the control messages for the Worker, it sends the results to the
	 * Master and receives a message from the Master that will inform the worker if to continue
	 * or to end the computation
	 * @param message the message received from the Master
	 * @param solutionsFound Number of solutions found for the current bound
	 * @return True if the worker should end the computation, False otherwise
	 */
	private boolean handleControlMessages(String message, int solutionsFound)
			throws Exception {
		boolean end = false;
		//checking that message received is the one expected
		if (message.equals(Rubiks.PAUSE_WORKER_COMPUTATION)) {
			
			sendResultToMaster(solutionsFound);
			
			//Message to stop or to continue
			ReadMessage rm = workerReceivePort.receive(); 
			String msg = rm.readString();
			rm.finish();
			
			if (msg.equals(Rubiks.FINALIZE_MESSAGE)) {
				end = true;
			} else if (!msg.equals(Rubiks.CONTINUE_COMPUTATION)) { //Checking the expected message
				System.err.println("WEIRD MESSAGE FROM MASTER1");
			}
		} else {
			System.err.println("WEIRD MESSAGE FROM MASTER2");
		}
		return end;
	}
	
	/**
	 * Sends the solutions found to the Master
	 */
	private void sendResultToMaster(int value) throws Exception {
		WriteMessage w = workerSendPort.newMessage();
		w.writeString(Integer.toString(value));
		w.finish();
	}
	
	/**
	 * Creates and connects the ports and starts the Worker computation
	 * @param cubeSize Size of the cube, needed for the cache
	 * @param rubiks Instance of the Rubiks class
	 */
	public Worker(int cubeSize, Rubiks rubiks) throws Exception {
		cache = new CubeCache(cubeSize);
		this.rubiks = rubiks;
		
		//Connecting ports
		workerSendPort = rubiks.myIbis
				.createSendPort(Rubiks.portWorkerToMaster);
		workerReceivePort = rubiks.myIbis.createReceivePort(
				Rubiks.portMasterToWorker, "receive port");
		workerReceivePort.enableConnections();
		workerSendPort.connect(rubiks.master, "receive port");
		
		workerComputation();
	}
}
