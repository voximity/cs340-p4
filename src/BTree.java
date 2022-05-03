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

    boolean debug = false;

    private int minKeys() {
        return (int) Math.ceil(order / 2.0) - 1;
    }

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
            int c = count();
            int i = 0;
            while (i < c && key >= keys[i])
                i++;
            return i;
        }

        private long sibling() {
            return children[order - 1];
        }

        private void setSibling(long to) {
            children[order - 1] = to;
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
            while (i < count() && key >= keys[i])
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
            newChildren[bl] = children[order];

            count = al;
            BTreeNode split = new BTreeNode(bl, newKeys, newChildren);
            return new SplitResult(split, keys[l]);
        }

        private long removeKey(int key) {
            if (!isLeaf())
                return NONE;

            int c = count();
            for (int i = 0; i < c; i++) {
                if (keys[i] == key) {
                    long val = children[i];
                    for (int j = i; j < c; j++) {
                        keys[j] = keys[j + 1];
                        children[j] = children[j + 1];
                    }
                    count = -(c - 1);
                    return val;
                }
            }

            return NONE;
        }

        private int getKeyForChild(long addr) {
            if (isLeaf())
                return Integer.MIN_VALUE;

            int i;
            for (i = 1; i <= count(); i++) {
                if (children[i] == addr) {
                    return keys[i - 1];
                }
            }

            return Integer.MIN_VALUE;
        }

        private void removeKeyLeftOf(long addr) {
            if (isLeaf())
                return;

            int i;
            for (i = 1; i <= count(); i++) {
                if (children[i] == addr) {
                    break;
                }
            }

            for (int j = i; j <= count(); j++) {
                keys[j - 1] = keys[j];
                children[j] = children[j + 1];
            }

            count -= 1;
        }

        private void removeKeyRightOf(long addr) {
            if (isLeaf())
                return;

            int i;
            for (i = 0; i < count(); i++) {
                if (children[i] == addr) {
                    break;
                }
            }

            for (int j = i; j < count(); j++) {
                keys[j] = keys[j + 1];
                children[j] = children[j + 1];
            }

            count -= 1;
        }

        private boolean tooSmall() {
            return root == address ? false : count() < minKeys();
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
        free = new BTreeNode(free).children[0];
        return cur;
    }

    private void setRoot(long root) throws IOException {
        this.root = root;
        f.seek(0);
        f.writeLong(root);
    }

    private void addToFree(BTreeNode node) throws IOException {
        node.children[0] = free;
        free = node.address;
        node.write();
        f.seek(8);
        f.writeLong(free);
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
            int[] keys = new int[order];
            long[] children = new long[order + 1];
            keys[0] = key;
            children[0] = addr;
            BTreeNode node = new BTreeNode(-1, keys, children);
            node.write(nextFree());
            setRoot(node.address);
            return true;
        }

        boolean split = false;
        Stack<BTreeNode> path = searchPath(key);
        BTreeNode node = path.pop();
        if (debug)
            System.out.println("Attempting to insert into node @ " + node.address);

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
            long oldSibling = node.sibling();

            node.insertKeyAddr(key, addr);

            // split the values up
            BTreeNode newNode = node.splitLeaf();

            // update sibling relationship
            newNode.setSibling(oldSibling);

            // let val be the smallest value in newnode
            val = newNode.keys[0];

            // write newnode into the file
            newNode.write(nextFree());

            // set left sibling to right node
            node.setSibling(newNode.address);

            // write node to the file
            node.write();

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

    private void borrowFromRight(BTreeNode borrowTo, BTreeNode borrowFrom, BTreeNode parent) throws IOException {
        if (borrowTo.isLeaf()) {
            if (debug)
                System.out.println("Leaf borrowing from right");
            // borrowing between leaves

            // grab the first child from borrowFrom
            int firstKey = borrowFrom.keys[0];
            long firstChild = borrowFrom.children[0];
            for (int i = 0; i < borrowFrom.count() - 1; i++) {
                borrowFrom.keys[i] = borrowFrom.keys[i + 1];
                borrowFrom.children[i] = borrowFrom.children[i + 1];
            }
            borrowFrom.count = -(borrowFrom.count() - 1);

            // put it into borrowTo
            borrowTo.insertKeyAddr(firstKey, firstChild);

            // update borrowFrom in parent
            for (int i = 1; i <= parent.count; i++) {
                if (parent.children[i] == borrowFrom.address) {
                    parent.keys[i - 1] = borrowFrom.keys[0];
                    break;
                }
            }

            borrowFrom.write();
            borrowTo.write();
            parent.write();
        } else {
            if (debug)
                System.out.println("Branch borrowing from right");
            // borrowing between branches

            long firstChild = borrowFrom.children[0];
            for (int i = 0; i < borrowFrom.count() - 1; i++) {
                borrowFrom.keys[i] = borrowFrom.keys[i + 1];
                borrowFrom.children[i] = borrowFrom.children[i + 1];
            }

            BTreeNode firstChildNode = new BTreeNode(firstChild);

            borrowTo.insertKeyAddr(firstChildNode.keys[0], firstChild);

            // update borrowFrom in the parent
            for (int i = 1; i <= parent.count; i++) {
                if (parent.children[i] == borrowFrom.address) {
                    parent.keys[i - 1] = borrowFrom.keys[0];
                    break;
                }
            }

            borrowFrom.write();
            borrowTo.write();
            parent.write();
        }
    }

    private void borrowFromLeft(BTreeNode borrowTo, BTreeNode borrowFrom, BTreeNode parent) throws IOException {
        if (borrowTo.isLeaf()) {
            if (debug)
                System.out.println("Leaf borrowing from left");
            // borrowing between leaves

            // grab the last child from borrowFrom
            int lastKey = borrowFrom.keys[borrowFrom.count() - 1];
            long lastChild = borrowFrom.children[borrowFrom.count() - 1];
            borrowFrom.count = -(borrowFrom.count() - 1);

            // put it into borrowTo
            borrowTo.insertKeyAddr(lastKey, lastChild);

            // update it in parent
            for (int i = 1; i <= parent.count(); i++) {
                if (parent.children[i] == borrowTo.address) {
                    parent.keys[i - 1] = lastKey;
                    break;
                }
            }

            borrowFrom.write();
            borrowTo.write();
            parent.write();
        } else {
            if (debug)
                System.out.println("Branch borrowing from left");
            // borrowing between branches

            long lastChild = borrowFrom.children[borrowFrom.count()];
            borrowFrom.count -= 1;

            for (int i = borrowTo.keys.length - 2; i >= 0; i--) {
                borrowTo.keys[i + 1] = borrowTo.keys[i];
            }
            for (int i = borrowTo.children.length - 2; i >= 0; i--) {
                borrowTo.children[i + 1] = borrowTo.children[i];
            }

            borrowTo.keys[0] = new BTreeNode(borrowTo.children[1]).keys[0];
            borrowTo.children[0] = lastChild;
            borrowTo.count += 1;

            for (int i = 1; i <= parent.count(); i++) {
                if (parent.children[i] == borrowTo.address) {
                    parent.keys[i - 1] = borrowTo.keys[0];
                    break;
                }
            }

            borrowFrom.write();
            borrowTo.write();
            parent.write();
        }
    }

    private void mergeIntoLeft(BTreeNode source, BTreeNode dest, BTreeNode parent) throws IOException {
        if (source.isLeaf()) {
            if (debug)
                System.out.println("Leaf merging into left");

            dest.setSibling(source.sibling());

            // insert all source keys into the destination
            for (int i = 0; i < source.count(); i++) {
                dest.insertKeyAddr(source.keys[i], source.children[i]);
            }

            // remove source from the parent
            parent.removeKeyLeftOf(source.address);

            // add source to the free list
            addToFree(source);

            // write out dest and parent
            dest.write();
            parent.write();
        } else {
            if (debug)
                System.out.println("Branch merging into left");

            // insert phantom key
            int key = parent.getKeyForChild(source.address);
            dest.insertKeyAddr(key, source.children[0]);

            // insert other keys
            for (int i = 1; i <= source.count(); i++) {
                dest.insertKeyAddr(source.keys[i - 1], source.children[i]);
            }

            // remove source from the parent
            parent.removeKeyLeftOf(source.address);

            // add source to the free list
            addToFree(source);

            // write out dest and parent
            dest.write();
            parent.write();
        }
    }

    public long remove(int key) throws IOException {
        /*
         * If the key is in the Btree, remove the key and return the address of the
         * row
         * return 0 if the key is not found in the B+tree
         */

        long removedAddr = NONE;
        boolean tooSmall = false;
        Stack<BTreeNode> path = searchPath(key);
        BTreeNode node = path.pop();
        if (node.hasKey(key)) {
            // remove it
            removedAddr = node.removeKey(key);
            node.write();

            if (root == node.address && node.count == 0) {
                addToFree(node);
                setRoot(NONE);
            }

            // if the node is too small set tooSmall to true
            if (node.tooSmall()) {
                tooSmall = true;
            }
        } else {
            return NONE;
        }

        while (!path.empty() && tooSmall) {
            BTreeNode child = node;
            node = path.pop();

            // check neighbors of child
            // `node` is guaranteed to be a branch
            int i;
            for (i = 0; i <= node.count(); i++)
                if (node.children[i] == child.address)
                    break;

            BTreeNode borrowFrom = null;
            if (i > 0) {
                BTreeNode leftN = new BTreeNode(node.children[i - 1]);
                if (leftN.count() > minKeys()) {
                    borrowFrom = leftN;
                }
            }
            if (i < node.count()) {
                BTreeNode rightN = new BTreeNode(node.children[i + 1]);
                if (rightN.count() > minKeys()) {
                    borrowFrom = rightN;
                }
            }

            if (borrowFrom != null) {
                // perform the borrow
                if (borrowFrom.keys[0] < child.keys[0]) {
                    borrowFromLeft(child, borrowFrom, node);
                } else {
                    // borrowing from right neighbor
                    borrowFromRight(child, borrowFrom, node);
                }
                tooSmall = false;
            } else {
                // combine
                if (i > 0) {
                    // merge with left node
                    mergeIntoLeft(child, new BTreeNode(node.children[i - 1]), node);
                } else if (i < node.count()) {
                    // merge with right node
                    mergeIntoLeft(new BTreeNode(node.children[i + 1]), child, node);
                }

                if (node.count() >= minKeys()) {
                    tooSmall = false;
                }
            }
        }

        if (tooSmall) {
            // root has been split
            BTreeNode rootNode = new BTreeNode(root);
            setRoot(rootNode.children[0]);
            addToFree(rootNode);
        }

        return removedAddr;
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
                if (cur.keys[i] >= low && cur.keys[i] <= high) {
                    addrs.add(cur.children[i]);
                }
                if (cur.keys[i] > high) {
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
            System.out.print("- (leaf @ " + node.address + ") count " + count + ", keys: ");
            for (int i = 0; i < count; i++) {
                System.out.print(node.keys[i]);// + " : " + node.children[i]);
                if (i < count - 1)
                    System.out.print(", ");
            }
            System.out.print(" (sibling " + node.sibling() + ")");
            System.out.println();
        } else {
            int count = node.count;
            System.out.print("> (branch @ " + node.address + ") count " + count + ", keys: ");
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

    public static void main(String[] args) throws IOException {
        BTree myTree = new BTree("test_btree", 60);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Key to insert: ");
            String line = scanner.nextLine();
            if (!line.startsWith("!")) {
                int key = Integer.parseInt(line);
                int addr = (int) (Math.random() * 10000);
                System.out.println("Inserting key " + key + ", addr " + addr);
                myTree.insert(key, addr);
            } else {
                int key = Integer.parseInt(line.substring(1));
                System.out.println("Removing key " + key);
                System.out.println("Remove result: " + myTree.remove(key));
            }
            myTree.print();
            System.out.println("");
        }
    }
}
