package ru.itmo.ctlab.sgmwcs.graph;

import java.util.*;

public class Graph {
    private Map<Edge, Link> links;
    private Map<Node, Map<Node, LinksList>> connected;
    private Map<Node, LinksList> adj;
    private Map<Node, Integer> degree;

    public Graph() {
        links = new LinkedHashMap<>();
        adj = new LinkedHashMap<>();
        connected = new HashMap<>();
        degree = new HashMap<>();
    }

    public Graph(Graph that) {
        this();
        Map<Node, Node> oldToNew = new HashMap<>();
        that.vertexSet().forEach(v -> {
            Node newV = new Node(v);
            addVertex(newV);
            oldToNew.put(v, newV);
        });
        that.edgeSet().forEach(e -> addEdge(
                oldToNew.get(that.getEdgeSource(e)),
                oldToNew.get(that.getEdgeTarget(e)),
                new Edge(e))
        );
    }

    public void addVertex(Node v) {
        if (adj.containsKey(v)) {
            throw new IllegalArgumentException();
        }
        adj.put(v, new LinksList());
        connected.put(v, new LinkedHashMap<>());
        degree.put(v, 0);
    }

    public void addEdge(Node v, Node u, Edge e) {
        if (links.containsKey(e)) {
            throw new IllegalArgumentException();
        }
        Link link = new Link(v, u, e);
        links.put(e, link);
        adj.get(v).add(link);
        adj.get(u).add(link);
        addToConnected(v, u, link);
        addToConnected(u, v, link);
        degree.put(v, degree.get(v) + 1);
        degree.put(u, degree.get(u) + 1);

    }

    public Set<Edge> edgesOf(Node v) {
        Set<Edge> res = new LinkedHashSet<>();
        for (Link l : adj.get(v)) {
            res.add(l.e);
        }
        return res;
    }

    private void addToConnected(Node v, Node u, Link l) {
        Map<Node, LinksList> m = connected.get(v);
        if (!m.containsKey(u)) {
            m.put(u, new LinksList());
        }
        m.get(u).add(l);
    }

    public Node getOppositeVertex(Node v, Edge e) {
        return getOppositeVertex(v, links.get(e));
    }

    public boolean containsEdge(Edge e) {
        return links.containsKey(e);
    }

    private Node getOppositeVertex(Node v, Link l) {
        if (l.v.equals(v)) {
            return l.u;
        }
        if (l.u.equals(v)) {
            return l.v;
        }
        throw new IllegalArgumentException();
    }

    public void removeVertex(Node v) {
        List<Node> neighbors = neighborListOf(v);
        for (Edge e : edgesOf(v)) {
            removeEdge(e);
        }
        for (Node u : neighbors) {
            connected.get(u).remove(v);
        }
        adj.remove(v);
        connected.remove(v);
    }

    public List<Edge> getAllEdges(Node v, Node u) {
        List<Edge> res = new ArrayList<>();
        for (Link l : connected.get(v).get(u)) {
            res.add(l.e);
        }
        return res;
    }

    public Edge getEdge(Node v, Node u) {
        Map<Node, LinksList> vadj = connected.get(v);
        if (vadj == null) {
            throw new IllegalArgumentException();
        }
        LinksList edges = connected.get(v).get(u);
        if (edges == null) {
            return null;
        }
        Iterator<Link> it = edges.iterator();
        return it.hasNext() ? it.next().e : null;
    }

    public List<Node> neighborListOf(Node v) {
        Set<Node> res = new LinkedHashSet<>();
        for (Link l : adj.get(v)) {
            res.add(getOppositeVertex(v, l));
        }
        return new ArrayList<>(res);
    }

    public void removeEdge(Edge e) {
        Link l = links.get(e);
        links.remove(e);
        l.removed = true;
        degree.put(l.v, degree.get(l.v) - 1);
        degree.put(l.u, degree.get(l.u) - 1);
    }

    public Set<Node> vertexSet() {
        return Collections.unmodifiableSet(adj.keySet());
    }

    public Set<Edge> edgeSet() {
        return Collections.unmodifiableSet(links.keySet());
    }

    public Node getEdgeSource(Edge e) {
        Link l = links.get(e);
        return l.v;
    }

    public Node getEdgeTarget(Edge e) {
        Link l = links.get(e);
        return l.u;
    }

    public List<Node> disjointVertices(Edge e) {
        Link l = links.get(e);
        return Arrays.asList(l.u, l.v);
    }

    public Graph subgraph(Set<Node> nodes) {
        Set<Edge> edges = new LinkedHashSet<>();
        for (Node v : nodes) {
            for (Node u : neighborListOf(v)) {
                if (nodes.contains(u)) {
                    edges.addAll(getAllEdges(v, u));
                }
            }
        }
        return subgraph(nodes, edges);
    }

    public Graph edgesSubgraph(Set<Edge> edges) {
        Set<Node> nodes = new HashSet<>();
        for (Edge e : edges) {
            nodes.add(getEdgeSource(e));
            nodes.add(getEdgeTarget(e));
        }
        return subgraph(nodes, edges);
    }


    public Graph subgraph(Set<Node> nodes, Set<Edge> edges) {
        Graph res = new Graph();
        nodes.forEach(res::addVertex);
        for (Edge e : edges) {
            if (containsEdge(e)) {
                res.addEdge(getEdgeSource(e), getEdgeTarget(e), e);
            }
        }
        return res;
    }

    public List<Set<Node>> connectedSets() {
        List<Set<Node>> res = new ArrayList<>();
        Set<Node> vis = new HashSet<>();
        vertexSet().stream().filter(v -> !vis.contains(v)).forEach(v -> {
            Set<Node> curr = new LinkedHashSet<>();
            dfs(v, curr);
            res.add(curr);
            vis.addAll(curr);
        });
        return res;
    }

    private void dfs(Node v, Set<Node> vis) {
        vis.add(v);
        for (Link l : adj.get(v)) {
            Node u = getOppositeVertex(v, l);
            if (!vis.contains(u)) {
                dfs(u, vis);
            }
        }
    }

    public int degreeOf(Node v) {
        return degree.get(v);
    }

    public boolean containsVertex(Node v) {
        return adj.containsKey(v);
    }

    private static class LinksList implements Iterable<Link> {
        private List<Link> l;

        public LinksList() {
            l = new LinkedList<>();
        }

        public void add(Link link) {
            l.add(link);
        }

        @Override
        public Iterator<Link> iterator() {
            return new RemovingIterator(l.iterator());
        }
    }

    private static class RemovingIterator implements Iterator<Link> {
        private Iterator<Link> it;
        private Link next;

        public RemovingIterator(Iterator<Link> it) {
            this.it = it;
            step();
        }

        private void step() {
            next = null;
            while (it.hasNext()) {
                Link l = it.next();
                if (l.removed) {
                    it.remove();
                } else {
                    next = l;
                    return;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Link next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            Link res = next;
            step();
            return res;
        }
    }


    private static class Link {
        public Edge e;
        public Node v;
        public Node u;
        public boolean removed;

        public Link(Node v, Node u, Edge e) {
            this.v = v;
            this.u = u;
            this.e = e;
        }
    }
}
