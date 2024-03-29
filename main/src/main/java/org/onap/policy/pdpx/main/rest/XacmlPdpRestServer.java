/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Nordix Foundation.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.parameters.RestServerParameters;
import org.onap.policy.common.gson.GsonMessageBodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to manage life cycle of xacml pdp rest server.
 *
 */
public class XacmlPdpRestServer implements Startable {

    private static final String SEPARATOR = ".";
    private static final String HTTP_SERVER_SERVICES = "http.server.services";
    private static final Logger LOGGER = LoggerFactory.getLogger(XacmlPdpRestServer.class);

    private List<HttpServletServer> servers = new ArrayList<>();

    private final RestServerParameters restServerParameters;

    /**
     * Constructor for instantiating XacmlPdpRestServer.
     *
     * @param restServerParameters the rest server parameters
     */
    public XacmlPdpRestServer(final RestServerParameters restServerParameters) {
        this.restServerParameters = restServerParameters;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean start() {
        try {
            LOGGER.info("Starting XacmlPdpRestServer...");

            //
            // Get the server properties
            //
            servers = HttpServletServerFactoryInstance.getServerFactory().build(getServerProperties());
            //
            // Start all the servers
            //
            for (final HttpServletServer server : servers) {
                if (server.isAaf()) {
                    server.addFilterClass(null, XacmlPdpAafFilter.class.getName());
                }
                server.start();
            }
            LOGGER.info("servers are started");
        } catch (final Exception exp) {
            LOGGER.error("Failed to start xacml pdp http server", exp);
            return false;
        }
        return true;
    }

    /**
     * Creates the server properties object using restServerParameters.
     *
     * @return the properties object
     */
    private Properties getServerProperties() {
        final Properties props = new Properties();
        props.setProperty(HTTP_SERVER_SERVICES, restServerParameters.getName());
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".host",
                restServerParameters.getHost());
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".port",
                Integer.toString(restServerParameters.getPort()));
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".restClasses",
                XacmlPdpRestController.class.getName());
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".managed", "false");
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".swagger", "true");
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".userName",
                restServerParameters.getUserName());
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".password",
                restServerParameters.getPassword());
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".https",
                String.valueOf(restServerParameters.isHttps()));
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".aaf",
                String.valueOf(restServerParameters.isAaf()));
        props.setProperty(HTTP_SERVER_SERVICES + SEPARATOR + restServerParameters.getName() + ".serialization.provider",
                GsonMessageBodyHandler.class.getName());
        return props;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean stop() {
        for (final HttpServletServer server : servers) {
            try {
                server.shutdown();
            } catch (final Exception exp) {
                LOGGER.error("Failed to stop xacml pdp http server", exp);
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown() {
        stop();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isAlive() {
        return !servers.isEmpty();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("XacmlPdpRestServer [servers=");
        builder.append(servers);
        builder.append("]");
        return builder.toString();
    }

}
