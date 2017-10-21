package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.simple.JSONArray;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.PlanetMap;

import common.Rover;

import communicationInterface.Communication;

import enums.RoverDriveType;
import enums.RoverMode;
import enums.RoverToolType;
import enums.Science;
import enums.Terrain;

import searchStrategy.AstarSearch;
import searchStrategy.SearchStrategy;
import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;

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
		UPDATING_PATH, MOVING, GATHERING, FINDING_RESOURCE, REACHED_TARGET, EXPLORING
	}
	
	private SearchStrategy searchStrategy = new AstarSearch(); // the search that we are using to find paths on graph
	private Set<String> drivableTerrain = new HashSet<String>(); // the terrain rover can drive on
	private Set<String> gatherableTerrain = new HashSet<String>(); // the terrain rover can gather on
	
	private RoverMode mode = RoverMode.GATHER; // start mode          GATHER, EXPLORE
	private State roverState = State.UPDATING_PATH; // start state    UPDATING_PATH, EXPLORING, FINDING_RESOURCE, GATHERING, MOVING, REACHED_TARGET
	
	private long timeSinceLastMove = 10000L;
	private long timeSinceLastGather = 10000L;
	
	private long lagCushion = 25L; // helps performance
	
	private long moveCooldown = 10000L;
	private long gatherCooldown = 3400L + lagCushion; // default to gather speed from RPC + 30
	

	
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
		rovername = "ROVER_01"; // rover 1 is fasted used for testing
		System.out.println(rovername + " rover object constructed");
	}
	
	public ROVER_07(String serverAddress) {
		// constructor
		rovername = "ROVER_01"; // rover 1 is fasted used for testing
		System.out.println(rovername + " rover object constructed");
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
//			targetLocation = new Coord(20, 20);
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
	        
			searchStrategy = new AstarSearch(); // strategy used in pathfinding
			
	
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 *  add more as needed
			 */
	         
	    	
	    	
			Graph graph = null; // graph created to search on
	    	List<Edge> path = null; // path rover will take using searchStrategy and graph
	        Edge nextMove = null; // edge containing from current -> next node in graph coord
	        long startTime = 0; // used to check how long  outer while loop takes
	        
	        
	        Coord finalTarget = null; //TODO use this to implement search for resources onway to target;
	        
			/**
			 *  ####  Rover controller process loop  ####
			 *  This is where all of the rover behavior code will go
			 *  
			 */
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
					
				
				startTime = System.currentTimeMillis();
				
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();			

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan();
				
				// prints the scanMap to the Console output for debug purposes
//				scanMap.debugPrintMap();
					
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.
				
				communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
	     

				// ***** get GlobalMap from server *****
				// gets the GlobalMap from the server to and update its local map for pathing/searching
	            JSONArray getGlobalMapResponse = communication.getGlobalMap();
	            System.out.println();
	            
	            globalMap = new PlanetMap(getGlobalMapResponse, currentLoc, targetLocation);
	            globalMap.addScanDataMissing(scanMap);
	            System.out.println();
	            
	            // prints the globalMap to the Console output for debug purposes, are marked as ::
	            // globalMap doesn't tiles of Terrain.NONE from server, it is reserved for unexplored tiles
//	            globalMap.debugPrintMap();
	              
	            // testing...
//	            System.out.println(com.getAllRoverDetails());
	            
				System.out.println(rovername + " currentLoc at: " + currentLoc);
				System.out.println(rovername + " targetLocation at: " + targetLocation);
				
				System.out.println("resources " + rovername + " tiles in map:" + globalMap.getAllTiles().size());
				System.out.println("resources " + rovername + " can gather: " + tilesRoverCanGather().size());
				System.out.println("resources " + rovername + " can reach: " + tilesRoverCanReach().size());
				System.out.println("resources " + rovername + " can explore: " + unkownTiles().size());
	            
	            
	            // Rover State Machine new states can be added with new cases with new State enums
	            
	            do {
	            	
	            	switch (roverState) {
	            	
	            	case EXPLORING: // exploring unknown tiles
	            		
	            		System.out.println("tiles to explore: " + unkownTiles().size());
	            		
	            		Coord unknownTile = closestUnknownTile();
	            		
	            		if (unknownTile!= null) {
	            			
	            			targetLocation = unknownTile;
	            			
	            			System.out.println("new target to explore found entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
	            		}
	            		else {
	            			//TODO run around tiles that u can see to refresh
	            		}
	            		
	            		
	            		break;
	            	
					case FINDING_RESOURCE: // tile selection for gathering
						
						//TODO put inside of a method way to much code
						
						Set<Coord> locations = tilesRoverCanGather();
						
						System.out.println("known resources: " + locations);
						
						if (locations.size() == 0) { // no tiles you can gather
							
							System.out.println("map has no gatherable resources entering state EXPLORE...");
							roverState = State.EXPLORING; // enters exploring regardless of mode if there is no resources to gather
							break;
						}		
						
						
						graph = new Graph(globalMap.getWidth(), globalMap.getHeight(), tilesRoverCanReach());
						Map<Coord, List<Edge>> paths = new HashMap<Coord, List<Edge>>();
						
						List<Edge> newPath = null;
						
						Iterator<Coord> locitor = locations.iterator();
						Coord location = null;
						
						while(locitor.hasNext()){
							
							location = locitor.next();
							
							newPath = findPath(graph, currentLoc, location);
							
							if (newPath != null) {
								if (newPath.get(0).getValue() != 0) {
									paths.put(location, newPath);
								}	
							}
						}
						
						int shortest = Integer.MAX_VALUE;
						newPath = null;
						Coord newTarget = null;
						int temp = 0;
						
						Map.Entry<Coord, List<Edge>>  entry = null;
						
						Iterator<Entry<Coord, List<Edge>>> pathitor = paths.entrySet().iterator();
						
						while(pathitor.hasNext()){
						
							entry = pathitor.next();
									
							newTarget = entry.getKey();
							temp = entry.getValue().size();
							
							System.out.print(temp + " ");
							
							if (temp < shortest) {	// map is not updated again until after this loop so must ignore size 1
								
								shortest = temp;
								path = entry.getValue();
								targetLocation = newTarget;
							}
							
						}
			
						System.out.println("new gather target and path found entering state MOVING...");
						roverState = State.MOVING;
						break;
						
					case UPDATING_PATH: // refreshes path if obstacle is found
						
						Set<Coord> tiles = tilesRoverCanReach();
						path = null;
						
						if (tiles.contains(targetLocation)) {
							
							graph = new  Graph(globalMap.getWidth(), globalMap.getHeight(), tiles);
							path = findPath(graph, currentLoc, targetLocation);
						}
						
						if (path == null) {
							
							System.out.println("target unreachable ");
							
							if (mode.equals(RoverMode.GATHER)) {
								
								System.out.println("rover mode is GATHER entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
							else {
								
								System.out.println("rover mode is EXPLORE entering state EXPLORING...");
								roverState = State.EXPLORING;
							}
						}
						else {
							System.out.println("path found entering state MOVING...");
							roverState = State.MOVING;
						}
						break;
						
					case REACHED_TARGET: // what to do once target is reached
						
						if (mode.equals(RoverMode.GATHER)){
							
							Science sci = globalMap.getTile(currentLoc).getScience();
							System.out.print("rover mode is GATHER ");
							
							if (!sci.equals(Science.NONE)) {
								
								System.out.println("location has attainable resources entering state GATHERING...");
								roverState = State.GATHERING; // if has material that rover can pick up, needs new function
							}
							else {
								System.out.println("location has no attainable resources entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
						}
						else {
							
							System.out.println("rover mode is EXPLORE entering state EXPLORING...");
							roverState = State.EXPLORING;
						}
						break;
		
					case GATHERING: // gathering steps
						
						Thread.sleep(gatherCooldownRemaining(gatherCooldown));
						
						gatherScience(currentLoc);
						
						globalMap.getTile(currentLoc).setScience(Science.NONE);
						
						resetGatherCooldown();
						
						System.out.println("gathering complete entering state FINDING_RESOURCE...");
						roverState = State.FINDING_RESOURCE;
						
						break;
						
					case MOVING: // governs how rover moves along path
						
						if (reachedTarget(currentLoc, targetLocation)) {
							
							System.out.println("reached target entering state REACHED_TARGET...");
							roverState = State.REACHED_TARGET;
							break;
						}
						
						// get next move uses current location and a path
						nextMove = getNexMove(path);
						
						// check is next tile has an obstacle
						// this is useful for pathing to unkown tiles or rovers
						if (canMoveTo(nextMove)) {
							
							// sleep until move cooldown is over
							Thread.sleep(moveCooldownRemaining(moveCooldown));
							
							// move to tile
							move(nextMove);
							
							// resets move cooldown after move
							 resetMoveCooldown();
							
							System.out.println("moved to next MapTile entering state MOVING...");
							roverState = State.MOVING;
						}
						else {
							
							System.out.println("path blocked entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
						}
						break;
					}
	            	
	            } while (roverState != State.MOVING);
	            
	            timeRemaining = getTimeRemaining();
				System.out.println(rovername + " ------------ END PROCESS CONTROLL LOOP -----TIME: " + (System.currentTimeMillis() - startTime));
				
				// this is the Rover's HeartBeat, it regulates how fast the Rover cycles through the control loop
				// ***** get TIMER time remaining *****
//				resetMoveCooldown();


				
			}  // ***** END of Rover control While(true) loop *****
					
		// This catch block hopefully closes the open socket connection to the server
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println(rovername + " problem closing socket");
	            }
	        }
	    }

	} // END of Rover run thread
	
	// ####################### Additional Support Methods #############################
	

	
	// add new methods and functions here
	
	// sends move command to RPC to move to next position on path
	private void move(Edge edge) {

		Coord current = (Coord)edge.getFrom().getData();
		Coord next = (Coord)edge.getTo().getData();
		
		if (current.xpos < next.xpos) moveEast();
		if (current.xpos > next.xpos) moveWest();
		if (current.ypos < next.ypos) moveSouth();
		if (current.ypos > next.ypos) moveNorth();
	}
	
	// checks if next position on graph is blocked by a rover or a Terrain rover cannot walk on
	// this is useful because a rover will be able to path to unexplored MapTiles and will not change
	// paths unless tile is an obstacle
	private boolean canMoveTo(Edge nextMove) {
		
		Coord currentCoord = (Coord)nextMove.getFrom().getData();
		
		Coord nextCoord = (Coord)nextMove.getTo().getData();
		MapTile nextMoveGobalTile = globalMap.getTile(nextCoord);
		
		boolean nextHasRover = nextMoveGobalTile.getHasRover();
		String nextTerrain = nextMoveGobalTile.getTerrain().getTerString();
		
		return (!nextHasRover && drivableTerrain.contains(nextTerrain));
	}
	
	private Edge getNexMove(List<Edge> path) {
		
		for (Edge edge: path) {
			
			if(((Coord)edge.getFrom().getData()).equals(currentLoc)) {
				return edge;
			}
		}
		return null;
	}
	
	// passed target to allow to use in waypoints
	private boolean reachedTarget(Coord current, Coord target) {
		
		return current.equals(target);
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
		
		Node fromNode = graph.getNode(new Node<>(from));
		Node toNode = graph.getNode(new Node<>(to));
		
		return searchStrategy.search(graph, fromNode, toNode);	
	}
	
	// sets terrain rover can drive on used in SearchStrategy used once before the loop
	private void setDrivableTerrain(RoverDriveType drive) {
		
		switch (drive) {
		
		case WALKER:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.ROCK.getTerString());
			moveCooldown = 1200L + lagCushion;
			break;
			
		case TREADS:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.SAND.getTerString());
			moveCooldown = 900L + lagCushion;
			break;
			
		case WHEELS:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			moveCooldown = 400L + lagCushion;
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
			
			if(drivableTerrain.contains(terrain) && !tile.getHasRover()) {
				
				canWalkOn.add(coord);
			}
		}
		
		return canWalkOn;
	}
	
private Coord closestUnknownTile() {
	
	Coord closestCoord = null;
	Coord coord = null;
	int distance = 0;
	
	Iterator<Coord> tileItor = unkownTiles().iterator();
	
	int closestDistance = Integer.MAX_VALUE;
	
	while(tileItor.hasNext()) {
		
		coord = tileItor.next();
		distance = Math.abs(currentLoc.xpos - coord.xpos) + Math.abs(currentLoc.ypos - coord.ypos);
		
		if (distance < closestDistance) {
			
			closestDistance = distance;
			closestCoord = coord;
		}	
	}
	
	return closestCoord;
}
	
private Set<Coord> unkownTiles() {
		
		Set<Coord> uknownTiles = new HashSet<>();
		Map<Coord, MapTile> tiles = globalMap.getAllTiles();
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		for (Map.Entry<Coord, MapTile> entry : tiles.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			terrain = tile.getTerrain().getTerString();
			
			if(terrain.equals(Terrain.UNKNOWN.getTerString())) {
				
				uknownTiles.add(coord);
			}
		}
		
		return uknownTiles;
	}
}