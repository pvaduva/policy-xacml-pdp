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

package org.onap.policy.pdp.xacml.application.common;

import com.att.research.xacml.api.Identifier;
import com.att.research.xacml.api.XACML3;
import com.att.research.xacml.std.IdentifierImpl;

public final class ToscaDictionary {

    private ToscaDictionary() {
        super();
    }

    /*
     * These are the ID's for various TOSCA Policy Types we are supporting in the Applications.
     */
    public static final Identifier URN_ONAP =
            new IdentifierImpl("urn:org:onap");

    public static final Identifier ID_RESOURCE_POLICY_ID =
            XACML3.ID_RESOURCE_RESOURCE_ID;

    public static final Identifier ID_RESOURCE_POLICY_TYPE =
            new IdentifierImpl(URN_ONAP, "policy-type");

    public static final Identifier ID_RESOURCE_POLICY_TYPE_VERSION =
            new IdentifierImpl(URN_ONAP, "policy-type-version");

    public static final Identifier ID_OBLIGATION_REST_BODY =
            new IdentifierImpl(URN_ONAP, "rest:body");

    public static final Identifier ID_OBLIGATION_POLICY_MONITORING =
            new IdentifierImpl(URN_ONAP, ":obligation:monitoring");

    public static final Identifier ID_OBLIGATION_POLICY_MONITORING_CONTENTS =
            new IdentifierImpl(URN_ONAP, ":obligation:monitoring:contents");

    public static final Identifier ID_OBLIGATION_POLICY_MONITORING_CATEGORY =
            XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE;

    public static final Identifier ID_OBLIGATION_POLICY_MONITORING_DATATYPE =
            XACML3.ID_DATATYPE_STRING;

    public static final Identifier ID_OBLIGATION_ISSUER =
            new IdentifierImpl(URN_ONAP, "issuer:monitoring");


}