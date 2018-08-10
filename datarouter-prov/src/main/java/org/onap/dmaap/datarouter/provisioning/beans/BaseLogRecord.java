/*******************************************************************************
 * ============LICENSE_START==================================================
 * * org.onap.dmaap
 * * ===========================================================================
 * * Copyright © 2017 AT&T Intellectual Property. All rights reserved.
 * * ===========================================================================
 * * Licensed under the Apache License, Version 2.0 (the "License");
 * * you may not use this file except in compliance with the License.
 * * You may obtain a copy of the License at
 * *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 * *
 *  * Unless required by applicable law or agreed to in writing, software
 * * distributed under the License is distributed on an "AS IS" BASIS,
 * * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * * See the License for the specific language governing permissions and
 * * limitations under the License.
 * * ============LICENSE_END====================================================
 * *
 * * ECOMP is a trademark and service mark of AT&T Intellectual Property.
 * *
 ******************************************************************************/


package org.onap.dmaap.datarouter.provisioning.beans;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.onap.dmaap.datarouter.provisioning.utils.LOGJSONObject;

/**
 * Define the common fields used by the three types of records generated by DR nodes.
 *
 * @author Robert Eby
 * @version $Id: BaseLogRecord.java,v 1.10 2013/10/29 16:57:57 eby Exp $
 */
public class BaseLogRecord implements LOGJSONable, Loadable {
    protected static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private long eventTime;
    private String publishId;
    private int feedid;
    private String requestUri;
    private String method;
    private String contentType;
    private long contentLength;

    protected BaseLogRecord(String[] pp) throws ParseException {
//        This throws exceptions occasionally - don't know why.
//        Date d = null;
//        synchronized (sdf) {
//            d = sdf.parse(pp[0]);
//        }
        Date d = parseDate(pp[0]);
        this.eventTime     = d.getTime();
        this.publishId     = pp[2];
        this.feedid        = Integer.parseInt(pp[3]);
        if (pp[1].equals("DLX")) {
            this.requestUri    = "";
            this.method        = "GET";    // Note: we need a valid value in this field, even though unused
            this.contentType   = "";
            this.contentLength = Long.parseLong(pp[5]);
        } else  if (pp[1].equals("PUB") || pp[1].equals("LOG") || pp[1].equals("PBF")) {
            this.requestUri    = pp[4];
            this.method        = pp[5];
            this.contentType   = pp[6];
            this.contentLength = Long.parseLong(pp[7]);
        } else {
            this.requestUri    = pp[5];
            this.method        = pp[6];
            this.contentType   = pp[7];
            this.contentLength = Long.parseLong(pp[8]);
        }
    }
    protected BaseLogRecord(ResultSet rs) throws SQLException {
        this.eventTime     = rs.getLong("EVENT_TIME");
        this.publishId     = rs.getString("PUBLISH_ID");
        this.feedid        = rs.getInt("FEEDID");
        this.requestUri    = rs.getString("REQURI");
        this.method        = rs.getString("METHOD");
        this.contentType   = rs.getString("CONTENT_TYPE");
        this.contentLength = rs.getLong("CONTENT_LENGTH");
    }
    protected Date parseDate(final String s) throws ParseException {
        int[] n = new int[7];
        int p = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                p++;
            } else {
                if (p > n.length)
                    throw new ParseException("parseDate()", 0);
                n[p] = (n[p] * 10) + (c - '0');
            }
        }
        if (p != 7)
            throw new ParseException("parseDate()", 1);
        Calendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, n[0]);
        cal.set(Calendar.MONTH, n[1]-1);
        cal.set(Calendar.DAY_OF_MONTH, n[2]);
        cal.set(Calendar.HOUR_OF_DAY, n[3]);
        cal.set(Calendar.MINUTE, n[4]);
        cal.set(Calendar.SECOND, n[5]);
        cal.set(Calendar.MILLISECOND, n[6]);
        return cal.getTime();
    }
    public long getEventTime() {
        return eventTime;
    }
    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }
    public String getPublishId() {
        return publishId;
    }
    public void setPublishId(String publishId) {
        this.publishId = publishId;
    }
    public int getFeedid() {
        return feedid;
    }
    public void setFeedid(int feedid) {
        this.feedid = feedid;
    }
    public String getRequestUri() {
        return requestUri;
    }
    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    public long getContentLength() {
        return contentLength;
    }
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }
    @Override
    public LOGJSONObject asJSONObject() {
        LOGJSONObject jo = new LOGJSONObject();
        String t = "";
        synchronized (sdf) {
            t = sdf.format(eventTime);
        }
        jo.put("date", t);
        jo.put("publishId", publishId);
        jo.put("requestURI", requestUri);
        jo.put("method", method);
        if (method.equals("PUT")) {
            jo.put("contentType", contentType);
            jo.put("contentLength", contentLength);
        }
        return jo;
    }
    @Override
    public void load(PreparedStatement ps) throws SQLException {
        ps.setLong  (2, getEventTime());
        ps.setString(3, getPublishId());
        ps.setInt   (4, getFeedid());
        ps.setString(5, getRequestUri());
        ps.setString(6, getMethod());
        ps.setString(7, getContentType());
        ps.setLong  (8, getContentLength());
    }
}
