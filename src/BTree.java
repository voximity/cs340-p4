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
            keys = new int[order - 1];
            children = new long[order];
            for (int i = 0; i < keys.length; i++)
                keys[i] = f.readInt();
            for (int i = 0; i < children.length; i++)
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
            return children[children.length - 1];
        }

        private boolean hasKey(int key) {
            for (int i = 0; i < Math.abs(count); i++)
                if (keys[i] == key)
                    return true;
            return false;
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

        Stack<BTreeNode> path = searchPath(key);
    }

    public long remove(int key) {
        /*
         * If the key is in the Btree, remove the key and return the address of the
         * row
         * return 0 if the key is not found in the B+tree
         */
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
