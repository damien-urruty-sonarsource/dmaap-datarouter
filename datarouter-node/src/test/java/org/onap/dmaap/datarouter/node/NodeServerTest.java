/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019 Nordix Foundation.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */
package org.onap.dmaap.datarouter.node;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("org.onap.dmaap.datarouter.node.NodeConfigManager")
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class NodeServerTest {

    private NodeConfigManager config = mock(NodeConfigManager.class);
    @Before
    public void setUp() throws Exception {
        setUpConfig();
        setUpNodeMainDelivery();
        createFilesAndDirectories();
    }

    @AfterClass
    public static void tearDown() {
        deleteCreatedDirectories();
    }

    @Test
    public void Verify_Node_Server_Is_Configured_Correctly() {
        Assert.assertNotNull(NodeServer.getServerInstance());
    }

    private void setUpConfig() throws IllegalAccessException {
        PowerMockito.mockStatic(NodeConfigManager.class);
        when(config.isShutdown()).thenReturn(false);
        when(config.isConfigured()).thenReturn(true);
        when(config.getSpoolDir()).thenReturn("spool/f");
        when(config.getSpoolBase()).thenReturn("spool");
        when(config.getLogDir()).thenReturn("log/dir");
        when(config.getPublishId()).thenReturn("User1");
        when(config.isAnotherNode(anyString(), anyString())).thenReturn(true);
        when(config.getEventLogInterval()).thenReturn("40");
        when(config.isDeletePermitted("1")).thenReturn(true);
        when(config.getAllDests()).thenReturn(new DestInfo[0]);
        when(config.getKSType()).thenReturn("PKCS12");
        when(config.getKSFile()).thenReturn("src/test/resources/aaf/org.onap.dmaap-dr.p12");
        when(config.getKSPass()).thenReturn("tVac2#@Stx%tIOE^x[c&2fgZ");
        when(config.getTstype()).thenReturn("jks");
        when(config.getTsfile()).thenReturn("src/test/resources/aaf/org.onap.dmaap-dr.trust.jks");
        when(config.getTspass()).thenReturn("XHX$2Vl?Lk*2CB.i1+ZFAhZd");
        FieldUtils.writeDeclaredStaticField(NodeServlet.class, "config", config, true);
        FieldUtils.writeDeclaredStaticField(NodeRunner.class, "nodeConfigManager", config, true);
        PowerMockito.when(NodeConfigManager.getInstance()).thenReturn(config);
    }

    private void setUpNodeMainDelivery() throws IllegalAccessException{
        Delivery delivery = mock(Delivery.class);
        doNothing().when(delivery).resetQueue(anyObject());
        FieldUtils.writeDeclaredStaticField(NodeServer.class, "delivery", delivery, true);
    }

    private void createFilesAndDirectories() throws IOException {
        File nodeDir = new File("spool/n/172.0.0.1");
        File spoolDir = new File("spool/s/0/1");
        File dataFile = new File("spool/s/0/1/dmaap-dr-node.1234567");
        File metaDataFile = new File("spool/s/0/1/dmaap-dr-node.1234567.M");
        nodeDir.mkdirs();
        spoolDir.mkdirs();
        dataFile.createNewFile();
        metaDataFile.createNewFile();
    }

    private static void deleteCreatedDirectories() {
        File spoolDir = new File("spool");
        delete(spoolDir);
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles()) {
                delete(f);
            }
        }
        if (!file.delete()) {
            System.out.println("Failed to delete: " + file);
        }
    }

}
