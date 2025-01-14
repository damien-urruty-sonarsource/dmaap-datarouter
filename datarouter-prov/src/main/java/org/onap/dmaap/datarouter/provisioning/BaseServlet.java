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


package org.onap.dmaap.datarouter.provisioning;

import static com.att.eelf.configuration.Configuration.MDC_KEY_REQUEST_ID;
import static com.att.eelf.configuration.Configuration.MDC_SERVER_FQDN;
import static com.att.eelf.configuration.Configuration.MDC_SERVER_IP_ADDRESS;
import static com.att.eelf.configuration.Configuration.MDC_SERVICE_NAME;

import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.onap.dmaap.datarouter.authz.Authorizer;
import org.onap.dmaap.datarouter.authz.impl.ProvAuthorizer;
import org.onap.dmaap.datarouter.authz.impl.ProvDataProvider;
import org.onap.dmaap.datarouter.provisioning.beans.Deleteable;
import org.onap.dmaap.datarouter.provisioning.beans.Feed;
import org.onap.dmaap.datarouter.provisioning.beans.Group;
import org.onap.dmaap.datarouter.provisioning.beans.Insertable;
import org.onap.dmaap.datarouter.provisioning.beans.NodeClass;
import org.onap.dmaap.datarouter.provisioning.beans.Parameters;
import org.onap.dmaap.datarouter.provisioning.beans.Subscription;
import org.onap.dmaap.datarouter.provisioning.beans.Updateable;
import org.onap.dmaap.datarouter.provisioning.utils.Poker;
import org.onap.dmaap.datarouter.provisioning.utils.ProvDbUtils;
import org.onap.dmaap.datarouter.provisioning.utils.SynchronizerTask;
import org.onap.dmaap.datarouter.provisioning.utils.ThrottleFilter;
import org.slf4j.MDC;


/**
 * This is the base class for all Servlets in the provisioning code. It provides standard constants and some common
 * methods.
 *
 * @author Robert Eby
 * @version $Id: BaseServlet.java,v 1.16 2014/03/12 19:45:40 eby Exp $
 */
@SuppressWarnings("serial")
public class BaseServlet extends HttpServlet implements ProvDataProvider {

    public static final String BEHALF_HEADER = "X-DMAAP-DR-ON-BEHALF-OF";

    public static final String EXCLUDE_AAF_HEADER = "X-EXCLUDE-AAF";

    private static final String AAF_CADI_FEED_TYPE = "org.onap.dmaap.datarouter.provserver.aaf.feed.type";
    private static final String AAF_CADI_SUB_TYPE = "org.onap.dmaap.datarouter.provserver.aaf.sub.type";
    private static final String AAF_INSTANCE = "org.onap.dmaap.datarouter.provserver.aaf.instance";
    private static final String AAF_CADI_FEED = "org.onap.dmaap-dr.feed";
    private static final String AAF_CADI_SUB = "org.onap.dmaap-dr.sub";

    static final String CREATE_PERMISSION = "create";
    static final String EDIT_PERMISSION = "edit";
    static final String DELETE_PERMISSION = "delete";
    private static final String PUBLISH_PERMISSION = "publish";
    private static final String SUSPEND_PERMISSION = "suspend";
    private static final String RESTORE_PERMISSION = "restore";
    private static final String SUBSCRIBE_PERMISSION = "subscribe";
    static final String APPROVE_SUB_PERMISSION = "approveSub";

    static final String FEED_BASECONTENT_TYPE = "application/vnd.dmaap-dr.feed";
    public static final String FEED_CONTENT_TYPE = "application/vnd.dmaap-dr.feed; version=2.0";
    public static final String FEEDFULL_CONTENT_TYPE = "application/vnd.dmaap-dr.feed-full; version=2.0";
    public static final String FEEDLIST_CONTENT_TYPE = "application/vnd.dmaap-dr.feed-list; version=1.0";
    static final String SUB_BASECONTENT_TYPE = "application/vnd.dmaap-dr.subscription";
    public static final String SUB_CONTENT_TYPE = "application/vnd.dmaap-dr.subscription; version=2.0";
    public static final String SUBFULL_CONTENT_TYPE = "application/vnd.dmaap-dr.subscription-full; version=2.0";
    static final String SUBLIST_CONTENT_TYPE = "application/vnd.dmaap-dr.subscription-list; version=1.0";

