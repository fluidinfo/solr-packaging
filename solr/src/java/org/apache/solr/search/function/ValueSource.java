/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.search.function;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.search.SolrSortField;

import java.io.IOException;
import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * Instantiates {@link org.apache.solr.search.function.DocValues} for a particular reader.
 * <br>
 * Often used when creating a {@link FunctionQuery}.
 *
 * @version $Id: ValueSource.java 1136465 2011-06-16 14:50:22Z mikemccand $
 */
public abstract class ValueSource implements Serializable {

  @Deprecated
  public DocValues getValues(IndexReader reader) throws IOException {
    return getValues(null, reader);
  }

  /**
   * Gets the values for this reader and the context that was previously
   * passed to createWeight()
   */
  public DocValues getValues(Map context, IndexReader reader) throws IOException {
    return getValues(reader);
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  /**
   * description of field, used in explain()
   */
  public abstract String description();

  @Override
  public String toString() {
    return description();
  }

    /**
   * Implementations should propagate createWeight to sub-ValueSources which can optionally store
   * weight info in the context. The context object will be passed to getValues()
   * where this info can be retrieved.
   */
  public void createWeight(Map context, Searcher searcher) throws IOException {
  }

  /**
   * Returns a new non-threadsafe context map.
   */
  public static Map newContext() {
    return new IdentityHashMap();
  }

  //
  // Sorting by function
  //

  /**
   * EXPERIMENTAL: This method is subject to change.
   * <br>WARNING: Sorted function queries are not currently weighted.
   * <p>
   * Get the SortField for this ValueSource.  Uses the {@link #getValues(java.util.Map, IndexReader)}
   * to populate the SortField.
   *
   * @param reverse true if this is a reverse sort.
   * @return The {@link org.apache.lucene.search.SortField} for the ValueSource
   * @throws IOException if there was a problem reading the values.
   */
  public SortField getSortField(boolean reverse) throws IOException {
    return new ValueSourceSortField(reverse);
  }

  private static FieldComparatorSource dummyComparator = new FieldComparatorSource() {
    @Override
    public FieldComparator newComparator(String fieldname, int numHits, int sortPos, boolean reversed) throws IOException {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unweighted use of sort " + fieldname);
    }
  };

  class ValueSourceSortField extends SortField implements SolrSortField {
    public ValueSourceSortField(boolean reverse) {
      super(description(), dummyComparator, reverse);
    }

    public SortField weight(IndexSearcher searcher) throws IOException {
      Map context = newContext();
      createWeight(context, searcher);
      return new SortField(getField(), new ValueSourceComparatorSource(context), getReverse());
    }
  }

  class ValueSourceComparatorSource extends FieldComparatorSource {
    private final Map context;

    public ValueSourceComparatorSource(Map context) {
      this.context = context;
    }

    @Override
    public FieldComparator newComparator(String fieldname, int numHits,
                                         int sortPos, boolean reversed) throws IOException {
      return new ValueSourceComparator(context, numHits);
    }
  }

  /**
   * Implement a {@link org.apache.lucene.search.FieldComparator} that works
   * off of the {@link org.apache.solr.search.function.DocValues} for a ValueSource
   * instead of the normal Lucene FieldComparator that works off of a FieldCache.
   */
  class ValueSourceComparator extends FieldComparator<Double> {
    private final double[] values;
    private DocValues docVals;
    private double bottom;
    private Map fcontext;

    ValueSourceComparator(Map fcontext, int numHits) {
      this.fcontext = fcontext;
      values = new double[numHits];
    }

    @Override
    public int compare(int slot1, int slot2) {
      final double v1 = values[slot1];
      final double v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }

    }

    @Override
    public int compareBottom(int doc) {
      final double v2 = docVals.doubleVal(doc);
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    @Override
    public void copy(int slot, int doc) {
      values[slot] = docVals.doubleVal(doc);
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
      docVals = getValues(fcontext, reader);
    }

    @Override
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    @Override
    public Double value(int slot) {
      return values[slot];
    }
  }
}


class ValueSourceScorer extends Scorer {
  protected IndexReader reader;
  private int doc = -1;
  protected final int maxDoc;
  protected final DocValues values;
  protected boolean checkDeletes;

  protected ValueSourceScorer(IndexReader reader, DocValues values) {
    super(null, null);
    this.reader = reader;
    this.maxDoc = reader.maxDoc();
    this.values = values;
    setCheckDeletes(true);
  }

  public IndexReader getReader() {
    return reader;
  }

  public void setCheckDeletes(boolean checkDeletes) {
    this.checkDeletes = checkDeletes && reader.hasDeletions();
  }

  public boolean matches(int doc) {
    return (!checkDeletes || !reader.isDeleted(doc)) && matchesValue(doc);
  }

  public boolean matchesValue(int doc) {
    return true;
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int nextDoc() throws IOException {
    for (; ;) {
      doc++;
      if (doc >= maxDoc) return doc = NO_MORE_DOCS;
      if (matches(doc)) return doc;
    }
  }

  @Override
  public int advance(int target) throws IOException {
    // also works fine when target==NO_MORE_DOCS
    doc = target - 1;
    return nextDoc();
  }

  public int doc() {
    return doc;
  }

  public boolean next() {
    for (; ;) {
      doc++;
      if (doc >= maxDoc) return false;
      if (matches(doc)) return true;
    }
  }

  public boolean skipTo(int target) {
    doc = target - 1;
    return next();
  }


  @Override
  public float score() throws IOException {
    return values.floatVal(doc);
  }

  public Explanation explain(int doc) throws IOException {
    return values.explain(doc);
  }
}


