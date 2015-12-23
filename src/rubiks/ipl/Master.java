package rubiks.ipl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Master implements MessageUpcall {

	private Map<IbisIdentifier, SendPort> masterSendPorts = new HashMap<IbisIdentifier, SendPort>();

	private ReceivePort receiveControlPort = null;

	private ReceivePort receiveJobRequestsPort = null;

	/**
	 * Starting cube
	 */
	private Cube startCube = null;

	private CubeCache cache = null;

	private Rubiks rubiks;

	boolean finished = true;

	int jobsPerWorker = 10;

	private static int NUMBER_OF_LOCAL_TWISTS = 3;

	Object roundLock = new Object();

	boolean roundEnded = false;

	/**
	 * Queue of the cube jobs that will be sent to the workers
	 */
	private LinkedList<Cube> cubesQueue = new LinkedList<Cube>();

	private ArrayList<Cube> cubesToSend = new ArrayList<Cube>();

	private SendPort getSendPort(IbisIdentifier receiver) throws IOException {
		// System.out.println("GETTING "+ receiver.name());
		SendPort port = masterSendPorts.get(receiver);
		return port;
	}

	/**
	 * Function called by Ibis to give us a newly arrived message.
	 * 
	 * @param message
	 *            the message
	 * @throws IOException
	 *             when the message cannot be read
	 */
	public void upcall(ReadMessage message) throws IOException {
		System.out.println("UPCALL");
		String s = message.readString();
		IbisIdentifier currentWorker = message.origin().ibisIdentifier();

		synchronized (roundLock) {
			if (roundEnded) // no jobs to be sended
				return;
		}
		try {
			synchronized (cubesQueue) {
				System.out.println("PRE_WAIT");
				while (cubesQueue.size() <= jobsPerWorker) {
					cubesQueue.wait();
				}
				System.out.println("GETTING_CUBES");
				for (int i = 0; i < jobsPerWorker; i++) {
					cubesToSend.add(cubesQueue.poll());
				}
			}
			
			SendPort port = getSendPort(currentWorker);
			WriteMessage w = port.newMessage();
			w.writeObject(cubesToSend);
			w.finish();
			System.out.println("CUBES_SENT");
			for (Cube c : cubesToSend) {
				cache.put(c);
			}
			cubesToSend.clear();
		} catch (InterruptedException exc) {
			System.out.print("Error in master upcall");
			System.exit(0);
		}
	}

	synchronized void setFinished() {
		finished = true;
		notifyAll();
	}

	private void generateJobsForCurrentBound(Cube cube, CubeCache cache)
			throws IOException {
		// System.out.println("Generate");
		if (cube.getTwists() >= cube.getBound()) {
			return;
		}
		// generate all possible cubes from this one by twisting it in
		// every possible way. Gets new objects from the cache
		Cube[] children = cube.generateChildren(cache); // ****
		for (Cube child : children) {
			if (child.getTwists() >= NUMBER_OF_LOCAL_TWISTS) {
				/*
				 * ReadMessage r = masterReceivePort.receive(); String s =
				 * r.readString(); IbisIdentifier currentWorker =
				 * r.origin().ibisIdentifier(); r.finish();
				 */
				synchronized (cubesQueue) {
					cubesQueue.add(child);
					if (cubesQueue.size() == jobsPerWorker)
						cubesQueue.notify();
				}
			} else {
				generateJobsForCurrentBound(child, cache); // recursive call
			}

		}
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
		System.out.print("Bound now:");
		while (result == 0) {
			bound++;
			startCube.setBound(bound);
			System.out.print(" " + bound);
			if (bound <= NUMBER_OF_LOCAL_TWISTS) { // local computation
				result = Rubiks.solutions(startCube, cache);
			} else { // send work to workers
				generateJobsForCurrentBound(startCube, cache);
				synchronized (roundLock) {
					roundEnded = true;
				}
				sendMessageToAllWorkers(Rubiks.PAUSE_WORKER_COMPUTATION);
				result = collectResultsFromWorkers();
				String msg = Rubiks.CONTINUE_COMPUTATION;
				if (result > 0) {
					msg = Rubiks.FINALIZE_MESSAGE;
				}
				sendMessageToAllWorkers(msg);
				synchronized (roundLock) {
					roundEnded = false;
				}
				// ora dovrei checkare se sono state trovate soluzioni
			}
		}
		System.out.println();
		System.out.println("Solving cube possible in " + result + " ways of "
				+ bound + " steps");
	}

	private int collectResultsFromWorkers() throws Exception {
		// System.out.println("starting to collect results from workers");
		int solutionsFinded = 0;
		for (int i = 0; i < rubiks.ibisNodes.length - 1; i++) {
			ReadMessage r = receiveControlPort.receive();
			int solutions = Integer.parseInt(r.readString());
			// System.out.println("solutions: " + solutions);
			r.finish();
			if (solutions < 0 | solutions > 30000) {
				System.out.println("WEIRD SOLUTIONS");
			}
			solutionsFinded += solutions;
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

	/**
	 * Waits a message from all the workers, after receiving from them it
	 * replies with the message given in input
	 * 
	 * @param message
	 *            The message to be delivered
	 */
	private void sendMessageToAllWorkers(String message) throws Exception {
		ArrayList<WriteMessage> msgs = new ArrayList<WriteMessage>();
		for (Map.Entry<IbisIdentifier, SendPort> entry : masterSendPorts.entrySet()) {
			SendPort port = entry.getValue();
			WriteMessage w = port.newMessage();
			w.writeString(message);
			msgs.add(w);
		}
		for (WriteMessage w : msgs) {
			w.finish();
		}
	}

	public Master(String[] arguments, Rubiks rubiks) throws Exception {
		try {
			createStartCube(arguments);
			this.rubiks = rubiks;
			cache = new CubeCache(startCube.getSize());
			receiveControlPort = rubiks.myIbis.createReceivePort(
					Rubiks.portWorkerToMasterControl, "control port");
			receiveControlPort.enableConnections();
			receiveJobRequestsPort = rubiks.myIbis.createReceivePort(
					Rubiks.portWorkerToMasterJobs, "jobs port");
			receiveJobRequestsPort.enableConnections();

			receiveJobRequestsPort.enableMessageUpcalls();

			// Close receive port.
			receiveJobRequestsPort.close();
			long start = System.currentTimeMillis();
			masterComputation();
			long end = System.currentTimeMillis();

			synchronized (this) {
				while (!finished) {
					try {
						wait();
					} catch (Exception e) {
						// ignored
					}
				}
			}
			// NOTE: this is printed to standard error! The rest of the output
			// is
			// constant for each set of parameters. Printing this to standard
			// error
			// makes the output of standard out comparable with "diff"
			System.err.println("Solving cube took " + (end - start)
					+ " milliseconds");

		} catch (Exception exc) {
			System.out.println(exc);
		}
	}

}
