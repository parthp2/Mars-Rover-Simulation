package searchStrategy.graph;

/**
 * 
 * @author luisfisherjr
 * 
 * holds Coord data and MapTile data used with Node class
 */

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.PlanetMap;
import enums.Terrain;

public class NodeData {
    private final int x;
    private final int y;
    private final Terrain type;
    private final boolean isOccupied;

    public NodeData(int x, int y, Terrain type, boolean isOccupied) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.isOccupied = isOccupied;
    }
    
    public NodeData(Coord coord, MapTile mapTile) {
        this.x = coord.xpos;
        this.y = coord.ypos;
        this.type = mapTile.getTerrain();
        this.isOccupied = mapTile.getHasRover();
    }
    
    public NodeData(PlanetMap planet, int x, int y) {
        this.x = x;
        this.y = y;
        this.type = planet.getTile(x, y).getTerrain();
        this.isOccupied = planet.getTile(x, y).getHasRover();
    }


    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
    
    public boolean occupied() {
    	return this.isOccupied;
    }

    public String getType() {
        return type.getTerString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeData)) return false;

        NodeData nd = (NodeData) o;

        if (getX() != nd.getX()) return false;
        if (getY() != nd.getY()) return false;
        if (getType() != nd.getType()) return false;
        if (occupied() != nd.occupied()) return false;
        
        return true;
    }

    @Override
    public int hashCode() {
        return (getX() * 10000) + getY() + getType().hashCode();
    }
    
    @Override
    public String toString() {
    	return x +"," + y + " " + "occupied: " + occupied() + " type: "+ getType() ;
    }
}