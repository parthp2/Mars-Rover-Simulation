package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.PlanetMap;
import MapSupport.ScanMap;
import common.Rover;
import communicationInterface.Communication;
import communicationInterface.CommunicationHelper;
import enums.RoverDriveType;
import enums.Science;
import enums.Terrain;
import rover_logic.Astar;
import searchStrategy.AstarSearch;
import searchStrategy.SearchStrategy;
import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;
import searchStrategy.graph.NodeData;

/*
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

/**
 * 
 * @author rkjc
 * 
 * ROVER_07 is intended to be a basic template to start building your rover on
 * Start by refactoring the class name to match your rovers name.
 * Then do a find and replace to change all the other instances of the 
 * name "ROVER_07" to match your rovers name.
 * 
 * The behavior of this robot is a simple travel till it bumps into something,
 * sidestep for a short distance, and reverse direction,
 * repeat.
 * 
 * This is a terrible behavior algorithm and should be immediately changed.
 *
 */

public class ROVER_07 extends Rover {

	
	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_07 client;
    	// if a command line argument is present it is used
		// as the IP address for connection to RoverControlProcessor instead of localhost 
		
		if(!(args.length == 0)){
			client = new ROVER_07(args[0]);
		} else {
			client = new ROVER_07();
		}
		
		client.run();
	}

	public ROVER_07() {
		// constructor
		System.out.println("ROVER_07 rover object constructed");
		rovername = "ROVER_07";
	}
	
	public ROVER_07(String serverAddress) {
		// constructor
		System.out.println("ROVER_07 rover object constructed");
		rovername = "ROVER_07";
		SERVER_ADDRESS = serverAddress;
	}
	
	/**************************
	 * Communications Functions
	 ***************************/
	// get data from server and update field map
	
	
	/**
	 * 
	 * The Rover Main instantiates and runs the rover as a runnable thread
	 * 
	 */
	private void run() throws IOException, InterruptedException {
		// Make a socket for connection to the RoverControlProcessor
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			// sets up the connections for sending and receiving text from the RCP
			receiveFrom_RCP = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			sendTo_RCP = new PrintWriter(socket.getOutputStream(), true);
			
			// Need to allow time for the connection to the server to be established
			sleepTime = 300;
			
			/*
			 * After the rover has requested a connection from the RCP
			 * this loop waits for a response. The first thing the RCP requests is the rover's name
			 * once that has been provided, the connection has been established and the program continues 
			 */
			while (true) {
				String line = receiveFrom_RCP.readLine();
				if (line.startsWith("SUBMITNAME")) {
					//This sets the name of this instance of a swarmBot for identifying the thread to the server
					sendTo_RCP.println(rovername); 
					break;
				}
			}

			/**
			 *  ### Retrieve static values from RoverControlProcessor (RCP) ###
			 *  These are called from outside the main Rover Process Loop
			 *  because they only need to be called once
			 */		
			
			// **** get equipment listing ****			
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			// sets drivable terrain list used in searching
			for(String equip: equipment) {
				if (RoverDriveType.getEnum(equip) != RoverDriveType.NONE) {
					setDrivableTerrain(RoverDriveType.getEnum(equip));
					break;
				}
			}
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			// 
			startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			targetLocation = getTargetLocation();
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
			
			
	        // **** Define the communication parameterspost message and open a connection to the 
			// SwarmCommunicationServer restful service through the Communication.java class interface
	        String url = "http://localhost:3000/api"; // <----------------------  this will have to be changed if multiple servers are needed
	        String corp_secret = "gz5YhL70a2"; // not currently used - for future implementation
	
	       
	        
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 *  add more as needed
			 */
	        
	        Communication com = new Communication(url, rovername, corp_secret);
	        
	    	SearchStrategy searchStrategy = new AstarSearch();
	    	
	    	List<Edge> path = new ArrayList<Edge>();
	    	int pathIndex = 0;
	        
	        boolean stopped = true;
	        boolean nextHasRover = false;
	        String nextTerrain = "";

	        Graph graph= null;
	        
	        Edge nextMove= null;
	        Node startNode= null;
	        Node targetNode= null;
	        
	        NodeData nextMoveToData= null;
	        MapTile nextMoveGobalTile = null;

			/**
			 *  ####  Rover controller process loop  ####
			 *  This is where all of the rover behavior code will go
			 *  
			 */
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
					
				
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();
				System.out.println(rovername + " currentLoc at: " + currentLoc);
				System.out.println(rovername + " targetLocation at: " + targetLocation);			

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan(); 
				
				// prints the scanMap to the Console output for debug purposes
//				scanMap.debugPrintMap();
				
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.
	            String postScanMapTilesResponse = com.postScanMapTiles(currentLoc, scanMap.getScanMap());
	     

				// ***** get GlobalMap from server *****
				// gets the GlobalMap from the server to and update its local map for pathing/searching
	            JSONArray getGlobalMapResponse = com.getGlobalMap();
	            System.out.println();
	            
	            System.out.println("updating globalMap ...");
	            globalMap = new PlanetMap(getGlobalMapResponse, currentLoc, targetLocation);
	            
	            System.out.println("adding scan data to globalMap..");
	            globalMap.addScanDataMissing(scanMap);
	            System.out.println();
	            
	            // prints the globalMap to the Console output for debug purposes, are marked as ::
	            // globalMap doesn't tiles of Terrain.NONE from server, it is reserved for unexplored tiles
	            globalMap.debugPrintMap();
	              
	            // testing...
//	            System.out.println(com.getAllRoverDetails());
	            
				
				// if stopped creates a new path to target and resets the path index
				if (stopped) {
					
					System.out.println("plotting new path: " + globalMap.getStartPosition() + " -> " + globalMap.getTargetPosition());
					
					stopped = false;
					
					graph = new Graph(globalMap);
					
					startNode = graph.getNode(new Node(new NodeData(globalMap.getStartPosition(),
							globalMap.getTile(globalMap.getStartPosition())))).get();
					
					targetNode = graph.getNode(new Node(new NodeData(globalMap.getTargetPosition(),
							globalMap.getTile(globalMap.getTargetPosition())))).get();
			
					path = graph.search(searchStrategy, drivableTerrain, startNode, targetNode);
					pathIndex = 0;
				}
					
			
				if (pathIndex < path.size() && !stopped) {
					
					nextMove = path.get(pathIndex);
					nextMoveToData = (NodeData)nextMove.getTo().getData();
					nextMoveGobalTile = globalMap.getTile(nextMoveToData.getX(), nextMoveToData.getY());
					nextHasRover = nextMoveGobalTile.getHasRover();
					nextTerrain = nextMoveGobalTile.getTerrain().getTerString();
					
					// checks if next tile is blocked by rover or it was unexplored when search started and is a terrain rover cannot travel on
					stopped = nextHasRover || !drivableTerrain.contains(nextTerrain);
					
					if (!stopped) {
						// goes until target is reached
						move(nextMove);
						
						
						
						pathIndex++;
					}
					
					
					System.out.println("drivable terrain");
					for(String s : drivableTerrain) {
						System.out.print(" " + s);
					}
					System.out.println();
					System.out.println("next terrain: " + nextTerrain);
					
					
					System.out.println("steps remaining: " + (path.size() - pathIndex));
					System.out.println("next move: " + nextMove);
					
					System.out.println("next hasRover: " + nextHasRover);
				}
				
				if (pathIndex == path.size() - 1) {
					System.out.println("reached target need new action...");
					targetLocation =  new Coord(globalMap.getWidth() - 1, globalMap.getHeight() - 1);
					System.out.println("trying to find bottom corner...");
					stopped = true;
				}
				System.out.println();
							
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				// ***** get TIMER time remaining *****
				timeRemaining = getTimeRemaining();
				sleepTime = timeRemaining + 350; // incorrect sleepTimer will break rover
				System.out.println("ROVER_07 ------------ end process control loop --------------");
				System.out.println();
				Thread.sleep(sleepTime);
			}  // ***** END of Rover control While(true) loop *****
					
			
		// This catch block hopefully closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_07 problem closing socket");
	            }
	        }
	    }

	} // END of Rover run thread
	
	// ####################### Additional Support Methods #############################
	

	
	// add new methods and functions here
	
	public void move(Edge edge) {

		NodeData current = (NodeData)edge.getFrom().getData();
		NodeData next = (NodeData)edge.getTo().getData();
		
		if (current.getX() < next.getX()) moveEast();
		if (current.getX() > next.getX()) moveWest();
		if (current.getY() < next.getY()) moveSouth();
		if (current.getY() > next.getY()) moveNorth();
	}
	
	


}