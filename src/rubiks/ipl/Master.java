package rubiks.ipl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

/**
 * The class represent the Master node of the system.
 * 
 * @author Alberto Simioni
 * 
 */
public class Master {

	/**
	 * Set of send ports, one port per each Worker node
	 */
	private Map<IbisIdentifier, SendPort> masterSendPorts = new HashMap<IbisIdentifier, SendPort>();

	/**
	 * Receive port of the master
	 */
	private ReceivePort masterReceivePort = null;

	/**
	 * Starting cube
	 */
	private Cube startCube = null;

	/**
	 * Cache of the master
	 */
	private CubeCache cache = null;

	/**
	 * Reference to the only instance of the Rubiks class, it's used to get some
	 * information inside the Rubiks class
	 */
	private Rubiks rubiks;

	/**
	 * Queue of the cube that are ready to be processed by the Workers
	 */
	private LinkedList<Cube> cubesQueue = new LinkedList<Cube>();

	/**
	 * Minimun number of cubes that can be sent to a worker, only when all the
	 * cubes for the current bound are generated, the Master can send less cubes
	 * in a message
	 */
	private static final int minCubesToSend = 3;

	/**
	 * Maximum number of cubes that can be sent in a message
	 */
	private static final int maxCubesToSend = 10;

	/**
	 * Number of local twists per cube that will be performed by the Master
	 */
	private static final int localTwistsBound = 3;

	/**
	 * Returns the send port for the worker given in input
	 */
	private SendPort getSendPort(IbisIdentifier receiver) throws IOException {
		SendPort port = masterSendPorts.get(receiver);
		return port;
	}

	/**
	 * Recursive function that generates the jobs to be sent to the workers for
	 * the current bound. It will be called from an extern function each time
	 * that the current bound is increased. The extern function call this
	 * function with the initial cube in input
	 */
	private void generateJobsForCurrentBound(Cube cube, CubeCache cache)
			throws IOException {

		// bound reached
		if (cube.getTwists() >= cube.getBound()) {
			return;
		}

		// generate all possible cubes from this one by twisting it in
		// every possible way. Gets new objects from the cache
		Cube[] children = cube.generateChildren(cache); // ****
		for (Cube child : children) {
			if (child.getTwists() >= localTwistsBound) {
				cubesQueue.add(child);
				sendJobs(false);
			} else
				generateJobsForCurrentBound(child, cache); // recursive call
		}
	}

	/**
	 * Function to send the jobs to the workers. If the queue is smaller than
	 * minCubesToSend the message won't be sent. If there isn't any ready
	 * request from a worker the function returns without sending. This is to
	 * avoid to waste time waiting the workers. Indeed the Master can continue
	 * to populate the queue
	 * 
	 * @param sendWithoutCheckingSize
	 *            if true the function is allowed to send less than
	 *            minCubesToSend cubes
	 */
	private void sendJobs(boolean sendWithoutCheckingSize) throws IOException {

		// checking the size of the queue
		if ((sendWithoutCheckingSize || (cubesQueue.size() >= minCubesToSend))) {
			ReadMessage r = masterReceivePort.poll();
			if (r != null) { // false = no worker ready
				IbisIdentifier currentWorker = r.origin().ibisIdentifier();
				r.finish();
				int cubesNumber = Math.min(maxCubesToSend, cubesQueue.size());

				// Taking the cubes from the queue
				Cube[] cubesToSend = new Cube[cubesNumber];
				for (int i = 0; !cubesQueue.isEmpty() && i < maxCubesToSend; i++) {
					cubesToSend[i] = cubesQueue.pollFirst();
				}

				// Sending the cubes
				SendPort port = getSendPort(currentWorker);
				WriteMessage w = port.newMessage();
				w.writeObject(cubesToSend);
				w.finish();

				// Putting the cubes in the cache
				for (Cube c : cubesToSend) {
					cache.put(c);
				}
			}
		}
	}

	/**
	 * Creates the send ports and connect them to the respective recive ports of
	 * the workers
	 * 
	 * @throws Exception
	 */
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

