package rubiks.ipl;

import java.io.IOException;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

/**
 * Solver for rubik's cube puzzle.
 * 
 * @author Niels Drost, Timo van Kessel
 * 
 */
public class Rubiks {
	

    PortType portType = new PortType(PortType.COMMUNICATION_RELIABLE, //reliable communications and election
            PortType.SERIALIZATION_DATA, PortType.RECEIVE_EXPLICIT,  //one_to_one, string can be sent
            PortType.CONNECTION_ONE_TO_ONE);

    IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT,
            IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
            IbisCapabilities.TERMINATION);
    
    /**
     * Identifiers of the nodes that are active inside the current execution
     */
    private IbisIdentifier[] ibisesNodes = null;
    
    /**
     * Identifier of the server node
     */
    private IbisIdentifier server = null;
    
    /**
     * Ibis object
     */
    private Ibis myIbis = null;
    
    /**
     * Starting cube
     */
    private Cube startCube = null;
    
    public static final boolean PRINT_SOLUTION = false;

    /**
     * Recursive function to find a solution for a given cube. Only searches to
     * the bound set in the cube object.
     * 
     * @param cube
     *            cube to solve
     * @param cache
     *            cache of cubes used for new cube objects
     * @return the number of solutions found
     */
    private static int solutions(Cube cube, CubeCache cache) {
        if (cube.isSolved()) { //***
            return 1;
        }

        if (cube.getTwists() >= cube.getBound()) {
            return 0;
        }

        // generate all possible cubes from this one by twisting it in
        // every possible way. Gets new objects from the cache
        Cube[] children = cube.generateChildren(cache); //****

        int result = 0;

        for (Cube child : children) {
            // recursion step
            int childSolutions = solutions(child, cache);
            if (childSolutions > 0) {
                result += childSolutions;
                if (PRINT_SOLUTION) {
                    child.print(System.err);
                }
            }
            // put child object in cache
            cache.put(child);
        }
        return result;
    }

    /**
     * Solves a Rubik's cube by iteratively searching for solutions with a
     * greater depth. This guarantees the optimal solution is found. Repeats all
     * work for the previous iteration each iteration though...
     * 
     * @param cube
     *            the cube to solve
     */
    private static void solve(Cube cube) {
        // cache used for cube objects. Doing new Cube() for every move
        // overloads the garbage collector
        CubeCache cache = new CubeCache(cube.getSize());
        int bound = 0;
        int result = 0;

        System.out.print("Bound now:");
        
        //the algorithms stops when at least one solution is found 
        while (result == 0) {
            bound++;
            cube.setBound(bound);

            System.out.print(" " + bound);
            result = solutions(cube, cache); //solutions returns the number of solutions found with the 
            								 //current number of steps
        }

        System.out.println();
        System.out.println("Solving cube possible in " + result + " ways of "
                + bound + " steps");
    }

    public static void printUsage() {
        System.out.println("Rubiks Cube solver");
        System.out.println("");
        System.out
                .println("Does a number of random twists, then solves the rubiks cube with a simple");
        System.out
                .println(" brute-force approach. Can also take a file as input");
        System.out.println("");
        System.out.println("USAGE: Rubiks [OPTIONS]");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("--size SIZE\t\tSize of cube (default: 3)");
        System.out
                .println("--twists TWISTS\t\tNumber of random twists (default: 11)");
        System.out
                .println("--seed SEED\t\tSeed of random generator (default: 0");
        System.out
                .println("--threads THREADS\t\tNumber of threads to use (default: 1, other values not supported by sequential version)");
        System.out.println("");
        System.out
                .println("--file FILE_NAME\t\tLoad cube from given file instead of generating it");
        System.out.println("");
    }

    
    /**
     * Function that retrieve the identifiers of all the nodes inside the system and 
     * get the identifier of the server node.
     * 
     * @throws Exception if something goes wrong due to connection problem
     */
    private void initialize() throws Exception {
        // Create an ibis instance.
        

        // sleep for a second
        Thread.sleep(1000);
        
        
        ibisesNodes = myIbis.registry().joinedIbises();
        
        System.out.println("NUMBER OF JOINED NODES:" + ibisesNodes.length);
        
        // Elect a server
        server = myIbis.registry().elect("Server"); // decide if the current ibis is a server
        														 // or a client
        System.out.println("Server is " + server);    
    }

    
    /**
     * Creates the initial cube
     * 
     * @param arguments given by the user at the start of the program
     */
    private void createStartCube(String[] arguments){
    	//3)SERVER CREATE THE CUBE
    	

        // default parameters of puzzle
        int size = 3;
        int twists = 11;
        int seed = 0;
        String fileName = null;

        // number of threads used to solve puzzle
        // (not used in sequential version)

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
            } else if (arguments[i].equalsIgnoreCase("--help") || arguments[i].equalsIgnoreCase("-h")) {
                printUsage();
                System.exit(0);
            } else {
                System.err.println("unknown option : " + arguments[i]);
                printUsage();
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
                + startCube.getSize() + ", twists = " + twists + ", seed = " + seed);
        startCube.print(System.out);
        System.out.flush();

        
    }
    
    /**
     * Starts the work that has to be performed by a server node
     * @throws IOException Thrown if there are any problem inside the network
     */
    private void server() throws IOException {

        // Create a receive port and enable connections.
        ReceivePort receiver = myIbis.createReceivePort(portType, "server"); //using the variable porttype
        receiver.enableConnections();

        // Read the message.
        ReadMessage r = receiver.receive(); //explicit receive as asked in the the port type
        String s = r.readString();
        r.finish();
        System.out.println("Server received: " + s);

        // Close receive port.
        receiver.close();
    }

    /**
     * Starts the work that has to be performed by a client node
     * 
     * @param server Identifier of the server node 
     * @throws IOException Thrown if there are any problem inside the network
     */
    private void client(IbisIdentifier server) throws IOException {

        // Create a send port for sending requests and connect.
        SendPort sender = myIbis.createSendPort(portType); //same type of the receiver port
        sender.connect(server, "server"); //connects the port to the sender

        // Send the message.
        WriteMessage w = sender.newMessage();
        w.writeString("Hi there");
        w.finish();

        // Close ports.
        sender.close();
    }
    
    
    
    private void run(String[] arguments) throws Exception {
    	myIbis = IbisFactory.createIbis(ibisCapabilities, null, portType);
    	
    	initialize();
    	
    	if (server.equals(myIbis.identifier())) {
    		//code 
    		createStartCube(arguments);
            //server(ibis);
        } else {
        	//GET CUBE FROM SERVER
            //client(ibis, server);
        }
    	
    	/**
        // solve
        long start = System.currentTimeMillis();
        //4)START TO SOLVE
        solve(startCube);
        long end = System.currentTimeMillis();**/

        // NOTE: this is printed to standard error! The rest of the output is
        // constant for each set of parameters. Printing this to standard error
        // makes the output of standard out comparable with "diff"
        //System.err.println("Solving cube took " + (end - start) + " milliseconds");

    	
    	myIbis.end();
    }
    
    
    /**
     * Main function, it starts creates a new object of the class that uses ibis to resolve
     * the Rubik's cube
     * 
     * @param arguments
     *            list of arguments
     */
    public static void main(String[] arguments) {
    	   try {
               new Rubiks().run(arguments);
           } catch (Exception e) {
               e.printStackTrace(System.err);
           }


    }

}