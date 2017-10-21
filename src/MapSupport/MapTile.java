package MapSupport;

import java.util.HashSet;
import java.util.Set;

import enums.RoverToolType;
import enums.Science;
import enums.Terrain;

public class MapTile {
	private Terrain terrain;
	public int elevation = 0;	// not currently used
	public int count = 0;  //undefined usage, possibly use on ScanMap for tracking visits
	private Science science;	//for use on ScanMap, not used on PlanetMap
	private boolean hasRover;	//for use on ScanMap, not used on PlanetMap
	private String scannedBy = null; //for keeping track of which rover first discovered this tile
	String scannedBySensor = "0000"; // digits from left to right are Chemical,Radar,Radiation,Spectral
	
	public MapTile(){
		this.terrain = Terrain.SOIL;
		this.science = Science.NONE;
		this.hasRover = false;
	}
	
	public MapTile(int notUsed){
		// use any integer as an argument to create MapTile with no terrain
		this.terrain = Terrain.UNKNOWN;
		this.science = Science.NONE;
		this.hasRover = false;
	}
	
	public MapTile(String terrainLetter){
		// use appropriate string to create MapTile with matching terrain
		this.terrain = Terrain.getEnum(terrainLetter);
		this.science = Science.NONE;
		this.hasRover = false;
	}
	
	public MapTile(Terrain ter, int elev){
		this.terrain = ter;
		this.science = Science.NONE;
		this.elevation = elev;
		this.hasRover = false;
	}
	
	public MapTile(Terrain ter, Science sci, boolean hasR){
		this.terrain = ter;
		this.science = sci;
		this.hasRover = hasR;
	}
	
	public MapTile(Terrain ter, Science sci, int elev, boolean hasR){
		this.terrain = ter;
		this.science = sci;
		this.elevation = elev;
		this.hasRover = hasR;
	}
	
	public MapTile(Terrain ter, Science sci, int elev, boolean hasR, String scanBy, int cnt){
		this.terrain = ter;
		this.science = sci;
		this.elevation = elev;
		this.hasRover = hasR;
		this.count = cnt;
		this.scannedBy = scanBy;
	}
	
	public MapTile getCopyOfMapTile(){
		MapTile rTile = new MapTile(this.terrain, this.science, this.elevation, this.hasRover, this.scannedBy, this.count);	
		return rTile;
	}

	// No setters in this class to make it thread safe
	
	public Terrain getTerrain() {
		return this.terrain;
	}

	public Science getScience() {
		return this.science;
	}

	public int getElevation() {
		return this.elevation;
	}
	
	public boolean getHasRover() {
		return this.hasRover;
	}
	
	public String getScannedBySensorValue() {
		return this.scannedBySensor;
	}
	
	public void setScannedBySensor(String sensors) {
		
		char[] sensorsToAdd = sensors.toCharArray();
		char[] currentSensors = scannedBySensor.toCharArray();
		
		// checks for correct length
		if (sensorsToAdd.length != 4) return;
		
		// checks for correct format
		for(char c: sensorsToAdd) {
			if (!(c == '0' || c == '1')) return;
		}
		
		for(int i = 0; i < scannedBySensor.length(); i++) {
			if (sensorsToAdd[i] == '1') currentSensors[i] = '1';
		}
		
		scannedBySensor = currentSensors.toString();
	}
	
	public Set<RoverToolType> getScannedBySensors() {
		
		Set<RoverToolType> sensors = new HashSet<RoverToolType>();
		
		char[] values = this.scannedBySensor.toCharArray();
		
		// order is Chemical,Radar,Radiation,Spectral
		if (values[0] == '1') sensors.add(RoverToolType.CHEMICAL_SENSOR);
		if (values[1] == '1') sensors.add(RoverToolType.RADAR_SENSOR);
		if (values[2] == '1') sensors.add(RoverToolType.RADIATION_SENSOR);
		if (values[3] == '1') sensors.add(RoverToolType.SPECTRAL_SENSOR);
		
		return sensors;
	}
	
	// well, this might have broke the thread safe rule
	
	public void setHasRoverTrue(){
		this.hasRover = true;
	}
	
	public void setHasRoverFalse(){
		this.hasRover = false;
	}
	
	public void setScience(Science sci){
		this.science = sci;
	}
	
	public boolean setScannedBy(String roverName) {
		if(this.scannedBy == null){
			this.scannedBy = roverName;
			return true;
		} else {
			return false;
		}
	}
}
