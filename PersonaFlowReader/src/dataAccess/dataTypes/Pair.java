package dataAccess.dataTypes;

public class Pair<E,K> {
    public E first;
    public K second;

    public Pair(E first, K second) {
        this.first = first;
        this.second = second;
    }
}