    //Adding groups functionality, ...1610
    static final String GROUP_BASECONTENT_TYPE = "application/vnd.dmaap-dr.group";
    static final String GROUP_CONTENT_TYPE = "application/vnd.dmaap-dr.group; version=2.0";
    static final String GROUPFULL_CONTENT_TYPE = "application/vnd.dmaap-dr.group-full; version=2.0";
    public static final String GROUPLIST_CONTENT_TYPE = "application/vnd.dmaap-dr.fegrouped-list; version=1.0";

    public static final String LOGLIST_CONTENT_TYPE = "application/vnd.dmaap-dr.log-list; version=1.0";
    public static final String PROVFULL_CONTENT_TYPE1 = "application/vnd.dmaap-dr.provfeed-full; version=1.0";
    public static final String PROVFULL_CONTENT_TYPE2 = "application/vnd.dmaap-dr.provfeed-full; version=2.0";
    public static final String CERT_ATTRIBUTE = "javax.servlet.request.X509Certificate";

    static final String DB_PROBLEM_MSG = "There has been a problem with the DB.  It is suggested you "
        + "try the operation again.";

    private static final int DEFAULT_MAX_FEEDS = 10000;
    private static final int DEFAULT_MAX_SUBS = 100000;
    private static final int DEFAULT_POKETIMER1 = 5;
    private static final int DEFAULT_POKETIMER2 = 30;
    private static final String DEFAULT_PROVSRVR_NAME = "dmaap-dr-prov";

    //Common Errors
    static final String MISSING_ON_BEHALF = "Missing X-DMAAP-DR-ON-BEHALF-OF header.";
    static final String MISSING_FEED = "Missing or bad feed number.";
    static final String POLICY_ENGINE = "Policy Engine disallows access.";
    static final String UNAUTHORIZED = "Unauthorized.";
    static final String BAD_SUB = "Missing or bad subscription number.";
    static final String BAD_JSON = "Badly formed JSON";
    static final String BAD_URL = "Bad URL.";

    public static final String API = "/api/";
    static final String LOGS = "/logs/";
    public static final String TEXT_CT = "text/plain";
    static final String INGRESS = "/ingress/";
    static final String EGRESS = "/egress/";
    static final String NETWORK = "/network/";
    static final String GROUPID = "groupid";
    public static final String FEEDID = "feedid";
    static final String FEEDIDS = "feedids";
    static final String SUBID = "subid";
    static final String EVENT_TYPE = "eventType";
    static final String OUTPUT_TYPE = "output_type";
    static final String REASON_SQL = "reasonSQL";
    static final String JSON_HASH_STRING = "password";

    /**
     * A boolean to trigger one time "provisioning changed" event on startup.
     */
    private static boolean startmsgFlag = true;
    /**
     * This POD should require SSL connections from clients; pulled from the DB (PROV_REQUIRE_SECURE).
     */
    private static boolean requireSecure = true;
    /**
     * This POD should require signed, recognized certificates from clients; pulled from the DB (PROV_REQUIRE_CERT).
     */
    private static boolean requireCert = true;
    /**
     * The set of authorized addresses and networks; pulled from the DB (PROV_AUTH_ADDRESSES).
     */
    private static Set<String> authorizedAddressesAndNetworks = new HashSet<>();
    /**
     * The set of authorized names; pulled from the DB (PROV_AUTH_SUBJECTS).
     */
    private static Set<String> authorizedNames = new HashSet<>();
    /**
     * The FQDN of the initially "active" provisioning server in this Data Router ecosystem.
     */
    private static String initialActivePod;
    /**
     * The FQDN of the initially "standby" provisioning server in this Data Router ecosystem.
     */
    private static String initialStandbyPod;
    /**
     * The FQDN of this provisioning server in this Data Router ecosystem.
     */
    private static String thisPod;
    /**
     * "Timer 1" - used to determine when to notify nodes of provisioning changes.
     */
    private static long pokeTimer1;
    /**
     * "Timer 2" - used to determine when to notify nodes of provisioning changes.
     */
    private static long pokeTimer2;
    /**
     * Array of nodes names and/or FQDNs.
     */
    private static String[] nodes = new String[0];
    /**
     * Array of node IP addresses.
     */
    private static InetAddress[] nodeAddresses = new InetAddress[0];
    /**
     * Array of POD IP addresses.
     */
    private static InetAddress[] podAddresses = new InetAddress[0];
    /**
     * The maximum number of feeds allowed; pulled from the DB (PROV_MAXFEED_COUNT).
     */
    static int maxFeeds = 0;
    /**
     * The maximum number of subscriptions allowed; pulled from the DB (PROV_MAXSUB_COUNT).
     */
    static int maxSubs = 0;
    /**
     * The current number of feeds in the system.
     */
    static int activeFeeds = 0;
    /**
     * The current number of subscriptions in the system.
     */
    static int activeSubs = 0;

