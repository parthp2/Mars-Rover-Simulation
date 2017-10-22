package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
import MapSupport.ScanMap;
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
 * @author original by: rkjc (ROVER_00)
 * 
 * @author heavility modified by: luisfisherjr
 * 
 * ROVER_07 is intended to be a the template for GreenCorp
 * Start by refactoring the class name to match your rovers name.
 * Then do a find and replace to change all the other instances of the 
 * name "ROVER_07" to match your rovers name.
 * 
 * The behavior of this robot will allow it to navigate the map properly
 * and collect resources using A* search
 * 
 * The behavior of this robot is determined by a state machine.
 *
 */

public class ROVER_07 extends Rover {
	
	private enum State {
		UPDATING_PATH, MOVING, GATHERING, FINDING_RESOURCE, REACHED_TARGET, EXPLORING
	}
	
	private SearchStrategy searchStrategy = new AstarSearch(); // the search that we are using to find paths on graph
	private Set<String> drivableTerrain = new HashSet<String>(); // the terrain rover can drive on
	private Set<String> gatherableTerrain = new HashSet<String>(); // the terrain rover can gather on
	private Set<String> sensors = new HashSet<String>(); 
	
	private RoverMode mode = RoverMode.GATHER; // start mode          GATHER, EXPLORE
	private State roverState = State.UPDATING_PATH; // start state    UPDATING_PATH, EXPLORING, FINDING_RESOURCE, GATHERING, MOVING, REACHED_TARGET
	
	private long timeSinceLastMove = 10000L;
	private long timeSinceLastGather = 10000L;
	
	private long lagCushion = 25L; // helps performance, might need to alter
	
