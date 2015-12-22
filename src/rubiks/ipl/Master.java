package rubiks.ipl;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Master {

	private Map<IbisIdentifier, SendPort> masterSendPorts = new HashMap<IbisIdentifier, SendPort>();

	private ReceivePort masterReceivePort = null;

	/**
	 * Starting cube
	 */
	private Cube startCube = null;

	private CubeCache cache = null;

	private Rubiks rubiks;

	private SendPort getSendPort(IbisIdentifier receiver) throws IOException {
		// System.out.println("GETTING "+ receiver.name());
		SendPort port = masterSendPorts.get(receiver);
		return port;
	}

	private void generateJobsForCurrentBound(Cube cube, CubeCache cache)
			throws IOException {
		System.out.println("Generate");
		if (cube.getTwists() >= cube.getBound()) {
			return;
		}
		// generate all possible cubes from this one by twisting it in
		// every possible way. Gets new objects from the cache
		Cube[] children = cube.generateChildren(cache); // ****
		for (Cube child : children) {
			if (child.getTwists() >= 3) {
				ReadMessage r = masterReceivePort.receive();
				String s = r.readString();
				IbisIdentifier currentWorker = r.origin().ibisIdentifier();
				r.finish();
				if (s.equals(Rubiks.READY_FOR_NEW_JOBS)) {
					SendPort port = getSendPort(currentWorker);
					WriteMessage w = port.newMessage();
					w.writeObject(child);
					w.finish();

				}
			} else
				generateJobsForCurrentBound(child, cache); // recursive call
			cache.put(child);
		}
		// System.out.println("MADDONNA GESUITA");
	}

	private void createSendPorts() throws Exception {
		for (IbisIdentifier ibis : rubiks.ibisNodes) {
			if (!ibis.equals(rubiks.myIbis.identifier())) {
				SendPort port = rubiks.myIbis
						.createSendPort(Rubiks.portMasterToWorker);
				masterSendPorts.put(ibis, port);
				port.connect(ibis, "receive port");
			}
		}
	}

	private void masterComputation() throws Exception {
		int bound = 0;
		int result = 0;
		createSendPorts();
		while (result == 0) {
			bound++;
			startCube.setBound(bound);
			System.out.print(" " + bound);
			if (bound <= 3) { // local computation
				result = Rubiks.solutions(startCube, cache);
			} else { // send work to workers
				generateJobsForCurrentBound(startCube, cache);
				sendMessageToAllWorkers(Rubiks.PAUSE_WORKER_COMPUTATION);
				result = collectResultsFromWorkers();
				// ora dovrei checkare se sono state trovate soluzioni
			}
		}
		sendMessageToAllWorkers(Rubiks.FINALIZE_MESSAGE);
		System.out.println();
		System.out.println("Solving cube possible in " + result + " ways of "
				+ bound + " steps");
	}

	private int collectResultsFromWorkers() throws Exception {
		System.out.println("starting to collect results from workers");
		int solutionsFinded = 0;
		for (int i = 0; i < rubiks.ibisNodes.length -1; i++) {
			ReadMessage r = masterReceivePort.receive();
			int solutions = Integer.parseInt(r.readString());
			System.out.println("solutions: " + solutions);
			r.finish();
			if (solutions < 0 | solutions > 30000) {
				System.out.println("WEIRD SOLUTIONS");
			}
			solutionsFinded += solutions;

		}
		System.out.println("Finished round, Solutions finded: "
				+ solutionsFinded);
		String msg = Rubiks.CONTINUE_COMPUTATION;
		if (solutionsFinded > 0) {
			msg = Rubiks.FINALIZE_MESSAGE;
		}
		for (IbisIdentifier ibisNode : rubiks.ibisNodes) {
			if (!ibisNode.equals(rubiks.myIbis.identifier())) {
				SendPort port = getSendPort(ibisNode);
				WriteMessage w = port.newMessage();
				w.writeString(msg);
				w.finish();
			}
		}

		return solutionsFinded;
	}

	/**
	 * Creates the initial cube
	 * 
	 * @param arguments
	 *            given by the user at the start of the program
	 */
	private void createStartCube(String[] arguments) {
		// 3)SERVER CREATE THE CUBE

		// default parameters of puzzle
		int size = 3;
		int twists = 11;
		int seed = 0;
		String fileName = null;

		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i].equalsIgnoreCase("--size")) {
				i++;
				size = Integer.parseInt(arguments[i]);
			} else if (arguments[i].equalsIgnoreCase("--twists")) {
				i++;
				twists = Integer.parseInt(arguments[i]);
			} else if (arguments[i].equalsIgnoreCase("--seed")) {
				i++;
				seed = Integer.parseInt(arguments[i]);
			} else if (arguments[i].equalsIgnoreCase("--file")) {
				i++;
				fileName = arguments[i];
			} else if (arguments[i].equalsIgnoreCase("--help")
					|| arguments[i].equalsIgnoreCase("-h")) {
				Rubiks.printUsage();
				System.exit(0);
			} else {
				System.err.println("unknown option : " + arguments[i]);
				Rubiks.printUsage();
				System.exit(1);
			}
		}

		// create cube
		if (fileName == null) {
			startCube = new Cube(size, twists, seed);
		} else {
			try {
				startCube = new Cube(fileName);
			} catch (Exception e) {
				System.err.println("Cannot load cube from file: " + e);
				System.exit(1);
			}
		}

		// print cube info
		System.out.println("Searching for solution for cube of size "
				+ startCube.getSize() + ", twists = " + twists + ", seed = "
				+ seed);
		startCube.print(System.out);
		System.out.flush();

	}

	// waits a message from a worker and then send him the message
	private void sendMessageToAllWorkers(String message) throws Exception { // AGGIORNARE
																			// I
																			// FINISH
																			// dopo
																			// invio
		System.out.println("Starting sending messages to workers");
		ArrayList<WriteMessage> msgs = new ArrayList<WriteMessage>();
		for (IbisIdentifier ibisNode : rubiks.ibisNodes) { // receiving from all
															// workers
			if (!ibisNode.equals(rubiks.myIbis.identifier())) {
				ReadMessage r = masterReceivePort.receive();
				String s = r.readString();
				IbisIdentifier currentWorker = r.origin().ibisIdentifier();
				r.finish();
				if (s.equals(Rubiks.READY_FOR_NEW_JOBS)) {
					SendPort port = getSendPort(currentWorker);
					WriteMessage w = port.newMessage();
					w.writeString(message);
					msgs.add(w);
					//w.finish();
					System.out.println("message sent to worker");
				} else {
					System.out.println("Unknown message from client");
				}
			}
		}
		for (WriteMessage w : msgs) {
			w.finish();
		}
		System.out.println("all messages sent");
	}

	public Master(String[] arguments, Rubiks rubiks) throws Exception {
		try {
			createStartCube(arguments);
			this.rubiks = rubiks;
			cache = new CubeCache(startCube.getSize());
			masterReceivePort = rubiks.myIbis.createReceivePort(
					Rubiks.portWorkerToMaster, "receive port");
			masterReceivePort.enableConnections();
			masterComputation();
		} catch (Exception exc) {
			System.out.println(exc);
		}
	}

}
