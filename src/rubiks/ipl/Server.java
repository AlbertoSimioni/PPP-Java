package rubiks.ipl;

import java.io.IOException;



import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class Server {
	
    private SendPort serverSendPort = null;
    
    private ReceivePort serverReceivePort = null;
    

    
    /**
     * Starting cube
     */
    private Cube startCube = null;
    
    
    private CubeCache cache = null;
    
    private Rubiks rubiks;
    
    private void generateJobsForCurrentBound(Cube cube,CubeCache cache, int bound)throws IOException{
        if (cube.getTwists() >= cube.getBound()) {
            return;
        }
        // generate all possible cubes from this one by twisting it in
        // every possible way. Gets new objects from the cache
        Cube[] children = cube.generateChildren(cache); //****
        for (Cube child : children) {
        	if(cube.getTwists() == 3){
        		ReadMessage r = serverReceivePort.receive(); 
                String s = r.readString();
                r.finish();
                if(s.equals(Rubiks.READY_FOR_NEW_JOBS)){
                	WriteMessage w = serverSendPort.newMessage();
                	w.writeObject(child);
                    w.finish();
                }
        	}
        	else generateJobsForCurrentBound(child, cache, bound);
            cache.put(child);
        }
    }
    
    
    private void serverComputation() throws Exception{
        int bound = 0;
        int result = 0;
        for(IbisIdentifier ibis: rubiks.ibisNodes){
        	if(!(ibis.name().equals(rubiks.myIbis.identifier())))
        		serverSendPort.connect(ibis, "receive port");
        }
    	while (result == 0) {
            bound++;
            startCube.setBound(bound);
            System.out.print(" " + bound);
            if(bound <= 3){
            	result = Rubiks.solutions(startCube,cache);
            }
            else{
            	generateJobsForCurrentBound(startCube,cache, bound);
        		sendMessageToAllWorkers(Rubiks.PAUSE_WORKER_COMPUTATION);
        		result = collectResultsFromWorkers();
            	//ora dovrei checkare se sono state trovate soluzioni
            }
    	}
    	sendMessageToAllWorkers(Rubiks.FINALIZE_MESSAGE);
    	System.out.println();
        System.out.println("Solving cube possible in " + result + " ways of "
                + bound + " steps");
    }
    
    
    private int collectResultsFromWorkers() throws Exception{
    	int solutionsFinded = 0;
    	for(int i = 0; i < rubiks.ibisNodes.length -1; i++){
	    	ReadMessage r = serverReceivePort.receive(); 
	        solutionsFinded += r.readInt();
	        r.finish();
    	}
    	return solutionsFinded;
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
                + startCube.getSize() + ", twists = " + twists + ", seed = " + seed);
        startCube.print(System.out);
        System.out.flush();
   
    }
    
    
    private void sendMessageToAllWorkers (String message) throws Exception{
    	for(IbisIdentifier ibisNode : rubiks.ibisNodes){
    		if(!ibisNode.name().equals(rubiks.myIbis.identifier())){
    			//serverSendPort.connect(ibisNode, "receive port");
	        	WriteMessage w = serverSendPort.newMessage();
	        	w.writeString("PAUSE");
	            w.finish();
    		}
    	}
    }
    
    public Server(String[] arguments, Rubiks rubiks) throws Exception{
       	createStartCube(arguments);
       	this.rubiks = rubiks;
    	cache = new CubeCache(startCube.getSize());
		serverSendPort = rubiks.myIbis.createSendPort(Rubiks.portOneToMany);
		serverReceivePort = rubiks.myIbis.createReceivePort(Rubiks.portManyToOne, "receive port");
		serverReceivePort.enableConnections();
		serverComputation();
		sendMessageToAllWorkers(Rubiks.FINALIZE_MESSAGE);
    }
    
    
    
}
