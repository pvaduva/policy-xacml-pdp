/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.pdpx.main.startstop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.BeforeClass;

import org.junit.Test;
import org.onap.policy.pdpx.main.PolicyXacmlPdpException;
import org.onap.policy.pdpx.main.parameters.CommonTestData;
import org.onap.policy.pdpx.main.parameters.XacmlPdpParameterGroup;
import org.onap.policy.pdpx.main.parameters.XacmlPdpParameterHandler;


/**
 * Class to perform unit test of XacmlPdpActivator.
 *
 */
public class TestXacmlPdpActivator {
    private static XacmlPdpActivator activator = null;

    /**
     * Setup the tests.
     * @throws PolicyXacmlPdpException when Xacml PDP Exceptional condition occurs
     */
    @BeforeClass
    public static void setup() throws PolicyXacmlPdpException {
        final String[] xacmlPdpConfigParameters = {"-c", "parameters/XacmlPdpConfigParameters.json"};

        final XacmlPdpCommandLineArguments arguments = new XacmlPdpCommandLineArguments(xacmlPdpConfigParameters);

        final XacmlPdpParameterGroup parGroup = new XacmlPdpParameterHandler().getParameters(arguments);

        activator = new XacmlPdpActivator(parGroup);
        activator.initialize();
    }

    @Test
    public void testXacmlPdpActivator() throws PolicyXacmlPdpException {
        assertTrue(activator.getParameterGroup().isValid());
        assertEquals(CommonTestData.PDPX_GROUP_NAME, activator.getParameterGroup().getName());
    }

    @After
    public void teardown() throws PolicyXacmlPdpException {
        activator.terminate();
    }
}