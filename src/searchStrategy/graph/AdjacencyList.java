package searchStrategy.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 
 * @author luisfisherjr
 * 
 * AdjacencyList used inside Graph class
 */


public class AdjacencyList {

    private Map<Node, Collection<Edge>> adjacencyList;
    ArrayList<Node> indexedNodes;

    public AdjacencyList() {

        indexedNodes = new ArrayList<Node>();
        adjacencyList = new HashMap<Node, Collection<Edge>>();
    }

    /**
    * Return true if there is an edge connecting x to y,  x -> y
    */

    public boolean adjacent(Node x, Node y) {

        ArrayList<Edge> edges = (ArrayList)adjacencyList.get(x);

        if (edges == null) return false;

        for(Edge e: edges) {
            if (e.getTo().equals(y)) return true;
        }

        return false;
    }
    
    /**
    * true a List of all nodes a leaving,  x -> a
    */
    public List<Node> neighbors(Node x) {

        ArrayList<Edge> edges = (ArrayList)adjacencyList.get(x);

        if (edges == null) return null;

        ArrayList<Node> neighbors = new ArrayList<Node>();

        for(Edge e: edges) {
            neighbors.add(e.getTo());
        }

        return neighbors;
    }

    /**
    * returns true if a NEW node is added to  adjacencyList
    */
    public boolean addNode(Node x) {

        if (adjacencyList.containsKey(x)) return false;

        indexedNodes.add(x);
        adjacencyList.put(x, new ArrayList<Edge>());

        return true;
    }

    /**
    * returns true if any node in x is added to adjacencyList
    */
    public boolean addAllNode(List<Node> x) {

        boolean added = false;

        for (Node n: x) {
            if (!adjacencyList.containsKey(n))  {
                adjacencyList.put(n, new ArrayList<Edge>());
                indexedNodes.add(n);
                added = true;
            }
        }

        return added;
    }

    
    /**
    * returns true if node is in graph and removes from adjacency list node and edges connected to the node 
    */
    public boolean removeNode(Node x) {

        if (!adjacencyList.containsKey(x)) return false;

        Node[] keys;
        ArrayList<Edge> edges;

        /* remove all the from x edges */
        adjacencyList.remove(x);
        indexedNodes.remove(x);

        keys = new Node[adjacencyList.size()];

        adjacencyList.keySet().toArray(keys);

        /* remove all the to x edges */
        for(Node n: keys) {

            edges = (ArrayList<Edge>) adjacencyList.get(n);

            /* iterates in reverse order to avoid null point exceptions, and keep runtime low*/
            for(int index = edges.size() - 1; index > -1; index--) {

                if (edges.get(index).getTo().equals(x)) edges.remove(index);
            }
        }

        return true;
    }
    
    /**
    * returns true and adds edge if edge doesn't exist and nodes are in adjacency list
    */
    public boolean addEdge(Edge x) {

        if (!adjacencyList.containsKey(x.getFrom()) ||
                !adjacencyList.containsKey(x.getTo())) return false;

        for(Edge e: adjacencyList.get(x.getFrom())) {
            if (e.equals(x)) return false;
        }

        adjacencyList.get(x.getFrom()).add(x);

        return true;
    }
    
    /**
    * returns true if any edge in xs is added to adjacencyList
    */
    public boolean addAllEdges(List<Edge> xs) {
    	
    	 boolean added = false;
    	
    	for(Edge x: xs) {
    		if (addEdge(x)) added = true;
    	}
  
        return added;
    }

    /**
    * returns true if edge is in graph and removes from adjacency list node and edges connected to the node 
    */
    public boolean removeEdge(Edge x) {

        if (!adjacencyList.containsKey(x.getFrom())) return false;
        if (!adjacencyList.get(x.getFrom()).contains(x)) return false;

        adjacencyList.get(x.getFrom()).remove(x);

        return true;
    }

    /**
    * returns the value of the edge connecting the from to to nodes or 0 if no connection
    */
    public int distance(Node from, Node to) {

        if (!adjacencyList.containsKey(from)) return 0;

        ArrayList<Edge> edges = (ArrayList)adjacencyList.get(from);

        for(int index = 0; index < edges.size(); index++) {
        if (edges.get(index).getTo().equals(to)) return edges.get(index).getValue();
        }
        return 0;
    }


    /**
    * returns all nodes
    */
    public Collection<Node> getNodes() {
        return adjacencyList.keySet();
    }

    /**
    * returns Optional that can retrieve node data
    */
    public Optional<Node> getNode(int index) {

        Node n = indexedNodes.get(index);

        return Optional.of(n);
    }

    public Optional<Node> getNode(Node node) {
        Iterator<Node> iterator = adjacencyList.keySet().iterator();
        Optional<Node> result = Optional.empty();
        while (iterator.hasNext()) {
            Node next = iterator.next();
            if (next.equals(node)) {
                result = Optional.of(next);
            }
        }
        return result;
    }
}