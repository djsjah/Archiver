package common.model;

public class FenwickTree {
    private final int[] tree;

    public FenwickTree(int size) {
        this.tree = new int[size + 1];
    }

    public void add(int index, int delta) {
        for (int i = index + 1; i < tree.length; i += i & -i) {
            tree[i] += delta;
        }
    }

    public int prefixSum(int exclusiveRight) {
        int sum = 0;
        for (int i = exclusiveRight; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    public int findByCumulative(int cumulative) {
        int target = cumulative + 1;
        int index = 0;
        int step = Integer.highestOneBit(tree.length - 1);

        while (step != 0) {
            int next = index + step;
            if (next < tree.length && tree[next] < target) {
                target -= tree[next];
                index = next;
            }
            step >>= 1;
        }

        return index;
    }
}
