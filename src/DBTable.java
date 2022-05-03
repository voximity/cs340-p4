
/**
 * DBTable, an abstraction over the B+Tree implementation.
 * 
 * @author Zander Franks
 * @version 5/3/2022
 */

import java.io.*;
import java.util.*;

public class DBTable {
    /**
     * An address that represents the lack of a row.
     */
    public static final long NONE = 0;

    /**
     * The random access file this DBTable interacts with.
     */
    private RandomAccessFile rows;

    /**
     * The address of the head of the free list.
     */
    private long free;

    /**
     * The number of other fields a DBTable row has.
     */
    private int numOtherFields;

    /**
     * The lengths of each other field of a DBTable row.
     */
    private int[] otherFieldLengths;

    /**
     * The B+Tree associated with this DBTable.
     */
    private BTree btree;

    /**
     * A DBTable row.
     */
    private class Row {
        /**
         * The key of the row.
         */
        private int keyField;

        /**
         * The other fields of the row.
         */
        private char[][] otherFields;

        /**
         * The address of the row in the file.
         */
        private long addr;

        /*
         * Each row consists of unique key and one or more character array fields.
         * Each character array field is a fixed length field (for example 10
         * characters).
         * Each field can have a different length.
         * Fields are padded with null characters so a field with a length of
         * of x characters always uses space for x characters.
         */

        public Row(int key, char[][] fields) {
            keyField = key;
            otherFields = fields;
            addr = NONE;
        }

        public Row(long addr) throws IOException {
            this.addr = addr;
            rows.seek(addr);
            keyField = rows.readInt();
            otherFields = new char[numOtherFields][];
            for (int i = 0; i < numOtherFields; i++) {
                otherFields[i] = new char[otherFieldLengths[i]];
                for (int j = 0; j < otherFieldLengths[i]; j++)
                    otherFields[i][j] = rows.readChar();
            }
        }

        /**
         * Write the row to the file given an address.
         */
        private void write(long addr) throws IOException {
            this.addr = addr;
            write();
        }

        /**
         * Write the row to the file.
         */
        private void write() throws IOException {
            rows.seek(addr);
            rows.writeInt(keyField);
            for (int i = 0; i < numOtherFields; i++)
                for (int j = 0; j < otherFieldLengths[i]; j++)
                    rows.writeChar(otherFields[i][j]);
        }

        /**
         * Get a linked list of strings representing the other string fields in this
         * row, excluding the null terminators.
         */
        private LinkedList<String> fields() {
            LinkedList<String> list = new LinkedList<>();

            for (int i = 0; i < numOtherFields; i++) {
                StringBuilder b = new StringBuilder();
                for (int j = 0; j < otherFieldLengths[i]; j++) {
                    if (otherFields[i][j] == '\0')
                        break;

                    b.append(otherFields[i][j]);
                }
                list.add(b.toString());
            }

            return list;
        }
    }

    /**
     * Get the next free address in the free list, shifting it forward.
     */
    private long nextFree() throws IOException {
        if (free == NONE)
            return rows.length();

        long c = free;
        rows.seek(free);
        free = rows.readLong();
        return c;
    }

    /**
     * Peek at the next free address in the free list. Does not move it forward.
     */
    private long peekNextFree() throws IOException {
        if (free == NONE)
            return rows.length();

        return free;
    }

    /**
     * Add a row to the free list.
     */
    private void addToFree(Row row) throws IOException {
        rows.seek(row.addr);
        rows.writeLong(free);
        free = row.addr;
        rows.seek(4 + 4 * numOtherFields);
        rows.writeLong(free);
    }

    public DBTable(String filename, int[] fL, int bsize) throws IOException {
        /*
         * Use this constructor to create a new DBTable.
         * filename is the name of the file used to store the table
         * fL is the lengths of the otherFields
         * fL.length indicates how many other fields are part of the row
         * bsize is the block size. It is used to calculate the order of the B+Tree
         * A B+Tree must be created for the key field in the table
         *
         * If a file with name filename exists, the file should be deleted before the
         * new file is created.
         */

        numOtherFields = fL.length;
        otherFieldLengths = fL;
        free = NONE;

        File file = new File(filename);
        if (file.exists())
            file.delete();

        rows = new RandomAccessFile(file, "rw");
        rows.writeInt(numOtherFields);
        for (int i = 0; i < numOtherFields; i++)
            rows.writeInt(otherFieldLengths[i]);

        rows.writeLong(free);

        // open btree
        btree = new BTree(filename + ".btree", bsize);
    }