    /**
     * The standard FQDN of the provisioning server in this Data Router ecosystem.
     */
    private static String provName = "feeds-drtr.web.att.com";

    /**
     * The standard FQDN of the ACTIVE_POD provisioning server in this Data Router ecosystem.
     */
    private static String activeProvName = "feeds-drtr.web.att.com";

    /**
     * This logger is used to log provisioning events.
     */
    protected static EELFLogger eventlogger;
    /**
     * This logger is used to log internal events (errors, etc.)
     */
    protected static EELFLogger intlogger;
    /**
     * Authorizer - interface to the Policy Engine.
     */
    protected static Authorizer authz;
    /**
     * The Synchronizer used to sync active DB to standby one.
     */
    private static SynchronizerTask synctask = null;

    //Data Router Subscriber HTTPS Relaxation feature USERSTORYID:US674047.
    private InetAddress thishost;
    private InetAddress loopback;

    //DMAAP-597 (Tech Dept) REST request source IP auth relaxation to accommodate OOM kubernetes deploy
    private static String isAddressAuthEnabled = ProvRunner.getProvProperties()
        .getProperty("org.onap.dmaap.datarouter.provserver.isaddressauthenabled", "false");

    static String isCadiEnabled = ProvRunner.getProvProperties()
        .getProperty("org.onap.dmaap.datarouter.provserver.cadi.enabled", "false");

    /**
     * Initialize data common to all the provisioning server servlets.
     */
    protected BaseServlet() {
        setUpFields();
        if (authz == null) {
            authz = new ProvAuthorizer(this);
        }
        String name = this.getClass().getName();
        intlogger.info("PROV0002 Servlet " + name + " started.");
    }

    private static void setUpFields() {
        if (eventlogger == null) {
            eventlogger = EELFManager.getInstance().getLogger("EventLog");
        }
        if (intlogger == null) {
            intlogger = EELFManager.getInstance().getLogger("InternalLog");
        }
        if (startmsgFlag) {
            startmsgFlag = false;
            provisioningParametersChanged();
        }
        if (synctask == null) {
            synctask = SynchronizerTask.getSynchronizer();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            thishost = InetAddress.getLocalHost();
            loopback = InetAddress.getLoopbackAddress();
        } catch (UnknownHostException e) {
            intlogger.info("BaseServlet.init: " + e.getMessage(), e);
        }
    }

