package searchStrategy;


import java.util.*;

import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;
import searchStrategy.graph.NodeComparator;
import searchStrategy.graph.NodeData;

/**
 * 
 * @author luisfisherjr
 * 
 * A* search implementation using SearchStrategy as interface
 */

public class AstarSearch implements SearchStrategy{

    // D is a scale value for you to adjust performance vs accuracy
    private final double D = 0.5;

    public List<Edge> search(Graph graph, Set<String> drivableTerrain, Node source, Node destination) {

        Queue<Node> frontier = new PriorityQueue<>(new NodeComparator());
        Set<Node> exploredSet = new HashSet<Node>();

        Node parent = null;

        Collection<Node> nodeCollection = graph.getNodes();

        // initialize g, h, parent for all nodes
        for(Node node: nodeCollection) {

            node.parent = null;
            node.g = Double.POSITIVE_INFINITY;
            node.h = heuristicManhatten(node, destination);
//            node.h = heuristicEuclidean(node, destination);
            
            NodeData nd = (NodeData)node.getData();

            // set source g value
            if (node.equals(source)) {
            	parent = node;
                node.g = 0;
            }
            
            // remove occupied nodes that are not current and target from path
            if (nd.occupied() && !node.equals(source) && !node.equals(destination)) {
            	exploredSet.add(node); // remove occupied nodes from search
            }     
 
        }

        // add to frontier
        frontier.offer(parent);
       
        double tempGScore;

        while(!frontier.isEmpty()) {

            // remove node from frontier and add to explored set
            parent = frontier.poll();
            exploredSet.add(parent);

            // return path found
            if (parent.equals(destination)) {
                return constructPath(graph, parent);
            }

            for(Node child: graph.neighbors(parent)) {

                // skips previously explored paths
                if (exploredSet.contains(child)) {
                    continue;
                }

                NodeData childData = (NodeData) child.getData();
                String data = childData.getType();

                // if current child is not drivable terrain add to explored set
                if (!drivableTerrain.contains(data)) {
                    exploredSet.add(child);
                    continue;
                }

                tempGScore = parent.g + graph.distance(parent, child); // added penalty for having a rover on it acts like a wall

                if (tempGScore >= child.g) {
                    continue;// skip because we are at the worse path
                }
                else if (!frontier.contains(child)) {
                    child.parent = parent;
                    child.g = tempGScore;
                    frontier.offer(child);
                }
            }
        }
        return null;
    }

    // uses manhatten distance
    private double heuristicManhatten(Node node, Node destination) {

        NodeData nodeData = (NodeData) node.getData();
        NodeData destinationData = (NodeData) destination.getData();

        int dx = Math.abs(nodeData.getX() - destinationData.getX());
        int dy = Math.abs(nodeData.getY() - destinationData.getY());
        
        return D * dx + dy;
    }

    private List<Edge> constructPath(Graph graph, Node destination) {

        List<Edge> answer = new ArrayList<Edge>();
        Node node = destination;

        while (node.parent != null) {

            answer.add(new Edge(node.parent, node,
                    graph.distance(node.parent, node)));

            node = node.parent;
        }

        Collections.reverse(answer);
 
        return answer;
    }
}