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

import com.att.research.xacml.api.DataTypeException;
import com.att.research.xacml.api.Decision;
import com.att.research.xacml.api.Identifier;
import com.att.research.xacml.api.Request;
import com.att.research.xacml.api.Response;
import com.att.research.xacml.api.Result;
import com.att.research.xacml.api.XACML3;
import com.att.research.xacml.std.annotations.RequestParser;

import java.util.Collection;
import java.util.Map;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.AdviceExpressionType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AdviceExpressionsType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AllOfType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AnyOfType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.ApplyType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeAssignmentExpressionType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeDesignatorType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.AttributeValueType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.ConditionType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.EffectType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.MatchType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.ObjectFactory;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicyType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.RuleType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.TargetType;

import org.onap.policy.models.decisions.concepts.DecisionRequest;
import org.onap.policy.models.decisions.concepts.DecisionResponse;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.pdp.xacml.application.common.ToscaDictionary;
import org.onap.policy.pdp.xacml.application.common.ToscaPolicyConversionException;
import org.onap.policy.pdp.xacml.application.common.ToscaPolicyTranslator;
import org.onap.policy.pdp.xacml.application.common.ToscaPolicyTranslatorUtils;
import org.onap.policy.pdp.xacml.application.common.operationshistory.CountRecentOperationsPip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyGuardTranslator implements ToscaPolicyTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyGuardTranslator.class);

    private static final String FIELD_GUARD_ACTIVE_START = "guardActiveStart";
    private static final String FIELD_GUARD_ACTIVE_END = "guardActiveEnd";
    private static final String FIELD_TARGET = "targets";

    public LegacyGuardTranslator() {
        super();
    }

    @Override
    public PolicyType convertPolicy(ToscaPolicy toscaPolicy) throws ToscaPolicyConversionException {
        //
        // Policy name should be at the root
        //
        String policyName = toscaPolicy.getMetadata().get("policy-id");
        //
        // Set it as the policy ID
        //
        PolicyType newPolicyType = new PolicyType();
        newPolicyType.setPolicyId(policyName);
        //
        // Optional description
        //
        newPolicyType.setDescription(toscaPolicy.getDescription());
        //
        // There should be a metadata section
        //
        this.fillMetadataSection(newPolicyType, toscaPolicy.getMetadata());
        //
        // Set the combining rule
        //
        newPolicyType.setRuleCombiningAlgId(XACML3.ID_RULE_DENY_UNLESS_PERMIT.stringValue());
        //
        // Generate the TargetType - add true if not blacklist
        //
        newPolicyType.setTarget(this.generateTargetType(toscaPolicy.getProperties(),
                ! "onap.policies.controlloop.guard.Blacklist".equals(toscaPolicy.getType())));
        //
        // Now create the Permit Rule
        //
        RuleType rule = generatePermitRule(policyName, toscaPolicy.getType(), toscaPolicy.getProperties());
        //
        // Check if we were able to create the rule
        //
        if (rule == null) {
            LOGGER.error("Failed to create rule");
            return null;
        }
        //
        // Add the rule to the policy
        //
        newPolicyType.getCombinerParametersOrRuleCombinerParametersOrVariableDefinition().add(rule);
        //
        // Return our new policy
        //
        return newPolicyType;
    }

    @Override
    public Request convertRequest(DecisionRequest request) {
        LOGGER.info("Converting Request {}", request);
        try {
            return RequestParser.parseRequest(LegacyGuardPolicyRequest.createInstance(request));
        } catch (IllegalArgumentException | IllegalAccessException | DataTypeException e) {
            LOGGER.error("Failed to convert DecisionRequest: {}", e);
        }
        //
        // TODO throw exception
        //
        return null;
    }

    @Override
    public DecisionResponse convertResponse(Response xacmlResponse) {
        LOGGER.info("Converting Response {}", xacmlResponse);
        DecisionResponse decisionResponse = new DecisionResponse();
        //
        // Iterate through all the results
        //
        for (Result xacmlResult : xacmlResponse.getResults()) {
            //
            // Check the result
            //
            if (xacmlResult.getDecision() == Decision.PERMIT) {
                //
                // Just simply return a Permit response
                //
                decisionResponse.setStatus(Decision.PERMIT.toString());
            }
            if (xacmlResult.getDecision() == Decision.DENY) {
                //
                // Just simply return a Deny response
                //
                decisionResponse.setStatus(Decision.DENY.toString());
            }
            if (xacmlResult.getDecision() == Decision.NOTAPPLICABLE) {
                //
                // There is no guard policy, so we return a permit
                //
                decisionResponse.setStatus(Decision.PERMIT.toString());
            }
        }

        return decisionResponse;
    }

    /**
     * From the TOSCA metadata section, pull in values that are needed into the XACML policy.
     *
     * @param policy Policy Object to store the metadata
     * @param map The Metadata TOSCA Map
     * @return Same Policy Object
     * @throws ToscaPolicyConversionException If there is something missing from the metadata
     */
    protected PolicyType fillMetadataSection(PolicyType policy,
            Map<String, String> map) throws ToscaPolicyConversionException {
        if (! map.containsKey("policy-id")) {
            throw new ToscaPolicyConversionException(policy.getPolicyId() + " missing metadata policy-id");
        } else {
            //
            // Do nothing here - the XACML PolicyId is used from TOSCA Policy Name field
            //
        }
        if (! map.containsKey("policy-version")) {
            throw new ToscaPolicyConversionException(policy.getPolicyId() + " missing metadata policy-version");
        } else {
            //
            // Add in the Policy Version
            //
            policy.setVersion(map.get("policy-version").toString());
        }
        return policy;
    }

    protected TargetType generateTargetType(Map<String, Object> properties, boolean addTargets) {
        //
        // Go through potential properties
        //
        AllOfType allOf = new AllOfType();
        if (properties.containsKey("actor")) {
            addMatch(allOf, properties.get("actor"), ToscaDictionary.ID_RESOURCE_GUARD_ACTOR);
        }
        if (properties.containsKey("recipe")) {
            addMatch(allOf, properties.get("recipe"), ToscaDictionary.ID_RESOURCE_GUARD_RECIPE);
        }
        if (addTargets) {
            if (properties.containsKey("targets")) {
                addMatch(allOf, properties.get("targets"), ToscaDictionary.ID_RESOURCE_GUARD_TARGETID);
            }
        }
        if (properties.containsKey("clname")) {
            addMatch(allOf, properties.get("clname"), ToscaDictionary.ID_RESOURCE_GUARD_CLNAME);
        }
        //
        // Create target
        //
        TargetType target = new TargetType();
        AnyOfType anyOf = new AnyOfType();
        anyOf.getAllOf().add(allOf);
        target.getAnyOf().add(anyOf);
        return target;
    }

    private static AllOfType addMatch(AllOfType allOf, Object value, Identifier attributeId) {
        if (value instanceof String) {
            if (".*".equals(value.toString())) {
                //
                // There's no point to even have a match
                //
                return allOf;
            } else {
                //
                // Exact match
                //
                MatchType match = ToscaPolicyTranslatorUtils.buildMatchTypeDesignator(
                    XACML3.ID_FUNCTION_STRING_EQUAL,
                    value,
                    XACML3.ID_DATATYPE_STRING,
                    attributeId,
                    XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE);

                allOf.getMatch().add(match);
            }
            return allOf;
        }
        if (value instanceof Collection) {
            //
            // TODO support a collection of that attribute
            //
        }
        return allOf;
    }

    private static RuleType generatePermitRule(String policyName, String policyType, Map<String, Object> properties)
            throws ToscaPolicyConversionException {
        //
        // Now determine which policy type we are generating
        //
        if ("onap.policies.controlloop.guard.FrequencyLimiter".equals(policyType)) {
            return generateFrequencyPermit(policyName, properties);
        } else if ("onap.policies.controlloop.guard.MinMax".equals(policyType)) {
            return generateMinMaxPermit(policyName, properties);
        } else if ("onap.policies.controlloop.guard.Blacklist".equals(policyType)) {
            return generateBlacklistPermit(policyName, properties);
        }
        LOGGER.error("Missing policy type in the policy");
        return null;
    }

    private static RuleType generateFrequencyPermit(String policyName, Map<String, Object> properties)
            throws ToscaPolicyConversionException {
        //
        // See if its possible to generate a count
        //
        Integer limit = parseInteger(properties.get("limit").toString());
        if (limit == null) {
            LOGGER.error("Must have a limit value for frequency guard policy to be created");
            return null;
        }
        //
        // Get the properties that are common among guards
        //
        String timeWindow = null;
        if (properties.containsKey("timeWindow")) {
            Integer intTimeWindow = parseInteger(properties.get("timeWindow").toString());
            if (intTimeWindow == null) {
                throw new ToscaPolicyConversionException("timeWindow is not an integer");
            }
            timeWindow = intTimeWindow.toString();
        }
        String timeUnits = null;
        if (properties.containsKey("timeUnits")) {
            timeUnits = properties.get("timeUnits").toString();
        }
        String guardActiveStart = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_START)) {
            guardActiveStart = properties.get(FIELD_GUARD_ACTIVE_START).toString();
        }
        String guardActiveEnd = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_END)) {
            guardActiveEnd = properties.get(FIELD_GUARD_ACTIVE_END).toString();
        }
        //
        // Generate the time in range
        //
        final ApplyType timeRange = generateTimeInRange(guardActiveStart, guardActiveEnd);
        //
        // Generate a count
        //
        final ApplyType countCheck = generateCountCheck(limit, timeWindow, timeUnits);
        //
        // Now combine into an And
        //
        ApplyType applyAnd = new ApplyType();
        applyAnd.setDescription("return true if time range and count checks are true.");
        applyAnd.setFunctionId(XACML3.ID_FUNCTION_AND.stringValue());
        applyAnd.getExpression().add(new ObjectFactory().createApply(timeRange));
        applyAnd.getExpression().add(new ObjectFactory().createApply(countCheck));

        //
        // Create our condition
        //
        final ConditionType condition = new ConditionType();
        condition.setExpression(new ObjectFactory().createApply(applyAnd));

        //
        // Now we can create our rule
        //
        RuleType permit = new RuleType();
        permit.setDescription("Default is to PERMIT if the policy matches.");
        permit.setRuleId(policyName + ":rule");
        permit.setEffect(EffectType.PERMIT);
        permit.setTarget(new TargetType());
        //
        // Add the condition
        //
        permit.setCondition(condition);
        //
        // TODO Add the advice - Is the request id needed to be returned?
        //
        // permit.setAdviceExpressions(adviceExpressions);
        //
        // Done
        //
        return permit;
    }

    private static RuleType generateMinMaxPermit(String policyName, Map<String, Object> properties) {
        //
        // Get the properties that are common among guards
        //
        String guardActiveStart = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_START)) {
            guardActiveStart = properties.get(FIELD_GUARD_ACTIVE_START).toString();
        }
        String guardActiveEnd = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_END)) {
            guardActiveEnd = properties.get(FIELD_GUARD_ACTIVE_END).toString();
        }
        //
        // Generate the time in range
        //
        final ApplyType timeRange = generateTimeInRange(guardActiveStart, guardActiveEnd);
        //
        // See if its possible to generate a count
        //
        Integer min = null;
        if (properties.containsKey("min")) {
            min = parseInteger(properties.get("min").toString());
        }
        Integer max = null;
        if (properties.containsKey("max")) {
            max = parseInteger(properties.get("max").toString());
        }
        final ApplyType minApply = generateMinCheck(min);
        final ApplyType maxApply = generateMaxCheck(max);
        //
        // Make sure we have at least something to check here,
        // otherwise there really is no point to this policy.
        //
        if (timeRange == null && minApply == null && maxApply == null) {
            return null;
        }
        //
        // Create our rule
        //
        RuleType permit = new RuleType();
        permit.setDescription("Default is to PERMIT if the policy matches.");
        permit.setRuleId(policyName + ":rule");
        permit.setEffect(EffectType.PERMIT);
        permit.setTarget(new TargetType());
        //
        // Create our condition
        //
        final ConditionType condition = new ConditionType();
        //
        // Check if we have all the fields (this can be a little
        // ugly) but the ultimate goal is to simplify the policy
        // condition to only check for necessary attributes.
        //
        ObjectFactory factory = new ObjectFactory();
        if (timeRange != null && minApply != null && maxApply != null) {
            //
            // All 3 must apply
            //
            ApplyType applyAnd = new ApplyType();
            applyAnd.setDescription("return true if all the apply's are true.");
            applyAnd.setFunctionId(XACML3.ID_FUNCTION_AND.stringValue());
            applyAnd.getExpression().add(factory.createApply(timeRange));
            applyAnd.getExpression().add(factory.createApply(minApply));
            applyAnd.getExpression().add(factory.createApply(maxApply));
            //
            // Add into the condition
            //
            condition.setExpression(factory.createApply(applyAnd));
        } else {
            //
            // At least one of these applies is null. We need at least
            // two to require the And apply. Otherwise there is no need
            // for an outer And apply as the single condition can work
            // on its own.
            //
            if (timeRange != null && minApply == null && maxApply == null) {
                //
                // Only the time range check is necessary
                //
                condition.setExpression(factory.createApply(timeRange));
            } else if (timeRange == null && minApply != null && maxApply == null) {
                //
                // Only the min check is necessary
                //
                condition.setExpression(factory.createApply(minApply));
            } else if (timeRange == null && minApply == null) {
                //
                // Only the max check is necessary
                //
                condition.setExpression(factory.createApply(maxApply));
            } else {
                //
                // Ok we will need an outer And and have at least the
                // time range and either min or max check
                //
                ApplyType applyAnd = new ApplyType();
                applyAnd.setDescription("return true if all the apply's are true.");
                applyAnd.setFunctionId(XACML3.ID_FUNCTION_AND.stringValue());
                if (timeRange != null) {
                    applyAnd.getExpression().add(factory.createApply(timeRange));
                }
                if (minApply != null) {
                    applyAnd.getExpression().add(factory.createApply(minApply));
                }
                if (maxApply != null) {
                    applyAnd.getExpression().add(factory.createApply(maxApply));
                }
                //
                // Add into the condition
                //
                condition.setExpression(factory.createApply(applyAnd));
            }
        }
        //
        // Add the condition
        //
        permit.setCondition(condition);
        //
        // TODO Add the advice - Is the request id needed to be returned?
        //
        // permit.setAdviceExpressions(adviceExpressions);
        //
        // Done
        //
        return permit;
    }

    private static RuleType generateBlacklistPermit(String policyName, Map<String, Object> properties) {
        //
        // Generate target
        //
        if (! properties.containsKey(FIELD_TARGET)) {
            LOGGER.error("Missing target for blacklist policy");
            return null;
        }
        final ApplyType targetApply = generateTargetApply(properties.get(FIELD_TARGET));
        //
        // Get the properties that are common among guards
        //
        String guardActiveStart = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_START)) {
            guardActiveStart = properties.get(FIELD_GUARD_ACTIVE_START).toString();
        }
        String guardActiveEnd = null;
        if (properties.containsKey(FIELD_GUARD_ACTIVE_END)) {
            guardActiveEnd = properties.get(FIELD_GUARD_ACTIVE_END).toString();
        }
        //
        // Generate the time in range
        //
        final ApplyType timeRange = generateTimeInRange(guardActiveStart, guardActiveEnd);
        //
        // Create our rule
        //
        RuleType permit = new RuleType();
        permit.setDescription("Default is to PERMIT if the policy matches.");
        permit.setRuleId(policyName + ":rule");
        permit.setEffect(EffectType.PERMIT);
        permit.setTarget(new TargetType());
        //
        // Create our condition
        //
        ObjectFactory factory = new ObjectFactory();
        ApplyType innerApply;
        if (timeRange != null) {
            ApplyType applyAnd = new ApplyType();
            applyAnd.setDescription("Combine the timeRange with target to create AND");
            applyAnd.setFunctionId(XACML3.ID_FUNCTION_AND.stringValue());
            applyAnd.getExpression().add(factory.createApply(timeRange));
            applyAnd.getExpression().add(factory.createApply(targetApply));
            //
            // Now we need to NOT this so the permit happens
            //
            ApplyType applyNot = new ApplyType();
            applyNot.setDescription("This should be false for a  permit.");
            applyNot.setFunctionId(XACML3.ID_FUNCTION_NOT.stringValue());
            applyNot.getExpression().add(factory.createApply(applyAnd));
            innerApply = applyNot;
        } else {
            //
            // Just the target is needed
            //
            ApplyType applyNot = new ApplyType();
            applyNot.setDescription("This should be false for a  permit.");
            applyNot.setFunctionId(XACML3.ID_FUNCTION_NOT.stringValue());
            applyNot.getExpression().add(factory.createApply(targetApply));
            innerApply = applyNot;
        }
        //
        // Create our condition
        //
        final ConditionType condition = new ConditionType();
        //
        // Add into the condition
        //
        condition.setExpression(factory.createApply(innerApply));
        //
        // Add the condition
        //
        permit.setCondition(condition);
        return permit;
    }

    private static ApplyType generateTimeInRange(String start, String end) {
        if (start == null || end == null) {
            LOGGER.warn("Missing time range start {} end {}", start, end);
            return null;
        }
        if (start.isEmpty() || end.isEmpty()) {
            LOGGER.warn("Empty time range start {} end {}", start, end);
            return null;
        }

        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(XACML3.ID_ENVIRONMENT_CURRENT_TIME.stringValue());
        designator.setCategory(XACML3.ID_ATTRIBUTE_CATEGORY_ENVIRONMENT.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_TIME.stringValue());

        AttributeValueType valueStart = new AttributeValueType();
        valueStart.setDataType(XACML3.ID_DATATYPE_TIME.stringValue());
        valueStart.getContent().add(start);

        AttributeValueType valueEnd = new AttributeValueType();
        valueEnd.setDataType(XACML3.ID_DATATYPE_TIME.stringValue());
        valueEnd.getContent().add(end);

        ObjectFactory factory = new ObjectFactory();

        ApplyType applyOneAndOnly = new ApplyType();
        applyOneAndOnly.setDescription("Unbag the current time");
        applyOneAndOnly.setFunctionId(XACML3.ID_FUNCTION_TIME_ONE_AND_ONLY.stringValue());
        applyOneAndOnly.getExpression().add(factory.createAttributeDesignator(designator));

        ApplyType applyTimeInRange = new ApplyType();
        applyTimeInRange.setDescription("return true if current time is in range.");
        applyTimeInRange.setFunctionId(XACML3.ID_FUNCTION_TIME_IN_RANGE.stringValue());
        applyTimeInRange.getExpression().add(factory.createApply(applyOneAndOnly));
        applyTimeInRange.getExpression().add(factory.createAttributeValue(valueStart));
        applyTimeInRange.getExpression().add(factory.createAttributeValue(valueEnd));

        return applyTimeInRange;
    }

    private static ApplyType generateCountCheck(Integer limit, String timeWindow, String timeUnits) {
        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(ToscaDictionary.ID_RESOURCE_GUARD_OPERATIONCOUNT.stringValue());
        designator.setCategory(XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        // Setup issuer
        //
        String issuer = ToscaDictionary.GUARD_ISSUER_PREFIX
            + CountRecentOperationsPip.ISSUER_NAME
            + ":tw:" + timeWindow + ":" + timeUnits;
        designator.setIssuer(issuer);

        AttributeValueType valueLimit = new AttributeValueType();
        valueLimit.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        // Yes really use toString(), the marshaller will
        // throw an exception if this is an integer object
        // and not a string.
        //
        valueLimit.getContent().add(limit.toString());

        ObjectFactory factory = new ObjectFactory();

        ApplyType applyOneAndOnly = new ApplyType();
        applyOneAndOnly.setDescription("Unbag the limit");
        applyOneAndOnly.setFunctionId(XACML3.ID_FUNCTION_INTEGER_ONE_AND_ONLY.stringValue());
        applyOneAndOnly.getExpression().add(factory.createAttributeDesignator(designator));

        ApplyType applyLessThan = new ApplyType();
        applyLessThan.setDescription("return true if current count is less than.");
        applyLessThan.setFunctionId(XACML3.ID_FUNCTION_INTEGER_LESS_THAN.stringValue());
        applyLessThan.getExpression().add(factory.createApply(applyOneAndOnly));
        applyLessThan.getExpression().add(factory.createAttributeValue(valueLimit));

        return applyLessThan;
    }

    private static ApplyType generateMinCheck(Integer min) {
        if (min == null) {
            return null;
        }
        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(ToscaDictionary.ID_RESOURCE_GUARD_VFCOUNT.stringValue());
        designator.setCategory(XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        //
        //
        AttributeValueType valueLimit = new AttributeValueType();
        valueLimit.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        // Yes really use toString(), the marshaller will
        // throw an exception if this is an integer object
        // and not a string.
        //
        valueLimit.getContent().add(min.toString());
        ObjectFactory factory = new ObjectFactory();

        ApplyType applyOneAndOnly = new ApplyType();
        applyOneAndOnly.setDescription("Unbag the min");
        applyOneAndOnly.setFunctionId(XACML3.ID_FUNCTION_INTEGER_ONE_AND_ONLY.stringValue());
        applyOneAndOnly.getExpression().add(factory.createAttributeDesignator(designator));

        ApplyType applyGreaterThanEqual = new ApplyType();
        applyGreaterThanEqual.setDescription("return true if current count is greater than or equal.");
        applyGreaterThanEqual.setFunctionId(XACML3.ID_FUNCTION_INTEGER_GREATER_THAN_OR_EQUAL.stringValue());
        applyGreaterThanEqual.getExpression().add(factory.createApply(applyOneAndOnly));
        applyGreaterThanEqual.getExpression().add(factory.createAttributeValue(valueLimit));

        return applyGreaterThanEqual;
    }

    private static ApplyType generateMaxCheck(Integer max) {
        if (max == null) {
            return null;
        }
        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(ToscaDictionary.ID_RESOURCE_GUARD_VFCOUNT.stringValue());
        designator.setCategory(XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        //
        //
        AttributeValueType valueLimit = new AttributeValueType();
        valueLimit.setDataType(XACML3.ID_DATATYPE_INTEGER.stringValue());
        //
        // Yes really use toString(), the marshaller will
        // throw an exception if this is an integer object
        // and not a string.
        //
        valueLimit.getContent().add(max.toString());
        ObjectFactory factory = new ObjectFactory();

        ApplyType applyOneAndOnly = new ApplyType();
        applyOneAndOnly.setDescription("Unbag the min");
        applyOneAndOnly.setFunctionId(XACML3.ID_FUNCTION_INTEGER_ONE_AND_ONLY.stringValue());
        applyOneAndOnly.getExpression().add(factory.createAttributeDesignator(designator));

        ApplyType applyLessThanEqual = new ApplyType();
        applyLessThanEqual.setDescription("return true if current count is less than or equal.");
        applyLessThanEqual.setFunctionId(XACML3.ID_FUNCTION_INTEGER_LESS_THAN_OR_EQUAL.stringValue());
        applyLessThanEqual.getExpression().add(factory.createApply(applyOneAndOnly));
        applyLessThanEqual.getExpression().add(factory.createAttributeValue(valueLimit));

        return applyLessThanEqual;
    }

    @SuppressWarnings("unchecked")
    private static ApplyType generateTargetApply(Object targetObject) {
        ObjectFactory factory = new ObjectFactory();
        //
        // Create a bag of values
        //
        ApplyType applyStringBag = new ApplyType();
        applyStringBag.setDescription("Bag the target values");
        applyStringBag.setFunctionId(XACML3.ID_FUNCTION_STRING_BAG.stringValue());
        if (targetObject instanceof Collection) {
            for (Object target : ((Collection<Object>) targetObject)) {
                if (! (target instanceof String)) {
                    LOGGER.error("Collection of unsupported objects {}", target.getClass());
                    return null;
                }
                AttributeValueType value = new AttributeValueType();
                value.setDataType(XACML3.ID_DATATYPE_STRING.stringValue());
                value.getContent().add(target.toString());
                applyStringBag.getExpression().add(factory.createAttributeValue(value));
            }
        } else if (targetObject instanceof String) {
            AttributeValueType value = new AttributeValueType();
            value.setDataType(XACML3.ID_DATATYPE_STRING.stringValue());
            value.getContent().add(targetObject.toString());
            applyStringBag.getExpression().add(factory.createAttributeValue(value));
        } else {
            LOGGER.warn("Unsupported object for target {}", targetObject.getClass());
            return null;
        }
        //
        // Create our designator
        //
        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(ToscaDictionary.ID_RESOURCE_GUARD_TARGETID.stringValue());
        designator.setCategory(XACML3.ID_ATTRIBUTE_CATEGORY_RESOURCE.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_STRING.stringValue());
        //
        // Create apply for our AnyOf
        //
        ApplyType applyAnyOf = new ApplyType();
        applyAnyOf.setDescription("Find designator as anyof the possible values");
        applyAnyOf.setFunctionId(XACML3.ID_FUNCTION_ANY_OF.stringValue());
        applyAnyOf.getExpression().add(factory.createAttributeDesignator(designator));
        applyAnyOf.getExpression().add(factory.createApply(applyStringBag));
        return applyAnyOf;
    }

    private static Integer parseInteger(String strInteger) {
        Integer theInt = null;
        try {
            theInt = Integer.parseInt(strInteger);
        } catch (NumberFormatException e) {
            LOGGER.warn("Expecting an integer", e);
            try {
                Double dblLimit = Double.parseDouble(strInteger);
                theInt = dblLimit.intValue();
            } catch (NumberFormatException e1) {
                LOGGER.error("Failed to parse expected integer as a double", e);
                return null;
            }
        }
        return theInt;
    }

    @SuppressWarnings("unused")
    private static AdviceExpressionsType generateRequestIdAdvice() {
        AdviceExpressionType adviceExpression = new AdviceExpressionType();
        adviceExpression.setAppliesTo(EffectType.PERMIT);
        adviceExpression.setAdviceId(ToscaDictionary.ID_ADVICE_GUARD.stringValue());

        AttributeDesignatorType designator = new AttributeDesignatorType();
        designator.setAttributeId(ToscaDictionary.ID_SUBJECT_GUARD_REQUESTID.stringValue());
        designator.setCategory(XACML3.ID_SUBJECT_CATEGORY_ACCESS_SUBJECT.stringValue());
        designator.setDataType(XACML3.ID_DATATYPE_STRING.stringValue());

        AttributeAssignmentExpressionType assignment = new AttributeAssignmentExpressionType();
        assignment.setAttributeId(ToscaDictionary.ID_ADVICE_GUARD_REQUESTID.stringValue());
        assignment.setCategory(XACML3.ID_SUBJECT_CATEGORY_ACCESS_SUBJECT.stringValue());
        assignment.setExpression(new ObjectFactory().createAttributeDesignator(designator));

        adviceExpression.getAttributeAssignmentExpression().add(assignment);

        AdviceExpressionsType adviceExpressions = new AdviceExpressionsType();
        adviceExpressions.getAdviceExpression().add(adviceExpression);

        return adviceExpressions;
    }
}