    /**
     * Get ID from Path.
     * @param req HTTPServletRequest
     * @return int ID
     */
    public static int getIdFromPath(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.length() < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Read the request's input stream and return a JSONObject from it.
     *
     * @param req the HTTP request
     * @return the JSONObject, or null if the stream cannot be parsed
     */
    JSONObject getJSONfromInput(HttpServletRequest req) {
        JSONObject jo = null;
        try {
            jo = new JSONObject(new JSONTokener(req.getInputStream()));
            if (intlogger.isDebugEnabled()) {
                intlogger.debug("JSON: " + hashPasswords(new JSONObject(jo.toString())).toString());
            }
        } catch (Exception e) {
            intlogger.info("Error reading JSON: " + e);
        }
        return jo;
    }

    public static JSONObject hashPasswords(JSONObject jo) {
        if (!jo.isNull("authorization")) {
            JSONArray endpointIds = jo.getJSONObject("authorization").getJSONArray("endpoint_ids");
            for (int index = 0; index < endpointIds.length(); index++) {
                if ((!endpointIds.getJSONObject(index).isNull(JSON_HASH_STRING))) {
                    String password = endpointIds.getJSONObject(index).get(JSON_HASH_STRING).toString();
                    processPassword(endpointIds, index, password);
                }
            }
        }
        if (!jo.isNull("delivery")) {
            JSONObject deliveryObj = jo.getJSONObject("delivery");
            String password = deliveryObj.get(JSON_HASH_STRING).toString();
            processPassword(deliveryObj, password);
        }
        return jo;
    }

    private static void processPassword(JSONArray endpointIds, int index, String password) {
        try {
            endpointIds.getJSONObject(index).put(JSON_HASH_STRING, DigestUtils.sha256Hex(password));
        } catch (JSONException e) {
            intlogger.info("Error reading JSON while hashing: " + e);
        }
    }

    private static void processPassword(JSONObject deliveryObj, String password) {
        try {
            deliveryObj.put(JSON_HASH_STRING, DigestUtils.sha256Hex(password));
        } catch (JSONException e) {
            intlogger.info("Error reading JSON while hashing: " + e);
        }
    }

    /**
     * Check if the remote host is authorized to perform provisioning. Is the request secure? Is it coming from an
     * authorized IP address or network (configured via PROV_AUTH_ADDRESSES)? Does it have a valid client certificate
     * (configured via PROV_AUTH_SUBJECTS)?
     *
     * @param request the request
     * @return an error string, or null if all is OK
     */
    String isAuthorizedForProvisioning(HttpServletRequest request) {
        if (!Boolean.parseBoolean(isAddressAuthEnabled)) {
            return null;
        }
        // Is the request https?
        if (requireSecure && !request.isSecure()) {
            return "Request must be made over an HTTPS connection.";
        }
        String remoteHostCheck = checkRemoteHostAuthorization(request);
        if (remoteHostCheck != null) {
            return remoteHostCheck;
        }
        // Does remote have a valid certificate?
        if (requireCert) {
            X509Certificate[] certs = (X509Certificate[]) request.getAttribute(CERT_ATTRIBUTE);
            if (certs == null || certs.length == 0) {
                return "Client certificate is missing.";
            }
            // cert[0] is the client cert
            // see http://www.proto.research.att.com/java/java7/api/javax/net/ssl/SSLSession.html#getPeerCertificates()
            String name = certs[0].getSubjectX500Principal().getName();
            if (!authorizedNames.contains(name)) {
                return "No authorized certificate found.";
            }
        }
        // No problems!
        return null;
    }

    @Nullable
    private String checkRemoteHostAuthorization(HttpServletRequest request) {
        // Is remote IP authorized?
        String remote = request.getRemoteAddr();
        try {
            boolean found = false;
            InetAddress ip = InetAddress.getByName(remote);
            for (String addrnet : authorizedAddressesAndNetworks) {
                found |= addressMatchesNetwork(ip, addrnet);
            }
            if (!found) {
                return "Unauthorized address: " + remote;
            }
        } catch (UnknownHostException e) {
            intlogger.error("PROV0051 BaseServlet.isAuthorizedForProvisioning: " + e.getMessage(), e);
            return "Unauthorized address: " + remote;
        }
        return null;
    }

    /**
     * Check if the remote IP address is authorized to see the /internal URL tree.
     *
     * @param request the HTTP request
     * @return true iff authorized
     */
    boolean isAuthorizedForInternal(HttpServletRequest request) {
        try {
            if (!Boolean.parseBoolean(isAddressAuthEnabled)) {
                return true;
            }
            InetAddress ip = InetAddress.getByName(request.getRemoteAddr());
            for (InetAddress node : getNodeAddresses()) {
                if (ip.equals(node)) {
                    return true;
                }
            }
            for (InetAddress pod : getPodAddresses()) {
                if (ip.equals(pod)) {
                    return true;
                }
            }
            if (ip.equals(thishost)) {
                return true;
            }
            if (ip.equals(loopback)) {
                return true;
            }
        } catch (UnknownHostException e) {
            intlogger.error("PROV0052 BaseServlet.isAuthorizedForInternal: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Check if an IP address matches a network address.
     *
     * @param ip the IP address
     * @param str  the network address; a bare IP address may be matched also
     * @return true if they intersect
     */
    private static boolean addressMatchesNetwork(InetAddress ip, String str) {
        int mlen = -1;
        int substr = str.indexOf('/');
        if (substr >= 0) {
            mlen = Integer.parseInt(str.substring(substr + 1));
            str = str.substring(0, substr);
        }
        try {
            InetAddress i2 = InetAddress.getByName(str);
            byte[] b1 = ip.getAddress();
            byte[] b2 = i2.getAddress();
            if (b1.length != b2.length) {
                return false;
            }
            if (mlen > 0) {
                byte[] masks = {
                    (byte) 0x00, (byte) 0x80, (byte) 0xC0, (byte) 0xE0,
                    (byte) 0xF0, (byte) 0xF8, (byte) 0xFC, (byte) 0xFE
                };
                byte mask = masks[mlen % 8];
                for (substr = mlen / 8; substr < b1.length; substr++) {
                    b1[substr] &= mask;
                    b2[substr] &= mask;
                    mask = 0;
                }
            }
            for (substr = 0; substr < b1.length; substr++) {
                if (b1[substr] != b2[substr]) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            intlogger.error("PROV0053 BaseServlet.addressMatchesNetwork: " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * Something has changed in the provisioning data. Start the timers that will cause the pre-packaged JSON string to
     * be regenerated, and cause nodes and the other provisioning server to be notified.
     */
    public static void provisioningDataChanged() {
        long now = System.currentTimeMillis();
        Poker pkr = Poker.getPoker();
        pkr.setTimers(now + (pokeTimer1 * 1000L), now + (pokeTimer2 * 1000L));
    }

    /**
     * Something in the parameters has changed, reload all parameters from the DB.
     */
    public static void provisioningParametersChanged() {
        Map<String, String> map = Parameters.getParameters();
        requireSecure = getBoolean(map, Parameters.PROV_REQUIRE_SECURE);
        requireCert = getBoolean(map, Parameters.PROV_REQUIRE_CERT);
        authorizedAddressesAndNetworks = getSet(map, Parameters.PROV_AUTH_ADDRESSES);
        authorizedNames = getSet(map, Parameters.PROV_AUTH_SUBJECTS);
        nodes = getSet(map, Parameters.NODES).toArray(new String[0]);
        maxFeeds = getInt(map, Parameters.PROV_MAXFEED_COUNT, DEFAULT_MAX_FEEDS);
        maxSubs = getInt(map, Parameters.PROV_MAXSUB_COUNT, DEFAULT_MAX_SUBS);
        pokeTimer1 = getInt(map, Parameters.PROV_POKETIMER1, DEFAULT_POKETIMER1);
        pokeTimer2 = getInt(map, Parameters.PROV_POKETIMER2, DEFAULT_POKETIMER2);

        // The domain used to generate a FQDN from the "bare" node names
        provName = getString(map, Parameters.PROV_NAME, DEFAULT_PROVSRVR_NAME);
        activeProvName = getString(map, Parameters.PROV_ACTIVE_NAME, provName);
        initialActivePod = getString(map, Parameters.ACTIVE_POD, "");
        initialStandbyPod = getString(map, Parameters.STANDBY_POD, "");

        //Adding new param for static Routing - Rally:US664862-1610
        String staticRoutingNodes = getString(map, Parameters.STATIC_ROUTING_NODES, "");
        activeFeeds = Feed.countActiveFeeds();
        activeSubs = Subscription.countActiveSubscriptions();
        try {
            thisPod = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            thisPod = "";
            intlogger.warn("PROV0014 Cannot determine the name of this provisioning server.", e);
        }

        // Normalize the nodes, and fill in nodeAddresses
        InetAddress[] na = new InetAddress[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            try {
                na[i] = InetAddress.getByName(nodes[i]);
                intlogger.debug("PROV0003 DNS lookup: " + nodes[i] + " => " + na[i].toString());
            } catch (UnknownHostException e) {
                na[i] = null;
                intlogger.warn("PROV0004 Cannot lookup " + nodes[i] + ": " + e.getMessage(), e);
            }
        }

        //Reset Nodes arr after - removing static routing Nodes, Rally Userstory - US664862 .
        List<String> filterNodes = new ArrayList<>();
        for (String node : nodes) {
            if (!staticRoutingNodes.contains(node)) {
                filterNodes.add(node);
            }
        }
        nodes = filterNodes.toArray(new String[0]);

        nodeAddresses = na;
        NodeClass.setNodes(nodes);        // update NODES table

        // Normalize the PODs, and fill in podAddresses
        String[] pods = getPods();
        na = new InetAddress[pods.length];
        for (int i = 0; i < pods.length; i++) {
            try {
                na[i] = InetAddress.getByName(pods[i]);
                intlogger.debug("PROV0003 DNS lookup: " + pods[i] + " => " + na[i].toString());
            } catch (UnknownHostException e) {
                na[i] = null;
                intlogger.warn("PROV0004 Cannot lookup " + pods[i] + ": " + e.getMessage(), e);
            }
        }
        podAddresses = na;

        // Update ThrottleFilter
        ThrottleFilter.configure();

        // Check if we are active or standby POD
        if (!isInitialActivePOD() && !isInitialStandbyPOD()) {
            intlogger.warn("PROV0015 This machine is neither the active nor the standby POD.");
        }
    }

    public static String getProvName() {
        return provName;
    }

    public static String getActiveProvName() {
        return activeProvName;
    }

    /**
     * Get an array of all node names in the DR network.
     *
     * @return an array of Strings
     */
    public static String[] getNodes() {
        return nodes;
    }

    /**
     * Get an array of all node InetAddresses in the DR network.
     *
     * @return an array of InetAddresses
     */
    private static InetAddress[] getNodeAddresses() {
        return nodeAddresses;
    }

    /**
     * Get an array of all POD names in the DR network.
     *
     * @return an array of Strings
     */
    public static String[] getPods() {
        return new String[]{initialActivePod, initialStandbyPod};
    }

    /**
     * Get an array of all POD InetAddresses in the DR network.
     *
     * @return an array of InetAddresses
     */
    private static InetAddress[] getPodAddresses() {
        return podAddresses;
    }

    /**
     * Gets the FQDN of the initially ACTIVE_POD provisioning server (POD). Note: this used to be called isActivePOD(),
     * however, that is a misnomer, as the active status could shift to the standby POD without these parameters
     * changing.  Hence, the function names have been changed to more accurately reflect their purpose.
     *
     * @return the FQDN
     */
    public static boolean isInitialActivePOD() {
        return thisPod.equals(initialActivePod);
    }

    /**
     * Gets the FQDN of the initially STANDBY_POD provisioning server (POD).Note: this used to be called isStandbyPOD(),
     * however, that is a misnomer, as the standby status could shift to the active POD without these parameters
     * changing.  Hence, the function names have been changed to more accurately reflect their purpose.
     *
     * @return the FQDN
     */
    public static boolean isInitialStandbyPOD() {
        return thisPod.equals(initialStandbyPod);
    }

    /**
     * INSERT an {@link Insertable} bean into the database.
     *
     * @param bean the bean representing a row to insert
     * @return true if the INSERT was successful
     */
    protected boolean doInsert(Insertable bean) {
        boolean rv;
        try (Connection conn = ProvDbUtils.getInstance().getConnection()) {
            rv = bean.doInsert(conn);
        } catch (SQLException e) {
            rv = false;
            intlogger.warn("PROV0005 doInsert: " + e.getMessage(), e);
        }
        return rv;
    }

    /**
     * UPDATE an {@link Updateable} bean in the database.
     *
     * @param bean the bean representing a row to update
     * @return true if the UPDATE was successful
     */
    protected boolean doUpdate(Updateable bean) {
        boolean rv;
        try (Connection conn = ProvDbUtils.getInstance().getConnection()) {
            rv = bean.doUpdate(conn);
        } catch (SQLException e) {
            rv = false;
            intlogger.warn("PROV0006 doUpdate: " + e.getMessage(), e);
        }
        return rv;
    }

    /**
     * DELETE an {@link Deleteable} bean from the database.
     *
     * @param bean the bean representing a row to delete
     * @return true if the DELETE was successful
     */
    protected boolean doDelete(Deleteable bean) {
        boolean rv;
        try (Connection conn = ProvDbUtils.getInstance().getConnection()) {
            rv = bean.doDelete(conn);
        } catch (SQLException e) {
            rv = false;
            intlogger.warn("PROV0007 doDelete: " + e.getMessage(), e);
        }
        return rv;
    }

    private static boolean getBoolean(Map<String, String> map, String name) {
        String str = map.get(name);
        return "true".equalsIgnoreCase(str);
    }

    private static String getString(Map<String, String> map, String name, String dflt) {
        String str = map.get(name);
        return (str != null) ? str : dflt;
    }

    private static int getInt(Map<String, String> map, String name, int dflt) {
        try {
            String str = map.get(name);
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static Set<String> getSet(Map<String, String> map, String name) {
        Set<String> set = new HashSet<>();
        String str = map.get(name);
        if (str != null) {
            String[] pp = str.split("\\|");
            if (pp != null) {
                for (String t : pp) {
                    String t2 = t.trim();
                    if (t2.length() > 0) {
                        set.add(t2);
                    }
                }
            }
        }
        return set;
    }

    /**
     * A class used to encapsulate a Content-type header, separating out the "version" attribute (which defaults to
     * "1.0" if missing).
     */
    public class ContentHeader {

        private String type;
        private Map<String, String> map = new HashMap<>();

        ContentHeader() {
            this("", "1.0");
        }

        ContentHeader(String headertype, String version) {
            type = headertype.trim();
            map.put("version", version);
        }

        public String getType() {
            return type;
        }

        String getAttribute(String key) {
            String str = map.get(key);
            if (str == null) {
                str = "";
            }
            return str;
        }
    }

    /**
     * Get the ContentHeader from an HTTP request.
     *
     * @param req the request
     * @return the header, encapsulated in a ContentHeader object
     */
    ContentHeader getContentHeader(HttpServletRequest req) {
        ContentHeader ch = new ContentHeader();
        String str = req.getHeader("Content-Type");
        if (str != null) {
            String[] pp = str.split(";");
            ch.type = pp[0].trim();
            for (int i = 1; i < pp.length; i++) {
                int ix = pp[i].indexOf('=');
                if (ix > 0) {
                    String type = pp[i].substring(0, ix).trim();
                    String version = pp[i].substring(ix + 1).trim();
                    ch.map.put(type, version);
                } else {
                    ch.map.put(pp[i].trim(), "");
                }
            }
        }
        return ch;
    }

    // Methods for the Policy Engine classes - ProvDataProvider interface
    @Override
    public String getFeedOwner(String feedId) {
        try {
            int intID = Integer.parseInt(feedId);
            Feed feed = Feed.getFeedById(intID);
            if (feed != null) {
                return feed.getPublisher();
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    @Override
    public String getFeedClassification(String feedId) {
        try {
            int intID = Integer.parseInt(feedId);
            Feed feed = Feed.getFeedById(intID);
            if (feed != null) {
                return feed.getAuthorization().getClassification();
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    @Override
    public String getSubscriptionOwner(String subId) {
        try {
            int intID = Integer.parseInt(subId);
            Subscription sub = Subscription.getSubscriptionById(intID);
            if (sub != null) {
                return sub.getSubscriber();
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    /*
     * @Method - isUserMemberOfGroup - Rally:US708115
     * @Params - group object and user to check if exists in given group
     * @return - boolean value /true/false
     */
    private boolean isUserMemberOfGroup(Group group, String user) {

        String groupDetails = group.getMembers().replace("]", "").replace("[", "");
        String[] str = groupDetails.split("},");

        for (String value : str) {
            JSONObject jsonObj;
            try {
                jsonObj = new JSONObject(value + "}");
                if (jsonObj.get("id").equals(user)) {
                    return true;
                }
            } catch (JSONException e) {
                intlogger.error("JSONException: " + e.getMessage(), e);
            }
        }
        return false;

    }

    /*
     * @Method - getGroupByFeedGroupId- Rally:US708115
     * @Params - User to check in group and feedid which is assigned the group.
     * @return - string value groupid/null
     */
    @Override
    public String getGroupByFeedGroupId(String owner, String feedId) {
        try {
            Feed feed = Feed.getFeedById(Integer.parseInt(feedId));
            if (feed != null) {
                int groupid = feed.getGroupid();
                if (groupid > 0) {
                    Group group = Group.getGroupById(groupid);
                    if (group != null && isUserMemberOfGroup(group, owner)) {
                        return group.getAuthid();
                    }
                }
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    /*
     * @Method - getGroupBySubGroupId - Rally:US708115
     * @Params - User to check in group and subid which is assigned the group.
     * @return - string value groupid/null
     */
    @Override
    public String getGroupBySubGroupId(String owner, String subId) {
        try {
            int intID = Integer.parseInt(subId);
            Subscription sub = Subscription.getSubscriptionById(intID);
            if (sub != null) {
                int groupid = sub.getGroupid();
                if (groupid > 0) {
                    Group group = Group.getGroupById(groupid);
                    if (group != null && isUserMemberOfGroup(group, owner)) {
                        return group.getAuthid();
                    }
                }
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    /*
     * @Method - setIpFqdnRequestIDandInvocationIDForEelf
     * @Params - method, prints method name in EELF log.
     * @Params- Req, Request used to get RequestId and InvocationId
     */
    void setIpFqdnRequestIDandInvocationIDForEelf(String method, HttpServletRequest req) {
        setIpFqdnForEelf(method);
        setMDC(req, "X-ONAP-RequestID", MDC_KEY_REQUEST_ID);
        setMDC(req, "X-InvocationID", "InvocationId");
    }

    private void setMDC(HttpServletRequest req, String headerName, String keyName) {
        String mdcId = req.getHeader(headerName);
        if (StringUtils.isBlank(mdcId)) {
            mdcId = UUID.randomUUID().toString();
        }
        MDC.put(keyName, mdcId);
    }

    /*
     * @Method - setIpFqdnRequestIdForEelf - Rally:US664892
     * @Params - method, prints method name in EELF log.
     */
    void setIpFqdnForEelf(String method) {
        MDC.clear();
        MDC.put(MDC_SERVICE_NAME, method);
        try {
            MDC.put(MDC_SERVER_FQDN, InetAddress.getLocalHost().getHostName());
            MDC.put(MDC_SERVER_IP_ADDRESS, InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            intlogger.error("Exception: " + e.getMessage(), e);
        }

    }

    /*
     * AAF changes: TDP EPIC US# 307413
     * @Method - getFeedPermission - Forming permission string for feed part to check AAF access in CADI Framework
     * @Params - aafInstance Passing aafInstance as it's used in permission string
     * @Params - userAction Passing CONST values to set different actions in permission string
     */
    String getFeedPermission(String aafInstance, String userAction) {
        try {
            Properties props = ProvRunner.getProvProperties();
            String type = props.getProperty(AAF_CADI_FEED_TYPE, AAF_CADI_FEED);
            String action;
            switch (userAction) {
                case CREATE_PERMISSION:
                    action = CREATE_PERMISSION;
                    break;
                case EDIT_PERMISSION:
                    action = EDIT_PERMISSION;
                    break;
                case DELETE_PERMISSION:
                    action = DELETE_PERMISSION;
                    break;
                case PUBLISH_PERMISSION:
                    action = PUBLISH_PERMISSION;
                    break;
                case SUSPEND_PERMISSION:
                    action = SUSPEND_PERMISSION;
                    break;
                case RESTORE_PERMISSION:
                    action = RESTORE_PERMISSION;
                    break;
                default:
                    action = "*";
            }
            if (aafInstance == null || "".equals(aafInstance)) {
                aafInstance = props.getProperty(AAF_INSTANCE, "org.onap.dmaap-dr.NoInstanceDefined");
            }
            return type + "|" + aafInstance + "|" + action;
        } catch (Exception e) {
            intlogger.error("PROV7005 BaseServlet.getFeedPermission: " + e.getMessage(), e);
        }
        return null;
    }

    /*
     * AAF changes: TDP EPIC US# 307413
     * @Method - getSubscriberPermission - Forming permission string for subscription part to check
     * AAF access in CADI Framework
     * @Params - aafInstance Passing aafInstance as it's used in permission string
     * @Params - userAction Passing CONST values to set different actions in permission string
     */
    String getSubscriberPermission(String aafInstance, String userAction) {
        try {
            Properties props = ProvRunner.getProvProperties();
            String type = props.getProperty(AAF_CADI_SUB_TYPE, AAF_CADI_SUB);
            String action;
            switch (userAction) {
                case SUBSCRIBE_PERMISSION:
                    action = SUBSCRIBE_PERMISSION;
                    type = props.getProperty(AAF_CADI_FEED_TYPE, AAF_CADI_FEED);
                    break;
                case EDIT_PERMISSION:
                    action = EDIT_PERMISSION;
                    break;
                case DELETE_PERMISSION:
                    action = DELETE_PERMISSION;
                    break;
                case RESTORE_PERMISSION:
                    action = RESTORE_PERMISSION;
                    break;
                case SUSPEND_PERMISSION:
                    action = SUSPEND_PERMISSION;
                    break;
                case PUBLISH_PERMISSION:
                    action = PUBLISH_PERMISSION;
                    break;
                case APPROVE_SUB_PERMISSION:
                    action = APPROVE_SUB_PERMISSION;
                    type = props.getProperty(AAF_CADI_FEED_TYPE, AAF_CADI_FEED);
                    break;
                default:
                    action = "*";
            }
            if (aafInstance == null || "".equals(aafInstance)) {
                aafInstance = props.getProperty(AAF_INSTANCE, "org.onap.dmaap-dr.NoInstanceDefined");
            }
            return type + "|" + aafInstance + "|" + action;
        } catch (Exception e) {
            intlogger.error("PROV7005 BaseServlet.getSubscriberPermission: " + e.getMessage(), e);
        }
        return null;
    }
}
