import java.io.*;
import java.util.*;

public class BTree {
    private static final long NONE = 0;

    private static int orderFromBlockSize(int blockSize) {
        return blockSize / 12;
    }

    private RandomAccessFile f;
    private int order;
    private int blockSize;
    private long root;
    private long free;

    private class SplitResult {
        private BTreeNode node;
        private int middle;

        public SplitResult(BTreeNode node, int middle) {
            this.node = node;
            this.middle = middle;
        }
    }

    private class BTreeNode {
        private int count;
        private int[] keys;
        private long[] children;
        private long address;

        public BTreeNode(int count, int[] keys, long[] children) {
            this.count = count;
            this.keys = keys;
            this.children = children;
            address = NONE;
        }

        public BTreeNode(long addr) throws IOException {
            address = addr;
            f.seek(addr);
            count = f.readInt();
            keys = new int[order];
            children = new long[order + 1];
            for (int i = 0; i < order - 1; i++)
                keys[i] = f.readInt();
            for (int i = 0; i < order; i++)
                children[i] = f.readLong();
        }

        private void write(long addr) throws IOException {
            address = addr;
            write();
        }

        private void write() throws IOException {
            f.seek(address);
            f.writeInt(count);
            for (int i = 0; i < order - 1; i++)
                f.writeInt(keys[i]);
            for (int i = 0; i < order; i++)
                f.writeLong(children[i]);
        }

        private int childIdx(int key) {
            int i = 0;
            while (i < order - 1 && key > keys[i])
                i++;
            return i;
        }

        private long sibling() {
            return children[order - 1];
        }

        private boolean hasKey(int key) {
            for (int i = 0; i < Math.abs(count); i++)
                if (keys[i] == key)
                    return true;
            return false;
        }

        private boolean isLeaf() {
            return count < 0;
        }

        private int count() {
            return Math.abs(count);
        }

        private void insertKeyAddr(int key, long val) {
            int branchOffset = !isLeaf() ? 1 : 0;
            int i = 0;
            while (i < count() && key > keys[i])
                i++;

            for (int j = count() - 1; j >= i; j--) {
                keys[j + 1] = keys[j];
                children[j + 1 + branchOffset] = children[j + branchOffset];
            }

            keys[i] = key;
            children[i + branchOffset] = val;

            count = isLeaf() ? -(count() + 1) : count() + 1;
        }

        private BTreeNode splitLeaf() {
            if (!isLeaf())
                return null;

            int al = (int) Math.floor(order / 2.0);
            int bl = (int) Math.ceil(order / 2.0);

            int[] newKeys = new int[order];
            long[] newChildren = new long[order + 1];

            for (int i = 0; i < bl; i++) {
                newKeys[i] = keys[al + i];
                newChildren[i] = children[al + i];
            }

            count = -al;
            BTreeNode split = new BTreeNode(-bl, newKeys, newChildren);
            return split;
        }

        private SplitResult splitBranch() {
            if (isLeaf())
                return null;

            int l = order / 2;
            int al = l;
            int bl = order - l - 1;

            int[] newKeys = new int[order];
            long[] newChildren = new long[order + 1];

            for (int i = 0; i < bl; i++) {
                newKeys[i] = keys[al + 1 + i];
                newChildren[i] = children[al + 1 + i];
            }

            count = al;
            BTreeNode split = new BTreeNode(bl, newKeys, newChildren);
            return new SplitResult(split, keys[l]);
        }

        private void removeKey(int key) {
            int c = count();
            int branchOffset = isLeaf() ? 0 : 1;
            for (int i = 0; i < c; i++) {
                if (keys[i] == key) {
                    for (int j = i; j < c; j++) {
                        keys[j] = keys[j + 1];
                        children[j + branchOffset] = children[j + 1 + branchOffset];
                    }
                    count = isLeaf() ? -(c - 1) : c - 1;
                    return;
                }
            }
        }

        private boolean tooSmall() {
            return count() < Math.ceil(order / 2.0) - 1;
        }
    }

    public BTree(String filename, int bsize) throws IOException {
        // bsize is the block size. This value is used to calculate the order
        // of the B+Tree. all B+Tree nodes will use bsize bytes
        // makes a new B+Tree

        root = NONE;
        free = NONE;
        blockSize = bsize;
        order = orderFromBlockSize(blockSize);

        File file = new File(filename);
        if (file.exists())
            file.delete();

        f = new RandomAccessFile(file, "rw");
        f.writeLong(root);
        f.writeLong(free);
        f.writeInt(blockSize);
    }

    public BTree(String filename) throws IOException {
        // open an existing B+tree
        f = new RandomAccessFile(filename, "rw");
        root = f.readLong();
        free = f.readLong();
        blockSize = f.readInt();
        order = orderFromBlockSize(blockSize);
    }

    private long nextFree() throws IOException {
        if (free == NONE)
            return f.length();

        long cur = free;
        free = new BTreeNode(free).count;
        return cur;
    }

    private void setRoot(long root) throws IOException {
        this.root = root;
        f.seek(0);
        f.writeLong(root);
    }

