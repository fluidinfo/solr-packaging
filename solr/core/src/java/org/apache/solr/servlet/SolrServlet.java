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

package org.apache.solr.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.FastWriter;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.BinaryQueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;

/**
 * @deprecated Register a standard request handler instead of using this
 *             servlet. Add &lt;requestHandler name="standard"
 *             class="solr.StandardRequestHandler" default="true"&gt; to
 *             solrconfig.xml.
 */

@Deprecated
public class SolrServlet extends HttpServlet {
    
  final Logger log = LoggerFactory.getLogger(SolrServlet.class);
  private boolean hasMulticore = false;
    
  private static final Charset UTF8 = Charset.forName("UTF-8");

  @Override
  public void init() throws ServletException {
    log.info("SolrServlet.init()");
    
    // Check if the "solr.xml" file exists -- if so, this is an invalid servlet
    // (even if there is only one core...)
    String instanceDir = SolrResourceLoader.locateInstanceDir();
    File fconf = new File(instanceDir, "solr.xml");
    hasMulticore = fconf.exists();
    
    // we deliberately do not initialize a SolrCore because of SOLR-597
    // https://issues.apache.org/jira/browse/SOLR-597
    log.info("SolrServlet.init() done");
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request,response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if( hasMulticore ) {
      response.sendError( 400, "Missing solr core name in path" );
      return;
    }
    
    final SolrCore core = SolrCore.getSolrCore();
    SolrServletRequest solrReq = new SolrServletRequest(core, request);;
    SolrQueryResponse solrRsp = new SolrQueryResponse();
    try {

      SolrRequestHandler handler = core.getRequestHandler(solrReq.getQueryType());
      if (handler==null) {
        log.warn("Unknown Request Handler '" + solrReq.getQueryType() +"' :" + solrReq);
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,"Unknown Request Handler '" + solrReq.getQueryType() + "'", true);
      }
      core.execute(handler, solrReq, solrRsp );
      if (solrRsp.getException() == null) {
        QueryResponseWriter responseWriter = core.getQueryResponseWriter(solrReq);
        // Now write it out
        final String ct = responseWriter.getContentType(solrReq, solrRsp);
        // don't call setContentType on null
        if (null != ct) response.setContentType(ct); 
        if (responseWriter instanceof BinaryQueryResponseWriter) {
          BinaryQueryResponseWriter binWriter = (BinaryQueryResponseWriter) responseWriter;
          binWriter.write(response.getOutputStream(), solrReq, solrRsp);
        } else {
          String charset = ContentStreamBase.getCharsetFromContentType(ct);
          Writer out = (charset == null || charset.equalsIgnoreCase("UTF-8"))
            ? new OutputStreamWriter(response.getOutputStream(), UTF8)
            : new OutputStreamWriter(response.getOutputStream(), charset);
          out = new FastWriter(out);
          responseWriter.write(out, solrReq, solrRsp);
          out.flush();
        }
      } else {
        Exception e = solrRsp.getException();
        int rc=500;
        if (e instanceof SolrException) {
           rc=((SolrException)e).code();
        }
        sendErr(rc, SolrException.toStr(e), request, response);
      }
    } catch (SolrException e) {
      if (!e.logged) SolrException.log(log,e);
      sendErr(e.code(), SolrException.toStr(e), request, response);
    } catch (Throwable e) {
      SolrException.log(log,e);
      sendErr(500, SolrException.toStr(e), request, response);
    } finally {
      // This releases the IndexReader associated with the request
      solrReq.close();
    }
  }

  final void sendErr(int rc, String msg, HttpServletRequest request, HttpServletResponse response) {
    try {
      // hmmm, what if this was already set to text/xml?
      try{
        response.setContentType(QueryResponseWriter.CONTENT_TYPE_TEXT_UTF8);
        // response.setCharacterEncoding("UTF-8");
      } catch (Exception e) {}
      try{response.setStatus(rc);} catch (Exception e) {}
      PrintWriter writer = response.getWriter();
      writer.write(msg);
    } catch (IOException e) {
      SolrException.log(log,e);
    }
  }
}
