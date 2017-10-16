package searchStrategy.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import MapSupport.PlanetMap;
import searchStrategy.SearchStrategy;

public class Graph {
    private final AdjacencyList graph;

    public Graph(AdjacencyList graph) {
        this.graph = graph;
    }
    
    public Graph(PlanetMap map) {
    	
    	graph = new AdjacencyList();
    	List<Node> nodes =  new ArrayList<Node>();
    	List<Edge> edges = new ArrayList<Edge>();
    	
    	Node node;
    	Edge edge;
    	List<Node> neighbors;
    	
    	for (int j = 0; j < map.getHeight(); j++) {
    		for (int i = 0; i < map.getWidth(); i++) {
        		
    			node = new Node<NodeData>(new NodeData(map, i, j));
        		nodes.add(node);
        		
        		neighbors = new ArrayList<Node>();
        		
        		if( i > 0) neighbors.add(new Node<NodeData>(new NodeData(map, i - 1, j)));
        		if( i < map.getWidth() - 1) neighbors.add(new Node<NodeData>(new NodeData(map, i + 1, j)));
        		if( j > 0) neighbors.add(new Node<NodeData>(new NodeData(map, i, j - 1)));
        		if( j < map.getHeight() - 1) neighbors.add(new Node<NodeData>(new NodeData(map, i, j + 1)));
        		
        		for(Node neighbor: neighbors) {
        			edges.add(new Edge(node, neighbor, 1));
        		}  		
        	}
    	}
    	
    	graph.addAllNode(nodes);
		graph.addAllEdges(edges);
    }

    /**
     * Return true if node x is connecting to y false otherwise
     */
    public boolean adjacent(Node x, Node y) {
        return graph.adjacent(x, y);
    }

    /**
     * Return all neighbor nodes (that has at least one edge connected from node x)
     */
    public List<Node> neighbors(Node x) {
        return graph.neighbors(x);
    }

    /**
     * Add a node to graph
     *
     * Return false if node is already in graph, true if node is added to graph
     * successfully
     */
    public boolean addNode(Node x) {
        return graph.addNode(x);
    }

    /**
     * Remove a node to graph (note that you also need to remove edge if there
     * is any edge connecting to/from this node)
     *
     * Return true if the node is removed successfully, false if the node
     * doesn't exist in graph
     */
    public boolean removeNode(Node x) {
        return graph.removeNode(x);
    }

    /**
     * Add an edge to graph (connecting two nodes)
     *
     * Return true if the edge is added successfully, return false if the edge
     * already exists in graph
     */
    public boolean addEdge(Edge x) {
        return graph.addEdge(x);
    }

    /**
     * Remove an edge from graph (remember not to remove node)
     *
     * Return true if edge is removed successfully, return false if the edge is
     * not presented in graph
     */
    public boolean removeEdge(Edge x) {
        return graph.removeEdge(x);
    }

    /**
     * Get edge value between from node to to node
     */
    public int distance(Node from, Node to) {
        return graph.distance(from, to);
    }

    /**
     * A simple method to get a node out of graph
     */
    public Optional<Node> getNode(int index) {
        return graph.getNode(index);
    }

    /**
     * A simple method to get all nodes out of graph
     */
    public Collection<Node> getNodes() {
        return graph.getNodes();
    }

    /**
     * A simple method to get a node that is equal to current node inside a graph
     */
    public Optional<Node> getNode(Node node) {
        return graph.getNode(node);
    }

    /**
     * Search through this graph from sourceNode to distNode and return a list
     * of edges in between
     */
    public List<Edge> search(SearchStrategy strategy, Set<String> drivableTerrain, Node source, Node dist) {
        return strategy.search(this, drivableTerrain, source, dist);
    }

    /**
     * A simple method to add all nodes that are not in the list to a graph
     */
    public boolean addAllNode(List<Node> nodes) {
        return graph.addAllNode(nodes);
    }

    /**
     * A simple method to string method
     */
    @Override
    public String toString() {
        return graph.toString();
    }
}