    public boolean insert(int key, long addr) throws IOException {
        /*
         * If key is not a duplicate add key to the B+tree
         * addr (in DBTable) is the address of the row that contains the key
         * return true if the key is added
         * return false if the key is a duplicate
         */

        if (root == NONE) {
            // need to create a root
            return false;
        }

        boolean split = false;
        Stack<BTreeNode> path = searchPath(key);
        BTreeNode node = path.pop();

        int val = 0;
        long loc = 0;

        if (node.hasKey(key))
            return false;

        if (node.count() < order - 1) {
            // insert key into the node
            node.insertKeyAddr(key, addr);

            // write the node to the file
            node.write();

            // set split to false
            split = false;
        } else {
            node.insertKeyAddr(key, addr);

            // split the values up
            BTreeNode newNode = node.splitLeaf();

            // let val be the smallest value in newnode
            val = newNode.keys[0];

            // write node to the file
            node.write();

            // write newnode into the file
            newNode.write(nextFree());

            // let loc be the address in the file of newnode
            loc = newNode.address;

            // set split to true
            split = true;
        }

        while (!path.empty() && split) {
            node = path.pop();
            if (node.count() < order - 1) {
                // there's room for a new value
                node.insertKeyAddr(val, loc);

                // write the node into the file
                node.write();

                // set split to false
                split = false;
            } else {
                node.insertKeyAddr(val, loc);

                // split the node
                SplitResult splitRes = node.splitBranch();

                // let newnode be the split node
                BTreeNode newNode = splitRes.node;

                // let newVal be the middle value
                int newVal = splitRes.middle;

                // write the nodes
                node.write();
                newNode.write(nextFree());

                // update loc and val
                loc = newNode.address;
                val = newVal;
            }
        }

        if (split) {
            // the root was split
            int[] newKeys = new int[order];
            long[] newChildren = new long[order + 1];
            newChildren[0] = root;
            newKeys[0] = val;
            newChildren[1] = loc;
            BTreeNode newNode = new BTreeNode(1, newKeys, newChildren);
            newNode.write(nextFree());
            setRoot(newNode.address);
        }

        return true;
    }

    public long remove(int key) throws IOException {
        /*
         * If the key is in the Btree, remove the key and return the address of the
         * row
         * return 0 if the key is not found in the B+tree
         */

        boolean tooSmall = false;
        Stack<BTreeNode> path = searchPath(key);
        BTreeNode node = path.pop();
        if (node.hasKey(key)) {
            // remove it
        } else {
            return 0;
        }
    }

    private Stack<BTreeNode> searchPath(int k) throws IOException {
        Stack<BTreeNode> path = new Stack<>();
        if (root == NONE)
            return path;

        BTreeNode cur = new BTreeNode(root);
        path.add(cur);
        while (cur.count > 0) {
            // the current node is a branch
            cur = new BTreeNode(cur.children[cur.childIdx(k)]);
            path.add(cur);
        }

        return path;
    }

    public long search(int k) throws IOException {
        /*
         * This is an equality search
         * If the key is found return the address of the row with the key
         * otherwise return 0
         */

        Stack<BTreeNode> path = searchPath(k);
        if (path.isEmpty())
            return NONE;

        BTreeNode last = path.pop();
        for (int i = 0; i < -last.count; i++)
            if (last.keys[i] == k)
                return last.children[i];

        return NONE;
    }

    public LinkedList<Long> rangeSearch(int low, int high) throws IOException {
        // PRE: low <= high
        /*
         * return a list of row addresses for all keys in the range low to high
         * inclusive
         * return an empty list when no keys are in the range
         * 
         */

        LinkedList<Long> addrs = new LinkedList<>();
        Stack<BTreeNode> path = searchPath(low);
        BTreeNode cur = path.pop();
        while (true) {
            for (int i = 0; i < -cur.count; i++) {
                if (cur.keys[i] >= low || cur.keys[i] <= high) {
                    addrs.add(cur.children[i]);
                } else if (cur.keys[i] > high) {
                    return addrs;
                }
            }

            long sibling = cur.sibling();
            if (sibling != NONE) {
                cur = new BTreeNode(sibling);
            } else {
                return addrs;
            }
        }
    }

    private void printNode(BTreeNode node, int depth) throws IOException {
        for (int i = 0; i < depth; i++)
            System.out.print("  ");

        if (node.count < 0) {
            int count = -node.count;
            System.out.print("- (leaf " + node.address + ") count " + count + ", keys: ");
            for (int i = 0; i < count; i++) {
                System.out.print(node.keys[i]);
                if (i < count - 1)
                    System.out.print(", ");
            }
            System.out.println();
        } else {
            int count = node.count;
            System.out.print("> (branch " + node.address + ") count " + count + ", keys:");
            for (int i = 0; i < count; i++) {
                System.out.print(node.keys[i]);
                if (i < count - 1)
                    System.out.print(", ");
            }
            System.out.println();
            for (int i = 0; i < count + 1; i++)
                printNode(new BTreeNode(node.children[i]), depth + 1);
        }
    }

    public void print() throws IOException {
        // print the B+Tree to standard output
        // print one node per line
        // This method can be helpful for debugging
        if (root != NONE) {
            printNode(new BTreeNode(root), 0);
        }
    }

    public void close() throws IOException {
        // close the B+Tree. the tree should not be accessed after close is called
        f.close();
    }
}