	/**
	 * Function that starts the computation of the Master node. While the bound
	 * is <= than the localTwistsBound it tries to resolve locally the start
	 * cube Then it sends to cube to worker. If some solutions are found for the
	 * current bound it informs the worker that they can stop.
	 */
	private void masterComputation() throws Exception {
		int bound = 0;
		int result = 0;
		createSendPorts();
		System.out.print("Bound now:");
		while (result == 0) {
			bound++;
			startCube.setBound(bound);
			System.out.print(" " + bound);

			if (bound <= localTwistsBound) { // local computation
				result = Rubiks.solutions(startCube, cache);
			} else { // send work to workers
				generateJobsForCurrentBound(startCube, cache);
				// Making the queue empty
				while (!cubesQueue.isEmpty()) {
					sendJobs(true);
				}
				// Pausing the worker to ask for new jobs
				sendMessageToAllWorkers(Rubiks.PAUSE_WORKER_COMPUTATION);

				// Collecting the results
				result = collectResultsFromWorkers();
				
				// If no solutions are found the Master advises the Workers to
				// continue
				// otherwise it advises the Workers to stop
				String msg = Rubiks.CONTINUE_COMPUTATION;
				if (result > 0) {
					msg = Rubiks.FINALIZE_MESSAGE;
				}
				for (Map.Entry<IbisIdentifier, SendPort> entry : masterSendPorts
						.entrySet()) {
					SendPort port = entry.getValue();
					WriteMessage w = port.newMessage();
					w.writeString(msg);
					w.finish();
				}
				
			}
		}
		System.out.println();
		System.out.println("Solving cube possible in " + result + " ways of "
				+ bound + " steps");

	}

	/**
	 * Collects the solutions for the current bound from the workers
	 * 
	 * @return The number of solutions finded from all the workers
	 */
	private int collectResultsFromWorkers() throws Exception {
		int solutionsFound = 0;
		for (int i = 0; i < rubiks.ibisNodes.length - 1; i++) {
			ReadMessage r = masterReceivePort.receive();
			int solutions = Integer.parseInt(r.readString());
			r.finish();
			solutionsFound += solutions;

		}
		return solutionsFound;
	}

	/**
	 * Creates the initial cube
	 * 
	 * @param arguments
	 *            given by the user at the start of the program
	 */
	private void createStartCube(String[] arguments) {

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
		for (int i = 0; i < rubiks.ibisNodes.length - 1; i++) {
			ReadMessage r = masterReceivePort.receive();
			IbisIdentifier currentWorker = r.origin().ibisIdentifier();
			r.finish();
			SendPort port = getSendPort(currentWorker);
			WriteMessage w = port.newMessage();
			w.writeString(message);
			msgs.add(w);

		}
		//Until all the messages are sent to the workers, it doesn't let the Workers to continue
		//Otherwise this function could receive a message two times from the same Worker
		for (WriteMessage w : msgs) {
			w.finish();
		}
	}
	
	/**
	 * Constructor of the Master, it creates the Ports, the cache, the start cube and starts
	 * the computation measuring the time elapsed
	 * @param arguments Input arguments from shell
	 * @param rubiks Reference to the Rubiks instance
	 */
	public Master(String[] arguments, Rubiks rubiks) throws Exception {
		try {
			
			createStartCube(arguments);
			
			this.rubiks = rubiks;
			
			cache = new CubeCache(startCube.getSize());
			
			//Creating the ports
			masterReceivePort = rubiks.myIbis.createReceivePort(
					Rubiks.portWorkerToMaster, "receive port");
			masterReceivePort.enableConnections();

			
			//Computation
			long start = System.currentTimeMillis();
			masterComputation();
			long end = System.currentTimeMillis();

			// NOTE: this is printed to standard error! The rest of the output
			// is
			// constant for each set of parameters. Printing this to standard
			// error
			// makes the output of standard out comparable with "diff"
			System.err.println("Solving cube took " + (end - start)
					+ " milliseconds");

		} catch (Exception exc) {
			System.err.println(exc);
		}
	}

}
