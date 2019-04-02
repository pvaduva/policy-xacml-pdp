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

package org.onap.policy.pdpx.main.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.gson.GsonMessageBodyHandler;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.models.decisions.concepts.DecisionRequest;
import org.onap.policy.models.decisions.concepts.DecisionResponse;
import org.onap.policy.models.errors.concepts.ErrorResponse;
import org.onap.policy.pdpx.main.PolicyXacmlPdpException;
import org.onap.policy.pdpx.main.startstop.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestDecision {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDecision.class);

    private static Main main;

    /**
     * BeforeClass setup environment.
     */
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
        main = startXacmlPdpService();
    }

    @AfterClass
    public static void after() throws PolicyXacmlPdpException {
        stopXacmlPdpService(main);
    }

    @Test
    public void testDecision_UnsupportedAction() throws KeyManagementException, NoSuchAlgorithmException,
        ClassNotFoundException {

        LOGGER.info("Running test testDecision_UnsupportedAction");

        DecisionRequest request = new DecisionRequest();
        request.setOnapName("DROOLS");
        request.setAction("foo");
        Map<String, Object> guard = new HashMap<String, Object>();
        guard.put("actor", "foo");
        guard.put("recipe", "bar");
        guard.put("target", "somevnf");
        guard.put("clname", "phoneyloop");
        request.setResource(guard);

        ErrorResponse response = getErrorDecision(request);
        LOGGER.info("Response {}", response);
        assertThat(response.getResponseCode()).isEqualTo(Status.BAD_REQUEST);
        assertThat(response.getErrorMessage()).isEqualToIgnoringCase("No application for action foo");
    }

    @Test
    public void testDecision_Guard() throws InterruptedException, IOException {
        LOGGER.info("Running test testDecision_Guard");

        DecisionRequest request = new DecisionRequest();
        request.setOnapName("DROOLS");
        request.setAction("guard");
        Map<String, Object> guard = new HashMap<String, Object>();
        guard.put("actor", "foo");
        guard.put("recipe", "bar");
        guard.put("target", "somevnf");
        guard.put("clname", "phoneyloop");
        request.setResource(guard);

        DecisionResponse response = getDecision(request);
        LOGGER.info("Response {}", response);
        //assertThat(response.getErrorMessage()).isEqualToIgnoringCase("No application for action foo");
    }

    private static Main startXacmlPdpService() {
        final String[] XacmlPdpConfigParameters = {"-c", "parameters/XacmlPdpConfigParameters.json", "-p",
            "parameters/topic.properties"};
        return new Main(XacmlPdpConfigParameters);
    }

    private static void stopXacmlPdpService(final Main main) throws PolicyXacmlPdpException {
        main.shutdown();
    }

    private DecisionResponse getDecision(DecisionRequest request) throws InterruptedException, IOException {
        final ClientConfig clientConfig = new ClientConfig();

        final HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic("healthcheck", "zb!XztG34");
        clientConfig.register(feature);

        final Client client = ClientBuilder.newClient(clientConfig);
        final WebTarget webTarget = client.target("http://localhost:6969/policy/pdpx/v1/decision");

        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);

        if (!NetworkUtil.isTcpPortOpen("localhost", 6969, 6, 10000L)) {
            throw new IllegalStateException("Cannot connect to port 6969");
        }

        return invocationBuilder.post(Entity.json(request), DecisionResponse.class);
    }

    private ErrorResponse getErrorDecision(DecisionRequest request) throws KeyManagementException,
        NoSuchAlgorithmException, ClassNotFoundException {

        HttpClient client = getNoAuthHttpClient();

        Entity<DecisionRequest> entityRequest = Entity.entity(request, MediaType.APPLICATION_JSON);
        Response response = client.post("", entityRequest, Collections.emptyMap());

        assertEquals(400, response.getStatus());

        return HttpClient.getBody(response, ErrorResponse.class);
    }

    private HttpClient getNoAuthHttpClient()
            throws KeyManagementException, NoSuchAlgorithmException, ClassNotFoundException {
        return HttpClient.factory.build(BusTopicParams.builder()
                .clientName("testDecisionClient")
                .serializationProvider(GsonMessageBodyHandler.class.getName())
                .useHttps(false).allowSelfSignedCerts(false).hostname("localhost").port(6969)
                .basePath("policy/pdpx/v1/decision")
                .userName("healthcheck").password("zb!XztG34").managed(true).build());
    }


}