	private long moveCooldown = 10000L; // gets set in run() depending on drive type
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
		rovername = "ROVER_07"; // rover 1 is fasted used for testing
		System.out.println(rovername + " rover object constructed");
	}
	
	public ROVER_07(String serverAddress) {
		// constructor
		rovername = "ROVER_07"; // rover 1 is fasted used for testing
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
	        String url = "http://localhost:3742/api"; // <----------------------  this will have to be changed if multiple servers are needed
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
			
			// separates the elements of list into more useful subgroups used below
			setEquipment(equipment);
		
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			// 
			startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			targetLocation = getTargetLocation();
//			targetLocation = new Coord(20, 20); // target coord that is closer for testing
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
	        
			
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
				
				// modifies scanMap before sending it to communication server
				addScannedDataToScanMap(scanMap);
				
				// prints the scanMap to the Console output for debug purposes
//				scanMap.debugPrintMap();
					
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.
				communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
	     

				// ***** get GlobalMap from server *****
				// gets the GlobalMap in the form of a JSONArray from the server to and update its local map to it for pathing/searching
	            JSONArray getGlobalMapResponse = communication.getGlobalMap();
	            
	            // for testing
//	            System.out.println(getGlobalMapResponse);
	            
	            // creates the GlobalMap from the JSONArray provided by the server and sets the local map 
	            globalMap = new PlanetMap(getGlobalMapResponse, currentLoc, targetLocation);
	            
	            // adds the locations of other rovers from the scanMap to the local map for pathing/searching
	            globalMap.addScanDataMissing(scanMap);
	           
	            
	            // prints the globalMap to the Console output for debug purposes, unexplored tiles are marked as ::
	            // globalMap doesn't have tiles of Terrain.UNKOWN from server, it is reserved for unexplored tiles
//	            System.out.println();
//	            globalMap.debugPrintMap();
	              
	            // testing...
	            System.out.println(communication.getAllRoverDetails());
				System.out.println(rovername + " currentLoc at: " + currentLoc);
				System.out.println(rovername + " targetLocation at: " + targetLocation);
				System.out.println("resources " + rovername + " tiles in map:" + globalMap.getAllTiles().size());
				System.out.println("resources " + rovername + " tiles can reach: " + tilesRoverCanWalkOn().size());
				System.out.println("resources " + rovername + " tiles can gather: " + tilesRoverCanGather().size());
				System.out.println("resources " + rovername + " tiles to explore: " + unkownTiles().size());
				System.out.println("resources " + rovername + " tiles to scan: " + tilesSensorsCanScan().size());
	            
	            
	            // Rover State Machine new states can be added with new cases with new State enums
	            
	            do {
	            	
	            	switch (roverState) {
	            	
	            	case EXPLORING: // exploring unknown tiles
	            		
	            		Coord unknownTile = closestUnknownTile();
	            		
	            		if (unknownTile!= null) {
	            			
	            			targetLocation = unknownTile;
	            			
	            			System.out.println("new target to explore found entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
	            		}
	            		else {
	            			//TODO run around tiles that u can see to refresh for other rovers
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
						
						
						graph = new Graph(globalMap.getWidth(), globalMap.getHeight(), tilesRoverCanWalkOn());
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
						
						//XXX for pathing to tiles rover can scan, for exploration to work correctly
						//TODO test change
						tilesRoverCanWalkOn().addAll(tilesSensorsCanScan());
						
						Set<Coord> tiles = tilesRoverCanWalkOn();
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
						
						// testing out mode might remove later right now is just set to gather
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
						
						// sleep until gather cooldown is over
						Thread.sleep(gatherCooldownRemaining());
						
						// gather tile command sent to RPC and removed, this will also remove from server
						gatherScience(currentLoc);
						
						// this is just to remove tile from other lists you will generate
						globalMap.getTile(currentLoc).setScience(Science.NONE);
						
						// reset gather cooldown
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
							Thread.sleep(moveCooldownRemaining());
							
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


	/**
 	 * Method that sends the move command to RPC to move to next position on path.This
 	 * method simplifies calls to moveEast(), moveWest(), moveSouth(), moveNorth() by taking a
 	 * move in the form of the edge (current -> next) and selecting the correct move.
 	 *
 	 * @param Edge edge : the Edge (current -> next)
	 */
	private void move(Edge edge) {

		Coord current = (Coord)edge.getFrom().getData();
		Coord next = (Coord)edge.getTo().getData();
		
		if (current.xpos < next.xpos) moveEast();
		if (current.xpos > next.xpos) moveWest();
		if (current.ypos < next.ypos) moveSouth();
		if (current.ypos > next.ypos) moveNorth();
	}
	
	
	/**
 	 * Method that returns a true if the target in the edge (origin -> target) can be reached by this rover.
  	 * 
  	 * @param Edge edge : the Edge (current -> next)
	 * 
	 * @return boolean : is the target is a valid destination
	 */
	private boolean canMoveTo(Edge nextMove) {

		Coord nextCoord = (Coord)nextMove.getTo().getData();
		MapTile nextMoveGobalTile = globalMap.getTile(nextCoord);
		
		boolean nextHasRover = nextMoveGobalTile.getHasRover();
		String nextTerrain = nextMoveGobalTile.getTerrain().getTerString();
		
		return (!nextHasRover && drivableTerrain.contains(nextTerrain));
	}
	
	
	/**
 	 * Method that returns a the next move (Edge) in the path (List<Edge>) based 
 	 * on the variable currentLoc.
 	 * 
  	 * @param List<Edge> path : the path to target
	 * 
	 * @return Edge : the next move
	 */
	private Edge getNexMove(List<Edge> path) {
		
		for (Edge edge: path) {
			
			if(((Coord)edge.getFrom().getData()).equals(currentLoc)) {
				return edge;
			}
		}
		return null;
	}
	
	
	/**
	 * Method that returns if two Coord are equal. This method is a simple wrapper
	 * that adds readability to the code in state machine.
  	 * 
  	 * @param Coord current : current coordinate
   	 * @param Coord target : target coordinate
	 * 
	 * @return boolean : is the rover at the target coordinate
	 */
	private boolean reachedTarget(Coord current, Coord target) {
		
		return current.equals(target);
	}

	
	/**
 	 * Method that returns a long representing the milliseconds until the RPC gather 
 	 * cooldown is finished for this rover. If the cooldown is complete returns 0.
	 * 
	 * @return Long : the time until RCP will accept another gather
	 */
	private long gatherCooldownRemaining() {
	
		long remaindingTime =  gatherCooldown - (System.currentTimeMillis() - timeSinceLastGather);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	
	/**
	 * Method that modifies the variable timeSinceLastGather by reseting it to the System.currentTimeMillis().
	 * this method is a simple wrapper that adds readability to the code in state machine.
	 */
	private void resetGatherCooldown() {
		
		timeSinceLastGather = System.currentTimeMillis();
	}
	
	
	/**
 	 * Method that returns a long representing the milliseconds until the RPC move 
 	 * cooldown is finished for this rover. If the cooldown is complete returns 0.
	 * 
	 * @return Long representing the time until RCP will accept another move
	 */
	private long moveCooldownRemaining() {

		long remaindingTime =  moveCooldown - (System.currentTimeMillis() - timeSinceLastMove);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	
	/**
	 * Method that modifies the variable timeSinceLastMove by reseting it to the System.currentTimeMillis().
	 * this method is a simple wrapper that adds readability to the code in state machine.
	 */
	private void resetMoveCooldown() {
		
		timeSinceLastMove = System.currentTimeMillis();
	}

	
	/**
 	 * Method that returns list of edges or "path" from start to target (start -> target)
 	 * If start or target are not inside of the graph returns null.
 	 * If there is no path from start to target returns null.
 	 * 
  	 * @param Graph graph : graph built with the constructor Graph(int , int, Set<Coord>) 
  	 * @param Coord start : start coordinate in the graph
  	 * @param Coord target : target coordinate in the graph
	 * 
	 * @return List<Edge> representing the path, start -> target.
	 */
	private List<Edge> findPath(Graph graph , Coord start, Coord target) {
		
		@SuppressWarnings("rawtypes")
		Node startNode = graph.getNode(new Node<>(start));
		
		@SuppressWarnings("rawtypes")
		Node targetNode = graph.getNode(new Node<>(target));
		
		if (graph.getNode(startNode) == null ||
				graph.getNode(targetNode) == null) return null;
		
		return searchStrategy.search(graph, startNode, targetNode);	
	}
	
	
	/**
	 * Method that modifies the variables sensors, drivableTerrain, and gatherableTerrain. This method
	 * adds the correct elements to those variables from a list of parts.
	 * 
 	 * @param List<String> parts : equipment list received from RCP
	 */
	private void setEquipment(List<String> parts) {

		for(String part: parts) {
			setDrivableTerrain(RoverDriveType.getEnum(part));
			setGatherableTerrain(RoverToolType.getEnum(part));
			setSensors(RoverToolType.getEnum(part));
		}	
	}
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the drivableTerrain class variable by adding elements to it and also sets the correct
	 * moveCooldown for the drive.
	 * 
	 * @param RoverDriveType drive : drive type of the rover
	 */
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
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the gatherableTerrain class variable by adding elements to it.
	 * 
	 * @param RoverDriveType tool : tool type of the rover
	 */
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
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the sensor class variable by adding elements to it.
	 * 
	 * @param RoverDriveType tool : tool type of the rover
	 */
	private void setSensors(RoverToolType tool) {
		// all types can traverse these
		
		switch (tool) {
		
		case CHEMICAL_SENSOR:
			sensors.add("CHEMICAL_SENSOR");
			break;
			
		case RADAR_SENSOR:
			sensors.add("RADAR_SENSOR");
			break;
			
		case RADIATION_SENSOR:
			sensors.add("RADIATION_SENSOR");
			break;
			
		case SPECTRAL_SENSOR:
			sensors.add("SPECTRAL_SENSOR");
			break;
		default:
			break;
		}
	}
	
	
	//TODO rewrite to make more robust, should get you the best next Unkown tile to explore
	/**
 	 * Method that returns the next tile to explore.
	 * 
	 * @return Coord : next tile to explore
	 */
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
	
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that this rover can gather (tiles rover can walk on and has tools to gather that terrain type).
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanGather() {
		
		Set<Coord> canWalkOnWithResources = tilesRoverCanWalkOn();
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
	
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have Terrain of types that this rover can move to that do not have rovers.
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanWalkOn() {
		
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

	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have Terrain of type UNKOWN.
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
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
	
	
	/**
	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have not been scanned by a scanner that this rover possess.
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesSensorsCanScan() {
		
		Set<Coord> tilesSensorsCanScan = new HashSet<>();
		Map<Coord, MapTile> tiles = globalMap.getAllTiles();
		
		Set<String> scannedBy;
		
		Coord coord;
		MapTile tile;
		
		for (Map.Entry<Coord, MapTile> entry : tiles.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			
			scannedBy = tile.getScannedBySensors();
			
			for (String sensor: sensors) {
				
				if(scannedBy.contains(sensor)) {
					
					tilesSensorsCanScan.add(coord);
					break;
				}
			}
		}
		
		return tilesSensorsCanScan;
	}

	
	/**
	 * Method that modifies the MapTiles in a ScanMap by altering the binary
	 * String that represents the sensors that have scanned the MapTiles. The the altered
	 * binary String will indicate that the sensors the rover has scanned the MapTile.
	 * 
	 * @param ScanMap scanMap : ScanMap to modify
	 */
	private void addScannedDataToScanMap(ScanMap scanMap) {
		
		MapTile[][] mapTiles = scanMap.getScanMap();
		
		for(int j = 0; j < mapTiles.length; j++) {
			for(int i = 0; i < mapTiles[0].length; i++) {

				mapTiles[i][j].setScannedBySensor(sensorsToString());
			}
		}	
	}
	
	
	/**
	 * Helper method used in addScannedDataToScanMap(ScanMap) that returns
	 * a binary representation of what sensors this rover has.
	 * 
	 * @return Returns a String of 0's and 1's length 4
	 */
	private String sensorsToString() {
		
		char[] sensorArray = {'0','0','0','0'};
		
		if (sensors.contains("CHEMICAL_SENSOR")) sensorArray[0] = '1';
		if (sensors.contains("RADAR_SENSOR")) sensorArray[1] = '1';
		if (sensors.contains("RADIATION_SENSOR")) sensorArray[2] = '1';
		if (sensors.contains("SPECTRAL_SENSOR")) sensorArray[3] = '1';
		
		System.out.println(new String(sensorArray));
		
		return new String(sensorArray);
	}
	
}