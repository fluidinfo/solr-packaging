package org.apache.solr;

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

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.util.AbstractSolrTestCase;

/**
 * <p> Test disabling components</p>
 *
 * @version $Id: TestPluginEnable.java 1065290 2011-01-30 14:25:12Z rmuir $
 * @since solr 1.4
 */
public class TestPluginEnable extends AbstractSolrTestCase {


  public void testSimple() throws SolrServerException {
    assertNull(h.getCore().getRequestHandler("disabled"));
    assertNotNull(h.getCore().getRequestHandler("enabled"));

  }


  @Override
  public String getSchemaFile() {
    return "schema-replication1.xml";
  }

  @Override
  public String getSolrConfigFile() {
    return "solrconfig-enableplugin.xml";
  }

}
