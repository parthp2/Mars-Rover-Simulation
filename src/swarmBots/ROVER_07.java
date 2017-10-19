package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.PlanetMap;

import common.Rover;

import communicationInterface.Communication;

import enums.RoverDriveType;
import enums.RoverToolType;
import enums.Science;
import enums.Terrain;

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
	
	private enum State {
		UPDATING_PATH, MOVING, GATHERING, FINDING_TARGET, REACHED_TARGET
	}
	
	private SearchStrategy searchStrategy;
	private Set<String> drivableTerrain = new HashSet<String>(); // the terrain rover can drive on
	private Set<String> gatherableTerrain = new HashSet<String>();
	private State roverState;
	
	private long timeSinceLastMove = 10000L;
	private long timeSinceLastGather = 10000L;
	
	private long moveCooldown = 400L; // default to wheel speed from RPC
	private long gatherCooldown = 3400L; // default to gather speed from RPC
	
	
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
			
	        // **** Define the communication parameterspost message and open a connection to the 
			// SwarmCommunicationServer restful service through the Communication.java class interface
	        String url = "http://localhost:3000/api"; // <----------------------  this will have to be changed if multiple servers are needed
	        String corp_secret = "gz5YhL70a2"; // not currently used - for future implementation
	        communication = new Communication(url, rovername, corp_secret);
		       

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
			
			// look for drive type and sets drivable terrain and gatherable list used in searching
			for(String equip: equipment) {
					setDrivableTerrain(RoverDriveType.getEnum(equip));
					setGatherableTerrain(RoverToolType.getEnum(equip));
			}
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			// 
			startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			targetLocation = getTargetLocation();
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
	        
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 *  add more as needed
			 */
	         
	    	searchStrategy = new AstarSearch(); // strategy used in pathfinding
	    	roverState = State.UPDATING_PATH; // rover start state is set to UPDATING_PATH
	    	
	    	List<Edge> path = null; // path rover will take using searchStrategy and graph
	    	
	    	
	    	int pathIndex = 0; // index of path to target
	        Edge nextMove = null; // current edge from current -> next node in graph
	        
	        long startTime = 0; // start of loop time
	        
	        
			/**
			 *  ####  Rover controller process loop  ####
			 *  This is where all of the rover behavior code will go
			 *  
			 */
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
					
				
				startTime = System.currentTimeMillis();
				
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();
				System.out.println(rovername + " currentLoc at: " + currentLoc);
				System.out.println(rovername + " targetLocation at: " + targetLocation);			

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan();
				
				
//				if (scanMap.getScanMap()[3][6].getTerrain().equals(Terrain.NONE)) {
//					xCornerScanned = currentLoc.xpos - 1;
//				}
//				if (scanMap.getScanMap()[3][6].getTerrain().equals(Terrain.NONE)) {
//					yCornerScanned = currentLoc.ypos - 1;
//				}
				
				// prints the scanMap to the Console output for debug purposes
//				scanMap.debugPrintMap();
				
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.
				
				communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
//	            String postScanMapTilesResponse = communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
	     

				// ***** get GlobalMap from server *****
				// gets the GlobalMap from the server to and update its local map for pathing/searching
	            JSONArray getGlobalMapResponse = communication.getGlobalMap();
	            System.out.println();
	            
//	            System.out.println("updating globalMap ...");
	            globalMap = new PlanetMap(getGlobalMapResponse, currentLoc, targetLocation);
	            
//	            System.out.println("adding scan data to globalMap..");
	            globalMap.addScanDataMissing(scanMap);
	            System.out.println();
	            
	            // prints the globalMap to the Console output for debug purposes, are marked as ::
	            // globalMap doesn't tiles of Terrain.NONE from server, it is reserved for unexplored tiles
//	            globalMap.debugPrintMap();
	              
	            // testing...
