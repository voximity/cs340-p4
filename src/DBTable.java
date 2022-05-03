import java.io.*;
import java.util.*;

public class DBTable {
    public static final long NONE = 0;

    private RandomAccessFile rows;
    private long free;
    private int numOtherFields;
    private int[] otherFieldLengths;

    private BTree btree;

    private class Row {
        private int keyField;
        private char[][] otherFields;
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

        private void write(long addr) throws IOException {
            this.addr = addr;
            write();
        }

        private void write() throws IOException {
            rows.seek(addr);
            rows.writeInt(keyField);
            for (int i = 0; i < numOtherFields; i++)
                for (int j = 0; j < otherFieldLengths[i]; j++)
                    rows.writeChar(otherFields[i][j]);
        }
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

    public boolean insert(int key, char[][] fields) {
        // PRE: the length of each row is fields matches the expected length

        /*
         * If a row with the key is not in the table, the row is added and the method
         * returns true otherwise the row is not added and the metgit hod returns
         * false.
         * The method must use the B+tree to determine if a row with the key exists.
         * If the row is added the key is also added into the B+tree.
         */

        return false;
    }

    public boolean remove(int key) {
        /*
         * If a row with the key is in the table it is removed and true is returned
         * otherwise false is returned.
         * The method must use the B+Tree to determine if a row with the key exists.
         *
         * If the row is deleted the key must be deleted from the B+Tree
         */

        return false;
    }

    public LinkedList<String> search(int key) {
        /*
         * If a row with the key is found in the table return a list of the other
         * fields
         * in
         * the row.
         * The string values in the list should not include the null characters.
         * If a row with the key is not found return an empty list
         * The method must use the equality search in B+Tree
         */

        return null;
    }

    public LinkedList<LinkedList<String>> rangeSearch(int low, int high) {
        // PRE: low <= high
        /*
         * For each row with a key that is in the range low to high inclusive a list
         * of the fields (including the key) in the row is added to the list
         * returned by the call.
         * If there are no rows with a key in the range return an empty list
         * The method must use the range search in B+Tree
         */

        return null;
    }

    public void print() {
        // Print the rows to standard output in ascending order (based on the keys)
        // One row per line
    }

    public void close() throws IOException {
        // close the DBTable. The table should not be used after it is closed
        btree.close();
        rows.close();
    }
}
