package rubiks.ipl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;


/**
 * Solver for rubik's cube puzzle.
 * 
 * @author Niels Drost, Timo van Kessel
 * 
 */
public class Rubiks {
	

    static PortType portOneToMany = new PortType(PortType.COMMUNICATION_RELIABLE, //reliable communications and election
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_EXPLICIT,  //one_to_one, string can be sent
            PortType.CONNECTION_ONE_TO_MANY);
    
    static PortType portManyToOne = new PortType(PortType.COMMUNICATION_RELIABLE, //reliable communications and election
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_EXPLICIT,  //one_to_one, string can be sent
            PortType.CONNECTION_MANY_TO_ONE);

    static IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT,
            IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
            IbisCapabilities.TERMINATION);
    

    /**
     * Identifier of the server node
     */
    public IbisIdentifier server = null;
    
    static String READY_FOR_NEW_JOBS = "r";
    
    static String PAUSE_WORKER_COMPUTATION = "p";
    
    static String FINALIZE_MESSAGE = "f";
    
	public Ibis myIbis = null;
	
    public IbisIdentifier[] ibisNodes = null;
	
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
    public static int solutions(Cube cube, CubeCache cache) {
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
    	myIbis = IbisFactory.createIbis(ibisCapabilities, null,portOneToMany,portManyToOne);
        // sleep for a second
        Thread.sleep(500);
        
        ibisNodes  = myIbis.registry().joinedIbises();
        /*
        System.out.println("NUMBER OF JOINED NODES:" + ibisNodes.length);
        for(IbisIdentifier ibisids : ibisNodes){
        	System.out.println(ibisids.name()); 
        }*/
        
        // Elect a server
        server = myIbis.registry().elect("Server");

       // System.out.println("Server is " + server);    
    }
    
    private void run(String[] arguments) throws Exception { 
    	int size = 3;
    	if ( arguments.length > 0 && arguments[0].equalsIgnoreCase("--size")) {
            size = Integer.parseInt(arguments[0]);
    	}
    	initialize();
    	
    	if (server.equals(myIbis.identifier())) {
    		new Server(arguments,this);
        } else {
            new Worker(size,this).workerComputation();
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
        //System.err.println("Solving cube took " + (end - start) + " milliseconds")
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