//	            System.out.println(com.getAllRoverDetails());
	            
	            
	            // Rover State Machine new states can be added with new cases with new State enums
	            
	            do {
	            	
	            	switch (roverState) {
	            	
					case FINDING_TARGET: // target selection
						
						Set<Coord> locations = tilesRoverCanGather(); // gathered tiles are not updated on server must fix or looping happens
						
						List<Edge> newPath = null;
						
						Coord newTarget = null;
						
						Graph graph = new  Graph(globalMap, tilesRoverCanReach());
						
						int maxLength = Integer.MAX_VALUE;
						
						for (Coord location: locations) {
							
							newPath = findPath(graph, currentLoc, location);
							
							if (newPath != null) {
								
								if (newPath.size() < maxLength && newPath.size() > 0) {
									
									newTarget = location;
									maxLength = newPath.size();
									path = newPath;
								}
							}
						}
						
						targetLocation = newTarget;
						pathIndex = 0;
						
						System.out.println("new gather target and path found entering state MOVING...");
						roverState = State.MOVING;
						
						break;
						
					case UPDATING_PATH: // refreshes path if obstacle is found
						
						path = findPath(new Graph(globalMap, tilesRoverCanReach()), currentLoc, targetLocation);
						pathIndex = 0;
						
						if (path != null) {
							
							System.out.println("path found entering state MOVING...");
							roverState = State.MOVING;
						}
						else {
							
							System.out.println("path not found entering state MOVING...");
							roverState = State.FINDING_TARGET;
						}
						break;
						
					case REACHED_TARGET: // what to do once target is reached
						
						Science sci = globalMap.getTile(currentLoc).getScience();
						
						if (!sci.equals(Science.NONE)) {
						
							System.out.println("location has attainable resources entering state GATHERING...");
							roverState = State.GATHERING; // if has material that rover can pick up, needs new function
						}
						else {
							
							System.out.println("location has no attainable resources entering state FINDING_TARGET...");
							roverState = State.FINDING_TARGET;
						}
						break;
		
					case GATHERING: // gathering steps
						
						System.out.println(gatherCooldownRemaining(gatherCooldown));
						
						if (gatherCooldownRemaining(gatherCooldown) > 3400)
							Thread.sleep(0);
						else Thread.sleep(gatherCooldownRemaining(gatherCooldown));
					
						gatherScience(currentLoc);
						
						resetGatherCooldown();
						
						System.out.println("gathering complete entering state FINDING_TARGET...");
						roverState = State.FINDING_TARGET;
						
						break;
						
					case MOVING: // governs how rover moves along path
						
						if (pathIndex == path.size()) {
							
							System.out.println("reached target entering state REACHED_TARGET...");
							roverState = State.REACHED_TARGET;
							break;
						}
						
						nextMove = path.get(pathIndex);
						
						if (canMoveTo(nextMove)) {
							
							Thread.sleep(moveCooldownRemaining(moveCooldown));
							
							move(nextMove);
							
							resetMoveCooldown();
							
							pathIndex++;
							
							System.out.println("moved to next MapTile entering state MOVING...");
							roverState = State.MOVING;
						}
						else {
							
							System.out.println("path blocked entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
						}
						break;
					}
	            	
	            } while (roverState != State.MOVING && roverState != State.GATHERING);
	            
	            timeRemaining = getTimeRemaining();

				System.out.println("loop time : " + (System.currentTimeMillis() - startTime));
				System.out.println("ROVER_07 ------------ end process control loop --------------");
				System.out.print("\n\n\n\n\n");
				
				timeSinceLastMove = System.currentTimeMillis() ;
				
				// this is the Rover's HeartBeat, it regulates how fast the Rover cycles through the control loop
				// ***** get TIMER time remaining *****
				
			}  // ***** END of Rover control While(true) loop *****
					
		// This catch block hopefully closes the open socket connection to the server
		} catch (Exception e) {
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
	
	// sends move command to RPC to move to next position on path
	private void move(Edge edge) {

		NodeData current = (NodeData)edge.getFrom().getData();
		NodeData next = (NodeData)edge.getTo().getData();
		
		if (current.getX() < next.getX()) moveEast();
		if (current.getX() > next.getX()) moveWest();
		if (current.getY() < next.getY()) moveSouth();
		if (current.getY() > next.getY()) moveNorth();
	}
	
	// checks if next position on graph is blocked by a rover or a Terrain rover cannot walk on
	// this is useful because a rover will be able to path to unexplored MapTiles and will not change
	// paths unless tile is an obstacle
	private boolean canMoveTo(Edge nextMove) {
		
		NodeData nextMoveToData = (NodeData)nextMove.getTo().getData();
		MapTile nextMoveGobalTile = globalMap.getTile(nextMoveToData.getX(), nextMoveToData.getY());
		
		boolean nextHasRover = nextMoveGobalTile.getHasRover();
		String nextTerrain = nextMoveGobalTile.getTerrain().getTerString();
		
		return (!nextHasRover && drivableTerrain.contains(nextTerrain));
	}
	
	private long gatherCooldownRemaining(long cooldown) {
	
		long remaindingTime =  cooldown - (System.currentTimeMillis() - timeSinceLastGather);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	private void resetGatherCooldown() {
		
		timeSinceLastGather = System.currentTimeMillis();
	}
	
	private long moveCooldownRemaining(long cooldown) {

		long remaindingTime =  cooldown - (System.currentTimeMillis() - timeSinceLastMove);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	private void resetMoveCooldown() {
		
		timeSinceLastMove = System.currentTimeMillis();
	}
	
	
	// returns a new list of edges "path"  Rover.currentLoc -> Rover.targetLocation
	// using globalMap. globalMap is updated every loop
	private List<Edge> findPath(Graph graph , Coord from, Coord to) {
		
		Node fromNode = graph.getNode(new Node(new NodeData(globalMap, from))).get();
			
		Node toNode = graph.getNode(new Node(new NodeData(globalMap, to))).get();

		return searchStrategy.search(graph, fromNode, toNode);	
	}
	
	// sets terrain rover can drive on used in SearchStrategy used once before the loop
	private void setDrivableTerrain(RoverDriveType drive) {
		
		switch (drive) {
		
		case WALKER:
			drivableTerrain.add(Terrain.NONE.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.ROCK.getTerString());
			moveCooldown = 1200;
			break;
			
		case TREADS:
			drivableTerrain.add(Terrain.NONE.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.SAND.getTerString());
			moveCooldown = 900;
			break;
			
		default:
			break;
		}
	}
	
	private void setGatherableTerrain(RoverToolType tool) {
		// all types can traverse these
		
		switch (tool) {
		
		case EXCAVATOR:
			gatherableTerrain.add(Terrain.SAND.getTerString());
			gatherableTerrain.add(Terrain.SOIL.getTerString());
			break;
			
		case DRILL:
			gatherableTerrain.add(Terrain.ROCK.getTerString());
			gatherableTerrain.add(Terrain.GRAVEL.getTerString());
			break;
			
		default:
			break;
		}
	}
	
	private Set<Coord> tilesRoverCanGather() {
		
		Set<Coord> canWalkOnWithResources = tilesRoverCanReach();
		Set<Coord> canGatherOn = new HashSet<Coord>();
		
		MapTile tile = null;
		String terrain = "";
		
		for(Coord coord: canWalkOnWithResources) {
		
			
			tile = globalMap.getTile(coord);
			
			if (tile.getScience().equals(Science.NONE)) continue;
			
			terrain = tile.getTerrain().getTerString();
			
			if(gatherableTerrain.contains(terrain)) {
				
				canGatherOn.add(coord);
			}
		}
		
		return canGatherOn;
	}
	
	private Set<Coord> tilesRoverCanReach() {
		
		Set<Coord> canWalkOn = new HashSet<>();
		Map<Coord, MapTile> tiles = globalMap.getAllTiles();
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		for (Map.Entry<Coord, MapTile> entry : tiles.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			terrain = tile.getTerrain().getTerString();
			
			if(drivableTerrain.contains(terrain)) {
				
				canWalkOn.add(coord);
			}
		}
		
		return canWalkOn;
	}
}