    public DBTable(String filename) throws IOException {
        // Open an existing DBTable
        rows = new RandomAccessFile(filename, "rw");
        numOtherFields = rows.readInt();
        otherFieldLengths = new int[numOtherFields];
        for (int i = 0; i < numOtherFields; i++)
            otherFieldLengths[i] = rows.readInt();
        free = rows.readLong();

        // open btree
        btree = new BTree(filename + ".btree");
    }

    /**
     * Insert a particular key and its string fields to the DBTable.
     */
    public boolean insert(int key, char[][] fields) throws IOException {
        // PRE: the length of each row is fields matches the expected length

        /*
         * If a row with the key is not in the table, the row is added and the method
         * returns true otherwise the row is not added and the method returns
         * false.
         * The method must use the B+tree to determine if a row with the key exists.
         * If the row is added the key is also added into the B+tree.
         */

        if (btree.insert(key, peekNextFree())) {
            new Row(key, fields).write(nextFree());
            return true;
        }

        return false;
    }

    /**
     * Remove a particular key from the DBTable.
     */
    public boolean remove(int key) throws IOException {
        /*
         * If a row with the key is in the table it is removed and true is returned
         * otherwise false is returned.
         * The method must use the B+Tree to determine if a row with the key exists.
         *
         * If the row is deleted the key must be deleted from the B+Tree
         */

        long removed = btree.remove(key);
        if (removed != NONE) {
            addToFree(new Row(removed));
            return true;
        }

        return false;
    }

    /**
     * Search for a particular key in the DBTable, returning its string fields.
     */
    public LinkedList<String> search(int key) throws IOException {
        /*
         * If a row with the key is found in the table return a list of the other
         * fields
         * in
         * the row.
         * The string values in the list should not include the null characters.
         * If a row with the key is not found return an empty list
         * The method must use the equality search in B+Tree
         */

        long addr = btree.search(key);
        if (addr != NONE)
            return new Row(addr).fields();

        return new LinkedList<>();
    }

    /**
     * Search the DBTable over a particular range of keys, returning a list of rows
     * transformed such that each row represents a linked list of its stringified
     * key and each of its other string fields.
     */
    public LinkedList<LinkedList<String>> rangeSearch(int low, int high) throws IOException {
        // PRE: low <= high
        /*
         * For each row with a key that is in the range low to high inclusive a list
         * of the fields (including the key) in the row is added to the list
         * returned by the call.
         * If there are no rows with a key in the range return an empty list
         * The method must use the range search in B+Tree
         */

        LinkedList<Long> range = btree.rangeSearch(low, high);
        LinkedList<LinkedList<String>> list = new LinkedList<>();
        Iterator<Long> iter = range.iterator();

        while (iter.hasNext()) {
            Row row = new Row(iter.next());
            LinkedList<String> fields = row.fields();
            fields.addFirst(Integer.toString(row.keyField));

            list.add(fields);
        }

        return list;
    }

    /**
     * Print this DBTable in ascending order by key.
     */
    public void print() throws IOException {
        // Print the rows to standard output in ascending order (based on the keys)
        // One row per line

        LinkedList<Long> range = btree.rangeSearch(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Iterator<Long> iter = range.iterator();

        while (iter.hasNext()) {
            Row row = new Row(iter.next());
            Iterator<String> fieldIter = row.fields().iterator();
            System.out.printf("%5d) ", row.keyField);
            int i = 0;
            while (fieldIter.hasNext()) {
                System.out.printf("%-" + otherFieldLengths[i++] + "s ", fieldIter.next());
            }
            System.out.println();
        }
    }

    /**
     * Print this DBTable's B+Tree. Used for debugging.
     */
    public void printBTree() throws IOException {
        btree.print();
    }

    /**
     * Close the DBTable.
     */
    public void close() throws IOException {
        // close the DBTable. The table should not be used after it is closed
        btree.close();
        rows.close();
    }
}
