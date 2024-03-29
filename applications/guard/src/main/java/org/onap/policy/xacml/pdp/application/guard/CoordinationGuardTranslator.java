/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
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

package org.onap.policy.xacml.pdp.application.guard;

import com.att.research.xacml.api.Request;
import com.att.research.xacml.api.Response;
import com.att.research.xacml.util.XACMLPolicyScanner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicyType;

import org.apache.commons.io.IOUtils;
import org.onap.policy.models.decisions.concepts.DecisionRequest;
import org.onap.policy.models.decisions.concepts.DecisionResponse;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.pdp.xacml.application.common.ToscaPolicyConversionException;
import org.onap.policy.pdp.xacml.application.common.ToscaPolicyTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class CoordinationGuardTranslator implements ToscaPolicyTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinationGuardTranslator.class);

    public CoordinationGuardTranslator() {
        super();
    }

    @Override
    public PolicyType convertPolicy(ToscaPolicy toscaPolicy) throws ToscaPolicyConversionException {
        LOGGER.debug("Using CoordinationGuardTranslator.convertPolicy");
        //
        // Policy name should be at the root
        //
        String type = toscaPolicy.getType();
        String coordinationFunctionPath = "src/main/resources/coordination/function";
        Map<String, Object> policyProps = toscaPolicy.getProperties();
        LOGGER.debug("path = {}", coordinationFunctionPath);
        LOGGER.debug("props = {}", policyProps);
        @SuppressWarnings("unchecked")
        List<String> controlLoop = (List<String>) policyProps.get("controlLoop");
        CoordinationDirective cd = new CoordinationDirective();
        cd.setCoordinationFunction(type);
        cd.setControlLoop(controlLoop);
        LOGGER.debug("CoordinationDirective = {}", cd);
        //
        // Generate the xacml policy as a string
        //
        String xacmlStr = generateXacmlFromCoordinationDirective(cd, coordinationFunctionPath);
        LOGGER.debug("xacmlStr\n{}", xacmlStr);
        //
        // Scan the string and convert to PoilcyType
        //
        try (InputStream is = new ByteArrayInputStream(xacmlStr.getBytes(StandardCharsets.UTF_8))) {
            return (PolicyType) XACMLPolicyScanner.readPolicy(is);
        } catch (IOException e) {
            throw new ToscaPolicyConversionException("Failed to read policy", e);
        }
    }

    @Override
    public Request convertRequest(DecisionRequest request) {
        LOGGER.info("this convertRequest shouldn't be used");
        return null;
    }

    @Override
    public DecisionResponse convertResponse(Response xacmlResponse) {
        LOGGER.info("this convertResponse shouldn't be used");
        return null;
    }

    /**
     * Load YAML coordination directive.
     *
     * @param directiveFilename yaml directive file to load
     * @return the CoordinationDirective
     */
    public static CoordinationDirective loadCoordinationDirectiveFromFile(String directiveFilename) {
        try (InputStream is = new FileInputStream(new File(directiveFilename))) {
            String contents = IOUtils.toString(is, StandardCharsets.UTF_8);
            //
            // Read the yaml into our Java Object
            //
            Yaml yaml = new Yaml(new Constructor(CoordinationDirective.class));
            Object obj = yaml.load(contents);
            LOGGER.debug(contents);
            return (CoordinationDirective) obj;
        } catch (IOException e) {
            LOGGER.error("Error while loading YAML coordination directive", e);
        }
        return null;
    }

    /**
     * Generate Xacml rule implementing specified CoordinationDirective.
     *
     * @param cd the CoordinationDirective
     * @param protoDir the directory containing Xacml implementation prototypes
     * @return the generated Xacml policy
     */
    public static String generateXacmlFromCoordinationDirective(CoordinationDirective cd,
                                                                String protoDir) throws ToscaPolicyConversionException {
        /*
         * Determine file names
         */
        String xacmlProtoFilename = protoDir + File.separator + cd.getCoordinationFunction() + ".xml";
        LOGGER.debug("xacmlProtoFilename={}", xacmlProtoFilename);
        /*
         * Values to be used for placeholders
         */
        final String uniqueId = UUID.randomUUID().toString();
        final String cLOne = cd.getControlLoop(0);
        final String cLTwo = cd.getControlLoop(1);
        /*
         * Replace function placeholders with appropriate values
         */
        try (Stream<String> stream = Files.lines(Paths.get(xacmlProtoFilename))) {
            return stream.map(s -> s.replaceAll("UNIQUE_ID", uniqueId))
                .map(s -> s.replaceAll("CONTROL_LOOP_ONE", cLOne))
                .map(s -> s.replaceAll("CONTROL_LOOP_TWO", cLTwo))
                .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new
                ToscaPolicyConversionException("Error while generating XACML policy for coordination directive", e);
        }
    }

}
