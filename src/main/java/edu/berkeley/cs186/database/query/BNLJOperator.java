package edu.berkeley.cs186.database.query; //hw4

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.stats.TableStats;

public class BNLJOperator extends JoinOperator {

  private int numBuffers;

  public BNLJOperator(QueryOperator leftSource,
                      QueryOperator rightSource,
                      String leftColumnName,
                      String rightColumnName,
                      Database.Transaction transaction) throws QueryPlanException, DatabaseException {
    super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.BNLJ);

    this.numBuffers = transaction.getNumMemoryPages();
  }

  public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
    return new BNLJIterator();
  }

  public int estimateIOCost() throws QueryPlanException {
    //This method implements the the IO cost estimation of the Block Nested Loop Join

    int usableBuffers = numBuffers - 2; //Common mistake have to first calculate the number of usable buffers

    int numLeftPages = getLeftSource().getStats().getNumPages();

    int numRightPages = getRightSource().getStats().getNumPages();

    return ((int) Math.ceil((double) numLeftPages / (double) usableBuffers)) * numRightPages + numLeftPages;

  }

  /**
   * An implementation of Iterator that provides an iterator interface for this operator.
   */
  
  private class BNLJIterator extends JoinIterator {
	    /**
	     * Some member variables are provided for guidance, but there are many possible solutions.
	     * You should implement the solution that's best for you, using any member variables you need.
	     * You're free to use these member variables, but you're not obligated to.
	     */

    private Iterator<Page> leftPageIterator = null;
    private Iterator<Page> rightPageIterator = null;
    private BacktrackingIterator<Record> leftRecordIterator = null;
    private BacktrackingIterator<Record> rightRecordIterator = null;
    private Record leftRecord = null;
    private Record rightRecord = null;
    private Record nextRecord = null;

    public BNLJIterator() throws QueryPlanException, DatabaseException {
      super();
      this.leftPageIterator = BNLJOperator.this.getPageIterator(this.getLeftTableName());
      this.rightPageIterator = BNLJOperator.this.getPageIterator(this.getRightTableName());
      this.leftPageIterator.next(); // skip header page
      this.rightPageIterator.next();
      this.leftRecordIterator = BNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftPageIterator, numBuffers - 2);
      this.rightRecordIterator = BNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightPageIterator, 1);

      this.nextRecord = null;

      this.leftRecord = leftRecordIterator.hasNext() ? leftRecordIterator.next() : null;
      this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;

      // We mark the first record so we can reset to it when we advance the left record.
      if (rightRecord != null) {
        rightRecordIterator.mark();
      }
      else return;

      if (leftRecord != null) {
        leftRecordIterator.mark();
      }

      try {
        fetchNextRecord();
      } catch (DatabaseException e) {
        this.nextRecord = null;
      }
    }

    private void resetRightRecord(){
      this.rightRecordIterator.reset();
      assert(rightRecordIterator.hasNext());
      rightRecord = rightRecordIterator.next();
      rightRecordIterator.mark();
    }

    private void resetLeftRecord(){
      this.leftRecordIterator.reset();
      assert(leftRecordIterator.hasNext());
      leftRecord = leftRecordIterator.next();
      leftRecordIterator.mark();
    }

    private void fetchNextRecord() throws DatabaseException {
      if (this.leftRecord == null) throw new DatabaseException("No new record to fetch");
      this.nextRecord = null;
      do {
        if (this.rightRecord != null) {
          DataBox leftJoinValue = this.leftRecord.getValues().get(BNLJOperator.this.getLeftColumnIndex());
          DataBox rightJoinValue = this.rightRecord.getValues().get(BNLJOperator.this.getRightColumnIndex());
          if (leftJoinValue.equals(rightJoinValue)) {
            List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
            List<DataBox> rightValues = new ArrayList<>(this.rightRecord.getValues());
            leftValues.addAll(rightValues);
            this.nextRecord = new Record(leftValues);
          }
          this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
        } else if (leftRecordIterator.hasNext()) {
          this.leftRecord = leftRecordIterator.next();
          resetRightRecord();
        } else if (rightPageIterator.hasNext()) {
          this.rightRecordIterator = BNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightPageIterator, 1);
          this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
          rightRecordIterator.mark();
          resetLeftRecord();
        } else if (leftPageIterator.hasNext()) {
          this.leftRecordIterator = BNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftPageIterator, numBuffers - 2);
          this.leftRecord = leftRecordIterator.hasNext() ? leftRecordIterator.next() : null;
          leftRecordIterator.mark();

          this.rightPageIterator = BNLJOperator.this.getPageIterator(this.getRightTableName());
          this.rightPageIterator.next();
          this.rightRecordIterator = BNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightPageIterator, 1);
          this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
          rightRecordIterator.mark();
        } else {
          throw new DatabaseException("No new record to fetch");
        }
      } while (!hasNext());
    }
	    
    
    /**
     * Checks if there are more record(s) to yield
     *
     * @return true if this iterator has another record to yield, otherwise false
     */
    public boolean hasNext() {
      return this.nextRecord != null;
    }

      /**
       * Yields the next record of this iterator.
       *
       * @return the next Record
       * @throws NoSuchElementException if there are no more Records to yield
       */
    public Record next() {
      if (!this.hasNext()) {
        throw new NoSuchElementException();
      }

      Record nextRecord = this.nextRecord;
      try {
        this.fetchNextRecord();
      } catch (DatabaseException e) {
        this.nextRecord = null;
      }
      return nextRecord;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

  }
}
