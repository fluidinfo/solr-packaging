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

import java.io.IOException;
import java.util.Map;

/**
 * <code>QueryValueSource</code> returns the relevance score of the query
 */
public class QueryValueSource extends ValueSource {
  final Query q;
  final float defVal;

  public QueryValueSource(Query q, float defVal) {
    this.q = q;
    this.defVal = defVal;
  }

  public Query getQuery() { return q; }
  public float getDefaultValue() { return defVal; }

  @Override
  public String description() {
    return "query(" + q + ",def=" + defVal + ")";
  }

  @Override
  public DocValues getValues(Map context, IndexReader reader) throws IOException {
    return new QueryDocValues(reader, q, defVal, context==null ? null : (Weight)context.get(this));
  }

  @Override
  public int hashCode() {
    return q.hashCode() * 29;
  }

  @Override
  public boolean equals(Object o) {
    if (QueryValueSource.class != o.getClass()) return false;
    QueryValueSource other = (QueryValueSource)o;
    return this.q.equals(other.q) && this.defVal==other.defVal;
  }

  @Override
  public void createWeight(Map context, Searcher searcher) throws IOException {
    Weight w = searcher.createNormalizedWeight(q);
    context.put(this, w);
  }
}


class QueryDocValues extends DocValues {
  final Query q;
  final IndexReader reader;
  final Weight weight;
  final float defVal;

  Scorer scorer;
  int scorerDoc; // the document the scorer is on
  boolean noMatches = false;

  // the last document requested... start off with high value
  // to trigger a scorer reset on first access.
  int lastDocRequested=Integer.MAX_VALUE;

  public QueryDocValues(IndexReader reader, Query q, float defVal, Weight w) throws IOException {
    this.reader = reader;
    this.q = q;
    this.defVal = defVal;
    weight = w!=null ? w : new IndexSearcher(reader).createNormalizedWeight(q);
  }

  @Override
  public float floatVal(int doc) {
    try {
      if (doc < lastDocRequested) {
        if (noMatches) return defVal;
        scorer = weight.scorer(reader, true, false);
        if (scorer==null) {
          noMatches = true;
          return defVal;
        }
        scorerDoc = -1;
      }
      lastDocRequested = doc;

      if (scorerDoc < doc) {
        scorerDoc = scorer.advance(doc);
      }

      if (scorerDoc > doc) {
        // query doesn't match this document... either because we hit the
        // end, or because the next doc is after this doc.
        return defVal;
      }

      // a match!
      return scorer.score();
    } catch (IOException e) {
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "caught exception in QueryDocVals("+q+") doc="+doc, e);
    }
  }  

  @Override
  public int intVal(int doc) {
    return (int)floatVal(doc);
  }
  @Override
  public long longVal(int doc) {
    return (long)floatVal(doc);
  }
  @Override
  public double doubleVal(int doc) {
    return (double)floatVal(doc);
  }
  @Override
  public String strVal(int doc) {
    return Float.toString(floatVal(doc));
  }
  @Override
  public String toString(int doc) {
    return "query(" + q + ",def=" + defVal + ")=" + floatVal(doc);
  }
}