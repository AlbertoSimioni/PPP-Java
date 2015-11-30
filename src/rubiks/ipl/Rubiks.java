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
	

    PortType portType = new PortType(PortType.COMMUNICATION_RELIABLE, //reliable communications and election
            PortType.SERIALIZATION_DATA, PortType.RECEIVE_EXPLICIT,  //one_to_one, string can be sent
            PortType.CONNECTION_ONE_TO_ONE);

    IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT);
    
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
    private Ibis ibis = null;
    
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

    private void initialize(String[] arguments) throws Exception {
        // Create an ibis instance.
        

        // sleep for a second
        Thread.sleep(1000);
        
        
        ibisesNodes = ibis.registry().joinedIbises();
        
        System.out.println("NUMBER OF JOINED NODES:" + ibisesNodes.length);
        
        // Elect a server
        server = ibis.registry().elect("Server"); // decide if the current ibis is a server
        														 // or a client
        System.out.println("Server is " + server);    
    }
    
        
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
    
    private void run(String[] arguments) throws Exception {
    	ibis = IbisFactory.createIbis(ibisCapabilities, null, portType);
    	initialize(arguments);
    	if (server.equals(ibis.identifier())) {
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
        System.err.println("Solving cube took " + (end - start)
                + " milliseconds");

    	
    	ibis.end();
    }
    
    
    /**
     * Main function.
     * 
     * @param arguments
     *            list of arguments
     */
    public static void main(String[] arguments) {
        
    	
    	//1)LET'S WAIT FOR EVERYBODY - BY SETTING A SLEEP OF 1 SECOND (MEABY IT'S NOT NEEDED)
    	   try {
               new Rubiks().run(arguments);
           } catch (Exception e) {
               e.printStackTrace(System.err);
           }


    }

}
